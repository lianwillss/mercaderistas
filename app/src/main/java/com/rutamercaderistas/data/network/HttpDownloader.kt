package com.rutamercaderistas.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

private const val DEFAULT_MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

class DownloadSizeLimitException(val maxBytes: Long) :
    IOException("Descarga excede el límite de $maxBytes bytes")

suspend fun headForETag(
    url: String,
    connectTimeout: Int = 10_000,
    readTimeout: Int = 10_000,
): String? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        conn.connect()
        if (conn.responseCode in 200..299) {
            return@withContext conn.getHeaderField("ETag") ?: conn.getHeaderField("etag")
                ?: conn.getHeaderField("Last-Modified")
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

suspend fun downloadBytes(
    url: String,
    connectTimeout: Int = 30_000,
    readTimeout: Int = 60_000,
    maxRedirects: Int = 5,
    maxBytes: Long = DEFAULT_MAX_DOWNLOAD_BYTES,
): Result<ByteArray> = withContext(Dispatchers.IO) {
    if (maxBytes <= 0) {
        return@withContext Result.failure(IllegalArgumentException("maxBytes debe ser mayor que cero"))
    }
    if (maxRedirects < 0) {
        return@withContext Result.failure(IllegalArgumentException("maxRedirects no puede ser negativo"))
    }

    var currentUrl = url
    var redirects = 0
    while (redirects <= maxRedirects) {
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
                if (redirects >= maxRedirects) {
                    return@withContext Result.failure(IOException("Demasiados redirects"))
                }
                val location = conn.getHeaderField("Location")
                    ?: return@withContext Result.failure(IOException("Redirect sin Location"))
                val redirectUrl = try {
                    URI(currentUrl).resolve(location).toURL()
                } catch (e: Exception) {
                    return@withContext Result.failure(IOException("Redirect inválido: $location", e))
                }
                if (redirectUrl.protocol != "http" && redirectUrl.protocol != "https") {
                    return@withContext Result.failure(IOException("Protocolo de redirect inválido"))
                }
                currentUrl = redirectUrl.toString()
                conn.disconnect()
                conn = null
                redirects++
                continue
            }

            if (status !in 200..299) {
                return@withContext Result.failure(HttpStatusException(status))
            }

            val contentLength = conn.getHeaderFieldLong("Content-Length", -1L)
            if (contentLength > maxBytes) {
                return@withContext Result.failure(DownloadSizeLimitException(maxBytes))
            }
            val initialCapacity = if (contentLength in 1..Int.MAX_VALUE.toLong()) {
                contentLength.toInt()
            } else {
                8192
            }
            val output = ByteArrayOutputStream(initialCapacity)
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var totalBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (totalBytes > maxBytes - count) {
                        return@withContext Result.failure(DownloadSizeLimitException(maxBytes))
                    }
                    output.write(buffer, 0, count)
                    totalBytes += count
                }
            }
            return@withContext Result.success(output.toByteArray())
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
    Result.failure(IOException("Demasiados redirects"))
}
