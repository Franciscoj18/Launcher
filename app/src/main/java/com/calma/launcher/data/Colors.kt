package com.calma.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

/** Colores sacados de imágenes (portadas y fondos). */
object Colors {

    /** Color más vivo y representativo de una imagen. Devuelve null si es casi gris. */
    fun vivid(src: Bitmap): Int? {
        val small = try { Bitmap.createScaledBitmap(src, 24, 24, true) } catch (e: Exception) { return null }
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        val score = FloatArray(12)
        val sumH = FloatArray(12)
        val sumS = FloatArray(12)
        val sumV = FloatArray(12)
        val count = IntArray(12)
        val hsv = FloatArray(3)
        for (p in px) {
            Color.colorToHSV(p, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.15f) continue
            val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val w = s * s * v
            score[bucket] += w
            sumH[bucket] += hsv[0]
            sumS[bucket] += s
            sumV[bucket] += v
            count[bucket]++
        }
        var best = -1
        for (i in 0 until 12) if (count[i] > 0 && (best < 0 || score[i] > score[best])) best = i
        if (best < 0 || score[best] < 0.6f) return null
        val n = count[best].toFloat()
        val avgS = sumS[best] / n
        if (avgS < 0.18f) return null
        return Color.HSVToColor(floatArrayOf(sumH[best] / n, avgS, sumV[best] / n))
    }

    /** Ajusta un color para que se lea bien sobre un fondo oscuro. */
    fun readable(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = hsv[1].coerceIn(0.40f, 0.75f)
        hsv[2] = hsv[2].coerceAtLeast(0.90f)
        return Color.HSVToColor(hsv)
    }

    /** Color de acento automático a partir del fondo de pantalla. */
    fun fromWallpaper(ctx: Context, custom: Bitmap?): Int? {
        if (custom != null) vivid(custom)?.let { return readable(it) }
        return try {
            val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
            val options = listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor).map { it.toArgb() }
            val hsv = FloatArray(3)
            val pick = options.maxByOrNull { Color.colorToHSV(it, hsv); hsv[1] } ?: return null
            readable(pick)
        } catch (e: Exception) { null }
    }
}
