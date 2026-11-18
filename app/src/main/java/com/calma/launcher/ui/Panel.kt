package com.calma.launcher.ui

import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.CalendarContract
import android.content.ContentUris
import com.calma.launcher.MainActivity
import com.calma.launcher.data.Dates
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Pantalla 3: resumen del día. */
@Composable
fun PanelPage(d: Dates, activity: MainActivity, enter: () -> Float) {
    val ctx = LocalContext.current
    val es = Prefs.lang == "es"
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.appear(enter, 0)) {
                Text(
                    if (es) "resumen" else "overview",
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Row {
                    Text(if (es) "hoy" else "today", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
                    Text(".", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
                }
            }

            // Próxima alarma
            val alarm = DeviceInfo.nextAlarm
            InfoRow(
                icon = Icons.Filled.Alarm,
                title = if (es) "Próxima alarma" else "Next alarm",
                value = if (alarm > 0) whenText(alarm, false, d) else if (es) "Sin alarmas" else "No alarms",
                modifier = Modifier.appear(enter, 1),
            ) { openSafe(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }

            // Próximo evento
            val ev = DeviceInfo.nextEvent
            InfoRow(
                icon = Icons.Filled.Event,
                title = when {
                    !DeviceInfo.calendarAllowed -> if (es) "Calendario" else "Calendar"
                    ev != null -> ev.title
                    else -> if (es) "Calendario" else "Calendar"
                },
                value = when {
                    !DeviceInfo.calendarAllowed -> if (es) "Toca para ver tus eventos aquí" else "Tap to show your events"
                    ev != null -> whenText(ev.begin, ev.allDay, d)
                    else -> if (es) "Nada en los próximos 7 días" else "Nothing in the next 7 days"
                },
                modifier = Modifier.appear(enter, 2),
            ) {
                if (!DeviceInfo.calendarAllowed) {
                    activity.requestCalendar()
                } else if (ev != null) {
                    val uri = ContentUris.withAppendedId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(), ev.begin)
                    openSafe(ctx, Intent(Intent.ACTION_VIEW, uri))
                } else {
                    openSafe(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                }
            }

            // Clima de la semana
            val days = Weather.days().take(7)
            if (days.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .appear(enter, 3)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val locale = if (es) Locale("es", "MX") else Locale.ENGLISH
                    days.forEachIndexed { i, day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val name = if (i == 0) (if (es) "hoy" else "now")
                            else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".").take(3)
                            Text(name, color = Color.White.copy(alpha = if (i == 0) 1f else 0.7f), fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Icon(Weather.icon(day.code, true), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("${day.max}°", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("${day.min}°", color = Color.White.copy(alpha = 0.5f), fontFamily = Poppins, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Batería
            val bat = DeviceInfo.battery
            if (bat >= 0) {
                BarRow(
                    title = if (es) "Batería" else "Battery",
                    value = "$bat%" + if (DeviceInfo.charging) (if (es) " · cargando" else " · charging") else "",
                    fraction = bat / 100f,
                    modifier = Modifier.appear(enter, 4),
                ) { openSafe(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            }

            // Almacenamiento
            if (DeviceInfo.storageTotal > 0) {
                BarRow(
                    title = if (es) "Almacenamiento" else "Storage",
                    value = if (es) "${DeviceInfo.gb(DeviceInfo.storageUsed)} de ${DeviceInfo.gb(DeviceInfo.storageTotal)}"
                    else "${DeviceInfo.gb(DeviceInfo.storageUsed)} of ${DeviceInfo.gb(DeviceInfo.storageTotal)}",
                    fraction = DeviceInfo.storageUsed.toFloat() / DeviceInfo.storageTotal,
                    modifier = Modifier.appear(enter, 5),
                ) { openSafe(ctx, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = Color.White.copy(alpha = 0.6f), fontFamily = Poppins, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BarRow(title: String, value: String, fraction: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, color = Color.White.copy(alpha = 0.65f), fontFamily = Poppins, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accent)
            )
        }
    }
}

/** "hoy 7:00", "mañana 9:30", "vie 18:00", "hoy · todo el día". */
fun whenText(ms: Long, allDay: Boolean, d: Dates): String {
    val es = d.lang == "es"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(if (allDay) ZoneId.of("UTC") else zone).toLocalDate()
    val today = LocalDate.now(zone)
    val locale = if (es) Locale("es", "MX") else Locale.ENGLISH
    val dayText = when (date) {
        today -> if (es) "hoy" else "today"
        today.plusDays(1) -> if (es) "mañana" else "tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".") + " " + date.dayOfMonth
    }
    if (allDay) return dayText + if (es) " · todo el día" else " · all day"
    val t = Dates(ms, d.lang, d.use24h)
    return "$dayText ${t.time}${if (t.amPm.isNotEmpty()) " " + t.amPm.lowercase() else ""}"
}
