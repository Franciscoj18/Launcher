package com.calma.launcher.panel

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.Prefs
import com.calma.launcher.ui.CalmaTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad de Calma: pone una franja invisible en la esquina de arriba
 * y, al deslizar hacia abajo desde ahí en cualquier app, muestra el panel de controles.
 * No lee el contenido de la pantalla.
 */
class PanelService : AccessibilityService(), PanelHost {

    companion object {
        @Volatile var instance: PanelService? = null
            private set

        fun isEnabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.startsWith(ctx.packageName + "/") }
        }
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private var trigger: View? = null
    private var panelRoot: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private val visible = mutableStateOf(false)

    override val hostContext: Context get() = this

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        addTrigger()
        scope.launch {
            HomeWallpaper.load(applicationContext)
            HomeWallpaper.refreshAccent(applicationContext)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeTrigger()
        removePanel()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    /** Vuelve a colocar la franja (por ejemplo, si cambiaste de esquina en Ajustes). */
    fun refreshTrigger() {
        removeTrigger()
        addTrigger()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTrigger() {
        val dm = resources.displayMetrics
        val d = dm.density
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.36f).toInt(),
            (26 * d).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (Prefs.panelSide == "left") Gravity.START else Gravity.END)
        }
        var startY = 0f
        var fired = false
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = ev.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> if (!fired && ev.rawY - startY > 22 * d) {
                    fired = true
                    showPanel()
                }
            }
            true
        }
        try {
            wm.addView(v, lp)
            trigger = v
        } catch (_: Exception) {}
    }

    private fun removeTrigger() {
        trigger?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        trigger = null
    }

    fun showPanel() {
        if (panelRoot != null) return
        val o = OverlayOwner().also { it.start() }
        owner = o
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) closePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(o)
        root.setViewTreeSavedStateRegistryOwner(o)
        val compose = ComposeView(this).apply {
            setContent {
                CalmaTheme {
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = slideInVertically { -it / 4 } + fadeIn(),
                        exit = slideOutVertically { -it / 4 } + fadeOut(),
                    ) {
                        ControlPanel(host = this@PanelService)
                    }
                }
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(root, lp)
            panelRoot = root
            root.requestFocus()
            main.post { visible.value = true }
        } catch (e: Exception) {
            o.stop()
            owner = null
        }
    }

    override fun closePanel(then: (() -> Unit)?) {
        if (panelRoot == null) { then?.invoke(); return }
        visible.value = false
        main.postDelayed({
            removePanel()
            then?.invoke()
        }, 260)
    }

    private fun removePanel() {
        panelRoot?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panelRoot = null
        owner?.stop()
        owner = null
        visible.value = false
    }

    override fun launch(intent: Intent) {
        closePanel {
            try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    override fun launchPending(pi: PendingIntent?, pkg: String) {
        closePanel {
            var ok = false
            if (pi != null) try { pi.send(); ok = true } catch (_: Exception) {}
            if (!ok && pkg.isNotEmpty()) {
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    try { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
            }
        }
    }

    override fun global(action: Int): Boolean {
        closePanel {
            // Pequeña pausa para que el panel no salga en la captura
            main.postDelayed({ performGlobalAction(action) }, 250)
        }
        return true
    }

    override val locked: Boolean
        get() = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
}
