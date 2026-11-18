package com.calma.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tu imagen de inicio elegida en Calma (para el efecto de profundidad),
 * una copia difuminada para el cajón de apps y el color de acento automático.
 */
object HomeWallpaper {
    var image by mutableStateOf<Bitmap?>(null)
        private set
    var blurred by mutableStateOf<Bitmap?>(null)
        private set
    /** Color de acento calculado del fondo (para el modo automático). */
    var autoAccent by mutableStateOf<Color?>(null)
        private set

    private var loadedUri = "-"
    private var colorsListener: WallpaperManager.OnColorsChangedListener? = null

    suspend fun load(ctx: Context) {
        val uri = Prefs.homeImage
        if (uri == loadedUri) return
        loadedUri = uri
        val pair = withContext(Dispatchers.IO) {
            if (uri.isEmpty()) return@withContext null
            try {
                val src = ImageDecoder.createSource(ctx.contentResolver, Uri.parse(uri))
                val dm = ctx.resources.displayMetrics
                val targetW = (dm.widthPixels * 1.15f).toInt()
                val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    var sample = 1
                    while (info.size.width / (sample * 2) >= targetW) sample *= 2
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                bmp to blur(bmp)
            } catch (e: Exception) { null }
        }
        image = pair?.first
        blurred = pair?.second
        refreshAccent(ctx)
    }

    fun refreshAccent(ctx: Context) {
        val c = Colors.fromWallpaper(ctx, image)
        autoAccent = c?.let { Color(it) }
    }

    fun watchSystemColors(ctx: Context) {
        if (colorsListener != null) return
        val l = WallpaperManager.OnColorsChangedListener { _, _ -> refreshAccent(ctx) }
        colorsListener = l
        try {
            WallpaperManager.getInstance(ctx).addOnColorsChangedListener(l, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {}
    }

    /** Difuminado barato: se reduce mucho la imagen y se vuelve a agrandar con suavizado. */
    private fun blur(src: Bitmap): Bitmap {
        val w = (src.width / 14).coerceAtLeast(8)
        val h = (src.height / 14).coerceAtLeast(8)
        val tiny = Bitmap.createScaledBitmap(src, w, h, true)
        val mid = Bitmap.createScaledBitmap(tiny, w * 3, h * 3, true)
        val out = Bitmap.createBitmap(src.width / 2, src.height / 2, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(
            mid,
            android.graphics.Rect(0, 0, mid.width, mid.height),
            android.graphics.Rect(0, 0, out.width, out.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }
}
