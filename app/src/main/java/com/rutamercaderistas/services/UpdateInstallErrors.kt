package com.rutamercaderistas.services

import android.content.pm.PackageInstaller
import com.rutamercaderistas.R

/**
 * Traduce los códigos de PackageInstaller a mensajes propios. El texto que
 * manda Android en EXTRA_STATUS_MESSAGE es técnico (y a veces en inglés):
 * se deja solo para logs, nunca para la tarjeta.
 */
internal object UpdateInstallErrors {

    /** Mensaje visible y si tiene sentido volver a intentar. */
    fun messageRes(status: Int): Int = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.update_instalacion_bloqueada
        PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_instalacion_cancelada
        PackageInstaller.STATUS_FAILURE_INVALID -> R.string.update_instalacion_apk_invalido
        PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.update_instalacion_conflicto
        PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_instalacion_sin_espacio
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.update_instalacion_incompatible
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> R.string.update_instalacion_timeout
        else -> R.string.update_instalacion_error
    }

    /** Nombre legible del código para logs. */
    fun statusName(status: Int): String = when (status) {
        PackageInstaller.STATUS_SUCCESS -> "STATUS_SUCCESS"
        PackageInstaller.STATUS_PENDING_USER_ACTION -> "STATUS_PENDING_USER_ACTION"
        PackageInstaller.STATUS_FAILURE -> "STATUS_FAILURE"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "STATUS_FAILURE_BLOCKED"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "STATUS_FAILURE_ABORTED"
        PackageInstaller.STATUS_FAILURE_INVALID -> "STATUS_FAILURE_INVALID"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "STATUS_FAILURE_CONFLICT"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "STATUS_FAILURE_STORAGE"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "STATUS_FAILURE_INCOMPATIBLE"
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> "STATUS_FAILURE_TIMEOUT"
        else -> "STATUS_DESCONOCIDO"
    }
}
