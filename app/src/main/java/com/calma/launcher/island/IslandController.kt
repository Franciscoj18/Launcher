package com.calma.launcher.island

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.Prefs
import com.calma.launcher.panel.OverlayOwner
import com.calma.launcher.panel.PanelService
import com.calma.launcher.ui.CalmaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Medidas de la isla según la cámara de cada teléfono (en dp). */
class IslandGeometry(val pillHeight: Float, val cameraGap: Float, val expandedWidth: Float)

/**
 * Dibuja la isla dinámica en una ventanita sobre todas las apps (vive dentro del servicio
 * de accesibilidad del panel) y escucha cargador, sonido, audífonos, cámara y micrófono.
 */
class IslandController(private val svc: PanelService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private var ready = false
    private var lp: WindowManager.LayoutParams? = null
    private var camX = 0
    private var screenW = 0
    private var curW = -1
    private var curH = -1
    lateinit var geometry: IslandGeometry
        private set

    // ---------- Ventana ----------

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        if (root != null) return
        val dm = svc.resources.displayMetrics
        val d = dm.density
        val cut: Rect? = cameraCutout()
        val camW = (cut?.width() ?: 0) / d
        val camH = (cut?.height() ?: 0) / d
        val pillH = if (cut == null) 30f else min(40f, max(30f, camH + 8f))
        val gap = if (cut == null) 14f else camW + 14f
        geometry = IslandGeometry(pillH, gap, dm.widthPixels / d - 24f)

        val centerX = cut?.centerX() ?: (dm.widthPixels / 2)
        camX = centerX
        screenW = dm.widthPixels
        val yPx = if (cut == null) (6 * d).toInt()
        else max(0, (cut.exactCenterY() - pillH * d / 2f).roundToInt())

        val o = OverlayOwner().also { it.start() }
        owner = o
        val r = FrameLayout(svc)
        r.setViewTreeLifecycleOwner(o)
        r.setViewTreeSavedStateRegistryOwner(o)
        r.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) Island.outsideTaps++
            false
        }
        val compose = ComposeView(svc).apply {
            setContent { CalmaTheme { IslandUi(this@IslandController) } }
        }
        r.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - 1).coerceAtLeast(0)
            y = yPx + (Prefs.islandY * d).roundToInt()
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(r, params)
            root = r
            lp = params
            curW = 1
            curH = 1
        } catch (e: Exception) {
            o.stop()
            owner = null
            return
        }
        registerListeners()
    }

    /** Cambia el tamaño de la ventana, siempre centrada sobre la cámara. */
    fun setWindowSize(width: Int, height: Int) {
        val r = root ?: return
        val p = lp ?: return
        val wpx = width.coerceIn(1, screenW)
        val hpx = height.coerceAtLeast(1)
        if (wpx == curW && hpx == curH) return
        curW = wpx
        curH = hpx
        p.width = wpx
        p.height = hpx
        p.x = (camX - wpx / 2).coerceIn(0, (screenW - wpx).coerceAtLeast(0))
        try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
    }

    fun detach() {
        unregisterListeners()
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        owner?.stop()
        owner = null
        scope.cancel()
    }

    @Suppress("DEPRECATION")
    private fun cameraCutout(): Rect? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val c = wm.defaultDisplay.cutout ?: return null
            val r = c.boundingRectTop
            if (r.isEmpty) null else r
        } catch (e: Exception) { null }
    }

    // ---------- Acciones desde la isla ----------

    fun open(pi: PendingIntent?, pkg: String) = svc.launchPending(pi, pkg)
    fun openPanel() = svc.showPanel()
    val context: Context get() = svc

    fun copy(code: String) {
        try {
            val cm = svc.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Código", code))
            Toast.makeText(svc, "Código copiado", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        Island.dismissAlert()
    }

    // ---------- Escuchas ----------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> if (Island.has("charge")) {
                    // Espera un poco para que Android calcule el tiempo restante
                    main.postDelayed({ showCharging() }, 1200)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    if (isInitialStickyBroadcast || !Island.has("sound")) return
                    val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    val es = Prefs.lang == "es"
                    val text = when (mode) {
                        AudioManager.RINGER_MODE_SILENT -> if (es) "Silencio" else "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> if (es) "Vibrar" else "Vibrate"
                        else -> if (es) "Sonido" else "Ring"
                    }
                    Island.show(IslandAlert(AlertKind.SOUND, text, "", level = mode, durationMs = 1800))
                }
            }
        }
    }

    private fun showCharging() {
        val i = ContextCompat.registerReceiver(svc, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val level = i?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) (l * 100f / s).roundToInt() else -1
        } ?: -1
        val es = Prefs.lang == "es"
        var text = ""
        if (Build.VERSION.SDK_INT >= 28) {
            val ms = try { svc.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1L } catch (e: Exception) { -1L }
            if (ms > 0) {
                val mins = (ms / 60_000L).toInt()
                val t = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
                text = if (es) "lleno en $t" else "full in $t"
            }
        }
        Island.show(IslandAlert(AlertKind.CHARGING, if (es) "Cargando" else "Charging", text, level = level, durationMs = 3500))
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        private val busy = HashSet<String>()
        override fun onCameraAvailable(cameraId: String) {
            busy.remove(cameraId)
            Island.camera = busy.isNotEmpty()
        }
        override fun onCameraUnavailable(cameraId: String) {
            busy.add(cameraId)
            Island.camera = true
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            Island.mic = !configs.isNullOrEmpty()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!ready || !Island.has("phones")) return
            val dev = addedDevices?.firstOrNull { it.isSink && it.type in headphoneTypes() } ?: return
            val name = dev.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (Prefs.lang == "es") "Audífonos" else "Headphones"
            Island.show(
                IslandAlert(
                    AlertKind.HEADPHONES, name,
                    if (Prefs.lang == "es") "Conectados" else "Connected",
                    durationMs = 3000,
                )
            )
        }
    }

    private fun headphoneTypes(): Set<Int> {
        val s = mutableSetOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        if (Build.VERSION.SDK_INT >= 31) s.add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        return s
    }

    private fun registerListeners() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(svc, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        val am = svc.getSystemService(AudioManager::class.java)
        try { am?.registerAudioRecordingCallback(recordingCallback, main) } catch (_: Exception) {}
        try { am?.registerAudioDeviceCallback(deviceCallback, main) } catch (_: Exception) {}
        try { Island.mic = !am?.activeRecordingConfigurations.isNullOrEmpty() } catch (_: Exception) {}
        try { svc.getSystemService(CameraManager::class.java)?.registerAvailabilityCallback(cameraCallback, main) } catch (_: Exception) {}
        // Los dispositivos ya conectados se reportan al registrar: los ignoramos
        main.postDelayed({ ready = true }, 1500)

        // Aviso de eventos próximos (10 minutos antes)
        scope.launch {
            var alerted = 0L
            var count = 0
            while (true) {
                if (Island.has("event")) {
                    if (count % 5 == 0) DeviceInfo.refreshCalendar(svc)
                    val ev = DeviceInfo.nextEvent
                    val now = System.currentTimeMillis()
                    if (ev != null && !ev.allDay && ev.begin != alerted && ev.begin - now in 1..10 * 60_000L) {
                        alerted = ev.begin
                        val mins = ((ev.begin - now) / 60_000L).coerceAtLeast(1)
                        val es = Prefs.lang == "es"
                        Island.show(
                            IslandAlert(
                                AlertKind.EVENT, ev.title,
                                if (es) "en $mins min" else "in $mins min",
                                durationMs = 6000,
                            )
                        )
                    }
                }
                count++
                delay(60_000)
            }
        }
    }

    private fun unregisterListeners() {
        try { svc.unregisterReceiver(receiver) } catch (_: Exception) {}
        val am = svc.getSystemService(AudioManager::class.java)
        try { am?.unregisterAudioRecordingCallback(recordingCallback) } catch (_: Exception) {}
        try { am?.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Exception) {}
        try { svc.getSystemService(CameraManager::class.java)?.unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) {}
    }
}
