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
    val apkUrl: String
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
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }

            if (apkUrl == null) {
                Timber.w("No se encontró APK en el release")
                return@withContext noUpdate()
            }

            val remoteVersion = parseVersion(versionName)
            val localVersion = parseVersionFromCode(currentVersionCode)
            if (remoteVersion == null) {
                Timber.w("Tag inválido: %s", tagName)
                return@withContext noUpdate()
            }

            if (remoteVersion.first > localVersion.first ||
                (remoteVersion.first == localVersion.first && remoteVersion.second > localVersion.second)
            ) {
                Timber.i("Actualización disponible: %s > %s", versionName, formatVersion(localVersion))
                UpdateInfo(
                    available = true,
                    versionCode = currentVersionCode + 1,
                    versionName = versionName,
                    apkUrl = apkUrl
                )
            } else {
                Timber.i("Sin actualizaciones: remote=%s, local=%s", versionName, formatVersion(localVersion))
                noUpdate()
            }
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

    /** Parse "11.45" → Pair(11, 45) */
    private fun parseVersion(versionName: String): Pair<Int, Int>? {
        val parts = versionName.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major to minor
    }

    /** Decode versionCode 11045 → Pair(11, 45) */
    private fun parseVersionFromCode(versionCode: Int): Pair<Int, Int> {
        val major = versionCode / 1000
        val minor = versionCode % 1000
        return major to minor
    }

    private fun formatVersion(v: Pair<Int, Int>): String = "${v.first}.${v.second}"

    private fun noUpdate() = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkUrl = ""
    )
}