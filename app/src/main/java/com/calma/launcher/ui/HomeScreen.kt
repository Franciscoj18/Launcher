package com.calma.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.calma.launcher.MainActivity
import com.calma.launcher.data.AppEntry
import com.calma.launcher.data.Apps
import com.calma.launcher.data.Dates
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.IconLoader
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.lock.Live
import com.calma.launcher.panel.ControlPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

const val PAGES = 3

@Composable
fun HomeScreen(activity: MainActivity) {
    val ctx = LocalContext.current
    val nowMs = activity.now.longValue
    val dates = remember(nowMs, Prefs.lang, Prefs.use24h) { Dates(nowMs, Prefs.lang, Prefs.use24h) }
    val pager = rememberPagerState { PAGES }
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

    // Animación de entrada al volver al inicio
    val enter = remember { Animatable(1f) }
    val resumes = activity.resumes.intValue
    LaunchedEffect(resumes) {
        if (Prefs.enterAnim && resumes > 0 && !drawerOpen) {
            enter.snapTo(0f)
            enter.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
        } else {
            enter.snapTo(1f)
        }
    }
    val enterValue: () -> Float = { enter.value }

    // El fondo del sistema se desplaza al cambiar de pantalla (si el fondo lo permite)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }
            .collect { activity.setWallpaperScroll(it / (PAGES - 1)) }
    }

    val drawerP = animateFloatAsState(if (drawerOpen) 1f else 0f, tween(320), label = "drawer")

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -swipePx) drawerOpen = true
                        else if (total > swipePx) {
                            if (Prefs.panelHome) activity.panelOpen.value = true else expandNotifications(ctx)
                        }
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
        HomeImageLayer(pager, activity.tilt)
        if (Prefs.dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Prefs.dim)))
        }
        MusicTintLayer(pager)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.35f to Color.Transparent,
                        0.6f to Color.Black.copy(alpha = 0.10f),
                        1f to Color.Black.copy(alpha = 0.58f),
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawerP.value
                    alpha = 1f - p
                    scaleX = 1f - 0.06f * p
                    scaleY = 1f - 0.06f * p
                }
                .systemBarsPadding()
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 2,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    when (page) {
                        0 -> ClockPage(
                            d = dates,
                            enter = enterValue,
                            onClock = { openSafe(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                            onDate = {
                                openSafe(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                            },
                        )
                        1 -> InfoPage(d = dates, activity = activity, enter = enterValue, onAddWidget = { widgetPicker = true })
                        else -> PanelPage(d = dates, activity = activity, enter = enterValue)
                    }
                }
            }
            PageDots(current = pager.currentPage, count = PAGES)
            Spacer(Modifier.height(18.dp))
            Dock(
                enter = enterValue,
                onLaunch = { Apps.launch(ctx, it) },
                onPick = { slot -> pickSlot = slot; drawerOpen = true },
            )
            Spacer(Modifier.height(if (Prefs.showLabels) 22.dp else 34.dp))
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

        // Panel de controles
        val panelOpen = activity.panelOpen.value
        BackHandler(enabled = panelOpen) { activity.closePanel() }
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInVertically { -it / 4 } + fadeIn(),
            exit = slideOutVertically { -it / 4 } + fadeOut(),
        ) {
            ControlPanel(host = activity)
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

/** Tu imagen de inicio, un poco más grande que la pantalla para poder moverse. */
@Composable
private fun HomeImageLayer(pager: PagerState, tilt: Tilt) {
    val img = HomeWallpaper.image ?: return
    val bmp = remember(img) { img.asImageBitmap() }
    val density = LocalDensity.current
    val tiltPx = with(density) { 12.dp.toPx() }
    val pagePx = with(density) { 18.dp.toPx() }
    Image(
        bmp,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.18f
                scaleY = 1.18f
                val pos = pager.currentPage + pager.currentPageOffsetFraction
                translationX = -(pos - 1f) * pagePx + tilt.x.floatValue * tiltPx
                translationY = tilt.y.floatValue * tiltPx
            },
    )
}

/** Brillo del color de la portada que suena, visible en la segunda pantalla. */
@Composable
private fun MusicTintLayer(pager: PagerState) {
    val target = if (Prefs.musicTint && Live.playing) Live.musicColor else null
    val c by animateColorAsState(target ?: Color.Transparent, tween(900), label = "tint")
    if (c.alpha < 0.01f) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pos = pager.currentPage + pager.currentPageOffsetFraction
                alpha = (1f - abs(pos - 1f)).coerceIn(0f, 1f)
            }
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to c.copy(alpha = c.alpha * 0.22f),
                    0.72f to c.copy(alpha = c.alpha * 0.45f),
                    1f to c.copy(alpha = c.alpha * 0.12f),
                )
            )
    )
}

/** Hace aparecer un elemento con un desvanecido y un pequeño desplazamiento. */
fun Modifier.appear(anim: () -> Float, index: Int): Modifier = this.graphicsLayer {
    val v = ((anim() - index * 0.09f) / 0.55f).coerceIn(0f, 1f)
    alpha = v
    translationY = (1f - v) * 22.dp.toPx()
}

// ---------------- Pantalla 2: información y widget ----------------

@Composable
fun InfoPage(d: Dates, activity: MainActivity, enter: () -> Float, onAddWidget: () -> Unit) {
    val lang = Prefs.lang
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 34.dp, end = 34.dp, bottom = 18.dp)
        ) {
            Text(
                d.itIs,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.appear(enter, 0),
            )
            Row(Modifier.appear(enter, 1)) {
                Text(d.month, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
                Text(".", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
            }
            Text(
                "${d.weekday} ${d.day}, ${d.year}",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-4).dp).appear(enter, 2),
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
                modifier = Modifier.widthIn(max = 250.dp).appear(enter, 3),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.appear(enter, 4)) { WidgetSlot(activity, onAddWidget) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(enter, 5)) {
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
                .background(CardFill)
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
            val w by animateFloatAsState(if (i == current) 12f else 5f, tween(250), label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .width(w.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f))
            )
        }
    }
}

@Composable
fun Dock(enter: () -> Float, onLaunch: (AppEntry) -> Unit, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 34.dp),
        verticalArrangement = Arrangement.spacedBy(if (Prefs.showLabels) 10.dp else 20.dp),
    ) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 4) {
                    val slot = row * 4 + col
                    Box(Modifier.appear(enter, 6 + row)) {
                        DockSlot(slot, onLaunch, onPick)
                    }
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.82f else 1f,
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val labels = Prefs.showLabels
    Column(
        Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            if (e != null) {
                AppIcon(
                    e, 40.dp,
                    Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .combinedClickable(
                            interactionSource = source,
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
        if (labels) {
            Text(
                e?.label ?: "",
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = Poppins,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
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
