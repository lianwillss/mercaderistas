package com.rutamercaderistas.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.DownloadProgress
import com.rutamercaderistas.services.DownloadResult
import com.rutamercaderistas.services.UpdateNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_APK_URL = "apk_url"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_VERSION_TAG = "version_tag"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"
        const val KEY_RETRYABLE = "retryable"
        const val KEY_APK_PATH = "apk_path"
    }

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL)
        if (apkUrl.isNullOrBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "Falta la URL del APK"))
        }
        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty()
        var lastPercent = -1

        updateForeground(0, 0)

        val result = try {
            ApkDownloader.download(applicationContext, apkUrl) { progress: DownloadProgress ->
                setProgress(
                    workDataOf(
                        KEY_BYTES to progress.bytes,
                        KEY_TOTAL_BYTES to progress.totalBytes,
                        KEY_PERCENT to progress.percent,
                    ),
                )
                if (progress.percent != lastPercent) {
                    lastPercent = progress.percent
                    updateForeground(progress.percent, progress.totalBytes)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isStopped) return Result.failure()
            Timber.e(e, "Error inesperado descargando el APK")
            DownloadResult.Error("No se pudo descargar la actualización", retryable = true)
        }

        return when (result) {
            is DownloadResult.Success -> {
                if (isStopped) return Result.failure()
                val info = ApkDownloader.readApkInfo(applicationContext, result.file)
                if (info == null || info.versionCode <= BuildConfig.VERSION_CODE) {
                    ApkDownloader.clearDownloadedApk(applicationContext)
                    UpdateNotifier.cancelProgress(applicationContext)
                    Result.failure(
                        workDataOf(
                            KEY_ERROR to "El archivo descargado está dañado o no es más reciente",
                            KEY_RETRYABLE to true,
                        ),
                    )
                } else {
                    UpdateNotifier.cancelProgress(applicationContext)
                    UpdateNotifier.postReady(applicationContext, versionName)
                    setProgress(
                        workDataOf(
                            KEY_BYTES to result.file.length(),
                            KEY_TOTAL_BYTES to result.file.length(),
                            KEY_PERCENT to 100,
                        ),
                    )
                    Timber.i("Descarga completada: %s (%d)", info.versionName, info.versionCode)
                    Result.success(workDataOf(KEY_APK_PATH to result.file.absolutePath))
                }
            }
            is DownloadResult.Error -> {
                UpdateNotifier.cancelProgress(applicationContext)
                if (isStopped) return Result.failure()
                UpdateNotifier.postDownloadError(applicationContext, result.message)
                Result.failure(
                    workDataOf(
                        KEY_ERROR to result.message,
                        KEY_RETRYABLE to result.retryable,
                    ),
                )
            }
            DownloadResult.Cancelled -> {
                UpdateNotifier.cancelProgress(applicationContext)
                Result.failure(workDataOf(KEY_ERROR to "Descarga cancelada", KEY_RETRYABLE to true))
            }
        }
    }

    private suspend fun updateForeground(percent: Int, totalBytes: Long) {
        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty()
        val notification = UpdateNotifier.progressNotification(
            applicationContext,
            versionName,
            percent,
            totalBytes,
        )
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                UpdateNotifier.PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(UpdateNotifier.PROGRESS_NOTIFICATION_ID, notification)
        }
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Si el sistema no permite foreground (permiso negado, app en
            // segundo plano), la descarga sigue y el aviso persistente queda.
            Timber.w(e, "No se pudo publicar la notificación de progreso")
        }
    }
}
