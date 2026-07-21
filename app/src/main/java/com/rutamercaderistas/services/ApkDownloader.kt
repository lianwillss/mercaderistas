package com.rutamercaderistas.services

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Error(val message: String) : DownloadResult
}

object ApkDownloader {

    private const val APK_FILE_NAME = "update.apk"
    private const val MIN_APK_BYTES = 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val apkDir = File(context.cacheDir, "apk").also { it.mkdirs() }
            val apkFile = File(apkDir, APK_FILE_NAME)
            apkFile.delete()

            val freeBytes = apkFile.parentFile?.freeSpace ?: 0L
            if (freeBytes < 200_000_000L) {
                Timber.w("Poco espacio en caché: %d MB libres", freeBytes / 1_000_000)
                return@withContext DownloadResult.Error("Espacio insuficiente en el dispositivo")
            }

            val request = Request.Builder().url(apkUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("HTTP %d al descargar APK", response.code)
                return@withContext DownloadResult.Error("Error de servidor (HTTP ${response.code})")
            }

            val body = response.body ?: return@withContext DownloadResult.Error("Respuesta vacía del servidor")
            val contentLength = body.contentLength()
            Timber.i("APK Content-Length: %d", contentLength)

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(65536)
                    var reads: Int
                    var total = 0L
                    var lastPct = -1

                    while (input.read(buffer).also { reads = it } != -1) {
                        output.write(buffer, 0, reads)
                        total += reads
                        if (contentLength > 0) {
                            val pct = ((total * 100) / contentLength).toInt().coerceIn(0, 100)
                            if (pct > lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }

            if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
                Timber.e("APK inválido: %d bytes", apkFile.length())
                return@withContext DownloadResult.Error("Archivo de actualización corrupto")
            }

            Timber.i("APK descargado: %d bytes", apkFile.length())
            DownloadResult.Success(apkFile)
        } catch (e: Exception) {
            Timber.e(e, "Error en download")
            DownloadResult.Error("Error de conexión: ${e.localizedMessage ?: "desconocido"}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
