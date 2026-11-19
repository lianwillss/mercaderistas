package com.rutamercaderistas.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rutamercaderistas.MainActivity
import timber.log.Timber

/**
 * Tras una actualización Android reemplaza el APK y mata el proceso. Este
 * receiver escucha ACTION_MY_PACKAGE_REPLACED y hace un intento de reabrir la
 * app para que el usuario no quede en el launcher.
 *
 * Sin notificaciones: si el sistema bloquea el arranque desde background
 * (Android 10+), el intento falla en silencio y la próxima apertura manual
 * sigue mostrando "Actualizado a X" desde pending_upgrade_notice.
 */
class UpdatePackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Timber.w(it, "No se pudo reabrir la app tras actualizarse") }
    }
}
