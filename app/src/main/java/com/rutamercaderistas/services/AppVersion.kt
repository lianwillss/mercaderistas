package com.rutamercaderistas.services

import android.content.Context
import android.os.Build
import com.rutamercaderistas.BuildConfig

/**
 * Versión numérica por segmentos. Nunca se compara como texto ni como
 * decimal: "12.51" > "12.9" porque 51 > 9, y "12.51.1" > "12.51".
 */
data class VersionNumber(val parts: List<Int>) : Comparable<VersionNumber> {

    val major: Int get() = parts.getOrElse(0) { 0 }
    val minor: Int get() = parts.getOrElse(1) { 0 }
    val patch: Int get() = parts.getOrElse(2) { 0 }

    override fun compareTo(other: VersionNumber): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val remote = parts.getOrElse(i) { 0 }
            val local = other.parts.getOrElse(i) { 0 }
            if (remote != local) return remote.compareTo(local)
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")
}

/** Lo que el PackageManager dice de la app realmente instalada. */
data class InstalledVersion(
    val versionCode: Int,
    val versionName: String,
) {
    companion object {
        /**
         * Fuente de verdad en ejecución: PackageInfo.longVersionCode y
         * versionName. Si el sistema no responde (o un test relajado devuelve
         * valores vacíos), cae a las constantes del build en curso.
         */
        fun read(context: Context): InstalledVersion {
            val fromPackage = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                InstalledVersion(
                    versionCode = code.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    versionName = info.versionName.orEmpty(),
                )
            }.getOrNull()

            return if (fromPackage != null &&
                fromPackage.versionCode > 0 &&
                AppVersion.parse(fromPackage.versionName) != null
            ) {
                fromPackage
            } else {
                buildConfig()
            }
        }

        fun buildConfig(): InstalledVersion = InstalledVersion(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}

/** Comparación pura (sin Android) para poder probarla en JVM. */
object AppVersion {

    /** "v12.51" → [12, 51]; "12.51.1" → [12, 51, 1]; null si no es numérica. */
    fun parse(raw: String?): VersionNumber? {
        val clean = raw
            ?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.trim()
            .orEmpty()
        if (clean.isBlank()) return null
        if (clean.any { !it.isDigit() && it != '.' }) return null
        val parts = clean.split('.').map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty() || parts.any { it < 0 }) return null
        return VersionNumber(parts)
    }

    /** Decodifica el esquema major * 1000 + minor cuando no hay versionName. */
    fun fromVersionCode(versionCode: Int): VersionNumber {
        if (versionCode <= 0) return VersionNumber(listOf(0))
        return VersionNumber(listOf(versionCode / 1000, versionCode % 1000))
    }

    /** Código oficial del release: major * 1000 + minor. Null si no encaja. */
    fun releaseCodeForTag(raw: String?): Int? {
        val tag = parse(raw) ?: return null
        if (tag.minor >= 1000 || tag.major > 2000) return null
        return tag.major * 1000 + tag.minor
    }

    /**
     * Clave de publicación: el tag debe ser numérico y coincidir con el
     * versionName que se va a compilar. Evita el bug de publicar v12.51 con
     * un APK 12.50 (mismo versionCode, bucle infinito).
     */
    fun releaseMatchesTag(tagName: String, buildVersionName: String): Boolean {
        val tag = parse(tagName) ?: return false
        val build = parse(buildVersionName) ?: return false
        return tag == build
    }

    private fun installedNumber(installed: InstalledVersion): VersionNumber =
        parse(installed.versionName) ?: fromVersionCode(installed.versionCode)

    /** ¿El tag remoto anuncia una versión posterior a la instalada? */
    fun isTagNewer(remoteTag: String, installed: InstalledVersion): Boolean {
        val remote = parse(remoteTag) ?: return false
        return remote > installedNumber(installed)
    }

    /** El release solo se ofrece si su versionCode también es mayor. */
    fun isReleaseCodeNewer(remoteVersionCode: Int, installed: InstalledVersion): Boolean =
        remoteVersionCode > installed.versionCode

    /**
     * ¿El APK descargado es instalable sobre lo que ya hay? Debe ser más
     * nuevo por nombre y no tener un versionCode menor (Android rechazaría
     * un downgrade). Igual code + nombre mayor = parche legítimo.
     */
    fun isApkNewer(apkVersionName: String, apkVersionCode: Int, installed: InstalledVersion): Boolean {
        val apk = parse(apkVersionName)
        val current = installedNumber(installed)
        val byName = if (apk != null) apk > current else apkVersionCode > installed.versionCode
        return byName && apkVersionCode >= installed.versionCode
    }
}
