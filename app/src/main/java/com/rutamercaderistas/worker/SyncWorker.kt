package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruteroManager: RuteroManager,
    private val repository: RuteroRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ts = System.currentTimeMillis()
            val url = "${Constants.DRIVE_EXPORT_URL}&ts=$ts"
            val bytes = downloadBytes(url = url).getOrNull()

            if (bytes == null) {
                Timber.w("downloadBytes devolvió null en SyncWorker (intento %d)", runAttemptCount)
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val changed = ruteroManager.saveMasterExcel(bytes)
            if (changed) {
                val ok = ruteroManager.createIndex()
                if (ok) {
                    repository.clear()
                    Timber.d("Background sync completed successfully")
                    Result.success()
                } else {
                    Result.failure()
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in background sync")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
