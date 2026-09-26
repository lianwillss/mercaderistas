package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.data.preferences.PreferencesRepository
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
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return ruteroManager.withSyncLock {
            try {
                val activeRoute = repository.getActiveRuteroName()
                val ts = System.currentTimeMillis()
                val url = "${Constants.DRIVE_EXPORT_URL}&ts=$ts"
                val bytes = downloadBytes(url = url).getOrElse { error ->
                    Timber.w(error, "Error descargando Excel para sync (intento %d)", runAttemptCount)
                    null
                }

                if (bytes == null) {
                    Timber.w("downloadBytes devolvió null en SyncWorker (intento %d)", runAttemptCount)
                    return@withSyncLock if (runAttemptCount < 3) Result.retry() else Result.failure()
                }

                val changed = ruteroManager.saveMasterExcel(bytes)
                if (!changed) {
                    Timber.w("El Excel descargado no pasó la validación")
                    return@withSyncLock if (runAttemptCount < 3) Result.retry() else Result.failure()
                }

                val ok = ruteroManager.createIndex()
                if (ok) {
                    preferencesRepository.setLastSyncTime(System.currentTimeMillis())
                    val routes = ruteroManager.loadIndex()
                        val routeToReload = activeRoute?.takeIf { it in routes } ?: routes.firstOrNull()
                        if (routeToReload != null) {
                            val entries = ruteroManager.loadRoute(routeToReload)
                            if (entries.isNotEmpty()) {
                                repository.setEntries(entries, routeToReload)
                            } else if (activeRoute != null) {
                                repository.clear()
                            }
                        } else if (activeRoute != null) {
                            repository.clear()
                        }
                        Timber.d("Background sync completed successfully")
                        Result.success()
                } else {
                    Timber.w("La indexación del Excel falló")
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in background sync")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
