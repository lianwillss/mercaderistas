package com.rutamercaderistas.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    data class Error(
        val message: String,
        val retryable: Boolean = true,
        /** El release completo no sirve para esta instalación: no reintentar. */
        val releaseRejected: Boolean = false,
    ) : DownloadResult
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
    data class Failed(
        val message: String,
        val retryable: Boolean = true,
        /**
         * El APK de este release nunca podrá instalarse sobre esta app
         * (versión anunciada distinta, APK no más nuevo, paquete ajeno).
         * El ViewModel guarda el tag para no volver a descargarlo en bucle.
         */
        val releaseRejected: Boolean = false,
    ) : ApkValidation
}

/** Resultado de abrir una sesión de PackageInstaller. */
sealed interface InstallStart {
    data object Started : InstallStart
    data class Error(val message: String, val retryable: Boolean = true) : InstallStart
}

/**
 * Descarga reanudable en filesDir/updates. El .part sobrevive a cierres,
 * reinicios y a que Android limpie la caché; el worker continúa con Range.
 */
object ApkDownloader {

    const val UPDATES_DIR = "updates"
    const val APK_NAME = "update.apk"
    const val PART_NAME = "update.apk.part"
    const val PART_META_NAME = "update.apk.part.meta"

    /** Acción que el sistema usa para devolver el resultado de la sesión. */
    const val ACTION_INSTALL_STATUS = "com.rutamercaderistas.action.UPDATE_INSTALL_STATUS"

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
        runCatching { partMetaFile(context).delete() }
    }

    /** Marca de identidad del .part: evita reanudar con la URL/tamaño de otro release. */
    private fun partMetaFile(context: Context): File = File(apkDir(context), PART_META_NAME)

    suspend fun download(
        context: Context,
        apkUrl: String,
        expectedSize: Long = 0L,
        expectedSha256: String = "",
        expectedVersionName: String = "",
        expectedVersionCode: Int = 0,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadResult = downloadInternal(
        context,
        apkUrl,
        expectedSize,
        expectedSha256,
        expectedVersionName,
        expectedVersionCode,
        onProgress,
        allowRangeRestart = true,
    )

    private suspend fun downloadInternal(
        context: Context,
        apkUrl: String,
        expectedSize: Long,
        expectedSha256: String,
        expectedVersionName: String,
        expectedVersionCode: Int,
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
        val meta = partMetaFile(context)
        fun discardPartial() {
            part.delete()
            meta.delete()
        }

        // Si ya hay un APK completo, se revalida o se descarta.
        if (apk.exists()) apk.delete()

        // Un .part de otro release (u otra URL/tamaño/hash) no se puede
        // reanudar: mezclaría bytes de dos APK distintos. Se descarta.
        val identity = listOf(
            apkUrl,
            expectedSize.toString(),
            expectedSha256,
            expectedVersionName,
            expectedVersionCode.toString(),
        ).joinToString("|")
        val previousIdentity = runCatching { meta.readText() }.getOrNull()
        if (part.exists() && previousIdentity != identity) {
            Timber.i("Se descarta .part de otro release")
            discardPartial()
        }
        runCatching { meta.writeText(identity) }

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
                discardPartial()
                return@withContext if (allowRangeRestart) {
                    downloadInternal(
                        context,
                        apkUrl,
                        expectedSize,
                        expectedSha256,
                        expectedVersionName,
                        expectedVersionCode,
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
                discardPartial()
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
                discardPartial()
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
                    discardPartial()
                    return@withContext DownloadResult.Error(
                        context.getString(R.string.update_error_guardar),
                        retryable = true,
                    )
                }
            }
            meta.delete()

            when (
                val validation = validateApk(
                    context,
                    apk,
                    expectedSize,
                    expectedSha256,
                    expectedVersionName,
                    expectedVersionCode,
                )
            ) {
                ApkValidation.Ok -> {
                    Timber.i("APK descargado y validado (tamaño, SHA-256, versión y firma)")
                    DownloadResult.Success(apk)
                }
                is ApkValidation.Failed -> {
                    ApkDownloader.clearDownloadedApk(context)
                    DownloadResult.Error(
                        validation.message,
                        validation.retryable,
                        validation.releaseRejected,
                    )
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
        expectedVersionName: String = "",
        expectedVersionCode: Int = 0,
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
                retryable = false,
                releaseRejected = true,
            )
        }

        // El APK debe ser exactamente el release anunciado. Si el CI publicó
        // un tag v12.51 con un APK compilado como 12.50, acá se detecta antes
        // de instalar y el tag queda rechazado (sin bucle de descargas).
        if (expectedVersionCode > 0 && info.versionCode != expectedVersionCode) {
            return@withContext ApkValidation.Failed(
                context.getString(
                    R.string.update_apk_no_corresponde,
                    expectedVersionName.ifBlank { expectedVersionCode.toString() },
                ),
                retryable = false,
                releaseRejected = true,
            )
        }
        if (expectedVersionName.isNotBlank()) {
            val published = AppVersion.parse(expectedVersionName)
            val actual = AppVersion.parse(info.versionName)
            if (published == null || actual == null || published != actual) {
                return@withContext ApkValidation.Failed(
                    context.getString(R.string.update_apk_no_corresponde, expectedVersionName),
                    retryable = false,
                    releaseRejected = true,
                )
            }
        }

        val installedVersion = InstalledVersion.read(context)
        if (!AppVersion.isApkNewer(info.versionName, info.versionCode, installedVersion)) {
            return@withContext ApkValidation.Failed(
                context.getString(R.string.update_version_no_reciente),
                retryable = false,
                releaseRejected = true,
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

    /**
     * Instala con una sesión de PackageInstaller (no con un Intent
     * ACTION_VIEW). En Android 12+ se pide USER_ACTION_NOT_REQUIRED: si el
     * sistema y el permiso lo permiten, la actualización se aplica sin
     * diálogo; si no, el receiver recibe STATUS_PENDING_USER_ACTION y abre
     * la confirmación mínima del sistema.
     */
    fun installApk(context: Context, apkFile: File): InstallStart {
        if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
            return InstallStart.Error(context.getString(R.string.update_archivo_faltante))
        }
        // Android necesita espacio para el APK ya descargado y otro tanto
        // para instalarlo; si no queda, la instalación falla tras una
        // descarga exitosa.
        val free = apkFile.parentFile?.usableSpace ?: Long.MAX_VALUE
        val needed = apkFile.length() * INSTALL_SPACE_FACTOR + DISK_MARGIN_BYTES
        if (free < needed) {
            val missingMb = ((needed - free) / 1_000_000.0)
            return InstallStart.Error(context.getString(R.string.update_poco_espacio, formatMb(missingMb)))
        }
        if (!canInstallPackages(context)) {
            return InstallStart.Error(context.getString(R.string.update_permiso_instalar))
        }

        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        return try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Requiere UPDATE_PACKAGES_WITHOUT_USER_ACTION y que la
                    // app se esté actualizando a sí misma. Si el sistema no
                    // lo permite, sigue el flujo de confirmación normal.
                    runCatching {
                        setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                        )
                    }.onFailure {
                        Timber.w(it, "El sistema no permite instalar sin acción del usuario")
                    }
                }
            }
            val id = installer.createSession(params)
            sessionId = id
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    apkFile.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }

                val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, id, statusIntent, flags)
                session.commit(pendingIntent.intentSender)
            }
            InstallStart.Started
        } catch (e: Exception) {
            Timber.e(e, "Error al abrir la sesión de instalación")
            sessionId?.let { runCatching { installer.abandonSession(it) } }
            InstallStart.Error(
                context.getString(
                    R.string.update_error_instalador,
                    e.localizedMessage ?: "desconocido",
                ),
            )
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
