package com.calma.launcher.volume

import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calma.launcher.data.Prefs
import com.calma.launcher.panel.OverlayOwner
import com.calma.launcher.panel.PanelService
import com.calma.launcher.ui.CalmaTheme
import kotlin.math.roundToInt

/**
 * Reemplaza el control de volumen del sistema: Calma recibe los botones de volumen,
 * cambia el volumen sin mostrar la barra de Motorola y dibuja la suya.
 */
class VolumeController(private val svc: PanelService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val am = svc.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null
    private var cameraBusy = false
    private val busyCameras = HashSet<String>()

    // ----- Estado que dibuja la barra -----
    var visible by mutableStateOf(false)
        private set
    var expanded by mutableStateOf(false)
        private set
    /** Volumen que mueven los botones (música o timbre). */
    var stream by mutableIntStateOf(AudioManager.STREAM_MUSIC)
        private set
    /** Sube con cada cambio para que la barra se redibuje. */
    var version by mutableIntStateOf(0)
        private set

    private val hideTask = Runnable { hide() }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            busyCameras.remove(cameraId)
            cameraBusy = busyCameras.isNotEmpty()
        }
        override fun onCameraUnavailable(cameraId: String) {
            busyCameras.add(cameraId)
            cameraBusy = true
        }
    }

    fun start() {
        try { svc.getSystemService(CameraManager::class.java)?.registerAvailabilityCallback(cameraCallback, main) } catch (_: Exception) {}
    }

    fun stop() {
        try { svc.getSystemService(CameraManager::class.java)?.unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) {}
        main.removeCallbacks(hideTask)
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        lp = null
        owner?.stop()
        owner = null
    }

    // ----- Botones físicos -----

    /** Devuelve true si Calma se encargó del botón (y el sistema ya no muestra su barra). */
    fun onKey(event: KeyEvent): Boolean {
        if (!Prefs.customVolume) return false
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val down = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!up && !down) return false
        // En llamadas, mientras suena el teléfono o con la cámara abierta, manda el sistema
        val mode = am?.mode ?: AudioManager.MODE_NORMAL
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
        ) return false
        if (cameraBusy) return false
        val manager = am ?: return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!visible) stream = if (manager.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
            try {
                manager.adjustStreamVolume(stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            } catch (e: SecurityException) {
                // Bajar el timbre hasta silencio puede requerir el permiso de No molestar
                try { manager.adjustStreamVolume(stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                return true
            }
            version++
            val screenOn = svc.getSystemService(PowerManager::class.java)?.isInteractive == true
            if (screenOn) show()
        }
        return true
    }

    // ----- Lecturas y cambios desde la barra -----

    fun level(s: Int): Float {
        val manager = am ?: return 0f
        val max = manager.getStreamMaxVolume(s).coerceAtLeast(1)
        return manager.getStreamVolume(s) / max.toFloat()
    }

    fun setLevel(s: Int, fraction: Float) {
        val manager = am ?: return
        val max = manager.getStreamMaxVolume(s)
        try {
            manager.setStreamVolume(s, (fraction * max).roundToInt().coerceIn(0, max), 0)
        } catch (_: Exception) {}
        version++
        touch()
    }

    fun ringerMode(): Int = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    fun cycleRinger() {
        val manager = am ?: return
        val next = when (manager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            manager.ringerMode = next
        } catch (e: SecurityException) {
            Toast.makeText(svc, "Permite a Calma el acceso a No molestar", Toast.LENGTH_LONG).show()
            hide()
            try {
                svc.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
            return
        }
        version++
        touch()
    }

    fun toggleExpanded() {
        expanded = !expanded
        applyWindowSize()
        touch()
    }

    /** Cada vez que interactúas, la barra espera otra vez antes de esconderse. */
    fun touch() {
        main.removeCallbacks(hideTask)
        main.postDelayed(hideTask, if (expanded) 3500 else 2200)
    }

    // ----- Ventana -----

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).roundToInt()

    private fun ensureWindow(): FrameLayout? {
        root?.let { return it }
        val o = OverlayOwner().also { it.start() }
        val r = FrameLayout(svc)
        r.setViewTreeLifecycleOwner(o)
        r.setViewTreeSavedStateRegistryOwner(o)
        r.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) hide()
            false
        }
        val compose = ComposeView(svc).apply {
            setContent { CalmaTheme { VolumeUi(this@VolumeController) } }
        }
        r.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        r.visibility = View.GONE
        val params = WindowManager.LayoutParams(
            dp(COLLAPSED_W), dp(COLLAPSED_H),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            hiddenFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(8)
            y = -dp(70)
        }
        return try {
            wm.addView(r, params)
            root = r
            lp = params
            owner = o
            r
        } catch (e: Exception) {
            o.stop()
            null
        }
    }

    private val shownFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    private val hiddenFlags = shownFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun applyWindowSize() {
        val r = root ?: return
        val p = lp ?: return
        p.width = dp(if (expanded) EXPANDED_W else COLLAPSED_W)
        p.height = dp(if (expanded) EXPANDED_H else COLLAPSED_H)
        try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
    }

    private fun show() {
        val r = ensureWindow() ?: return
        val p = lp ?: return
        if (!visible) {
            expanded = false
            p.flags = shownFlags
            p.width = dp(COLLAPSED_W)
            p.height = dp(COLLAPSED_H)
            r.visibility = View.VISIBLE
            try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
            visible = true
        }
        touch()
    }

    fun hide() {
        main.removeCallbacks(hideTask)
        if (!visible) return
        visible = false
        // Espera a que termine la animación de salida antes de soltar la ventana
        main.postDelayed({
            if (visible) return@postDelayed
            val r = root ?: return@postDelayed
            val p = lp ?: return@postDelayed
            expanded = false
            r.visibility = View.GONE
            p.flags = hiddenFlags
            try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
        }, 220)
    }

    companion object {
        const val COLLAPSED_W = 76
        const val COLLAPSED_H = 250
        const val EXPANDED_W = 250
        const val EXPANDED_H = 300
    }
}
