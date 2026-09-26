package com.rutamercaderistas.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val available: Boolean,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** Tag crudo ("v12.20.1"): clave de dedupe, versionCode colisiona en patch. */
    val tag: String = "",
)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/lianwillss/mercaderistas/releases/latest"

    suspend fun check(currentVersionCode: Int): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val json = fetchLatestRelease()
            if (json == null) {
                Timber.w("No se pudo obtener el release")
                return@withContext noUpdate()
            }

            val tagName = json.optString("tag_name", "")
            val versionName = tagName.removePrefix("v")

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                        if (asset?.optString("name", "") == "app-universal-release.apk") {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            if (apkUrl == null) {
                Timber.w("No se encontró APK en el release")
                return@withContext noUpdate()
            }

            if (!isRemoteNewer(tagName, currentVersionCode)) {
                Timber.i("Sin actualizaciones: remote=%s, local=%d", tagName, currentVersionCode)
                return@withContext noUpdate()
            }

            Timber.i("Actualización disponible: %s > %d", tagName, currentVersionCode)
            UpdateInfo(
                available = true,
                versionCode = tagToVersionCode(tagName) ?: 0,
                versionName = versionName,
                apkUrl = apkUrl,
                tag = tagName,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking update")
            noUpdate()
        }
    }

    private fun fetchLatestRelease(): JSONObject? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Mercaderistas-Android")

            if (conn.responseCode != 200) {
                Timber.w("GitHub API status: %d", conn.responseCode)
                return null
            }

            val bytes = conn.inputStream.use { it.readBytes() }
            return JSONObject(String(bytes))
        } catch (e: Exception) {
            Timber.w(e, "Error fetching release")
            return null
        } finally { conn?.disconnect() }
    }

    /** Parse "12.02.1" → [12, 2, 1]. Null si alguna parte no es número. */
    internal fun parseTagParts(tagName: String): List<Int>? {
        val versionName = tagName.removePrefix("v")
        if (versionName.isBlank()) return null
        return versionName.split(".").map { it.toIntOrNull() ?: return null }
    }

    /** Decodifica versionCode con esquema major*1000+minor → [major, minor]. */
    internal fun decodeVersionCode(versionCode: Int): List<Int> =
        listOf(versionCode / 1000, versionCode % 1000)

    /**
     * true si el tag remoto es más nuevo que el código instalado.
     * Compara parte por parte (12.02.1 > 12.02.0), sin límite de
     * dígitos por parte. Visible para tests.
     */
    internal fun isRemoteNewer(remoteTag: String, localVersionCode: Int): Boolean {
        val remote = parseTagParts(remoteTag) ?: return false
        val local = decodeVersionCode(localVersionCode)
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /**
     * Convierte un tag "v12.01" al esquema de versionCode
     * (major * 1000 + minor) solo para informar. La decisión de
     * actualizar la toma [isRemoteNewer]. Visible para tests.
     */
    internal fun tagToVersionCode(tagName: String): Int? {
        val parsed = parseTagParts(tagName) ?: return null
        val major = parsed.getOrElse(0) { 0 }
        val minor = parsed.getOrElse(1) { 0 }
        return major * 1000 + minor
    }

    private fun noUpdate() = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkUrl = ""
    )

    /**
     * true si el pendiente guardado sigue siendo más nuevo que lo instalado.
     * Usa el tag cuando existe (no colisiona en patch); si no, el code legacy.
     */
    internal fun isPendingNewer(pendingTag: String, pendingCode: Int, installedCode: Int): Boolean {
        if (pendingTag.isNotBlank()) return isRemoteNewer(pendingTag, installedCode)
        return pendingCode > installedCode
    }
}
