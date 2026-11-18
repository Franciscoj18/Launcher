package com.calma.launcher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.calma.launcher.data.Dates
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.ui.Accent
import com.calma.launcher.ui.CalmaTheme
import com.calma.launcher.ui.Poppins
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pantalla de bloqueo: hora enorme, fecha, clima y batería. Desliza hacia arriba para entrar. */
class LockActivity : ComponentActivity() {

    companion object {
        fun show(ctx: Context) {
            // No interrumpir llamadas
            val am = ctx.getSystemService(AudioManager::class.java)
            val mode = am?.mode ?: AudioManager.MODE_NORMAL
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
            ) return
            val i = Intent(ctx, LockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            try { ctx.startActivity(i) } catch (_: Exception) {}
        }
    }

    private val now = mutableLongStateOf(System.currentTimeMillis())
    private val battery = mutableIntStateOf(-1)
    private var background: Bitmap? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
                Intent.ACTION_USER_PRESENT -> {
                    // Desbloqueo con huella o PIN: ya no hace falta mostrar esta pantalla
                    val km = getSystemService(KeyguardManager::class.java)
                    if (km?.isKeyguardSecure == true) finishQuietly()
                }
                else -> now.longValue = System.currentTimeMillis()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this) { }
        background = loadBackground()

        setContent {
            CalmaTheme {
                LockScreen(
                    nowMs = now.longValue,
                    battery = battery.intValue,
                    image = background,
                    onUnlock = { unlock() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val sticky = ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        sticky?.let { if (it.action == Intent.ACTION_BATTERY_CHANGED) readBattery(it) }
        now.longValue = System.currentTimeMillis()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) battery.intValue = (level * 100f / scale).roundToInt()
    }

    private fun loadBackground(): Bitmap? {
        val s = Prefs.lockImage
        if (s.isEmpty()) return null
        return try {
            val src = ImageDecoder.createSource(contentResolver, Uri.parse(s))
            val screenW = resources.displayMetrics.widthPixels
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                var sample = 1
                while (w / (sample * 2) >= screenW) sample *= 2
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) { null }
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { finishQuietly() }
                override fun onDismissError() { finishQuietly() }
                override fun onDismissCancelled() { /* sigue en la pantalla de bloqueo */ }
            })
        } else {
            finishQuietly()
        }
    }

    @Suppress("DEPRECATION")
    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun LockScreen(nowMs: Long, battery: Int, image: Bitmap?, onUnlock: () -> Unit) {
    val d = remember(nowMs, Prefs.lang, Prefs.use24h) { Dates(nowMs, Prefs.lang, Prefs.use24h) }
    val density = LocalDensity.current
    val big = with(density) { 132.dp.toSp() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(density) { 160.dp.toPx() }
    val progress = (-offsetY.value / limit).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (-offsetY.value > limit * 0.6f) {
                                onUnlock()
                                offsetY.animateTo(0f)
                            } else {
                                offsetY.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { offsetY.snapTo((offsetY.value + dy).coerceIn(-limit * 1.5f, 0f)) }
                    },
                )
            }
    ) {
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.45f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (offsetY.value * 0.6f).roundToInt()) }
                .alpha(1f - progress * 0.8f)
                .padding(top = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.weekdayShort, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(
                "${d.month.replaceFirstChar { it.uppercase() }} ${d.day}",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                d.hour2,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = big,
                    lineHeight = big,
                ),
                modifier = Modifier.offset(y = 12.dp),
            )
            Text(
                d.minute2,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = big,
                    lineHeight = big,
                    drawStyle = Stroke(width = with(density) { 2.5.dp.toPx() }),
                ),
                modifier = Modifier.offset(y = (-12).dp),
            )
            // Línea fina: se llena según la batería
            Box(Modifier.width(150.dp).height(2.dp).background(Color.White.copy(alpha = 0.15f))) {
                Box(
                    Modifier
                        .fillMaxWidth(if (battery in 0..100) battery / 100f else 0.6f)
                        .height(2.dp)
                        .background(Accent.copy(alpha = 0.75f))
                )
            }
            Spacer(Modifier.height(14.dp))
            if (Prefs.hasWeather) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Weather.short(Prefs.lang), color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Weather.icon(Prefs.wCode, Prefs.wDay), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            if (battery >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    BatteryGlyph(battery)
                    Spacer(Modifier.width(5.dp))
                    Text("$battery%", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .alpha(1f - progress),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            Text(
                if (Prefs.lang == "es") "Desliza hacia arriba" else "Swipe up to unlock",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = Poppins,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int) {
    Canvas(Modifier.size(width = 8.dp, height = 13.dp)) {
        val sw = 1.2.dp.toPx()
        val capH = 2.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.3f, 0f),
            size = Size(size.width * 0.4f, capH),
            cornerRadius = CornerRadius(1f, 1f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(sw / 2, capH),
            size = Size(size.width - sw, size.height - capH - sw / 2),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = sw),
        )
        val innerH = (size.height - capH - sw * 2.5f) * (level.coerceIn(0, 100) / 100f)
        drawRect(
            color = Color.White,
            topLeft = Offset(sw * 1.6f, size.height - sw * 1.6f - innerH),
            size = Size(size.width - sw * 3.2f, innerH),
        )
    }
}
