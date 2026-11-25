package com.calma.launcher.volume

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calma.launcher.data.Prefs
import com.calma.launcher.ui.Accent
import com.calma.launcher.ui.PanelSurface
import com.calma.launcher.ui.Poppins

/** La barra de volumen de Calma, pegada al borde derecho junto a los botones. */
@Composable
fun VolumeUi(ctl: VolumeController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = ctl.visible,
            enter = slideInHorizontally(tween(180)) { it / 2 } + fadeIn(tween(150)),
            exit = slideOutHorizontally(tween(180)) { it / 2 } + fadeOut(tween(150)),
        ) {
            if (ctl.expanded) Expanded(ctl) else Collapsed(ctl)
        }
    }
}

@Composable
private fun Collapsed(ctl: VolumeController) {
    val v = ctl.version
    val s = ctl.stream
    val level = remember(v, s) { ctl.level(s) }
    Column(
        Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(PanelSurface.copy(alpha = 0.96f))
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSlider(level, streamIcon(s, ctl.ringerMode()), 170.dp) { ctl.setLevel(s, it) }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { ctl.toggleExpanded() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Tune, contentDescription = if (Prefs.lang == "es") "Más volúmenes" else "More volumes", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Expanded(ctl: VolumeController) {
    val es = Prefs.lang == "es"
    val v = ctl.version
    val ringer = remember(v) { ctl.ringerMode() }
    val streams = listOf(
        Triple(AudioManager.STREAM_MUSIC, if (es) "Música" else "Media", Icons.Filled.MusicNote),
        Triple(AudioManager.STREAM_RING, if (es) "Timbre" else "Ring", streamIcon(AudioManager.STREAM_RING, ringer)),
        Triple(AudioManager.STREAM_ALARM, if (es) "Alarma" else "Alarm", Icons.Filled.Alarm),
    )
    Column(
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(PanelSurface.copy(alpha = 0.97f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (es) "Volumen" else "Volume",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            // Sonido → vibrar → silencio
            val ringerIcon = when (ringer) {
                AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
                AudioManager.RINGER_MODE_SILENT -> Icons.Filled.NotificationsOff
                else -> Icons.Filled.NotificationsActive
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (ringer == AudioManager.RINGER_MODE_NORMAL) Color.White.copy(alpha = 0.12f) else Accent)
                    .clickable { ctl.cycleRinger() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ringerIcon,
                    contentDescription = if (es) "Sonido, vibrar o silencio" else "Ring, vibrate or silent",
                    tint = if (ringer == AudioManager.RINGER_MODE_NORMAL) Color.White else PanelSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((s, label, icon) in streams) {
                val level = remember(v, s) { ctl.level(s) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VSlider(level, icon, 186.dp) { ctl.setLevel(s, it) }
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun streamIcon(stream: Int, ringer: Int): ImageVector = when (stream) {
    AudioManager.STREAM_RING -> when (ringer) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.Filled.NotificationsOff
        else -> Icons.Filled.NotificationsActive
    }
    AudioManager.STREAM_ALARM -> Icons.Filled.Alarm
    else -> Icons.Filled.MusicNote
}

/** Barra vertical: se arrastra o se toca para cambiar el volumen; se llena desde abajo. */
@Composable
private fun VSlider(value: Float, icon: ImageVector, height: Dp, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val change by rememberUpdatedState(onChange)
    val shown by animateFloatAsState((drag ?: value).coerceIn(0f, 1f), tween(90), label = "vol")
    Box(
        Modifier
            .width(46.dp)
            .height(height)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        val v = (1f - off.y / size.height).coerceIn(0f, 1f)
                        drag = v
                        change(v)
                    },
                    onDragEnd = { drag = null },
                    onDragCancel = { drag = null },
                    onVerticalDrag = { c, _ ->
                        c.consume()
                        val v = (1f - c.position.y / size.height).coerceIn(0f, 1f)
                        drag = v
                        change(v)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { off -> change((1f - off.y / size.height).coerceIn(0f, 1f)) }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(shown)
                .background(Accent)
        )
        Icon(
            icon,
            contentDescription = null,
            tint = if (shown > 0.13f) PanelSurface else Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .size(20.dp),
        )
    }
}
