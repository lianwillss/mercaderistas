package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.InstalledVersion
import com.rutamercaderistas.services.UpdateChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Chequeo periódico silencioso. No notifica ni descarga: solo deja el
 * pendiente persistido para que la próxima apertura muestre la tarjeta
 * dentro de la app. La descarga la decide el usuario/el arranque en el
 * ViewModel, que es el único dueño del estado visible.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val installed = InstalledVersion.read(applicationContext)
            val rejected = preferencesRepository.getRejectedUpdateTag()
            val info = UpdateChecker.check(installed, rejected)

            if (info.error != null) {
                // Un fallo de red no debe borrar el pendiente.
                Timber.w("No se pudo comprobar la actualización: %s", info.error)
                return if (runAttemptCount < 2) Result.retry() else Result.success()
            }

            preferencesRepository.setLastUpdateCheck(System.currentTimeMillis())

            if (info.available) {
                val pending = PendingUpdate(
                    versionName = info.versionName,
                    versionCode = info.versionCode,
                    apkUrl = info.apkUrl,
                    versionTag = info.tag,
                    apkSize = info.apkSize,
                    apkSha256 = info.apkSha256,
                )
                // Se persiste para que el banner sobreviva a cierres.
                preferencesRepository.setPendingUpdate(pending)
                Timber.i("Update disponible en segundo plano: %s", info.versionName)
            } else {
                // La app ya está al día: el pendiente y el APK sobran.
                preferencesRepository.clearPendingUpdate()
                ApkDownloader.clearDownloadedApk(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error en UpdateCheckWorker")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
