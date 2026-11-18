package com.calma.launcher.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.calma.launcher.R
import com.calma.launcher.SettingsActivity
import com.calma.launcher.data.Prefs

/**
 * Servicio ligero que espera a que se apague la pantalla para dejar lista
 * la pantalla de bloqueo con la hora grande.
 */
class LockService : Service() {

    companion object {
        private const val CHANNEL = "bloqueo"
        private const val NOTIF_ID = 7

        fun sync(ctx: Context) {
            val i = Intent(ctx, LockService::class.java)
            if (Prefs.lockEnabled && Settings.canDrawOverlays(ctx)) {
                try { ContextCompat.startForegroundService(ctx, i) } catch (_: Exception) {}
            } else {
                try { ctx.stopService(i) } catch (_: Exception) {}
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && Prefs.lockEnabled) {
                LockActivity.show(context)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pantalla de bloqueo", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Mantiene activa la pantalla de bloqueo de Calma"
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Pantalla de bloqueo activa")
            .setContentText("Toca para ver los ajustes de Calma")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.lockEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
