package com.calma.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calma.launcher.MainActivity
import com.calma.launcher.data.AppEntry
import com.calma.launcher.data.Apps
import com.calma.launcher.data.Dates
import com.calma.launcher.data.IconLoader
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(activity: MainActivity) {
    val ctx = LocalContext.current
    val nowMs = activity.now.longValue
    val dates = remember(nowMs, Prefs.lang, Prefs.use24h) { Dates(nowMs, Prefs.lang, Prefs.use24h) }
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipePx = with(density) { 70.dp.toPx() }

    var drawerOpen by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableStateOf(-1) }
    var menuOpen by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }

    // Botón de inicio: cierra todo y regresa a la primera pantalla
    val presses = activity.homePresses.intValue
    LaunchedEffect(presses) {
        if (presses > 0) {
            drawerOpen = false
            pickSlot = -1
            menuOpen = false
            widgetPicker = false
            pager.animateScrollToPage(0)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.35f to Color.Transparent,
                    0.6f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Black.copy(alpha = 0.62f),
                )
            )
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -swipePx) drawerOpen = true
                        else if (total > swipePx) expandNotifications(ctx)
                    },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { menuOpen = true })
            }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    if (page == 0) {
                        ClockPage(
                            d = dates,
                            onClock = { openSafe(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                            onDate = {
                                openSafe(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                            },
                        )
                    } else {
                        InfoPage(d = dates, activity = activity, onAddWidget = { widgetPicker = true })
                    }
                }
            }
            PageDots(current = pager.currentPage, count = 2)
            Spacer(Modifier.height(18.dp))
            Dock(
                onLaunch = { Apps.launch(ctx, it) },
                onPick = { slot -> pickSlot = slot; drawerOpen = true },
            )
            Spacer(Modifier.height(34.dp))
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                pickMode = pickSlot >= 0,
                onDismiss = { drawerOpen = false; pickSlot = -1 },
                onChoose = { e ->
                    if (pickSlot >= 0) {
                        Prefs.setDockSlot(pickSlot, e.key)
                        pickSlot = -1
                        drawerOpen = false
                    } else {
                        Apps.launch(ctx, e)
                        scope.launch {
                            kotlinx.coroutines.delay(350)
                            drawerOpen = false
                        }
                    }
                },
            )
        }

        if (menuOpen) {
            HomeMenu(
                activity = activity,
                onDismiss = { menuOpen = false },
                onPickWidget = { menuOpen = false; widgetPicker = true },
            )
        }
        if (widgetPicker) {
            WidgetPicker(
                activity = activity,
                onDismiss = { widgetPicker = false },
                onPick = { info -> widgetPicker = false; activity.addWidget(info) },
            )
        }
    }
}

// ---------------- Pantalla 1: hora y mes ----------------

@Composable
fun ClockPage(d: Dates, onClock: () -> Unit, onDate: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null) { onClock() },
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(d.time, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            if (d.amPm.isNotEmpty()) {
                Text(
                    d.amPm.lowercase(),
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 3.dp, top = 12.dp),
                )
            }
        }
        EchoMonth(
            text = d.month.uppercase(),
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null) { onDate() },
        )
        Text(
            "${d.weekday.uppercase()}, ${d.day}",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.offset(y = (-6).dp),
        )
    }
}

/** El mes grande con dos "ecos" que se desvanecen debajo. */
@Composable
fun EchoMonth(text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val avail = maxWidth.value * 0.84f
        val sizeDp = (avail / (text.length.coerceAtLeast(3) * 0.47f)).coerceIn(44f, 90f)
        val step = sizeDp * 0.74f
        val fontSize = with(density) { sizeDp.dp.toSp() }
        Box(Modifier.fillMaxWidth().height((sizeDp * 1.12f + step * 2).dp)) {
            for (i in 2 downTo 0) {
                val alpha = when (i) { 0 -> 1f; 1 -> 0.42f; else -> 0.2f }
                var m = Modifier.align(Alignment.TopCenter).offset(y = (step * i).dp)
                if (i > 0) {
                    m = m
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                }
                Text(
                    text,
                    color = Color.White.copy(alpha = alpha),
                    fontFamily = Condensed,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    letterSpacing = (sizeDp * 0.03f).sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = m,
                )
            }
        }
    }
}

