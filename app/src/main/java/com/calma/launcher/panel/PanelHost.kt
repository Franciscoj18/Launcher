package com.calma.launcher.panel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Quien muestra el panel: la pantalla de inicio o el servicio que lo abre sobre otras apps. */
interface PanelHost {
    val hostContext: Context
    /** Cierra el panel con su animación y luego ejecuta [then]. */
    fun closePanel(then: (() -> Unit)? = null)
    /** Cierra el panel y abre una pantalla. */
    fun launch(intent: Intent)
    /** Cierra el panel y abre una notificación o el reproductor. */
    fun launchPending(pi: PendingIntent?, pkg: String)
    /** Captura, bloquear, panel del sistema… Devuelve false si falta activar el servicio. */
    fun global(action: Int): Boolean
    /** True si el teléfono está bloqueado (para ocultar las notificaciones). */
    val locked: Boolean
}
