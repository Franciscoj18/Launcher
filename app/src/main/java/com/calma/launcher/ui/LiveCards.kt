package com.calma.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.calma.launcher.lock.NotifItem

// Tarjetas compartidas por la pantalla de bloqueo y el panel de controles.

/** Tarjeta con la canción que suena y los botones anterior / reproducir / siguiente. */
@Composable
fun MusicCard(onOpen: () -> Unit) {
    val art = Live.art
    val img = remember(art) { art?.asImageBitmap() }
    Row(
        Modifier
            .width(310.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CardFill)
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                Live.title,
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (Live.artist.isNotBlank()) {
                Text(
                    Live.artist,
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = Poppins,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RoundButton(Icons.Filled.SkipPrevious, 34.dp, filled = false) { Live.previous() }
        RoundButton(if (Live.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 40.dp, filled = true) { Live.playPause() }
        RoundButton(Icons.Filled.SkipNext, 34.dp, filled = false) { Live.next() }
    }
}

@Composable
fun RoundButton(icon: ImageVector, size: Dp, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun NotifCard(n: NotifItem, es: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val avatar = if (n.largeIcon != null) rememberIconBitmap(n.largeIcon, tint = false, size = 96) else null
    val small = rememberIconBitmap(n.smallIcon, tint = true, size = 48, fallbackPkg = n.pkg)
    val showContent = Prefs.lockContent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardFill)
            .clickable { onOpen() }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            if (showContent && avatar != null) {
                Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (small != null) {
                Image(small, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.app,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = Poppins,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " · " + ago(n.time, es),
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = Poppins,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            if (showContent) {
                if (n.title.isNotBlank()) {
                    Text(n.title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (n.text.isNotBlank()) {
                    Text(n.text, color = Color.White.copy(alpha = 0.82f), fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(
                    if (es) "Notificación nueva" else "New notification",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = if (es) "Quitar" else "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

fun ago(time: Long, es: Boolean): String {
    val mins = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> if (es) "ahora" else "now"
        mins < 60 -> if (es) "hace $mins min" else "${mins}m ago"
        mins < 24 * 60 -> if (es) "hace ${mins / 60} h" else "${mins / 60}h ago"
        else -> if (es) "hace ${mins / (24 * 60)} d" else "${mins / (24 * 60)}d ago"
    }
}
