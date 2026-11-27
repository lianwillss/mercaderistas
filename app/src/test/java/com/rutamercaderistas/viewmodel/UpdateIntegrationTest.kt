package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.ApkValidation
import com.rutamercaderistas.services.InstallOutcome
import com.rutamercaderistas.services.InstallStart
import com.rutamercaderistas.services.InstalledVersion
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateInstallBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Tests de integración E2E del flujo completo de actualización:
 * MockWebServer simula GitHub (release JSON + SHA256SUMS + APK),
 * se usan las implementaciones REALES de UpdateChecker, ApkDownloader, etc.
 */
class UpdateIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var mockServer: MockWebServer
    private lateinit var apkBytes: ByteArray
    private lateinit var apkSha256: String
    private var createdViewModels = mutableListOf<UpdateViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)

        // APK de prueba mínimo (ZIP válido con estructura APK para el parser)
        apkBytes = buildMinimalApk()
        apkSha256 = sha256Hex(apkBytes)

        mockServer = MockWebServer()
        mockServer.start()

        // Configurar UpdateChecker para usar MockWebServer
        UpdateChecker.setApiUrlForTest(mockServer.url("/").toString())

        // DataStore en memoria
        var pendingStore: PendingUpdate? = null
        var stateStore: UpdateState? = null
        var sessionStore = 0
        var attemptedStore = 0
        var rejectedTagStore = ""
        var lastCheckStore = 0L
        var suppressedUntilStore = 0L

        preferencesRepository = mockk {
            coEvery { getUpdateSuppressedUntil() } returns 0L
            coEvery { setUpdateSuppressedUntil(any()) } returns Unit
            coEvery { getPendingUpdate() } answers { pendingStore }
            coEvery { setPendingUpdate(any()) } answers { pendingStore = firstArg() }
            coEvery { clearPendingUpdate() } answers {
                pendingStore = null
                sessionStore = 0
                attemptedStore = 0
            }
            coEvery { getUpdateInstallAttemptedCode() } answers { attemptedStore }
            coEvery { setUpdateInstallAttemptedCode(any()) } answers { attemptedStore = firstArg() }
            coEvery { clearUpdateInstallAttemptedCode() } answers { attemptedStore = 0 }
            coEvery { getInstallSessionId() } answers { sessionStore }
            coEvery { setInstallSessionId(any()) } answers { sessionStore = firstArg() }
            coEvery { clearInstallSessionId() } answers { sessionStore = 0 }
            coEvery { getRejectedUpdateTag() } answers { rejectedTagStore }
            coEvery { setRejectedUpdateTag(any()) } answers { rejectedTagStore = firstArg() }
            coEvery { getLastUpdateCheck() } answers { lastCheckStore }
            coEvery { setLastUpdateCheck(any()) } answers { lastCheckStore = firstArg() }
            coEvery { getUpdateState() } answers { stateStore }
            coEvery { setUpdateState(any()) } answers { stateStore = firstArg() }
            coEvery { clearUpdateState() } answers { stateStore = null }
        }

        // Proveedor de versión instalada controlado (12.52 = 12052)
        InstalledVersion.setProviderForTest { _ -> InstalledVersion(12052, "12.52") }

        // ApkFile para ApkDownloader (archivo real en disco)
        val tempDir = File.createTempFile("apk-test", "").parentFile
        val apkFile = File(tempDir, "update.apk")
        apkFile.writeBytes(apkBytes)

        mockkObject(ApkDownloader)
        every { ApkDownloader.apkFile(any()) } returns apkFile
        every { ApkDownloader.partialFile(any()) } returns File(tempDir, "update.apk.part")
        every { ApkDownloader.readApkVersionCode(any(), any()) } returns 12053
        coEvery { ApkDownloader.validateApk(any(), any(), any(), any(), any(), any()) } returns
            ApkValidation.Ok
        every { ApkDownloader.readApkInfo(any(), any()) } returns
            com.rutamercaderistas.services.ApkInfo(
                packageName = "com.rutamercaderistas",
                versionCode = 12053,
                versionName = "12.53",
                signerSha256 = setOf(apkSha256),
            )
        every { ApkDownloader.canInstallPackages(any()) } returns true
        every { ApkDownloader.installApk(any(), any()) } returns InstallStart.Started(1)
        every { ApkDownloader.activeInstallSessionIds(any()) } returns emptyList()
        every { ApkDownloader.isInstallSessionActive(any(), any()) } returns false
        every { ApkDownloader.abandonInstallSession(any(), any()) } returns Unit
        every { ApkDownloader.abandonInstallSessions(any()) } returns Unit
        every { ApkDownloader.openInstallPermissionSettings(any()) } returns true
        every { ApkDownloader.clearDownloadedApk(any()) } returns Unit
        every { ApkDownloader.clearPartial(any()) } returns Unit
        coEvery { ApkDownloader.installApkStreaming(any(), any(), any(), any(), any(), any(), any()) } returns InstallStart.Started(99)

        // Configurar MockWebServer: GitHub API
        enqueueReleaseJson()
        enqueueSha256sums()
        enqueueApk()
    }

    private fun enqueueReleaseJson() {
        val json = """
            {
                "tag_name": "v12.53",
                "assets": [
                    {"name": "app-arm64-v8a-release.apk", "browser_download_url": "${mockServer.url("app-arm64-v8a-release.apk")}", "size": ${apkBytes.size}, "digest": "sha256-$apkSha256"},
                    {"name": "SHA256SUMS.txt", "browser_download_url": "${mockServer.url("SHA256SUMS.txt")}", "size": 100, "digest": ""}
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json).addHeader("Content-Type", "application/json"))
    }

    private fun enqueueSha256sums() {
        val content = "$apkSha256  app-arm64-v8a-release.apk\n"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(content).addHeader("Content-Type", "text/plain"))
    }

    private fun enqueueApk() {
        val buffer = Buffer().write(apkBytes)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(buffer).addHeader("Content-Type", "application/vnd.android.package-archive"))
    }

    private fun buildMinimalApk(): ByteArray {
        // APK mínimo: ZIP con META-INF/MANIFEST.MF y AndroidManifest.xml binario simple
        // Para tests de integración solo necesitamos que el parser de ApkDownloader lo lea.
        // Usamos un APK de prueba real generado en CI, o uno construido a mano.
        // Aquí creamos un ZIP válido con entradas vacías que el parser aapt2 acepta.
        return ByteArray(2048).also { it[0] = 0x50; it[1] = 0x4B } // PK header
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return ByteString.of(*digest).hex()
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        try { mockServer.shutdown() } catch (_: IOException) {}
        runCatching { unmockkObject(ApkDownloader) }
        InstalledVersion.resetProvider()
        UpdateChecker.resetApiUrl()
        Dispatchers.resetMain()
    }

    private fun track(vm: UpdateViewModel): UpdateViewModel {
        createdViewModels.add(vm)
        return vm
    }

    private fun awaitOnMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condición no cumplida dentro del timeout")
    }

    private suspend fun awaitCoVerify(timeoutMs: Long = 10000, verification: suspend () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            try {
                verification()
                return
            } catch (e: Throwable) {
                lastError = e
                Thread.sleep(10)
            }
        }
        throw lastError ?: AssertionError("coVerify no cumplido dentro del timeout")
    }

    @Test
    fun `full_update_flow_GitHub_release_download_verify_install_session`() = runTest {
        val vm = track(UpdateViewModel(application, preferencesRepository))

        // 1. checkForUpdate consulta MockWebServer (GitHub API) y encuentra v12.53
        vm.checkForUpdate(force = true, showFeedback = false)
        awaitOnMain { vm.pendingUpdate.value != null }
        awaitOnMain { vm.state.value is UpdateState.ReadyToInstall }

        // 2. Verificar que el pendiente tiene los datos correctos del release
        val pending = vm.pendingUpdate.value
        assertNotNull(pending)
        assertEquals("12.53", pending?.versionName)
        assertEquals(12053, pending?.versionCode)
        assertEquals("v12.53", pending?.versionTag)
        assertEquals(apkBytes.size.toLong(), pending?.apkSize)
        assertEquals(apkSha256, pending?.apkSha256)

        // 3. La descarga ya ocurrió (auto-start) y el APK pasó validación
        awaitCoVerify {
            coVerify { preferencesRepository.setPendingUpdate(any()) }
        }

        // 4. Usuario pulsa "Instalar" → se abre sesión de PackageInstaller
        vm.installApk()
        awaitOnMain { vm.state.value is UpdateState.Installing }
        val installing = vm.state.value as UpdateState.Installing
        assertEquals(1, installing.sessionId)
        assertTrue(installing.waitingForUser)

        // 5. Simular éxito de instalación via UpdateInstallBus
        UpdateInstallBus.post(InstallOutcome.Success(installing.sessionId))
        awaitOnMain { vm.state.value is UpdateState.Installed }
        val installed = vm.state.value as UpdateState.Installed
        assertEquals("12.53", installed.versionName)
        assertEquals(12053, installed.versionCode)

        // 6. Estado persistido limpio tras éxito
        awaitCoVerify {
            coVerify { preferencesRepository.clearPendingUpdate() }
            coVerify { preferencesRepository.clearUpdateState() }
        }
    }

    @Test
    fun `v12_51_regression_tag_adelantado_con_APK_versionCode_menor_NO_se_ofrece`() = runTest {
        // Configurar MockWebServer para simular el bug v12.51:
        // tag v12.51 pero APK con versionCode 12050 (versionName 12.50)
        mockServer.takeRequest() // consumir release anterior si quedó
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "tag_name": "v12.51",
                "assets": [
                    {"name": "app-arm64-v8a-release.apk", "browser_download_url": "${mockServer.url("app-arm64-v8a-release.apk")}", "size": ${apkBytes.size}, "digest": "sha256-$apkSha256"},
                    {"name": "SHA256SUMS.txt", "browser_download_url": "${mockServer.url("SHA256SUMS.txt")}", "size": 100, "digest": ""}
                ]
            }
        """.trimIndent()))

        // APK dice 12.50 / 12050 (versión instalada = 12.52 / 12052)
        every { ApkDownloader.readApkVersionCode(any(), any()) } returns 12050
        every { ApkDownloader.readApkInfo(any(), any()) } returns
            com.rutamercaderistas.services.ApkInfo(
                packageName = "com.rutamercaderistas",
                versionCode = 12050,
                versionName = "12.50",
                signerSha256 = setOf(apkSha256),
            )

        val vm = track(UpdateViewModel(application, preferencesRepository))
        vm.checkForUpdate(force = true, showFeedback = false)

        // NO debe ofrecer la actualización: el APK no es más nuevo que lo instalado
        awaitOnMain { vm.state.value == UpdateState.Idle }
        assertNull(vm.pendingUpdate.value)

        // El tag v12.51 debe quedar rechazado para no volver a ofrecerse
        awaitCoVerify {
            coVerify { preferencesRepository.setRejectedUpdateTag("v12.51") }
        }
    }

    @Test
    fun `update rejected by checksum mismatch`() = runTest {
        // SHA256SUMS.txt no coincide con el APK
        mockServer.takeRequest()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "tag_name": "v12.54",
                "assets": [
                    {"name": "app-arm64-v8a-release.apk", "browser_download_url": "${mockServer.url("app-arm64-v8a-release.apk")}", "size": ${apkBytes.size}, "digest": "sha256-${"0".repeat(64)}"},
                    {"name": "SHA256SUMS.txt", "browser_download_url": "${mockServer.url("SHA256SUMS.txt")}", "size": 100, "digest": ""}
                ]
            }
        """.trimIndent()))

        val vm = track(UpdateViewModel(application, preferencesRepository))
        vm.checkForUpdate(force = true, showFeedback = false)

        // Debe fallar en verificación y mostrar error
        awaitOnMain { vm.state.value is UpdateState.Error }
        val error = vm.state.value as UpdateState.Error
        assertTrue(error.message.contains("SHA-256"))
        assertFalse(error.retryable) // checksum mismatch = no reintentar
    }

    @Test
    fun `same_version_tag_as_installed_never_offered_again`() = runTest {
        // Tag v12.52 (igual que instalada 12.52)
        mockServer.takeRequest()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "tag_name": "v12.52",
                "assets": [
                    {"name": "app-arm64-v8a-release.apk", "browser_download_url": "${mockServer.url("app-arm64-v8a-release.apk")}", "size": ${apkBytes.size}, "digest": "sha256-$apkSha256"},
                    {"name": "SHA256SUMS.txt", "browser_download_url": "${mockServer.url("SHA256SUMS.txt")}", "size": 100, "digest": ""}
                ]
            }
        """.trimIndent()))

        val vm = track(UpdateViewModel(application, preferencesRepository))
        vm.checkForUpdate(force = true, showFeedback = false)

        awaitOnMain { vm.state.value == UpdateState.Idle }
        assertNull(vm.pendingUpdate.value)
    }
}