package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val downloadManager: UpdateDownloadManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val info = UpdateChecker.check(BuildConfig.VERSION_CODE)

            if (info.error != null) {
                // Un fallo de red no debe borrar el pendiente ni el aviso.
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
                // Se persiste incluso si ya se notificó: así el banner y el
                // pendiente sobreviven a cierres y reinicios.
                preferencesRepository.setPendingUpdate(pending)

                if (info.tag.isNotBlank() &&
                    preferencesRepository.getLastNotifiedUpdateTag() == info.tag
                ) {
                    Timber.i("Update %s ya notificado", info.versionName)
                    UpdateNotifier.postPending(applicationContext, info.versionName)
                    return Result.success()
                }
                if (info.tag.isNotBlank()) {
                    preferencesRepository.setLastNotifiedUpdateTag(info.tag)
                }
                Timber.i("Update disponible en segundo plano: %s", info.versionName)
                UpdateNotifier.postPending(applicationContext, info.versionName)
            } else {
                // La app ya está al día: el aviso y el APK sobran.
                preferencesRepository.clearPendingUpdate()
                downloadManager.cancel()
                UpdateNotifier.cancelUpdate(applicationContext)
                ApkDownloader.clearDownloadedApk(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error en UpdateCheckWorker")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
