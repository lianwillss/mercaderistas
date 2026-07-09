package com.rutamercaderistas.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val available: Boolean,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_URL = "https://api.github.com/repos/lianwillss/mercaderistas/releases/latest"

    suspend fun check(currentVersionCode: Int): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val json = fetchLatestRelease()
            if (json == null) {
                Log.w(TAG, "No se pudo obtener el release")
                return@withContext noUpdate()
            }

            val tagName = json.optString("tag_name", "") // "v9.0"
            val versionName = tagName.removePrefix("v")

            val versionCode = extractVersionCode(versionName)
            if (versionCode < 0) {
                Log.w(TAG, "Tag inv\u00E1lido: $tagName")
                return@withContext noUpdate()
            }

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    if (asset?.optString("name", "") == "app-release.apk") {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }

            if (apkUrl == null) {
                Log.w(TAG, "No se encontr\u00F3 APK en el release")
                return@withContext noUpdate()
            }

            if (versionCode > currentVersionCode) {
                Log.i(TAG, "Actualizaci\u00F3n disponible: $versionName ($versionCode) > actual ($currentVersionCode)")
                UpdateInfo(
                    available = true,
                    versionCode = versionCode,
                    versionName = versionName,
                    apkUrl = apkUrl
                )
            } else {
                Log.i(TAG, "Sin actualizaciones: remote=$versionCode, local=$currentVersionCode")
                noUpdate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update", e)
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
                Log.w(TAG, "GitHub API status: ${conn.responseCode}")
                return null
            }

            val bytes = conn.inputStream.use { it.readBytes() }
            return JSONObject(String(bytes))
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching release", e)
            return null
        } finally { conn?.disconnect() }
    }

    private fun extractVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        return parts.firstOrNull()?.toIntOrNull() ?: -1
    }

    private fun noUpdate() = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkUrl = ""
    )
}