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

    private val APK_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    suspend fun downloadAndInstall(
        context: Context,
        apkFileId: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, APK_FILE_NAME)
            apkFile.delete()

            val realUrl = resolveApkUrl(apkFileId)
            if (realUrl == null) {
                Log.e(TAG, "No se pudo resolver URL del APK")
                return@withContext false
            }

            Log.i(TAG, "URL final: $realUrl")
            val ok = downloadToFile(realUrl, apkFile, onProgress)
            if (!ok || !apkFile.exists() || apkFile.length() < 1024) {
                Log.e(TAG, "Descarga fallida o archivo muy peque\u00F1o: ${apkFile.length()}")
                return@withContext false
            }

            if (!isValidApk(apkFile)) {
                Log.e(TAG, "El archivo descargado no es un APK v\u00E1lido")
                apkFile.delete()
                return@withContext false
            }

            Log.i(TAG, "APK v\u00E1lido descargado: ${apkFile.length()} bytes")
            withContext(Dispatchers.Main) { installApk(context, apkFile) }
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error en downloadAndInstall", e)
            false
        }
    }

    private fun resolveApkUrl(fileId: String): String? {
        val initialUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        var attempts = 5
        var url = initialUrl

        while (attempts > 0) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")

                when (conn.responseCode) {
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    303, 307, 308 -> {
                        url = conn.getHeaderField("Location") ?: return null
                        attempts--
                        continue
                    }
                    HttpURLConnection.HTTP_OK -> {
                        val contentType = conn.contentType ?: ""
                        val contentLength = conn.contentLength

                        if (contentType.startsWith("text/html") || (contentLength in 1 until 1024 * 1024)) {
                            val html = conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                            val uuid = extractField(html, "uuid")
                            val confirm = extractConfirm(html)

                            if (confirm != null) {
                                url = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$confirm"
                                if (uuid != null) url += "&uuid=$uuid"
                                attempts--
                                continue
                            }
                            if (uuid != null) {
                                url = "$initialUrl&uuid=$uuid"
                                attempts--
                                continue
                            }
                            return null
                        }
                        return url
                    }
                    else -> return null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error en resolveApkUrl", e)
                return null
            } finally { conn?.disconnect() }
        }
        return null
    }

    private fun extractField(html: String, name: String): String? {
        val marker = """name="$name" value="""
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        val end = html.indexOf('"', start)
        return if (end > start) html.substring(start, end) else null
    }

    private fun extractConfirm(html: String): String? {
        val actionMarker = """action=""""
        var idx = html.indexOf(actionMarker)
        if (idx < 0) return null
        val start = idx + actionMarker.length
        val end = html.indexOf('"', start)
        if (end <= start) return null
        val action = html.substring(start, end)
        val confirmKey = "confirm="
        val ci = action.indexOf(confirmKey)
        if (ci < 0) return null
        val cs = ci + confirmKey.length
        val ce = action.indexOf('&', cs)
        return if (ce > cs) action.substring(cs, ce) else action.substring(cs)
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
            Log.i(TAG, "Content-Length: $contentLength, Content-Type: ${conn.contentType}")

            val inputStream = conn.inputStream
            val outputStream = destination.outputStream()
            val buffer = ByteArray(65536)
            var reads: Int
            var total = 0L
            var lastPct = -1

            while (inputStream.read(buffer).also { reads = it } != -1) {
                outputStream.write(buffer, 0, reads)
                total += reads

                if (total < 200 && reads > 0) {
                    val head = String(buffer, 0, minOf(reads, 200), Charsets.UTF_8)
                    if (head.contains("<!DOCTYPE") || head.contains("<html")) {
                        Log.e(TAG, "Stream comienza con HTML, cancelando")
                        outputStream.close()
                        inputStream.close()
                        destination.delete()
                        return false
                    }
                }

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

    private fun isValidApk(file: File): Boolean {
        try {
            val magic = ByteArray(4)
            val stream = file.inputStream()
            var offset = 0
            while (offset < 4) {
                val read = stream.read(magic, offset, 4 - offset)
                if (read < 0) return false
                offset += read
            }
            stream.close()
            return magic.contentEquals(APK_MAGIC)
        } catch (e: Exception) {
            return false
        }
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