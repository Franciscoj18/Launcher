package com.calma.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Clima de Open-Meteo (gratis, sin cuenta). */
object Weather {
    private const val STALE_MS = 30 * 60 * 1000L
    @Volatile private var busy = false

    suspend fun refreshIfStale(force: Boolean = false) {
        if (!Prefs.hasLocation || busy) return
        if (!force && System.currentTimeMillis() - Prefs.wTime < STALE_MS) return
        busy = true
        try {
            val lat = Prefs.lat
            val lon = Prefs.lon
            val json = withContext(Dispatchers.IO) {
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day&timezone=auto"
                )
            } ?: return
            val cur = JSONObject(json).optJSONObject("current") ?: return
            Prefs.setWeather(
                cur.optDouble("temperature_2m", 0.0).toFloat(),
                cur.optInt("weather_code", 0),
                cur.optDouble("wind_speed_10m", 0.0).toFloat(),
                cur.optInt("relative_humidity_2m", 0),
                cur.optInt("is_day", 1) == 1,
            )
        } catch (e: Exception) {
            // Sin internet: se queda el último clima guardado
        } finally {
            busy = false
        }
    }

    suspend fun searchCity(query: String, lang: String): List<Place> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json")
                ?: return@withContext emptyList()
            val arr = JSONObject(json).optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val detail = listOf(o.optString("admin1"), o.optString("country"))
                    .filter { it.isNotBlank() }.joinToString(", ")
                Place(o.optString("name"), detail, o.optDouble("latitude"), o.optDouble("longitude"))
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Ubicación aproximada del teléfono (necesita el permiso de ubicación). */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun currentLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            try {
                lm.getLastKnownLocation(p)?.let { return it }
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
            else LocationManager.GPS_PROVIDER
            return withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, ctx.mainLooper)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { try { lm.removeUpdates(listener) } catch (_: Exception) {} }
            }
        }
    }

    suspend fun cityName(ctx: Context, loc: Location, lang: String): String = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val list = Geocoder(ctx, Locale(lang)).getFromLocation(loc.latitude, loc.longitude, 1)
            val a = list?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: if (lang == "es") "tu zona" else "your area"
        } catch (e: Exception) {
            if (lang == "es") "tu zona" else "your area"
        }
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "CalmaLauncher/1.0")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun describe(code: Int, lang: String): String {
        val es = lang == "es"
        return when (code) {
            0 -> if (es) "despejado" else "clear sky"
            1 -> if (es) "mayormente despejado" else "mainly clear"
            2 -> if (es) "parcialmente nublado" else "partly cloudy"
            3 -> if (es) "nublado" else "overcast clouds"
            45, 48 -> if (es) "con niebla" else "foggy"
            51, 53, 55 -> if (es) "con llovizna" else "drizzle"
            56, 57 -> if (es) "con llovizna helada" else "freezing drizzle"
            61 -> if (es) "con lluvia ligera" else "light rain"
            63 -> if (es) "con lluvia" else "rain"
            65 -> if (es) "con lluvia fuerte" else "heavy rain"
            66, 67 -> if (es) "con lluvia helada" else "freezing rain"
            71, 73, 75, 77 -> if (es) "con nieve" else "snow"
            80, 81, 82 -> if (es) "con chubascos" else "rain showers"
            85, 86 -> if (es) "con nevadas" else "snow showers"
            95 -> if (es) "con tormenta" else "thunderstorm"
            96, 99 -> if (es) "con tormenta y granizo" else "thunderstorm with hail"
            else -> if (es) "sin datos" else "unknown"
        }
    }

    fun icon(code: Int, day: Boolean): ImageVector = when (code) {
        0, 1 -> if (day) Icons.Filled.WbSunny else Icons.Filled.NightsStay
        2 -> Icons.Filled.WbCloudy
        3, 45, 48 -> Icons.Filled.Cloud
        71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
        95, 96, 99 -> Icons.Filled.FlashOn
        in 51..67, 80, 81, 82 -> Icons.Filled.Umbrella
        else -> Icons.Filled.Cloud
    }

    /** "Clima en Altamira: nublado con 25°. Viento a 7 km/h y humedad del 92%." */
    fun sentence(lang: String): String {
        val es = lang == "es"
        if (!Prefs.hasLocation) {
            return if (es) "Mantén presionado el inicio y abre Ajustes para elegir tu ciudad y ver el clima."
            else "Long-press the home screen and open Settings to choose your city."
        }
        if (!Prefs.hasWeather) return if (es) "Cargando el clima de ${Prefs.city}…" else "Loading weather for ${Prefs.city}…"
        val t = Prefs.wTemp.roundToInt()
        val w = Prefs.wWind.roundToInt()
        val desc = describe(Prefs.wCode, lang)
        return if (es) {
            "Clima en ${Prefs.city}: $desc, ${t}°. Viento a $w km/h y humedad del ${Prefs.wHum}%."
        } else {
            "Weather in ${Prefs.city} is $desc with temperature ${t}°. Wind speed $w km/h and humidity ${Prefs.wHum}%."
        }
    }

    fun short(lang: String): String {
        if (!Prefs.hasWeather) return ""
        val desc = describe(Prefs.wCode, lang).removePrefix("con ")
        return "${Prefs.wTemp.roundToInt()}° $desc"
    }
}
