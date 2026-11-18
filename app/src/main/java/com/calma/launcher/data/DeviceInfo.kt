package com.calma.launcher.data

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class CalEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Batería, almacenamiento, próxima alarma y próximo evento. */
object DeviceInfo {
    var battery by mutableStateOf(-1)
        private set
    var charging by mutableStateOf(false)
        private set
    var storageUsed by mutableStateOf(0L)
        private set
    var storageTotal by mutableStateOf(0L)
        private set
    var nextAlarm by mutableStateOf(0L)
        private set
    var nextEvent by mutableStateOf<CalEvent?>(null)
        private set
    var calendarAllowed by mutableStateOf(false)
        private set

    fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) battery = (level * 100f / scale).roundToInt()
        charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    fun refreshQuick(ctx: Context) {
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            storageTotal = st.totalBytes
            storageUsed = st.totalBytes - st.availableBytes
        } catch (_: Exception) {}
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            nextAlarm = am?.nextAlarmClock?.triggerTime ?: 0L
        } catch (_: Exception) {}
    }

    suspend fun refreshCalendar(ctx: Context) {
        val allowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        calendarAllowed = allowed
        if (!allowed) { nextEvent = null; return }
        val found = withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val begin = now - 12 * 3600_000L
                val end = now + 7 * 24 * 3600_000L
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(begin.toString()).appendPath(end.toString()).build()
                val proj = arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                )
                ctx.contentResolver.query(uri, proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                    var result: CalEvent? = null
                    while (c.moveToNext()) {
                        val title = c.getString(0) ?: continue
                        val b = c.getLong(1)
                        val e = c.getLong(2)
                        val allDay = c.getInt(3) == 1
                        // Eventos que no han terminado
                        if (e > now) { result = CalEvent(title, b, allDay); break }
                    }
                    result
                }
            } catch (e: Exception) { null }
        }
        nextEvent = found
    }

    fun gb(bytes: Long): String {
        val g = bytes / 1_000_000_000.0
        return if (g >= 10) "${g.roundToInt()} GB" else String.format(java.util.Locale.US, "%.1f GB", g)
    }
}
