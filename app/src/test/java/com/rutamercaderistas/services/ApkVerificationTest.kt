package com.rutamercaderistas.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempFile

class ApkVerificationTest {

    private val shaAbc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `sha256 de bytes conocidos`() {
        assertEquals(shaAbc, ApkVerification.sha256Hex("abc".toByteArray()))
    }

    @Test
    fun `sha256 de archivo coincide con el de bytes`() {
        val file = createTempFile(suffix = ".bin").toFile()
        try {
            file.writeText("abc")
            assertEquals(shaAbc, ApkVerification.sha256Hex(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `normaliza prefijo y mayusculas`() {
        assertEquals(shaAbc, ApkVerification.normalizeSha256("  SHA256:${shaAbc.uppercase()}  "))
    }

    @Test
    fun `rechaza un sha256 invalido`() {
        assertNull(ApkVerification.normalizeSha256(""))
        assertNull(ApkVerification.normalizeSha256("abc"))
        assertNull(ApkVerification.normalizeSha256("z".repeat(64)))
        assertFalse(ApkVerification.isSha256Hex("a".repeat(63)))
    }

    @Test
    fun `parsea sha256sums con espacios y asterisco`() {
        val text = """
            # generado por el CI
            $shaAbc  app-universal-release.apk
            1111111111111111111111111111111111111111111111111111111111111111 *app-arm64-v8a-release.apk
            2222222222222222222222222222222222222222222222222222222222222222  sub/dir/app-armeabi-v7a-release.apk

            linea invalida
        """.trimIndent()

        val parsed = ApkVerification.parseChecksums(text)

        assertEquals(3, parsed.size)
        assertEquals(shaAbc, parsed["app-universal-release.apk"])
        assertEquals("1".repeat(64), parsed["app-arm64-v8a-release.apk"])
        assertEquals("2".repeat(64), parsed["app-armeabi-v7a-release.apk"])
    }

    @Test
    fun `si un nombre se repite gana el primero`() {
        val parsed = ApkVerification.parseChecksums(
            """
            aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk
            bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  app.apk
            """.trimIndent(),
        )
        assertEquals("a".repeat(64), parsed["app.apk"])
    }

    @Test
    fun `sha256Equals compara sin distinguir mayusculas`() {
        assertTrue(ApkVerification.sha256Equals(shaAbc, shaAbc.uppercase()))
        assertFalse(ApkVerification.sha256Equals(shaAbc, "b".repeat(64)))
        assertFalse(ApkVerification.sha256Equals(shaAbc, shaAbc.dropLast(1)))
    }
}
