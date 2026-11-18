package com.calma.launcher.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Vuelve a encender la pantalla de bloqueo al reiniciar o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> LockService.sync(context)
        }
    }
}
