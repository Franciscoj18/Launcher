package com.calma.launcher.island

import android.graphics.drawable.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.calma.launcher.data.Prefs
import com.calma.launcher.lock.Live
import com.calma.launcher.ui.Accent
import com.calma.launcher.ui.Poppins
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)

/** La isla: una pastilla negra alrededor de la cámara que crece según lo que pase. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandUi(ctl: IslandController) {
    val g = ctl.geometry
    val ctx = LocalContext.current
    var expandedState by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Island.outsideTaps) { expandedState = false }
    LaunchedEffect(expandedState) {
        if (expandedState) {
            delay(8000)
            expandedState = false
        }
    }

    val alert = Island.alert
    val item = Island.items.firstOrNull()
    val music = Island.has("music") && Live.hasMedia
    val dots = Island.has("privacy") && (Island.camera || Island.mic)
    val hidden = !Prefs.islandEnabled || Island.panelOpen || (Island.landscape && Prefs.islandHideLandscape)
    val locked = Island.locked(ctx)

    val expanded = expandedState && (item != null || music)

    val showSomething = !hidden && (alert != null || item != null || music || dots || Prefs.islandIdle)
    if (!showSomething) {
        Box(Modifier.size(1.dp))
        return
    }

    val big = alert != null || expanded
    val shape = if (big) RoundedCornerShape(28.dp) else RoundedCornerShape(50)
    Box(
        Modifier
            .padding(top = 0.dp)
            .animateContentSize(spring(dampingRatio = 0.72f, stiffness = 420f))
            .clip(shape)
            .background(Color.Black)
            .combinedClickable(
                onClick = {
                    when {
                        alert != null && alert.kind == AlertKind.CODE -> Unit
                        alert != null -> {
                            if (alert.intent != null || alert.pkg.isNotEmpty()) ctl.open(alert.intent, alert.pkg)
                            Island.dismissAlert()
                        }
                        expanded -> {
                            if (item != null) ctl.open(item.intent, item.pkg)
                            else ctl.open(Live.playerIntent(), Live.playerPackage())
                            expandedState = false
                        }
                        item != null || music -> expandedState = true
                    }
                },
                onLongClick = { expandedState = false; ctl.openPanel() },
            ),
    ) {
        when {
            alert != null -> AlertView(alert, g, locked, onCopy = { ctl.copy(it) })
            expanded && item != null -> ExpandedItem(item, g, now)
            expanded && music -> ExpandedMusic(g, now)
            item != null -> Collapsed(g, dots, left = { ItemLeft(item) }, right = { ItemRight(item, now) })
            music -> Collapsed(g, dots, left = { MusicArt(22.dp) }, right = { Equalizer() })
            else -> Collapsed(g, dots, left = {}, right = {})
        }
    }
}

// ---------- Pastilla encogida: algo a la izquierda, la cámara al centro, algo a la derecha ----------

@Composable
private fun Collapsed(g: IslandGeometry, dots: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(
        Modifier
            .height(g.pillHeight.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 22.dp), contentAlignment = Alignment.CenterStart) { left() }
        Spacer(Modifier.width(g.cameraGap.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            right()
            if (dots) {
                Spacer(Modifier.width(6.dp))
                PrivacyDots()
            }
        }
        if (!dots) Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun PrivacyDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (Island.camera) Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
        if (Island.mic) Box(Modifier.size(7.dp).clip(CircleShape).background(Orange))
    }
}

@Composable
private fun ItemLeft(item: IslandItem) {
    when (item.kind) {
        Kind.CALL -> Icon(Icons.Filled.Call, null, tint = Green, modifier = Modifier.size(18.dp))
        Kind.TIMER -> Icon(Icons.Filled.Timer, null, tint = Orange, modifier = Modifier.size(18.dp))
        Kind.NAV -> {
            val large = rememberIcon(item.largeIcon, tint = false)
            if (large != null) Image(large, null, modifier = Modifier.size(20.dp))
            else Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        else -> AppGlyph(item.smallIcon, 18.dp)
    }
}

@Composable
private fun ItemRight(item: IslandItem, now: Long) {
    val text = when (item.kind) {
        Kind.CALL, Kind.TIMER -> if (item.chronoBase > 0) chrono(item, now) else item.title.take(10)
        Kind.NAV -> item.title.take(12)
        Kind.PROGRESS -> if (item.progress >= 0f) "${(item.progress * 100).roundToInt()}%" else ""
        Kind.RIDE -> item.title.take(14)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.kind == Kind.PROGRESS) {
            Ring(item.progress, 16.dp)
            if (text.isNotEmpty()) Spacer(Modifier.width(5.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                color = when (item.kind) { Kind.CALL -> Green; Kind.TIMER -> Orange; else -> Color.White },
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

private fun chrono(item: IslandItem, now: Long): String {
    val ms = if (item.countDown) (item.chronoBase - now) else (now - item.chronoBase)
    val total = (ms / 1000L).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ---------- Isla agrandada (al tocarla) ----------

@Composable
private fun ExpandedItem(item: IslandItem, g: IslandGeometry, now: Long) {
    val es = Prefs.lang == "es"
    Column(Modifier.width(g.expandedWidth.dp).padding(start = 18.dp, end = 18.dp, top = (g.pillHeight + 6).dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val large = rememberIcon(item.largeIcon, tint = false)
                if (large != null) Image(large, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else ItemLeft(item)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.app, color = Color.White.copy(alpha = 0.55f), fontFamily = Poppins, fontSize = 11.sp, maxLines = 1)
                Text(item.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.text.isNotBlank()) {
                    Text(item.text, color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if ((item.kind == Kind.CALL || item.kind == Kind.TIMER) && item.chronoBase > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    chrono(item, now),
                    color = if (item.kind == Kind.CALL) Green else Orange,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        if (item.progress >= 0f) {
            Spacer(Modifier.height(12.dp))
            Bar(item.progress)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (item.kind) {
                Kind.CALL -> if (es) "Toca para volver a la llamada" else "Tap to return to the call"
                else -> if (es) "Toca para abrir · mantén presionado para el panel" else "Tap to open · hold for the panel"
            },
            color = Color.White.copy(alpha = 0.45f),
            fontFamily = Poppins,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ExpandedMusic(g: IslandGeometry, now: Long) {
    val pos = remember(now, Live.positionMs, Live.playing) { Live.currentPosition() }
    val dur = Live.durationMs
    Column(Modifier.width(g.expandedWidth.dp).padding(start = 18.dp, end = 18.dp, top = (g.pillHeight + 6).dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MusicArt(52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Live.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Live.artist, color = Color.White.copy(alpha = 0.65f), fontFamily = Poppins, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Equalizer()
        }
        if (dur > 0) {
            Spacer(Modifier.height(12.dp))
            Bar((pos.toFloat() / dur).coerceIn(0f, 1f))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(mmss(pos), color = Color.White.copy(alpha = 0.5f), fontFamily = Poppins, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("-" + mmss(dur - pos), color = Color.White.copy(alpha = 0.5f), fontFamily = Poppins, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IslandButton(Icons.Filled.SkipPrevious, 40.dp) { Live.previous() }
            IslandButton(if (Live.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 48.dp) { Live.playPause() }
            IslandButton(Icons.Filled.SkipNext, 40.dp) { Live.next() }
        }
    }
}

private fun mmss(ms: Long): String {
    val t = (ms / 1000L).coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun IslandButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

// ---------- Avisos que aparecen solos ----------

@Composable
private fun AlertView(a: IslandAlert, g: IslandGeometry, locked: Boolean, onCopy: (String) -> Unit) {
    val es = Prefs.lang == "es"
    when (a.kind) {
        AlertKind.CODE -> {
            Column(Modifier.width(g.expandedWidth.dp).padding(start = 18.dp, end = 18.dp, top = (g.pillHeight + 4).dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (es) "Código de " else "Code from ") + a.title,
                        color = Color.White.copy(alpha = 0.65f),
                        fontFamily = Poppins,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (locked) "••••••" else a.code.orEmpty(),
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = 4.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (!locked) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Accent)
                                .clickable { a.code?.let(onCopy) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        ) {
                            Text(if (es) "Copiar" else "Copy", color = Color.Black, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
                if (locked) {
                    Text(
                        if (es) "Desbloquea para ver el código" else "Unlock to see the code",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = Poppins,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        AlertKind.MESSAGE -> {
            Row(
                Modifier.width(g.expandedWidth.dp).padding(start = 14.dp, end = 16.dp, top = (g.pillHeight + 2).dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatar = rememberIcon(a.largeIcon, tint = false)
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    if (avatar != null && !locked) Image(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else AppGlyph(a.smallIcon, 18.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (locked) (if (es) "Mensaje nuevo" else "New message") else a.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!locked) Text(a.text, color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        else -> {
            // Avisos cortos: carga, sonido, audífonos, evento
            val icon = when (a.kind) {
                AlertKind.CHARGING -> Icons.Filled.BatteryChargingFull
                AlertKind.HEADPHONES -> Icons.Filled.Headphones
                AlertKind.EVENT -> Icons.Filled.Event
                AlertKind.SOUND -> when (a.level) {
                    0 -> Icons.Filled.VolumeOff
                    1 -> Icons.Filled.Vibration
                    else -> Icons.Filled.VolumeUp
                }
                else -> Icons.Filled.NotificationsActive
            }
            val tint = when (a.kind) {
                AlertKind.CHARGING -> Green
                AlertKind.SOUND -> if (a.level == 2) Color.White else Orange
                else -> Accent
            }
            Row(
                Modifier
                    .height((g.pillHeight + 6).dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(a.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                Spacer(Modifier.width(g.cameraGap.dp))
                if (a.kind == AlertKind.CHARGING && a.level >= 0) {
                    Text("${a.level}%", color = Green, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (a.text.isNotEmpty()) Spacer(Modifier.width(6.dp))
                }
                if (a.text.isNotEmpty()) {
                    Text(a.text, color = Color.White.copy(alpha = 0.7f), fontFamily = Poppins, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                }
            }
        }
    }
}

// ---------- Piezas pequeñas ----------

@Composable
private fun MusicArt(size: Dp) {
    val art = Live.art
    val img = remember(art) { art?.asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** Barritas que bailan mientras suena la música. */
