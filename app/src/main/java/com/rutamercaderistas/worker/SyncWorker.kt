package com.rutamercaderistas.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.Constants
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruteroManager: RuteroManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ts = System.currentTimeMillis()
            val url = "${Constants.DRIVE_EXPORT_URL}&ts=$ts"
            val bytes = downloadBytes(url)

            if (bytes == null) {
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val changed = ruteroManager.saveMasterExcel(bytes)
            if (changed) {
                ruteroManager.invalidateAllCaches()
                RuteroRepository.clear()
                val ok = ruteroManager.createIndex()
                if (ok) {
                    Log.d("SyncWorker", "Background sync completed successfully")
                    Result.success()
                } else {
                    Result.failure()
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error in background sync", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray? {
        var currentUrl = url
        var limit = 5
        while (limit > 0) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                    currentUrl = conn.getHeaderField("Location") ?: return null
                    limit--
                    continue
                }
                return conn.inputStream.use { it.readBytes() }
            } catch (_: Exception) { return null }
            finally { conn?.disconnect() }
        }
        return null
    }
}
