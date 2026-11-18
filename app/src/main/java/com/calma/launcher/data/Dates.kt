package com.calma.launcher.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Textos de fecha y hora en español o inglés. */
class Dates(millis: Long, val lang: String, val use24h: Boolean) {
    private val dt: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    private val locale: Locale = if (lang == "es") Locale("es", "MX") else Locale.ENGLISH

    val month: String = dt.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).lowercase(locale).let { fixStandalone(it) }
    val weekday: String = dt.dayOfWeek.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
    val weekdayShort: String = dt.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".")
        .replaceFirstChar { it.titlecase(locale) }
    val day: Int = dt.dayOfMonth
    val year: Int = dt.year
    val hour24: Int = dt.hour
    val minute: Int = dt.minute

    /** Hora para mostrar: "6" o "18" según el formato. */
    val hourText: String = if (use24h) hour24.toString() else ((hour24 + 11) % 12 + 1).toString()
    /** Hora con dos dígitos para el reloj grande: "06". */
    val hour2: String = hourText.padStart(2, '0')
    val minute2: String = minute.toString().padStart(2, '0')
    val amPm: String = if (use24h) "" else if (hour24 < 12) "AM" else "PM"

    val time: String = "$hourText:$minute2"

    val greeting: String = when (hour24) {
        in 5..11 -> if (lang == "es") "¡buenos días!" else "good morning!"
        in 12..18 -> if (lang == "es") "¡buenas tardes!" else "good afternoon!"
        else -> if (lang == "es") "¡buenas noches!" else "good evening!"
    }

    val itIs: String = if (lang == "es") "es" else "it's"

    // Algunas versiones de Java devuelven el mes como número en "standalone"
    private fun fixStandalone(s: String): String {
        if (s.isNotEmpty() && s.all { it.isDigit() }) {
            return dt.month.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
        }
        return s
    }
}
