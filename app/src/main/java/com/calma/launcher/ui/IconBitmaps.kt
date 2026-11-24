package com.calma.launcher.ui

import android.content.Context
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Íconos de notificaciones ya convertidos a imagen, guardados en memoria.
 * Se preparan en segundo plano para que la isla, el bloqueo y el panel nunca se traben.
 */
object IconBitmaps {
    private val cache = LruCache<String, ImageBitmap>(120)

    fun key(icon: Icon?, pkg: String?, tint: Boolean, size: Int): String? {
        val base = when {
            icon != null -> try {
                if (icon.type == Icon.TYPE_RESOURCE) "r:${icon.resPackage}:${icon.resId}"
                else "o:${System.identityHashCode(icon)}"
            } catch (e: Exception) { "o:${System.identityHashCode(icon)}" }
            !pkg.isNullOrEmpty() -> "p:$pkg"
            else -> return null
        }
        return "$base:$tint:$size"
    }

    fun peek(key: String?): ImageBitmap? = key?.let { cache.get(it) }

    fun load(ctx: Context, icon: Icon?, pkg: String?, tint: Boolean, size: Int, key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        return try {
            val d = icon?.loadDrawable(ctx)
                ?: pkg?.takeIf { it.isNotEmpty() }?.let { ctx.packageManager.getApplicationIcon(it) }
                ?: return null
            val dd = d.mutate()
            if (tint) dd.setTint(android.graphics.Color.WHITE)
            val img = dd.toBitmap(size, size).asImageBitmap()
            cache.put(key, img)
            img
        } catch (e: Exception) { null }
    }
}

/** Ícono listo para dibujar; mientras se prepara devuelve null (no bloquea la pantalla). */
@Composable
fun rememberIconBitmap(icon: Icon?, tint: Boolean, size: Int = 64, fallbackPkg: String? = null): ImageBitmap? {
    val ctx = LocalContext.current
    val key = IconBitmaps.key(icon, fallbackPkg, tint, size)
    val img by produceState(initialValue = IconBitmaps.peek(key), key) {
        if (value == null && key != null) {
            value = withContext(Dispatchers.IO) { IconBitmaps.load(ctx, icon, fallbackPkg, tint, size, key) }
        }
    }
    return img
}
