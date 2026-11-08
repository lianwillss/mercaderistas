package com.rutamercaderistas.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.rutamercaderistas.R
import timber.log.Timber

/**
 * Recibe el estado de la sesión de PackageInstaller. Si el sistema exige
 * confirmación, abre el intent de usuario que devuelve en EXTRA_INTENT;
 * si no, reporta éxito o el motivo del fallo al ViewModel.
 *
 * El mensaje de Android (EXTRA_STATUS_MESSAGE) no se muestra: se traduce el
 * código a un texto claro y el original queda solo en el log.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkDownloader.ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirmation == null) {
                    Timber.w("Instalación sin intent de confirmación del sistema")
                    UpdateInstallBus.post(
                        InstallOutcome.Failed(
                            context.getString(R.string.update_instalacion_error),
                        ),
                    )
                    return
                }
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }.onFailure {
                    Timber.w(it, "No se pudo abrir la confirmación de instalación")
                    UpdateInstallBus.post(
                        InstallOutcome.Failed(
                            context.getString(R.string.update_instalacion_error),
                        ),
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallBus.post(InstallOutcome.Success)
            else -> {
                val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.w(
                    "Instalación fallida: %s (%d)",
                    UpdateInstallErrors.statusName(status),
                    status,
                )
                Timber.d("Mensaje técnico del instalador: %s", raw)
                UpdateInstallBus.post(
                    InstallOutcome.Failed(
                        context.getString(UpdateInstallErrors.messageRes(status)),
                    ),
                )
            }
        }
    }
}
