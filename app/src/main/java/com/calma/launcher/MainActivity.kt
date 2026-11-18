package com.calma.launcher

import android.Manifest
import android.app.AlarmManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.lock.LockService
import com.calma.launcher.ui.CalmaTheme
import com.calma.launcher.ui.HomeScreen
import com.calma.launcher.ui.Tilt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val HOST_ID = 2026
        private const val REQ_BIND = 11
        private const val REQ_CONFIG = 12
    }

    lateinit var widgetHost: AppWidgetHost
        private set
    lateinit var widgetManager: AppWidgetManager
        private set

    /** Hora actual; se actualiza cada minuto. */
    val now = mutableLongStateOf(System.currentTimeMillis())
    /** Sube cada vez que se presiona el botón de inicio estando aquí. */
    val homePresses = mutableIntStateOf(0)
    /** Sube cada vez que vuelves al inicio (para la animación de entrada). */
    val resumes = mutableIntStateOf(0)

    lateinit var tilt: Tilt
        private set

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        lifecycleScope.launch { DeviceInfo.refreshCalendar(this@MainActivity) }
    }

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> DeviceInfo.readBattery(intent)
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> DeviceInfo.refreshQuick(this@MainActivity)
                else -> now.longValue = System.currentTimeMillis()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, HOST_ID)
        tilt = Tilt(this)
        HomeWallpaper.watchSystemColors(applicationContext)

        // En la pantalla de inicio el botón atrás no hace nada (el cajón lo maneja Compose)
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            CalmaTheme {
                HomeScreen(activity = this)
            }
        }
        LockService.sync(this)
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(this, timeReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (sticky?.action == Intent.ACTION_BATTERY_CHANGED) DeviceInfo.readBattery(sticky)
        now.longValue = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        now.longValue = System.currentTimeMillis()
        resumes.intValue++
        DeviceInfo.refreshQuick(this)
        lifecycleScope.launch {
            HomeWallpaper.load(applicationContext)
            if (HomeWallpaper.autoAccent == null) HomeWallpaper.refreshAccent(applicationContext)
            if (Prefs.parallax && Prefs.homeImage.isNotEmpty()) tilt.start() else tilt.stop()
        }
        lifecycleScope.launch { Weather.refreshIfStale() }
        lifecycleScope.launch { DeviceInfo.refreshCalendar(this@MainActivity) }
    }

    override fun onPause() {
        super.onPause()
        tilt.stop()
    }

    fun requestCalendar() {
        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    /** Mueve el fondo del sistema al pasar de pantalla (si es más ancho que la pantalla). */
    fun setWallpaperScroll(fraction: Float) {
        val token = window?.decorView?.windowToken ?: return
        try {
            val wm = WallpaperManager.getInstance(this)
            wm.setWallpaperOffsetSteps(0.5f, 0f)
            wm.setWallpaperOffsets(token, fraction.coerceIn(0f, 1f), 0.5f)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(timeReceiver) } catch (_: Exception) {}
        try { widgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses.intValue++
        }
    }

    // ---------- Widgets ----------

    fun installedWidgets(): List<AppWidgetProviderInfo> =
        try { widgetManager.installedProviders.filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 } }
        catch (e: Exception) { emptyList() }

    fun widgetInfo(id: Int): AppWidgetProviderInfo? =
        if (id < 0) null else try { widgetManager.getAppWidgetInfo(id) } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        val ok = try {
            widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) { false }
        if (ok) {
            configureOrFinish(id, info)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try {
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                widgetHost.deleteAppWidgetId(id)
                Toast.makeText(this, "No se pudo agregar el widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
                // Si la configuración no abre, lo agregamos igual
            }
        }
        finishWidget(id)
    }

    private fun finishWidget(id: Int) {
        val old = Prefs.widgetId
        if (old >= 0 && old != id) {
            try { widgetHost.deleteAppWidgetId(old) } catch (_: Exception) {}
        }
        Prefs.setWidgetIdValue(id)
        pendingId = -1
        pendingInfo = null
    }

    fun removeWidget() {
        val old = Prefs.widgetId
        if (old >= 0) try { widgetHost.deleteAppWidgetId(old) } catch (_: Exception) {}
        Prefs.setWidgetIdValue(-1)
    }

    /** Vuelve a abrir la configuración del widget (para cambiarle la imagen, por ejemplo). */
    fun reconfigureWidget() {
        val id = Prefs.widgetId
        val info = widgetInfo(id) ?: return
        if (info.configure == null) {
            Toast.makeText(this, "Este widget no tiene opciones", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG + 100, null)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = pendingId
        val info = pendingInfo
        when (requestCode) {
            REQ_BIND -> {
                if (resultCode == RESULT_OK && id >= 0 && info != null) configureOrFinish(id, info)
                else if (id >= 0) { widgetHost.deleteAppWidgetId(id); pendingId = -1 }
            }
            REQ_CONFIG -> {
                if (resultCode == RESULT_OK && id >= 0) finishWidget(id)
                else if (id >= 0) { widgetHost.deleteAppWidgetId(id); pendingId = -1 }
            }
        }
    }
}
