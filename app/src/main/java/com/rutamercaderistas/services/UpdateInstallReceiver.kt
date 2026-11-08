package com.rutamercaderistas.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.rutamercaderistas.R

/**
 * Recibe el estado de la sesión de PackageInstaller. Si el sistema exige
 * confirmación, abre el intent de usuario que devuelve en EXTRA_INTENT;
 * si no, reporta éxito o el motivo del fallo al ViewModel.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkDownloader.ACTION_INSTALL_STATUS) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirmation == null) {
                    UpdateInstallBus.post(
                        InstallOutcome.Failed(
                            context.getString(R.string.update_instalacion_error),
                        ),
                    )
                    return
                }
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }.onFailure {
                    UpdateInstallBus.post(
                        InstallOutcome.Failed(
                            context.getString(R.string.update_instalacion_error),
                        ),
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallBus.post(InstallOutcome.Success)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    .orEmpty()
                    .ifBlank { context.getString(R.string.update_instalacion_error) }
                UpdateInstallBus.post(InstallOutcome.Failed(message))
            }
        }
    }
}
