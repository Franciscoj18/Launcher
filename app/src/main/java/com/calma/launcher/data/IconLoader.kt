package com.calma.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Prepara los íconos de las apps:
 * 1) Si hay paquete de íconos y trae el de esa app, usa ese.
 * 2) Si el estilo es "mono", convierte el ícono original en una silueta blanca.
 * 3) Si no, el ícono original.
 */
object IconLoader {
    private const val S = 144
    private val cache = LruCache<String, ImageBitmap>(400)
    private var pack: IconPack? = null
    private val lock = Any()

    fun cacheKey(e: AppEntry): String = "${e.key}|${Prefs.iconStyle}|${Prefs.iconPack}"

    fun peek(e: AppEntry): ImageBitmap? = cache.get(cacheKey(e))

    fun clear() = cache.evictAll()

    fun load(ctx: Context, e: AppEntry, style: String, packPkg: String): ImageBitmap {
        val ck = "${e.key}|$style|$packPkg"
        cache.get(ck)?.let { return it }
        val bmp = try { render(ctx, e, style, packPkg) } catch (t: Throwable) { blank() }
        val img = bmp.asImageBitmap()
        cache.put(ck, img)
        return img
    }

    private fun currentPack(ctx: Context, pkg: String): IconPack? = synchronized(lock) {
        if (pkg.isEmpty()) return null
        val p = pack
        if (p != null && p.pkg == pkg) return p
        val n = try { IconPack(ctx, pkg) } catch (t: Throwable) { null }
        pack = n
        n
    }

    private fun render(ctx: Context, e: AppEntry, style: String, packPkg: String): Bitmap {
        currentPack(ctx, packPkg)?.drawableFor(e.component)?.let { return drawToBitmap(it) }
        val density = ctx.resources.displayMetrics.densityDpi
        val icon = e.info.getIcon(density) ?: e.info.getBadgedIcon(density)
        return if (style == "mono") monochrome(icon) else drawToBitmap(icon)
    }

    private fun blank(): Bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)

    private fun drawToBitmap(d: Drawable): Bitmap {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val old = Rect(d.bounds)
        d.setBounds(0, 0, S, S)
        d.draw(c)
        d.bounds = old
        return b
    }

    /** Dibuja una capa de un ícono adaptable (que mide 1.5 veces la zona visible). */
    private fun drawLayer(d: Drawable, tint: Boolean): Bitmap {
        val b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pad = S / 4
        val d2 = d.mutate()
        if (tint) d2.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        d2.setBounds(-pad, -pad, S + pad, S + pad)
        d2.draw(c)
        return b
    }

    private fun monochrome(icon: Drawable): Bitmap {
        if (icon is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = icon.monochrome
                if (mono != null) return fit(drawLayer(mono, true), 0.80f)
            }
            val fg = icon.foreground
            if (fg != null) {
                val layer = drawLayer(fg, false)
                if (opaqueRatio(layer) > 0.01f) return fit(toWhite(layer), 0.80f)
            }
        }
        return fit(toWhite(drawToBitmap(icon)), 0.80f)
    }

    private fun opaqueRatio(b: Bitmap): Float {
        val px = IntArray(S * S)
        b.getPixels(px, 0, S, 0, 0, S, S)
        var n = 0
        for (p in px) if ((p ushr 24) > 128) n++
        return n / (S * S).toFloat()
    }

    /**
     * Convierte un ícono a color en una figura blanca:
     * - Si un color domina (la "placa" del ícono), queda la placa en blanco con el dibujo recortado.
     * - Si hay varios colores (como Chrome), queda la silueta con líneas que separan cada color.
     */
    private fun toWhite(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // Color dominante entre los píxeles visibles
        val buckets = IntArray(512)
        var opaque = 0
        for (p in px) {
            if ((p ushr 24) < 160) continue
            opaque++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            buckets[((r shr 5) shl 6) or ((g shr 5) shl 3) or (bl shr 5)]++
        }
        val out = IntArray(w * h)
        if (opaque == 0) {
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        var best = 0
        for (i in buckets.indices) if (buckets[i] > buckets[best]) best = i
        var sr = 0L; var sg = 0L; var sb = 0L; var cnt = 0
        for (p in px) {
            if ((p ushr 24) < 160) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            if ((((r shr 5) shl 6) or ((g shr 5) shl 3) or (bl shr 5)) == best) {
                sr += r; sg += g; sb += bl; cnt++
            }
        }
        val dr = (sr / max(cnt, 1)).toInt()
        val dg = (sg / max(cnt, 1)).toInt()
        val db = (sb / max(cnt, 1)).toInt()

        var close = 0
        for (p in px) {
            if ((p ushr 24) < 160) continue
            if (dist(p, dr, dg, db) < 60) close++
        }

        if (close >= opaque * 0.5f) {
            // Placa blanca con el dibujo recortado (o el dibujo, si es lo único que hay)
            for (i in px.indices) {
                val p = px[i]
                val a = p ushr 24
                if (a == 0) continue
                val d = dist(p, dr, dg, db)
                val k = (1f - (d - 40f) / 60f).coerceIn(0f, 1f)
                val na = (a * k).toInt()
                out[i] = (na shl 24) or 0xFFFFFF
            }
        } else {
            // Silueta con separaciones entre colores distintos
            val gap = BooleanArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val p = px[i]
                    if ((p ushr 24) < 128) continue
                    if (x + 1 < w) {
                        val q = px[i + 1]
                        if ((q ushr 24) >= 128 && diff(p, q) > 110) { gap[i] = true; gap[i + 1] = true }
                    }
                    if (y + 1 < h) {
                        val q = px[i + w]
                        if ((q ushr 24) >= 128 && diff(p, q) > 110) { gap[i] = true; gap[i + w] = true }
                    }
                }
            }
            for (i in px.indices) {
                val a = px[i] ushr 24
                if (a == 0 || gap[i]) continue
                out[i] = (a shl 24) or 0xFFFFFF
            }
        }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.setPixels(out, 0, w, 0, 0, w, h)
        return b
    }

    private fun dist(p: Int, r: Int, g: Int, b: Int): Int {
        return abs(((p shr 16) and 0xFF) - r) + abs(((p shr 8) and 0xFF) - g) + abs((p and 0xFF) - b)
    }

    private fun diff(p: Int, q: Int): Int {
        return abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)) +
            abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)) +
            abs((p and 0xFF) - (q and 0xFF))
    }

    /** Recorta lo transparente y centra la figura para que todos los íconos midan parecido. */
    private fun fit(src: Bitmap, fill: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 40) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return src
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val target = S * fill
        val scale = min(target / bw, target / bh)
        val dw = (bw * scale).toInt()
        val dh = (bh * scale).toInt()
        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val dst = Rect((S - dw) / 2, (S - dh) / 2, (S - dw) / 2 + dw, (S - dh) / 2 + dh)
        c.drawBitmap(src, Rect(minX, minY, maxX + 1, maxY + 1), dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
