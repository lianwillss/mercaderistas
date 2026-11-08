package com.rutamercaderistas.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
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
import okhttp3.ConnectionPool
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
    /** SHA-256 (hex) de los certificados con los que está firmado el APK. */
    val signerSha256: Set<String> = emptySet(),
)

/** Resultado de validar un APK antes de marcarlo listo o lanzar el instalador. */
sealed interface ApkValidation {
    data object Ok : ApkValidation
    data class Failed(val message: String, val retryable: Boolean = true) : ApkValidation
}

/**
 * Descarga reanudable en filesDir/updates. El .part sobrevive a cierres,
 * reinicios y a que Android limpie la caché; el worker continúa con Range.
 */
object ApkDownloader {

    const val UPDATES_DIR = "updates"
    const val APK_NAME = "update.apk"
    const val PART_NAME = "update.apk.part"

    private const val MIN_APK_BYTES = 1024L
    /** Android necesita aproximadamente otro tanto al instalar (copia en /data/app + metadatos). */
    internal const val INSTALL_SPACE_FACTOR = 2L
    internal const val DISK_MARGIN_BYTES = 20L * 1024 * 1024
    /** 1 MB de buffer reduce drásticamente las llamadas al sistema por chunk de descarga. */
    private const val DOWNLOAD_BUFFER_SIZE = 1024 * 1024
    private const val UNKNOWN_SIZE_MIN_FREE = 200L * 1024 * 1024

    /** Espacio libre necesario (bytes) para descargar e instalar un APK: ~2× el tamaño + margen. */
    internal fun espacioNecesarioParaInstalacion(apkSize: Long): Long =
        apkSize * INSTALL_SPACE_FACTOR + DISK_MARGIN_BYTES

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .connectionPool(ConnectionPool(8, 10, TimeUnit.MINUTES))
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
        expectedSize: Long = 0L,
        expectedSha256: String = "",
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadResult = downloadInternal(
        context,
        apkUrl,
        expectedSize,
        expectedSha256,
        onProgress,
        allowRangeRestart = true,
    )

    private suspend fun downloadInternal(
        context: Context,
        apkUrl: String,
        expectedSize: Long,
        expectedSha256: String,
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
                    downloadInternal(
                        context,
                        apkUrl,
                        expectedSize,
                        expectedSha256,
                        onProgress,
                        allowRangeRestart = false,
                    )
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

            // Falla rápido si el servidor anuncia otro tamaño que el asset de
            // GitHub: no tiene sentido gastar la descarga.
            if (expectedSize > 0 && total > 0 && total != expectedSize) {
                part.delete()
                return@withContext DownloadResult.Error(
                    context.getString(
                        R.string.update_tamano_no_coincide,
                        formatMbPrecise(expectedSize),
                        formatMbPrecise(total),
                    ),
                    retryable = true,
                )
            }

            val free = dir.usableSpace
            if (total > 0) {
                // Espacio para el APK descargado y para la instalación: Android
                // duplica el APK al instalarlo (copia en /data/app + metadatos).
                // Exigir ~2× el tamaño + margen evita que la descarga termine
                // bien y la instalación falle por falta de espacio.
                val needed = total * INSTALL_SPACE_FACTOR + DISK_MARGIN_BYTES
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
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
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

            when (val validation = validateApk(context, apk, expectedSize, expectedSha256)) {
                ApkValidation.Ok -> {
                    Timber.i("APK descargado y validado (tamaño, SHA-256 y firma)")
                    DownloadResult.Success(apk)
                }
                is ApkValidation.Failed -> {
                    apk.delete()
                    part.delete()
                    DownloadResult.Error(validation.message, validation.retryable)
                }
            }
        }
    }

