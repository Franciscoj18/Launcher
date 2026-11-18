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

val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Roboto Condensed del sistema: la letra alta y delgada del mes. */
val Condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed-light", android.graphics.Typeface.NORMAL))

val Accent = Color(0xFFFF6B6B)

@Composable
fun CalmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            secondary = Accent,
            surface = Color(0xFF1C1C1F),
            surfaceContainer = Color(0xFF1C1C1F),
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
