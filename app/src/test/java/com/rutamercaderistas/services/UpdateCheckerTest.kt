package com.rutamercaderistas.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun installed(name: String, code: Int) = InstalledVersion(code, name)

    @Test
    fun `tag v12_01 decodifica a 12001`() {
        assertEquals(12001, UpdateChecker.tagToVersionCode("v12.01"))
    }

    @Test
    fun `tag sin parche usa minor 0`() {
        assertEquals(12000, UpdateChecker.tagToVersionCode("v12"))
    }

    @Test
    fun `tag invalido devuelve null`() {
        assertNull(UpdateChecker.tagToVersionCode("release-final"))
    }

    @Test
    fun `misma version no ofrece update`() {
        assertFalse(UpdateChecker.isRemoteNewer("v12.51", installed("12.51", 12051)))
        assertFalse(UpdateChecker.isRemoteNewer("v12.51", installed("12.51", 12050)))
    }

    @Test
    fun `tag adelantado a su build se detecta pero el code no es mayor`() {
        // Caso real: tag v12.51 compilado con versionCode 12050.
        val local = installed("12.50", 12050)
        assertTrue(UpdateChecker.isRemoteNewer("v12.51", local))
        assertTrue(AppVersion.isReleaseCodeNewer(12051, local))
        // Si el build no subió el code, no se ofrece como release válido.
        assertFalse(AppVersion.isReleaseCodeNewer(12050, local))
    }

    @Test
    fun `comparacion numerica parte por parte, no decimal`() {
        assertFalse(UpdateChecker.isRemoteNewer("v12.9", installed("12.51", 12051)))
        assertTrue(UpdateChecker.isRemoteNewer("v12.100", installed("12.99", 12099)))
        assertFalse(UpdateChecker.isRemoteNewer("v12.02", installed("12.02", 12002)))
        assertFalse(UpdateChecker.isRemoteNewer("release-final", installed("12.02", 12002)))
    }

    @Test
    fun `parche de tres partes decide`() {
        assertTrue(UpdateChecker.isRemoteNewer("v12.02.1", installed("12.02", 12002)))
        assertFalse(UpdateChecker.isRemoteNewer("v12.02.0", installed("12.02", 12002)))
    }

    @Test
    fun `minor grande no rompe la comparacion`() {
        assertTrue(UpdateChecker.isRemoteNewer("v11.100", installed("11.99", 11099)))
        assertFalse(UpdateChecker.isRemoteNewer("v11.100", installed("11.100", 11100)))
    }

    @Test
    fun `decode del versionCode mantiene el esquema`() {
        assertEquals(VersionNumber(listOf(12, 2)), AppVersion.fromVersionCode(12002))
    }

    @Test
    fun `versionCode sin versionName parseable cae al code`() {
        assertTrue(UpdateChecker.isRemoteNewer("v12.03", installed("dev-build", 12002)))
        assertFalse(UpdateChecker.isRemoteNewer("v12.02", installed("dev-build", 12002)))
    }

    @Test
    fun `isPendingNewer usa tag cuando existe`() {
        val local = installed("12.20", 12020)
        assertTrue(UpdateChecker.isPendingNewer("v12.20.1", 12020, local))
        assertFalse(UpdateChecker.isPendingNewer("v12.20", 12020, local))
        // Un pendiente del tag que ya está instalado no se restaura.
        assertFalse(UpdateChecker.isPendingNewer("v12.51", 12051, installed("12.51", 12051)))
    }

    @Test
    fun `isPendingNewer cae a code sin tag (pendientes viejos)`() {
        val local = installed("12.20", 12020)
        assertTrue(UpdateChecker.isPendingNewer("", 12021, local))
        assertFalse(UpdateChecker.isPendingNewer("", 12020, local))
    }

    @Test
    fun `un tag rechazado no vuelve a ofrecerse`() {
        assertTrue(UpdateChecker.isRejectedTag("v12.51", "v12.51"))
        assertTrue(UpdateChecker.isRejectedTag("v12.51", " V12.51 "))
        assertFalse(UpdateChecker.isRejectedTag("v12.52", "v12.51"))
        assertFalse(UpdateChecker.isRejectedTag("v12.52", ""))
    }

    @Test
    fun `el tag publicado debe coincidir con el versionName del build`() {
        assertTrue(AppVersion.releaseMatchesTag("v12.52", "12.52"))
        assertFalse(AppVersion.releaseMatchesTag("v12.52", "12.51"))
        assertFalse(AppVersion.releaseMatchesTag("v12.51.1", "12.51"))
    }

    @Test
    fun `un APK de igual o menor versionCode no es instalable`() {
        val local = installed("12.50", 12050)
        assertFalse(AppVersion.isApkNewer("12.50", 12050, local))
        assertFalse(AppVersion.isApkNewer("12.50", 12049, local))
        assertTrue(AppVersion.isApkNewer("12.51", 12051, local))
        // Parche legítimo: mismo code, nombre mayor.
        assertTrue(AppVersion.isApkNewer("12.50.1", 12050, local))
    }

    @Test
    fun `prefiere el APK de la ABI del telefono`() {
        assertEquals(
            "app-arm64-v8a-release.apk",
            UpdateChecker.preferredAssetName(listOf("arm64-v8a", "armeabi-v7a")),
        )
        assertEquals(
            "app-armeabi-v7a-release.apk",
            UpdateChecker.preferredAssetName(listOf("armeabi-v7a")),
        )
        assertEquals(
            "app-universal-release.apk",
            UpdateChecker.preferredAssetName(listOf("x86_64")),
        )
    }

    @Test
    fun `usa el asset compatible y cae al universal`() {
        val assets = listOf(
            ReleaseAsset(
                name = "app-universal-release.apk",
                url = "https://example.com/universal.apk",
                size = 99_000_000L,
                digest = "sha256:" + "a".repeat(64),
            ),
            ReleaseAsset(
                name = "app-arm64-v8a-release.apk",
                url = "https://example.com/arm64.apk",
                size = 50_000_000L,
                digest = null,
            ),
        )
        val arm64 = UpdateChecker.findApkAsset(assets, listOf("arm64-v8a"))
        assertEquals("https://example.com/arm64.apk", arm64?.url)
        assertEquals(50_000_000L, arm64?.size)
        assertNull(arm64?.digest)

        val universal = UpdateChecker.findApkAsset(
            listOf(assets.first()),
            listOf("arm64-v8a"),
        )
        assertEquals("https://example.com/universal.apk", universal?.url)
        assertEquals(99_000_000L, universal?.size)
    }

    @Test
    fun `sin asset compatible no hay APK`() {
        val assets = listOf(
            ReleaseAsset("app-x86_64-release.apk", "https://example.com/x86.apk", 1L, null),
        )
        assertNull(UpdateChecker.findApkAsset(assets, listOf("arm64-v8a")))
    }
}
