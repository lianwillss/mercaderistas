package com.rutamercaderistas.services

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Verificaciones puras del APK de actualización (SHA-256 y archivo de
 * checksums). Sin Android para poder probarlas en JVM.
 */
object ApkVerification {

    const val SHA256_HEX_LENGTH = 64

    private const val BUFFER_SIZE = 64 * 1024
    private const val HEX_CHARS = "0123456789abcdef"

    // Se declara una vez: se evalúa por línea de SHA256SUMS.
    private val CHECKSUM_LINE = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$""")

    fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun isSha256Hex(value: String): Boolean =
        value.length == SHA256_HEX_LENGTH &&
            value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** Acepta "sha256:<hex>", mayúsculas y espacios; null si no es un SHA-256 válido. */
    fun normalizeSha256(value: String?): String? {
        val clean = value
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
            .removePrefix("sha256:")
        return clean.takeIf { isSha256Hex(it) }
    }

    /**
     * Parsea un archivo SHA256SUMS (`<hex>  <nombre>`, también `*nombre`).
     * Ignora comentarios y líneas inválidas; si un nombre se repite gana el
     * primero. Los nombres se reducen a su basename.
     */
    fun parseChecksums(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val match = CHECKSUM_LINE.matchEntire(line) ?: return@forEach
            val hash = match.groupValues[1].lowercase(Locale.US)
            val name = match.groupValues[2].substringAfterLast('/')
            if (name.isNotEmpty() && name !in result) result[name] = hash
        }
        return result
    }

    /** Comparación sin corto-circuito para no filtrar cuántos bytes coinciden. */
    fun sha256Equals(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].lowercaseChar().code xor actual[i].lowercaseChar().code)
        }
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val value = this[i].toInt() and 0xff
            out[i * 2] = HEX_CHARS[value ushr 4]
            out[i * 2 + 1] = HEX_CHARS[value and 0x0f]
        }
        return String(out)
    }
}
