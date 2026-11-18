package com.calma.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ajustes guardados. Cada valor es un estado de Compose, así que la pantalla
 * de inicio se actualiza sola cuando cambias algo en Ajustes.
 */
object Prefs {
    const val DOCK_SIZE = 8

    private lateinit var sp: SharedPreferences

    // Inicio
    var dock by mutableStateOf(List(DOCK_SIZE) { "" })
        private set
    var dockInitialized = false
        private set

    // Íconos: "mono" (blancos) u "original"
    var iconStyle by mutableStateOf("mono")
        private set
    var iconPack by mutableStateOf("")
        private set

    // Fecha y hora
    var lang by mutableStateOf("es")
        private set
    var use24h by mutableStateOf(false)
        private set

    // Clima
    var city by mutableStateOf("")
        private set
    var lat by mutableStateOf(Double.NaN)
        private set
    var lon by mutableStateOf(Double.NaN)
        private set
    var wTemp by mutableStateOf(Float.NaN)
        private set
    var wCode by mutableStateOf(-1)
        private set
    var wWind by mutableStateOf(0f)
        private set
    var wHum by mutableStateOf(0)
        private set
    var wDay by mutableStateOf(true)
        private set
    var wTime by mutableStateOf(0L)
        private set

    // Widget
    var widgetId by mutableStateOf(-1)
        private set
    var widgetHeight by mutableStateOf(170)
        private set

    // Pantalla de bloqueo
    var lockEnabled by mutableStateOf(false)
        private set
    var lockImage by mutableStateOf("")
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("calma", Context.MODE_PRIVATE)
        dockInitialized = sp.contains("dock")
        val raw = sp.getString("dock", "") ?: ""
        val parts = raw.split("|")
        dock = List(DOCK_SIZE) { i -> parts.getOrNull(i) ?: "" }
        iconStyle = sp.getString("iconStyle", "mono") ?: "mono"
        iconPack = sp.getString("iconPack", "") ?: ""
        lang = sp.getString("lang", "es") ?: "es"
        use24h = sp.getBoolean("use24h", false)
        city = sp.getString("city", "") ?: ""
        lat = sp.getString("lat", null)?.toDoubleOrNull() ?: Double.NaN
        lon = sp.getString("lon", null)?.toDoubleOrNull() ?: Double.NaN
        wTemp = sp.getFloat("wTemp", Float.NaN)
        wCode = sp.getInt("wCode", -1)
        wWind = sp.getFloat("wWind", 0f)
        wHum = sp.getInt("wHum", 0)
        wDay = sp.getBoolean("wDay", true)
        wTime = sp.getLong("wTime", 0L)
        widgetId = sp.getInt("widgetId", -1)
        widgetHeight = sp.getInt("widgetHeight", 170)
        lockEnabled = sp.getBoolean("lockEnabled", false)
        lockImage = sp.getString("lockImage", "") ?: ""
    }

    fun setDockSlot(index: Int, key: String) {
        if (index !in 0 until DOCK_SIZE) return
        val list = dock.toMutableList()
        // Si la app ya estaba en otro lugar, se mueve en vez de duplicarse
        if (key.isNotEmpty()) {
            val old = list.indexOf(key)
            if (old >= 0 && old != index) list[old] = ""
        }
        list[index] = key
        saveDock(list)
    }

    fun saveDock(list: List<String>) {
        dock = List(DOCK_SIZE) { i -> list.getOrNull(i) ?: "" }
        dockInitialized = true
        sp.edit().putString("dock", dock.joinToString("|")).apply()
    }

    fun setIconStyleValue(v: String) { iconStyle = v; sp.edit().putString("iconStyle", v).apply() }
    fun setIconPackValue(v: String) { iconPack = v; sp.edit().putString("iconPack", v).apply() }
    fun setLangValue(v: String) { lang = v; sp.edit().putString("lang", v).apply() }
    fun setUse24hValue(v: Boolean) { use24h = v; sp.edit().putBoolean("use24h", v).apply() }

    fun setLocation(name: String, latitude: Double, longitude: Double) {
        city = name; lat = latitude; lon = longitude
        wTime = 0L
        sp.edit()
            .putString("city", name)
            .putString("lat", latitude.toString())
            .putString("lon", longitude.toString())
            .putLong("wTime", 0L)
            .apply()
    }

    fun setWeather(temp: Float, code: Int, wind: Float, hum: Int, day: Boolean) {
        wTemp = temp; wCode = code; wWind = wind; wHum = hum; wDay = day
        wTime = System.currentTimeMillis()
        sp.edit()
            .putFloat("wTemp", temp)
            .putInt("wCode", code)
            .putFloat("wWind", wind)
            .putInt("wHum", hum)
            .putBoolean("wDay", day)
            .putLong("wTime", wTime)
            .apply()
    }

    fun setWidgetIdValue(v: Int) { widgetId = v; sp.edit().putInt("widgetId", v).apply() }
    fun setWidgetHeightValue(v: Int) { widgetHeight = v; sp.edit().putInt("widgetHeight", v).apply() }
    fun setLockEnabledValue(v: Boolean) { lockEnabled = v; sp.edit().putBoolean("lockEnabled", v).apply() }
    fun setLockImageValue(v: String) { lockImage = v; sp.edit().putString("lockImage", v).apply() }

    val hasLocation: Boolean get() = !lat.isNaN() && !lon.isNaN()
    val hasWeather: Boolean get() = !wTemp.isNaN() && wCode >= 0
}
