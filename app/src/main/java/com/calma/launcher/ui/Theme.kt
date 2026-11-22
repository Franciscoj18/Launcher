package com.calma.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import com.calma.launcher.R
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.Prefs

val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Roboto Condensed del sistema: la letra alta y delgada del mes. */
val Condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed-light", android.graphics.Typeface.NORMAL))

/** Color de acento elegido en Ajustes (o sacado del fondo en modo automático). */
val Accent: Color
    get() = if (Prefs.accentMode == "auto") (HomeWallpaper.autoAccent ?: Color(Prefs.accentColor))
    else Color(Prefs.accentColor)

/** Fondo de paneles y menús: oscuro, teñido con el color del fondo de pantalla. */
val PanelSurface: Color
    get() = (if (Prefs.wallTheme) HomeWallpaper.surface else null) ?: Color(0xFF1C1C1F)

/** Un tono más claro, para tarjetas dentro de los paneles. */
val PanelSurfaceHi: Color
    get() = (if (Prefs.wallTheme) HomeWallpaper.surfaceHi else null) ?: Color(0xFF2C2C30)

/** Relleno de tarjetas sobre el fondo de pantalla. */
val CardFill: Color
    get() = if (Prefs.wallTheme && HomeWallpaper.surfaceHi != null) PanelSurfaceHi.copy(alpha = 0.6f)
    else Color.White.copy(alpha = 0.08f)

/** Colores para elegir en Ajustes. */
val AccentChoices = listOf(
    0xFFFF6B6B, 0xFFFF8FB1, 0xFFFF9F43, 0xFFFFD166,
    0xFF6EE7B7, 0xFF67C8FF, 0xFFB69CFF, 0xFFFFFFFF,
).map { it.toInt() }

@Composable
fun CalmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            secondary = Accent,
            surface = PanelSurface,
            surfaceContainer = PanelSurface,
            background = Color.Black,
        ),
        content = content,
    )
}

/** Gira un texto 90° y además intercambia ancho y alto al acomodarlo. */
fun Modifier.vertical(): Modifier = this
    .layout { measurable, constraints ->
        val c = Constraints(
            maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
            maxHeight = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
        )
        val p = measurable.measure(c)
        layout(p.height, p.width) {
            p.place(x = -(p.width / 2 - p.height / 2), y = -(p.height / 2 - p.width / 2))
        }
    }
    .rotate(90f)