// ---------------- Pantalla 2: información y widget ----------------

@Composable
fun InfoPage(d: Dates, activity: MainActivity, onAddWidget: () -> Unit) {
    val lang = Prefs.lang
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 34.dp, end = 34.dp, bottom = 18.dp)
        ) {
            Text(d.itIs, color = Color.White.copy(alpha = 0.85f), fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Row {
                Text(d.month, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
                Text(".", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
            }
            Text(
                "${d.weekday} ${d.day}, ${d.year}",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-4).dp),
            )
            Box(
                Modifier
                    .padding(top = 4.dp, bottom = 8.dp)
                    .width(34.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.6f))
            )
            Text(
                Weather.sentence(lang),
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = Poppins,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.widthIn(max = 250.dp),
            )
            Spacer(Modifier.height(16.dp))
            WidgetSlot(activity, onAddWidget)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Prefs.hasWeather) {
                    Icon(
                        Weather.icon(Prefs.wCode, Prefs.wDay),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                } else {
                    Text("✳ ", color = Accent, fontSize = 11.sp)
                }
                Text(d.greeting, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp)
            }
        }
        Text(
            "●  ${d.hour2}:${d.minute2}${d.amPm}",
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .vertical(),
        )
    }
}

@Composable
fun WidgetSlot(activity: MainActivity, onAddWidget: () -> Unit) {
    val id = Prefs.widgetId
    val info = remember(id, Apps.version) { activity.widgetInfo(id) }
    if (info == null) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .clickable { onAddWidget() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                Text(
                    if (Prefs.lang == "es") "Toca para agregar tu widget de música" else "Tap to add your music widget",
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = Poppins,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        key(id) {
            AndroidView(
                factory = { c ->
                    activity.widgetHost.createView(c, id, info).apply {
                        setPadding(0, 0, 0, 0)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(Prefs.widgetHeight.dp),
            )
        }
    }
}

// ---------------- Puntos y apps de inicio ----------------

@Composable
fun PageDots(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (i in 0 until count) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f))
            )
        }
    }
}

@Composable
fun Dock(onLaunch: (AppEntry) -> Unit, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 4) {
                    DockSlot(row * 4 + col, onLaunch, onPick)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockSlot(slot: Int, onLaunch: (AppEntry) -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val key = Prefs.dock.getOrNull(slot).orEmpty()
    val e = Apps.byKey[key]
    var menu by remember { mutableStateOf(false) }
    val noRipple = remember { MutableInteractionSource() }
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        if (e != null) {
            AppIcon(
                e, 40.dp,
                Modifier.combinedClickable(
                    interactionSource = noRipple,
                    indication = null,
                    onLongClick = { menu = true },
                    onClick = { onLaunch(e) },
                )
            )
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable { onPick(slot) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Cambiar app") }, onClick = { menu = false; onPick(slot) })
            DropdownMenuItem(text = { Text("Quitar de inicio") }, onClick = { menu = false; Prefs.setDockSlot(slot, "") })
            if (e != null) {
                DropdownMenuItem(text = { Text("Información de la app") }, onClick = { menu = false; Apps.openInfo(ctx, e) })
            }
        }
    }
}

@Composable
fun AppIcon(e: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val style = Prefs.iconStyle
    val pack = Prefs.iconPack
    val img by produceState(initialValue = IconLoader.peek(e), e.key, style, pack, Apps.version) {
        value = withContext(Dispatchers.Default) { IconLoader.load(ctx, e, style, pack) }
    }
    Box(modifier.size(size)) {
        img?.let { Image(bitmap = it, contentDescription = e.label, modifier = Modifier.fillMaxSize()) }
    }
}

// ---------------- Utilidades ----------------

fun openSafe(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No hay una app para esto", Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("WrongConstant")
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Exception) {
    }
}
