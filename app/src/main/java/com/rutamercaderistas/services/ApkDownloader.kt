package com.rutamercaderistas.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.rutamercaderistas.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ApkDownloader {

    private const val APK_FILE_NAME = "update.apk"
    private const val MIN_APK_BYTES = 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, APK_FILE_NAME)
            apkFile.delete()

            val freeBytes = apkFile.parentFile?.freeSpace ?: 0L
            if (freeBytes < 200_000_000L) {
                Timber.w("Poco espacio en caché: %d MB libres", freeBytes / 1_000_000)
                return@withContext "Espacio insuficiente en el dispositivo"
            }

            val downloadResult = downloadToFile(apkUrl, apkFile, onProgress)
            if (downloadResult != null) {
                return@withContext downloadResult
            }

            if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
                Timber.e("APK inválido: %d bytes", apkFile.length())
                return@withContext "Archivo de actualización corrupto"
            }

            Timber.i("APK descargado: %d bytes", apkFile.length())

            installApk(context, apkFile)
            null
        } catch (e: Exception) {
            Timber.e(e, "Error en downloadAndInstall")
            "Error inesperado: ${e.localizedMessage ?: "desconocido"}"
        }
    }

    private fun downloadToFile(
        url: String,
        destination: File,
        onProgress: (Int) -> Unit
    ): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("HTTP %d al descargar APK", response.code)
                return "Error de servidor (HTTP ${response.code})"
            }

            val body = response.body ?: return "Respuesta vacía del servidor"
            val contentLength = body.contentLength()
            Timber.i("APK Content-Length: %d", contentLength)

            body.byteStream().use { input ->
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

            if (!destination.exists() || destination.length() == 0L) {
                return "No se pudo guardar el archivo"
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Error en downloadToFile")
            destination.delete()
            "Error de conexión: ${e.localizedMessage ?: "desconocido"}"
        }
    }

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
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
