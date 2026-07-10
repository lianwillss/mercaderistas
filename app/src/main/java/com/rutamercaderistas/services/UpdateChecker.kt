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

            val versionCode = extractVersionCode(versionName)
            if (versionCode < 0) {
                Timber.w("Tag inválido: %s", tagName)
                return@withContext noUpdate()
            }

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

            if (versionCode > currentVersionCode) {
                Timber.i("Actualización disponible: %s (%d) > actual (%d)", versionName, versionCode, currentVersionCode)
                UpdateInfo(
                    available = true,
                    versionCode = versionCode,
                    versionName = versionName,
                    apkUrl = apkUrl
                )
            } else {
                Timber.i("Sin actualizaciones: remote=%d, local=%d", versionCode, currentVersionCode)
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

    private fun extractVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return -1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major * 1000 + minor
    }

    private fun noUpdate() = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkUrl = ""
    )
}