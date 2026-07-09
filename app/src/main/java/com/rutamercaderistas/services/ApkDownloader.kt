package com.rutamercaderistas.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkDownloader {

    private const val TAG = "ApkDownloader"
    private const val APK_FILE_NAME = "update.apk"

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, APK_FILE_NAME)
            apkFile.delete()

            val ok = downloadToFile(apkUrl, apkFile, onProgress)
            if (!ok || !apkFile.exists() || apkFile.length() < 1024) {
                Log.e(TAG, "Descarga fallida o archivo muy peque\u00F1o: ${apkFile.length()}")
                return@withContext false
            }

            Log.i(TAG, "APK descargado: ${apkFile.length()} bytes")
            withContext(Dispatchers.Main) { installApk(context, apkFile) }
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error en downloadAndInstall", e)
            false
        }
    }

    private fun downloadToFile(
        url: String,
        destination: File,
        onProgress: (Int) -> Unit
    ): Boolean {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30000
            conn.readTimeout = 180000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")

            val contentLength = conn.contentLength
            Log.i(TAG, "Content-Length: $contentLength")

            val inputStream = conn.inputStream
            val outputStream = destination.outputStream()
            val buffer = ByteArray(65536)
            var reads: Int
            var total = 0L
            var lastPct = -1

            while (inputStream.read(buffer).also { reads = it } != -1) {
                outputStream.write(buffer, 0, reads)
                total += reads
                if (contentLength > 0) {
                    val pct = ((total * 100) / contentLength).toInt().coerceIn(0, 100)
                    if (pct > lastPct) {
                        lastPct = pct
                        val cb = onProgress
                        kotlinx.coroutines.runBlocking {
                            withContext(Dispatchers.Main) { cb(pct) }
                        }
                    }
                }
            }

            outputStream.close()
            inputStream.close()
            return destination.exists() && destination.length() > 0

        } catch (e: Exception) {
            Log.e(TAG, "Error en downloadToFile", e)
            destination.delete()
            return false
        } finally { conn?.disconnect() }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error instalando APK v\u00EDa ACTION_VIEW", e)
            try {
                val fallback = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setData(FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    ))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Error fallback instalaci\u00F3n", e2)
            }
        }
    }
}