package com.rutamercaderistas.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_MOVED_PERM
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.URL

data class UpdateInfo(
    val available: Boolean,
    val versionCode: Int,
    val versionName: String,
    val apkFileId: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    suspend fun check(
        jsonDriveId: String,
        currentVersionCode: Int
    ): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = buildDriveUrl(jsonDriveId)
            val bytes = downloadBytes(url)
            if (bytes == null) {
                Log.w(TAG, "No se pudo descargar version.json")
                return@withContext noUpdate()
            }

            val json = JSONObject(String(bytes))
            val latestVersionCode = json.optInt("versionCode", currentVersionCode)
            val versionName = json.optString("versionName", "")
            val apkFileId = json.optString("apkFileId", "")

            if (latestVersionCode > currentVersionCode && apkFileId.isNotBlank()) {
                Log.i(TAG, "Actualización disponible: $latestVersionCode (actual: $currentVersionCode)")
                UpdateInfo(
                    available = true,
                    versionCode = latestVersionCode,
                    versionName = versionName,
                    apkFileId = apkFileId
                )
            } else {
                Log.i(TAG, "Sin actualizaciones")
                noUpdate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update", e)
            noUpdate()
        }
    }

    private fun noUpdate() = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkFileId = ""
    )

    private fun buildDriveUrl(fileId: String): String {
        return "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
    }

    private fun downloadBytes(url: String): ByteArray? {
        var currentUrl = url
        var limit = 5
        var redirect = true
        while (redirect && limit > 0) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                val status = conn.responseCode
                if (status == HTTP_MOVED_PERM || status == HTTP_MOVED_TEMP || status == 303 || status == 307 || status == 308) {
                    currentUrl = conn.getHeaderField("Location") ?: return null
                    limit--
                    continue
                }
                return conn.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Download error", e)
                return null
            } finally { conn?.disconnect() }
        }
        return null
    }
}
