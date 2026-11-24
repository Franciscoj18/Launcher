package com.calma.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Normalizer
import java.util.concurrent.Executors

class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    /** "paquete/clase": así se guardan las apps de inicio. */
    val key: String = component.flattenToString()
    val pkg: String get() = component.packageName
    val search: String = Apps.normalize(label)
}

/** Lista de apps instaladas, siempre al día. */
object Apps {
    private lateinit var appCtx: Context
    private lateinit var launcherApps: LauncherApps
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var list by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var byKey by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    /** Sube cada vez que cambian las apps; sirve para refrescar íconos. */
    var version by mutableStateOf(0)
        private set

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        launcherApps = appCtx.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
            override fun onPackagesAvailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        }, main)
        reload()
    }

    fun reload() {
        worker.execute {
            val me = appCtx.packageName
            val result = ArrayList<AppEntry>()
            val profiles = try { launcherApps.profiles } catch (e: Exception) { listOf(Process.myUserHandle()) }
            for (user in profiles) {
                val acts = try { launcherApps.getActivityList(null, user) } catch (e: Exception) { emptyList() }
                for (a in acts) {
                    // Ocultamos el propio launcher, pero dejamos sus ajustes
                    if (a.componentName.packageName == me && a.componentName.className.endsWith("MainActivity")) continue
                    val label = a.label?.toString()?.trim().orEmpty().ifEmpty { a.componentName.packageName }
                    result.add(AppEntry(label, a.componentName, user, a))
                }
            }
            result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            val map = HashMap<String, AppEntry>()
            for (e in result) if (!map.containsKey(e.key)) map[e.key] = e
            main.post {
                list = result
                byKey = map
                version++
                IconLoader.clear()
                ensureDefaultDock()
                fixDock()
                warmIcons()
            }
        }
    }

    /** Primera vez: llena las 8 apps de inicio con apps comunes. */
    private fun ensureDefaultDock() {
        if (Prefs.dockInitialized || list.isEmpty()) return
        val wanted = listOf(
            listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser"),
            listOf("com.google.android.youtube"),
            listOf("com.whatsapp"),
            listOf("com.sonora.player", "com.spotify.music", "com.google.android.apps.youtube.music"),
            listOf("com.google.android.apps.photos", "com.motorola.gallery", "com.android.gallery3d"),
            listOf("com.motorola.camera3", "com.motorola.camera2", "com.android.camera2", "org.lineageos.aperture", "com.android.camera"),
            listOf("com.android.vending"),
            listOf("com.android.settings"),
        )
        val chosen = ArrayList<String>()
        for (group in wanted) {
            val hit = group.firstNotNullOfOrNull { pkg -> list.firstOrNull { it.pkg == pkg } }
            if (hit != null && hit.key !in chosen) chosen.add(hit.key)
        }
        for (e in list) {
            if (chosen.size >= Prefs.DOCK_SIZE) break
            if (e.key !in chosen && e.pkg != appCtx.packageName) chosen.add(e.key)
        }
        Prefs.saveDock(chosen)
    }

    /** Si una app de inicio se desinstaló, o cambió de actividad, la arreglamos. */
    private fun fixDock() {
        if (list.isEmpty()) return
        var changed = false
        val fixed = Prefs.dock.map { key ->
            if (key.isEmpty() || byKey.containsKey(key)) key
            else {
                val pkg = key.substringBefore('/')
                val same = list.firstOrNull { it.pkg == pkg }
                changed = true
                same?.key ?: ""
            }
        }
        if (changed) Prefs.saveDock(fixed)
    }

    private val iconWorker = Executors.newSingleThreadExecutor { r ->
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }.apply { name = "calma-iconos" }
    }

    /** Prepara los íconos de inicio y del cajón antes de que los veas (sin trabar la pantalla). */
    fun warmIcons() {
        val snapshot = list
        val dockKeys = Prefs.dock.toSet()
        val style = Prefs.iconStyle
        val pack = Prefs.iconPack
        val ordered = snapshot.filter { it.key in dockKeys } + snapshot.filter { it.key !in dockKeys }
        iconWorker.execute {
            for (e in ordered) {
                try { IconLoader.load(appCtx, e, style, pack) } catch (_: Throwable) {}
            }
        }
    }

    fun launch(ctx: Context, e: AppEntry, bounds: Rect? = null) {
        try {
            launcherApps.startMainActivity(e.component, e.user, bounds, null)
        } catch (ex: Exception) {
            Toast.makeText(ctx, "No se pudo abrir ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInfo(ctx: Context, e: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(e.component, e.user, null, null)
        } catch (ex: Exception) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i) } catch (_: Exception) {}
        }
    }

    fun uninstall(ctx: Context, e: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{Mn}+"), "")
    }
}
