package com.rutamercaderistas.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

suspend fun downloadBytes(
    url: String,
    connectTimeout: Int = 30_000,
    readTimeout: Int = 60_000,
    maxRedirects: Int = 5,
): Result<ByteArray> = withContext(Dispatchers.IO) {
    var currentUrl = url
    var limit = maxRedirects
    while (limit > 0) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
            )
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val location = conn.getHeaderField("Location")
                    ?: return@withContext Result.failure(Exception("Redirect sin Location"))
                if (!location.startsWith("http://") && !location.startsWith("https://")) {
                    try {
                        currentUrl = URI(currentUrl).resolve(location).toString()
                    } catch (_: Exception) {
                        return@withContext Result.failure(Exception("Redirect inválido: $location"))
                    }
                } else {
                    currentUrl = location
                }
                conn.disconnect()
                conn = null
                limit--
                continue
            }
            return@withContext Result.success(conn.inputStream.use { it.readBytes() })
        } catch (e: SocketTimeoutException) {
            Timber.w(e, "Timeout conectando a %s", currentUrl)
            return@withContext Result.failure(e)
        } catch (e: UnknownHostException) {
            Timber.w(e, "Sin conexión a %s", currentUrl)
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Timber.w(e, "Error descargando %s", currentUrl)
            return@withContext Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
    return@withContext Result.failure(Exception("Demasiados redirects"))
}