@Composable
private fun Equalizer() {
    val t = rememberInfiniteTransition(label = "eq")
    val playing = Live.playing
    val color = Live.musicColor ?: Accent
    val h = listOf(
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a"),
        t.animateFloat(0.8f, 0.25f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "b"),
        t.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "c"),
        t.animateFloat(0.9f, 0.35f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "d"),
    )
    Canvas(Modifier.size(width = 20.dp, height = 16.dp)) {
        val w = size.width / 7f
        for (i in 0 until 4) {
            val v = if (playing) h[i].value else 0.2f
            val bh = size.height * v
            val x = w * (i * 2) + w / 2
            drawLine(color, Offset(x, size.height), Offset(x, size.height - bh), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Ring(progress: Float, size: Dp) {
    val color = Accent
    val t = rememberInfiniteTransition(label = "ring")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100)), label = "spin")
    Canvas(Modifier.size(size)) {
        val sw = 2.5.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.2f), style = Stroke(sw))
        if (progress >= 0f) {
            drawArc(color, -90f, 360f * progress, false, style = Stroke(sw, cap = StrokeCap.Round))
        } else {
            drawArc(color, spin, 90f, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Bar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.01f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
    }
}

@Composable
private fun AppGlyph(icon: Icon?, size: Dp) {
    val img = rememberIcon(icon, tint = true)
    if (img != null) Image(img, null, modifier = Modifier.size(size))
    else Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
}

@Composable
private fun rememberIcon(icon: Icon?, tint: Boolean): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(icon, tint) {
        try {
            val d = icon?.loadDrawable(ctx) ?: return@remember null
            if (tint) d.mutate().setTint(android.graphics.Color.WHITE)
            d.toBitmap(64, 64).asImageBitmap()
        } catch (e: Exception) { null }
    }
}
