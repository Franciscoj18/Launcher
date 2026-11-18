package com.calma.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calma.launcher.data.Dates
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.data.Words

/** Pantalla 1: el reloj con el estilo elegido en Ajustes. */
@Composable
fun ClockPage(d: Dates, enter: () -> Float, onClock: () -> Unit, onDate: () -> Unit) {
    when (Prefs.clockStyle) {
        "apilado" -> StackedClock(d, enter, onClock, onDate)
        "palabras" -> WordsClock(d, enter, onClock, onDate)
        "terminal" -> TerminalClock(d, enter, onClock)
        else -> EchoClock(d, enter, onClock, onDate)
    }
}

/** Mensaje propio o frase del día. */
@Composable
private fun messageFor(d: Dates): String {
    val own = Prefs.message.trim()
    if (own.isNotEmpty()) return own
    return if (Prefs.dailyQuote) Words.quote(d.dayOfYear, Prefs.lang) else ""
}

@Composable
private fun MessageLine(text: String, enter: () -> Float, index: Int, align: TextAlign = TextAlign.Center) {
    if (text.isEmpty()) return
    Text(
        text,
        color = Color.White.copy(alpha = 0.75f),
        fontFamily = Poppins,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        textAlign = align,
        modifier = Modifier.padding(top = 8.dp).appear(enter, index),
    )
}

// ---------- Eco (el del video) ----------

@Composable
private fun EchoClock(d: Dates, enter: () -> Float, onClock: () -> Unit, onDate: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .appear(enter, 0)
                .clickable(interactionSource = noRipple, indication = null) { onClock() },
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(d.time, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            if (d.amPm.isNotEmpty()) {
                Text(
                    d.amPm.lowercase(),
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 3.dp, top = 12.dp),
                )
            }
        }
        EchoMonth(
            text = d.month.uppercase(),
            enter = enter,
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null) { onDate() },
        )
        Text(
            "${d.weekday.uppercase()}, ${d.day}",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.offset(y = (-6).dp).appear(enter, 4),
        )
        MessageLine(messageFor(d), enter, 5)
    }
}

/** El mes grande con dos "ecos" que aparecen en cascada y se desvanecen. */
@Composable
fun EchoMonth(text: String, enter: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val avail = maxWidth.value * 0.84f
        val sizeDp = (avail / (text.length.coerceAtLeast(3) * 0.47f)).coerceIn(44f, 90f)
        val step = sizeDp * 0.74f
        val fontSize = with(density) { sizeDp.dp.toSp() }
        Box(Modifier.fillMaxWidth().height((sizeDp * 1.12f + step * 2).dp)) {
            for (i in 2 downTo 0) {
                val alpha = when (i) { 0 -> 1f; 1 -> 0.42f; else -> 0.2f }
                var m = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (step * i).dp)
                    .appear(enter, 1 + i)
                if (i > 0) {
                    m = m
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                }
                Text(
                    text,
                    color = Color.White.copy(alpha = alpha),
                    fontFamily = Condensed,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    letterSpacing = (sizeDp * 0.03f).sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = m,
                )
            }
        }
    }
}

// ---------- Apilado (como la pantalla de bloqueo) ----------

@OptIn(ExperimentalTextApi::class)
@Composable
private fun StackedClock(d: Dates, enter: () -> Float, onClock: () -> Unit, onDate: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val big = with(density) { 112.dp.toSp() }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${d.weekdayShort.uppercase()} ${d.day} ${d.monthShort.uppercase()}",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            modifier = Modifier
                .appear(enter, 0)
                .clickable(interactionSource = noRipple, indication = null) { onDate() },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null) { onClock() },
        ) {
            Text(
                d.hour2,
                style = TextStyle(color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = big, lineHeight = big),
                modifier = Modifier.offset(y = 10.dp).appear(enter, 1),
            )
            Text(
                d.minute2,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = big,
                    lineHeight = big,
                    drawStyle = Stroke(width = with(density) { 2.2.dp.toPx() }),
                ),
                modifier = Modifier.offset(y = (-10).dp).appear(enter, 2),
            )
        }
        Box(
            Modifier
                .appear(enter, 3)
                .width(40.dp)
                .height(2.dp)
                .background(Accent)
        )
        if (Prefs.hasWeather) {
            Text(
                Weather.short(Prefs.lang),
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp).appear(enter, 4),
            )
        }
        MessageLine(messageFor(d), enter, 5)
    }
}

