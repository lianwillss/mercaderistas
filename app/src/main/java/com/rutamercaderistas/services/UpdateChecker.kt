package com.rutamercaderistas.services

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val available: Boolean,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** Tag crudo ("v12.20.1"): clave de dedupe, versionCode colisiona en patch. */
    val tag: String = "",
    /** Tamaño real del asset según la API de GitHub (0 = no publicado). */
    val apkSize: Long = 0L,
    /** SHA-256 publicado por el CI; sin este dato no se ofrece instalar. */
    val apkSha256: String = "",
    /**
     * null = la consulta terminó bien y no hay versión nueva.
     * Mensaje en español = no se pudo consultar (red, servidor, JSON).
     * Nunca borrar un pendiente cuando esto no es null.
     */
    val error: String? = null,
)

/** Asset del release con los metadatos necesarios para verificar el APK. */
internal data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val digest: String?,
)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/lianwillss/mercaderistas/releases/latest"
    private const val UNIVERSAL_ASSET = "app-universal-release.apk"
    private const val ARM64_ASSET = "app-arm64-v8a-release.apk"
    private const val ARMV7_ASSET = "app-armeabi-v7a-release.apk"
    private const val CHECKSUMS_ASSET = "SHA256SUMS.txt"
    private const val MAX_CHECKSUMS_BYTES = 64 * 1024

    // Se declara una sola vez: se evalúa por release, no por ítem de lista.
    private val VERSION_CODE_IN_BODY = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)

    suspend fun check(currentVersionCode: Int): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val json = fetchLatestRelease()
            if (json == null) {
                Timber.w("No se pudo obtener el release")
                return@withContext checkError("No se pudo comprobar la actualización")
            }

            val tagName = json.optString("tag_name", "")
            val versionName = tagName.removePrefix("v")
            val body = json.optString("body", "")

            val assets = parseAssets(json.optJSONArray("assets"))
            val apkAsset = findApkAsset(assets, Build.SUPPORTED_ABIS.toList())
            if (apkAsset == null) {
                Timber.w("No se encontró APK compatible en el release")
                return@withContext checkError("El release no incluye un APK compatible")
            }

            // El release puede declarar el versionCode real en el cuerpo. Si
            // no lo hace, se deriva del tag con el esquema major*1000+minor.
            val remoteCode = VERSION_CODE_IN_BODY.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tagToVersionCode(tagName)
                ?: 0

            if (!isRemoteNewer(tagName, currentVersionCode, remoteCode)) {
                Timber.i("Sin actualizaciones: remote=%s (%d), local=%d", tagName, remoteCode, currentVersionCode)
                return@withContext noUpdate()
            }

            // La descarga debe pesar exactamente lo que declara el asset de
            // GitHub; sin tamaño no hay forma de detectar un archivo truncado.
            if (apkAsset.size <= 0L) {
                Timber.w("El release no publica el tamaño de %s", apkAsset.name)
                return@withContext checkError("El release no publica el tamaño del APK")
            }

            // SHA-256 publicado por el CI (SHA256SUMS.txt). Si el asset no
            // trae el archivo, se acepta el digest que calcula GitHub al subir.
            val checksumsAsset = assets.firstOrNull { it.name == CHECKSUMS_ASSET }
            val checksums = checksumsAsset?.let { fetchChecksums(it.url) }
            val apiDigest = ApkVerification.normalizeSha256(apkAsset.digest)
            val ciSha = checksums?.get(apkAsset.name)
            if (ciSha != null && apiDigest != null && ciSha != apiDigest) {
                Timber.e("SHA-256 del CI distinto al de GitHub para %s", apkAsset.name)
                return@withContext checkError("El SHA-256 del APK del CI no coincide con el de GitHub")
            }
            val apkSha = ciSha ?: apiDigest
            if (apkSha == null) {
                val message = when {
                    checksumsAsset == null -> "El release no publica el SHA-256 del APK"
                    checksums == null -> "No se pudo obtener el SHA-256 publicado por el CI"
                    else -> "El SHA-256 publicado no incluye ${apkAsset.name}"
                }
                Timber.w(message)
                return@withContext checkError(message)
            }

            Timber.i(
                "Actualización disponible: %s (%d) > %d, %d bytes, sha=%s",
                tagName,
                remoteCode,
                currentVersionCode,
                apkAsset.size,
                apkSha.take(12),
            )
            UpdateInfo(
                available = true,
                versionCode = remoteCode,
                versionName = versionName,
                apkUrl = apkAsset.url,
                tag = tagName,
                apkSize = apkAsset.size,
                apkSha256 = apkSha,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking update")
            checkError("No se pudo comprobar la actualización")
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
                if (conn.responseCode == 404) return null
                throw IOException("GitHub HTTP ${conn.responseCode}")
            }

            val bytes = conn.inputStream.use { it.readBytes() }
            return JSONObject(String(bytes))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Prefiere el APK de la ABI del teléfono (arm64 ≈ 50 MB vs universal
     * ≈ 99 MB) y cae al universal si el release no trae el específico.
     * Puro para poder probarlo en JVM.
     */
    internal fun preferredAssetName(supportedAbis: List<String>): String = when {
        supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) } -> ARM64_ASSET
        supportedAbis.any { it.equals("armeabi-v7a", ignoreCase = true) } -> ARMV7_ASSET
        else -> UNIVERSAL_ASSET
    }

    internal fun findApkAsset(
        assets: List<ReleaseAsset>,
        supportedAbis: List<String>,
    ): ReleaseAsset? {
        val preferred = preferredAssetName(supportedAbis)
        return assets.firstOrNull { it.name == preferred }
            ?: assets.firstOrNull { it.name == UNIVERSAL_ASSET }
    }

    private fun parseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name", "")
            val url = obj.optString("browser_download_url", "")
            if (name.isBlank() || url.isBlank()) return@mapNotNull null
            ReleaseAsset(
                name = name,
                url = url,
                size = obj.optLong("size", 0L),
                digest = obj.optString("digest", "").takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Descarga y parsea SHA256SUMS.txt. Cualquier fallo devuelve null: el
     * llamador decide si puede caer al digest de la API o debe abortar.
     */
    private fun fetchChecksums(url: String): Map<String, String>? {
        val text = fetchText(url, MAX_CHECKSUMS_BYTES) ?: return null
        return ApkVerification.parseChecksums(text).takeIf { it.isNotEmpty() }
    }

    private fun fetchText(url: String, maxBytes: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "text/plain")
            conn.setRequestProperty("User-Agent", "Mercaderistas-Android")
            if (conn.responseCode != 200) {
                Timber.w("Checksums HTTP %d", conn.responseCode)
                return null
            }
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "No se pudo leer %s", url)
            null
        } finally {
            conn?.disconnect()
        }
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
     * Decisión principal: primero compara el versionCode numérico del release;
     * si empatan (un patch v12.20.1 contra v12.20), el tag decide.
     */
    internal fun isRemoteNewer(
        remoteTag: String,
        localVersionCode: Int,
        remoteVersionCode: Int,
    ): Boolean {
        if (remoteVersionCode > localVersionCode) return true
        if (remoteVersionCode < localVersionCode) return false
        return isRemoteNewer(remoteTag, localVersionCode)
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
        apkUrl = "",
    )

    private fun checkError(message: String) = UpdateInfo(
        available = false,
        versionCode = 0,
        versionName = "",
        apkUrl = "",
        error = message,
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
