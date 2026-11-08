package com.rutamercaderistas.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Error(val message: String, val retryable: Boolean = true) : DownloadResult
    data object Cancelled : DownloadResult
}

data class DownloadProgress(
    val bytes: Long,
    val totalBytes: Long,
    val percent: Int,
)

data class ApkInfo(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
)

/**
 * Descarga reanudable en filesDir/updates. El .part sobrevive a cierres,
 * reinicios y a que Android limpie la caché; el worker continúa con Range.
 */
object ApkDownloader {

    const val UPDATES_DIR = "updates"
    const val APK_NAME = "update.apk"
    const val PART_NAME = "update.apk.part"

    private const val MIN_APK_BYTES = 1024L
    private const val DISK_MARGIN_BYTES = 20L * 1024 * 1024
    private const val UNKNOWN_SIZE_MIN_FREE = 200L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun apkDir(context: Context): File = File(context.filesDir, UPDATES_DIR)

    fun apkFile(context: Context): File = File(apkDir(context), APK_NAME)

    fun partialFile(context: Context): File = File(apkDir(context), PART_NAME)

    fun clearDownloadedApk(context: Context) {
        runCatching { apkDir(context).listFiles()?.forEach { it.deleteRecursively() } }
    }

    fun clearFinalApk(context: Context) {
        runCatching { apkFile(context).delete() }
    }

    fun clearPartial(context: Context) {
        runCatching { partialFile(context).delete() }
    }

    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadResult = downloadInternal(context, apkUrl, onProgress, allowRangeRestart = true)

    private suspend fun downloadInternal(
        context: Context,
        apkUrl: String,
        onProgress: suspend (DownloadProgress) -> Unit,
        allowRangeRestart: Boolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!isOnline(context)) {
            return@withContext DownloadResult.Error(
                context.getString(R.string.update_sin_internet),
                retryable = true,
            )
        }

        val dir = apkDir(context)
        if (!dir.exists() && !dir.mkdirs()) {
            return@withContext DownloadResult.Error(
                context.getString(R.string.update_error_guardar),
                retryable = true,
            )
        }

        val apk = File(dir, APK_NAME)
        val part = File(dir, PART_NAME)
        // Si ya hay un APK completo, se revalida o se descarta.
        if (apk.exists()) apk.delete()

        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(apkUrl)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Timber.w(e, "Sin conexión al descargar el APK")
            return@withContext DownloadResult.Error(
                context.getString(R.string.update_sin_internet),
                retryable = true,
            )
        }

        response.use { res ->
            if (res.code == 416 && existing > 0) {
                part.delete()
                return@withContext if (allowRangeRestart) {
                    downloadInternal(context, apkUrl, onProgress, allowRangeRestart = false)
                } else {
                    DownloadResult.Error(
                        context.getString(R.string.update_descarga_interrumpida),
                        retryable = true,
                    )
                }
            }

            if (res.code != 200 && res.code != 206) {
                Timber.e("HTTP %d al descargar APK", res.code)
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_error_servidor, res.code),
                    retryable = res.code >= 500 || res.code == 429,
                )
            }

            val resume = existing > 0 && res.code == 206
            if (!resume) part.delete()
            val already = if (resume) existing else 0L

            val body = res.body
            val totalFromRange = parseContentRangeTotal(res.header("Content-Range"))
            val bodyLength = body.contentLength()
            val total = when {
                resume -> totalFromRange ?: (already + bodyLength).takeIf { bodyLength > 0 } ?: 0L
                bodyLength > 0 -> bodyLength
                else -> 0L
            }

            val free = dir.usableSpace
            if (total > 0) {
                val needed = (total - already).coerceAtLeast(0L) + DISK_MARGIN_BYTES
                if (free < needed) {
                    val missingMb = ((needed - free) / 1_000_000.0)
                    return@withContext DownloadResult.Error(
                        context.getString(R.string.update_poco_espacio, formatMb(missingMb)),
                        retryable = true,
                    )
                }
            } else if (free < UNKNOWN_SIZE_MIN_FREE) {
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_poco_espacio, formatMbLong(UNKNOWN_SIZE_MIN_FREE)),
                    retryable = true,
                )
            }

            onProgress(DownloadProgress(already, total, percent(already, total)))

            try {
                body.byteStream().use { input ->
                    FileOutputStream(part, already > 0).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = already
                        var lastPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            downloaded += read
                            val current = percent(downloaded, total)
                            if (current != lastPercent) {
                                lastPercent = current
                                onProgress(DownloadProgress(downloaded, total, current))
                            }
                        }
                        output.fd.sync()
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "Descarga interrumpida")
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_descarga_interrumpida),
                    retryable = true,
                )
            }

            val downloaded = part.length()
            if (downloaded < MIN_APK_BYTES) {
                part.delete()
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_apk_corrupto),
                    retryable = true,
                )
            }
            if (total > 0 && downloaded < total) {
                // Conserva el .part: el próximo intento sigue desde acá.
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_descarga_interrumpida),
                    retryable = true,
                )
            }

            if (apk.exists()) apk.delete()
            if (!part.renameTo(apk)) {
                runCatching {
                    part.copyTo(apk, overwrite = true)
                    part.delete()
                }.onFailure {
                    part.delete()
                    return@withContext DownloadResult.Error(
                        context.getString(R.string.update_error_guardar),
                        retryable = true,
                    )
                }
            }

            val info = readApkInfo(context, apk)
            if (info == null || info.packageName != context.packageName) {
                apk.delete()
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_apk_corrupto),
                    retryable = true,
                )
            }
            if (info.versionCode <= BuildConfig.VERSION_CODE) {
                apk.delete()
                return@withContext DownloadResult.Error(
                    context.getString(R.string.update_version_no_reciente),
                    retryable = true,
                )
            }

            Timber.i("APK descargado y validado: %s (%d)", info.versionName, info.versionCode)
            DownloadResult.Success(apk)
        }
    }

    fun readApkInfo(context: Context, apkFile: File): ApkInfo? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_ACTIVITIES,
            ) ?: return null
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            ApkInfo(
                packageName = info.packageName.orEmpty(),
                versionCode = code,
                versionName = info.versionName.orEmpty(),
            )
        } catch (e: Exception) {
            Timber.w(e, "Error leyendo el APK descargado")
            null
        }
    }

    fun readApkVersionCode(context: Context, apkFile: File): Int =
        readApkInfo(context, apkFile)?.versionCode ?: 0

    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context): Boolean {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Probar el siguiente fallback.
            }
        }
        return false
    }

    fun installApk(context: Context, apkFile: File): String? {
        if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
            return context.getString(R.string.update_archivo_faltante)
        }
        if (!canInstallPackages(context)) {
            return context.getString(R.string.update_permiso_instalar)
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            Timber.e(e, "Error al lanzar instalación")
            context.getString(R.string.update_error_instalador, e.localizedMessage ?: "desconocido")
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun percent(downloaded: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((downloaded * 100) / total).toInt().coerceIn(0, 100)
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val total = header.substringAfter('/', "").trim()
        return total.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun formatMb(mb: Double): String = String.format(Locale.getDefault(), "%.0f", mb)

    private fun formatMbLong(bytes: Long): String =
        String.format(Locale.getDefault(), "%.0f", bytes / 1_000_000.0)
}