// ---------- En palabras ----------

@Composable
private fun WordsClock(d: Dates, enter: () -> Float, onClock: () -> Unit, onDate: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    val lines = Words.clock(d.hour24, d.minute, Prefs.lang)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 34.dp, bottom = 30.dp)
            .clickable(interactionSource = noRipple, indication = null) { onClock() },
    ) {
        Text(lines[0], color = Color.White.copy(alpha = 0.85f), fontFamily = Poppins, fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 30.sp, modifier = Modifier.appear(enter, 0))
        Text(lines[1], color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 58.sp, modifier = Modifier.appear(enter, 1))
        Row(Modifier.appear(enter, 2)) {
            Text(lines[2], color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)
            Text(".", color = Accent, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)
        }
        Text(lines[3], color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.appear(enter, 3))
        Text(
            if (Prefs.lang == "es") "${d.weekday} ${d.day} de ${d.month}" else "${d.weekday}, ${d.month} ${d.day}",
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = Poppins,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .appear(enter, 4)
                .clickable(interactionSource = noRipple, indication = null) { onDate() },
        )
        MessageLine(messageFor(d), enter, 5, TextAlign.Start)
    }
}

// ---------- Terminal ----------

@Composable
private fun TerminalClock(d: Dates, enter: () -> Float, onClock: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    val es = Prefs.lang == "es"
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursor by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor",
    )
    val mono = FontFamily.Monospace
    val prompt = "calma@moto:~$"
    val msg = messageFor(d)

    @Composable
    fun Cmd(cmd: String, index: Int) {
        Row(Modifier.padding(top = 8.dp).appear(enter, index)) {
            Text(prompt, color = Accent, fontFamily = mono, fontSize = 12.sp)
            Text(" $cmd", color = Color.White.copy(alpha = 0.7f), fontFamily = mono, fontSize = 12.sp)
        }
    }

    @Composable
    fun Out(text: String, index: Int) {
        Text(text, color = Color.White, fontFamily = mono, fontSize = 14.sp, modifier = Modifier.appear(enter, index))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, bottom = 30.dp)
            .clickable(interactionSource = noRipple, indication = null) { onClock() },
    ) {
        Cmd(if (es) "fecha" else "date", 0)
        Out("${d.weekdayShort.lowercase()} ${d.day} ${d.monthShort} ${d.year}", 0)
        Cmd(if (es) "hora" else "time", 1)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.appear(enter, 2)) {
            Text(d.time, color = Color.White, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp)
            if (d.amPm.isNotEmpty()) {
                Text(" ${d.amPm.lowercase()}", color = Color.White.copy(alpha = 0.7f), fontFamily = mono, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (Prefs.hasWeather) {
            Cmd(if (es) "clima" else "weather", 3)
            Out(Weather.short(Prefs.lang), 3)
        }
        if (DeviceInfo.battery >= 0) {
            Cmd(if (es) "bateria" else "battery", 4)
            Out("${DeviceInfo.battery}%" + if (DeviceInfo.charging) (if (es) " (cargando)" else " (charging)") else "", 4)
        }
        if (msg.isNotEmpty()) {
            Cmd("echo", 5)
            Out("\"$msg\"", 5)
        }
        Row(Modifier.padding(top = 8.dp).appear(enter, 6)) {
            Text(prompt, color = Accent, fontFamily = mono, fontSize = 12.sp)
            Text(" █", color = Color.White.copy(alpha = cursor), fontFamily = mono, fontSize = 12.sp)
        }
    }
}
