package com.calma.launcher.data

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calma.launcher.lock.Live
import kotlin.math.roundToInt

/** Lo que controla el panel: linterna, brillo, volumen, rotación, No molestar y estados de conexión. */
object Controls {
    var torch by mutableStateOf(false)
        private set
    private var torchId: String? = null
    private var torchReady = false

    private fun initTorch(ctx: Context) {
        if (torchReady) return
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        try {
            val ids = cm.cameraIdList
            torchId = ids.firstOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) torch = enabled
                }
            }, Handler(Looper.getMainLooper()))
            torchReady = true
        } catch (_: Exception) {}
    }

    fun prepare(ctx: Context) = initTorch(ctx.applicationContext)

    fun toggleTorch(ctx: Context) {
        initTorch(ctx.applicationContext)
        val id = torchId
        val cm = ctx.getSystemService(CameraManager::class.java)
        if (id == null || cm == null) {
            Toast.makeText(ctx, "Este teléfono no tiene linterna disponible", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            cm.setTorchMode(id, !torch)
        } catch (e: Exception) {
            Toast.makeText(ctx, "La cámara está en uso", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Brillo y rotación (necesitan "Modificar ajustes del sistema") ----------

    fun canWrite(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun writeSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun brightness(ctx: Context): Float = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    } catch (e: Exception) { 0.5f }

    fun autoBrightness(ctx: Context): Boolean = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (e: Exception) { false }

    fun setBrightness(ctx: Context, v: Float): Boolean {
        if (!canWrite(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
            true
        } catch (e: Exception) { false }
    }

    fun setAutoBrightness(ctx: Context, on: Boolean): Boolean {
        if (!canWrite(ctx)) return false
        return try {
            Settings.System.putInt(
                ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            true
        } catch (e: Exception) { false }
    }

    fun rotation(ctx: Context): Boolean = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    } catch (e: Exception) { false }

    fun setRotation(ctx: Context, on: Boolean): Boolean {
        if (!canWrite(ctx)) return false
        return try {
            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            true
        } catch (e: Exception) { false }
    }

    // ---------- Volumen ----------

    fun volume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return 0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(ctx: Context, v: Float) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt().coerceIn(0, max), 0) } catch (_: Exception) {}
    }

    // ---------- No molestar ----------

    fun dnd(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val f = nm.currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Devuelve false si falta el permiso. */
    fun toggleDnd(ctx: Context): Boolean {
        val target = if (dnd(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (nm.isNotificationPolicyAccessGranted) {
            return try { nm.setInterruptionFilter(target); true } catch (e: Exception) { false }
        }
        val svc = Live.service
        if (svc != null) {
            return try { svc.requestInterruptionFilter(target); true } catch (e: Exception) { false }
        }
        return false
    }

    // ---------- Estados de conexión (solo lectura) ----------

    fun wifi(ctx: Context): Boolean = try {
        ctx.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    } catch (e: Exception) { false }

    fun mobileData(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    } catch (e: Exception) { false }

    fun bluetooth(ctx: Context): Boolean = try {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (e: Exception) { false }
}
