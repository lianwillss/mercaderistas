package com.rutamercaderistas.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import com.rutamercaderistas.MainActivity
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApkDownloader {

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
                Timber.e("Descarga fallida o archivo muy pequeño: %d", apkFile.length())
                return@withContext false
            }

            Timber.i("APK descargado: %d bytes", apkFile.length())

            try {
                installApk(context, apkFile)
                true
            } catch (e: Exception) {
                Timber.e(e, "Error instalando APK")
                apkFile.delete()
                false
            }

        } catch (e: Exception) {
            Timber.e(e, "Error en downloadAndInstall")
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
            Timber.i("Content-Length: %d", contentLength)

            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
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
            return destination.exists() && destination.length() > 0

        } catch (e: Exception) {
            Timber.e(e, "Error en downloadToFile")
            destination.delete()
            return false
        } finally { conn?.disconnect() }
    }

    @Throws(Exception::class)
    private fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            installViaPackageInstaller(context, apkFile)
        } else {
            installViaIntent(context, apkFile)
        }
    }

    private fun installViaPackageInstaller(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            apkFile.inputStream().use { input ->
                session.openWrite("apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, sessionId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun installViaIntent(context: Context, apkFile: File) {
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
    }
}