    fun readApkInfo(context: Context, apkFile: File): ApkInfo? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                signingFlags(),
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
                signerSha256 = info.signerDigests(),
            )
        } catch (e: Exception) {
            Timber.w(e, "Error leyendo el APK descargado")
            null
        }
    }

    fun readApkVersionCode(context: Context, apkFile: File): Int =
        readApkInfo(context, apkFile)?.versionCode ?: 0

    /** 28+ expone SigningInfo; 26/27 solo signatures (también con v2). */
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerDigests(): Set<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                signingInfo
                    ?.apkContentsSigners
                    ?.map { ApkVerification.sha256Hex(it.toByteArray()) }
                    ?.toSet()
                    .orEmpty()
            } else {
                signatures
                    ?.map { ApkVerification.sha256Hex(it.toByteArray()) }
                    ?.toSet()
                    .orEmpty()
            }
        } catch (e: Exception) {
            Timber.w(e, "No se pudieron leer los firmantes del APK")
            emptySet()
        }
    }

    /**
     * Firma SHA-256 de los certificados de la app instalada. Null si el
     * PackageManager no pudo leerla.
     */
    fun installedSignerSha256(context: Context): Set<String>? {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, signingFlags())
                .signerDigests()
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.w(e, "No se pudo leer la firma de la app instalada")
            null
        }
    }

    /**
     * Valida un APK antes de marcarlo listo o de abrir el instalador:
     * tamaño publicado, SHA-256 del CI, paquete, versión y que la firma sea
     * el mismo certificado de la app instalada. Devuelve el motivo legible
     * del primer fallo, que es lo que el instalador no explica.
     */
    suspend fun validateApk(
        context: Context,
        apkFile: File,
        expectedSize: Long,
        expectedSha256: String,
    ): ApkValidation = withContext(Dispatchers.IO) {
        val length = if (apkFile.exists()) apkFile.length() else 0L
        if (length < MIN_APK_BYTES) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_archivo_faltante),
            )
        }

        if (expectedSize > 0L && length != expectedSize) {
            return@withContext ApkValidation.Failed(
                context.getString(
                    R.string.update_tamano_no_coincide,
                    formatMbPrecise(expectedSize),
                    formatMbPrecise(length),
                ),
                retryable = true,
            )
        }

        val expectedSha = ApkVerification.normalizeSha256(expectedSha256)
        if (expectedSha256.isNotBlank() && expectedSha == null) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_sha_no_coincide),
                retryable = true,
            )
        }
        if (expectedSha != null) {
            val actualSha = ApkVerification.sha256Hex(apkFile)
            if (!ApkVerification.sha256Equals(expectedSha, actualSha)) {
                return@withContext ApkValidation.Failed(
                    context.getString(R.string.update_sha_no_coincide),
                    retryable = true,
                )
            }
        }

        val info = readApkInfo(context, apkFile)
            ?: return@withContext ApkValidation.Failed(
                context.getString(R.string.update_apk_corrupto),
            )
        if (info.packageName != context.packageName) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_apk_corrupto),
            )
        }
        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_version_no_reciente),
            )
        }

        val installed = installedSignerSha256(context)
        if (info.signerSha256.isEmpty() || installed.isNullOrEmpty()) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_firma_ilegible),
                retryable = false,
            )
        }
        if (installed != info.signerSha256) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_firma_no_coincide),
                retryable = false,
            )
        }

        ApkValidation.Ok
    }

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
        // Antes de lanzar el instalador: Android necesita espacio para el APK
        // ya descargado y otro tanto para instalarlo; si no queda, la
        // instalación falla tras una descarga exitosa. Un directorio padre
        // nulo no debería ocurrir, pero no se trata como "sin espacio" para
        // no bloquear una instalación válida por un valor inesperado.
        val free = apkFile.parentFile?.usableSpace ?: Long.MAX_VALUE
        val needed = apkFile.length() * INSTALL_SPACE_FACTOR + DISK_MARGIN_BYTES
        if (free < needed) {
            val missingMb = ((needed - free) / 1_000_000.0)
            return context.getString(R.string.update_poco_espacio, formatMb(missingMb))
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

    internal fun formatMb(mb: Double): String = String.format(Locale.getDefault(), "%.0f", mb)

    private fun formatMbLong(bytes: Long): String =
        String.format(Locale.getDefault(), "%.0f", bytes / 1_000_000.0)

    private fun formatMbPrecise(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)
}
