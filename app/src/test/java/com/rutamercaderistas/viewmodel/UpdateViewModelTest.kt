package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.ApkValidation
import com.rutamercaderistas.services.DownloadResult
import com.rutamercaderistas.services.InstallOutcome
import com.rutamercaderistas.services.InstallStart
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateInstallBus
import com.rutamercaderistas.services.UpdateInfo
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var apkFile: File
    private lateinit var viewModel: UpdateViewModel
    private val createdViewModels = mutableListOf<UpdateViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)

        apkFile = File.createTempFile("update-test", ".apk").apply { delete() }

        // Almacenamiento en memoria: los tests de restauración dependen de que
        // lo que se persiste se pueda volver a leer, como en DataStore real.
        var pendingStore: PendingUpdate? = null
        var stateStore: UpdateState? = null
        var sessionStore = 0
        var attemptedStore = 0

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
            coEvery { getRejectedUpdateTag() } returns ""
            coEvery { setRejectedUpdateTag(any()) } returns Unit
            coEvery { getLastUpdateCheck() } returns 0L
            coEvery { setLastUpdateCheck(any()) } returns Unit
            coEvery { getUpdateState() } answers { stateStore }
            coEvery { setUpdateState(any()) } answers { stateStore = firstArg() }
            coEvery { clearUpdateState() } answers { stateStore = null }
        }

        mockkObject(ApkDownloader)
        every { ApkDownloader.apkFile(any()) } returns apkFile
        every { ApkDownloader.partialFile(any()) } returns File(apkFile.parentFile, "update.apk.part")
        every { ApkDownloader.readApkVersionCode(any(), any()) } returns 0
        coEvery { ApkDownloader.validateApk(any(), any(), any(), any(), any(), any()) } returns
            ApkValidation.Ok
        every { ApkDownloader.readApkInfo(any(), any()) } returns
            com.rutamercaderistas.services.ApkInfo(
                packageName = "com.rutamercaderistas",
                versionCode = BuildConfig.VERSION_CODE + 1,
                versionName = "12.53",
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
        coEvery {
            ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
        } returns DownloadResult.Success(apkFile)

        viewModel = track(UpdateViewModel(application, preferencesRepository))
    }

    private fun track(vm: UpdateViewModel): UpdateViewModel {
        createdViewModels.add(vm)
        return vm
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        runCatching { unmockkObject(ApkDownloader) }
        apkFile.delete()
        Dispatchers.resetMain()
    }

    private fun awaitOnMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
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

    private fun mockAvailableUpdate(code: Int = 12053, name: String = "12.53", tag: String = "v12.53") {
        mockkObject(UpdateChecker)
        coEvery {
            UpdateChecker.check(any(), any())
        } returns UpdateInfo(
            available = true,
            versionCode = code,
            versionName = name,
            apkUrl = "http://x.apk",
            tag = tag,
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(UpdateState.Idle, viewModel.state.value)
        assertNull(viewModel.pendingUpdate.value)
    }

    @Test
    fun `available persists pending y descarga sola`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value ==
                    PendingUpdate("12.53", 12053, "http://x.apk", "v12.53")
            }
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            awaitCoVerify {
                coVerify {
                    preferencesRepository.setPendingUpdate(
                        PendingUpdate("12.53", 12053, "http://x.apk", "v12.53"),
                    )
                }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `second check same version does not restart the download`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            viewModel.checkForUpdate(force = true, showFeedback = false)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UpdateState.ReadyToInstall::class.java, viewModel.state.value.javaClass)
            coVerify(exactly = 1) {
                ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `restore shows banner for stored newer pending`() = runTest {
        val newer = PendingUpdate("99.99", BuildConfig.VERSION_CODE + 1, "http://x.apk")
        coEvery { preferencesRepository.getPendingUpdate() } returns newer
        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitOnMain { restored.pendingUpdate.value == newer }
        // Sin APK descargado, la restauración deja el pendiente pero no inventa estado
        awaitOnMain { restored.state.value == UpdateState.Idle }
    }

    @Test
    fun `stale stored pending is cleared`() = runTest {
        coEvery { preferencesRepository.getPendingUpdate() } returns
            PendingUpdate("11.00", 11000, "http://old.apk")
        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitCoVerify { coVerify { preferencesRepository.clearPendingUpdate() } }
        awaitOnMain { restored.pendingUpdate.value == null }
        assertEquals(UpdateState.Idle, restored.state.value)
    }

    @Test
    fun `suppress hides banner but keeps pending`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value !is UpdateState.Idle }
            viewModel.suppressUntilTomorrow()
            awaitOnMain { viewModel.state.value is UpdateState.Idle }
            assertEquals(
                PendingUpdate("12.53", 12053, "http://x.apk", "v12.53"),
                viewModel.pendingUpdate.value,
            )
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `download verifies and waits for the user to install`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            // La descarga NO abre el instalador por su cuenta.
            verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }

            apkFile.writeBytes(ByteArray(2048))
            viewModel.installApk()
            awaitCoVerify {
                coVerify(exactly = 1) { ApkDownloader.installApk(any(), any()) }
            }
            awaitCoVerify {
                coVerify(exactly = 1) {
                    preferencesRepository.setUpdateInstallAttemptedCode(12053)
                }
            }
            awaitCoVerify {
                coVerify(exactly = 1) { preferencesRepository.setInstallSessionId(1) }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `restore with a pending session does not create another`() = runTest {
        val code = BuildConfig.VERSION_CODE + 1
        val newer = PendingUpdate("99.99", code, "http://x.apk", "v99.99")
        coEvery { preferencesRepository.getPendingUpdate() } returns newer
        coEvery { preferencesRepository.getUpdateInstallAttemptedCode() } returns code
        every { ApkDownloader.activeInstallSessionIds(any()) } returns listOf(7)
        apkFile.writeBytes(ByteArray(2048))

        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitOnMain {
            restored.state.value is UpdateState.Installing
        }

        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }
    }

    @Test
    fun `restore finds a stored session when mySessions is empty`() = runTest {
        val code = BuildConfig.VERSION_CODE + 1
        val newer = PendingUpdate("99.99", code, "http://x.apk", "v99.99")
        coEvery { preferencesRepository.getPendingUpdate() } returns newer
        coEvery { preferencesRepository.getInstallSessionId() } returns 7
        every { ApkDownloader.activeInstallSessionIds(any()) } returns emptyList()
        every { ApkDownloader.isInstallSessionActive(any(), 7) } returns true
        apkFile.writeBytes(ByteArray(2048))

        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitOnMain {
            restored.state.value is UpdateState.Installing
        }
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }
    }

    @Test
    fun `retry install abandons the old session and starts a new one`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            apkFile.writeBytes(ByteArray(2048))

            viewModel.retryInstall()
            awaitOnMain { viewModel.state.value is UpdateState.Installing }
            awaitCoVerify {
                coVerify(exactly = 1) { ApkDownloader.installApk(any(), any()) }
            }
            verify { ApkDownloader.abandonInstallSessions(any()) }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `download error shows retryable failure`() = runTest {
        mockAvailableUpdate()
        try {
            coEvery {
                ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
            } returns DownloadResult.Error("Sin conexión", retryable = true)
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.startUpdate()
            awaitOnMain {
                (viewModel.state.value as? UpdateState.Error)?.message == "Sin conexión"
            }
            assertTrue((viewModel.state.value as UpdateState.Error).retryable)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `release rejected is remembered so it never loops`() = runTest {
        mockAvailableUpdate()
        try {
            coEvery {
                ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
            } returns DownloadResult.Error(
                "La versión descargada no es más reciente",
                retryable = false,
                releaseRejected = true,
            )
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.startUpdate()
            awaitOnMain {
                (viewModel.state.value as? UpdateState.Error)?.retryable == false
            }
            awaitCoVerify {
                coVerify { preferencesRepository.setRejectedUpdateTag("v12.53") }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `cancel download clears partial and returns to available`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.startUpdate()
            viewModel.cancelDownload()
            awaitOnMain { viewModel.state.value == UpdateState.Idle }
            awaitCoVerify { coVerify { ApkDownloader.clearPartial(any()) } }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `startUpdate with insufficient space shows message and does not download`() = runTest {
        mockkObject(UpdateChecker)
        coEvery {
            UpdateChecker.check(any(), any())
        } returns UpdateInfo(
            available = true,
            versionCode = 12053,
            versionName = "12.53",
            apkUrl = "http://x.apk",
            tag = "v12.53",
            apkSize = 50_000_000L, // 50 MB
        )
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }

            // mockkObject(ApkDownloader) en setUp devuelve 0 para Long;
            // forzamos el cálculo real para 50MB: 50*2 + 20 = 120 MB.
            every { ApkDownloader.espacioNecesarioParaInstalacion(any()) } returns 120_000_000L
            val mockFilesDir = mockk<File>(relaxed = true)
            every { mockFilesDir.usableSpace } returns 10_000_000L // 10 MB libre
            every { application.filesDir } returns mockFilesDir

            viewModel.startUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            awaitOnMain { viewModel.message.value != null }
            assertTrue(viewModel.message.value!!.contains("No hay espacio suficiente"))
            // No debe haber iniciado la descarga
            awaitCoVerify {
                coVerify(exactly = 0) { ApkDownloader.download(any(), any(), any(), any(), any(), any(), any()) }
            }
            assertEquals(UpdateState.Idle, viewModel.state.value)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `network error keeps pending and shows message`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            coEvery {
                UpdateChecker.check(any(), any())
            } returns UpdateInfo(
                available = false,
                versionCode = 0,
                versionName = "",
                apkUrl = "",
                error = "Sin conexión a internet",
            )
            viewModel.checkForUpdate(force = true, showFeedback = true)
            awaitOnMain { viewModel.message.value != null }
            assertNotNull(viewModel.pendingUpdate.value)
            coVerify(exactly = 0) { preferencesRepository.clearPendingUpdate() }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `up to date clears pending and apk`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            coEvery {
                UpdateChecker.check(any(), any())
            } returns UpdateInfo(available = false, versionCode = 0, versionName = "", apkUrl = "")
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value == null }
            awaitCoVerify { coVerify { preferencesRepository.clearPendingUpdate() } }
            verify { ApkDownloader.clearDownloadedApk(any()) }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `launch check auto starts the download`() = runTest {
        mockAvailableUpdate()
        try {
            apkFile.writeBytes(ByteArray(2048))
            viewModel.onActivityResumed()
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall || viewModel.state.value is UpdateState.Installing }
            coVerify(exactly = 1) {
                ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `install without permission opens settings`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            apkFile.writeBytes(ByteArray(2048))
            every { ApkDownloader.canInstallPackages(any()) } returns false
            viewModel.installApk()
            awaitCoVerify { coVerify { ApkDownloader.openInstallPermissionSettings(any()) } }
            verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `pending stores published size and sha`() = runTest {
        mockkObject(UpdateChecker)
        try {
            val sha = "a".repeat(64)
            coEvery {
                UpdateChecker.check(any(), any())
            } returns UpdateInfo(
                available = true,
                versionCode = 12053,
                versionName = "12.53",
                apkUrl = "http://x.apk",
                tag = "v12.53",
                apkSize = 1234L,
                apkSha256 = sha,
            )
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value ==
                    PendingUpdate("12.53", 12053, "http://x.apk", "v12.53", 1234L, sha)
            }
            awaitCoVerify {
                coVerify {
                    preferencesRepository.setPendingUpdate(
                        PendingUpdate("12.53", 12053, "http://x.apk", "v12.53", 1234L, sha),
                    )
                }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `install blocks apk signed with another certificate`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            apkFile.writeBytes(ByteArray(2048))
            val message =
                "El APK está firmado con un certificado distinto al de esta app. Por seguridad no se instala."
            coEvery { ApkDownloader.validateApk(any(), any(), any(), any(), any(), any()) } returns
                ApkValidation.Failed(message, retryable = false)
            viewModel.installApk()
            awaitOnMain {
                (viewModel.state.value as? UpdateState.Error)?.message == message
            }
            verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }
            verify { ApkDownloader.clearDownloadedApk(any()) }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `restore does not mark invalid apk ready`() = runTest {
        val pending = PendingUpdate(
            "99.99",
            BuildConfig.VERSION_CODE + 1,
            "http://x.apk",
            "v99.99",
            2048L,
            "a".repeat(64),
        )
        coEvery { preferencesRepository.getPendingUpdate() } returns pending
        apkFile.writeBytes(ByteArray(2048))
        coEvery { ApkDownloader.validateApk(any(), any(), any(), any(), any(), any()) } returns
            ApkValidation.Failed("La verificación SHA-256 del APK falló")
        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitOnMain {
            (restored.state.value as? UpdateState.Error)?.message?.contains("SHA-256") == true
        }
        verify { ApkDownloader.clearDownloadedApk(any()) }
    }

    @Test
    fun `integrity error surfaces its specific message`() = runTest {
        mockkObject(UpdateChecker)
        try {
            coEvery {
                UpdateChecker.check(any(), any())
            } returns UpdateInfo(
                available = false,
                versionCode = 0,
                versionName = "",
                apkUrl = "",
                error = "El release no publica el SHA-256 del APK",
            )
            viewModel.checkForUpdate(force = true, showFeedback = true)
            awaitOnMain {
                viewModel.message.value == "El release no publica el SHA-256 del APK"
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `launch check respects suppress until tomorrow`() = runTest {
        mockAvailableUpdate()
        coEvery { preferencesRepository.getUpdateSuppressedUntil() } returns Long.MAX_VALUE
        try {
            viewModel.checkForUpdateOnLaunch()
            awaitOnMain { viewModel.pendingUpdate.value != null }
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UpdateState.Idle, viewModel.state.value)
            coVerify(exactly = 0) {
                ApkDownloader.download(any(), any(), any(), any(), any(), any(), any())
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `stale install session outcome is ignored`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            apkFile.writeBytes(ByteArray(2048))
            viewModel.installApk()
            awaitOnMain { (viewModel.state.value as? UpdateState.Installing)?.sessionId == 1 }

            UpdateInstallBus.post(InstallOutcome.Failed(sessionId = 99, message = "sesión vieja"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value is UpdateState.Installing)

            UpdateInstallBus.post(InstallOutcome.Failed(sessionId = 1, message = "cancelada"))
            awaitOnMain { viewModel.state.value is UpdateState.Error }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `install success clears persisted state`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            apkFile.writeBytes(ByteArray(2048))
            viewModel.installApk()
            awaitOnMain { (viewModel.state.value as? UpdateState.Installing)?.sessionId == 1 }

            UpdateInstallBus.post(InstallOutcome.Success(1))
            awaitOnMain { viewModel.state.value is UpdateState.Installed }
            awaitCoVerify { coVerify { preferencesRepository.clearPendingUpdate() } }
            awaitCoVerify { coVerify { preferencesRepository.clearUpdateState() } }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `install falls back to streaming when normal install fails due to space`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.state.value is UpdateState.ReadyToInstall }
            apkFile.writeBytes(ByteArray(2048))

            // Primera llamada falla por espacio, segunda (streaming) ok
            var callCount = 0
            every { ApkDownloader.installApk(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    InstallStart.Error("No hay espacio suficiente. Libera 100 MB e inténtalo de nuevo.")
                } else {
                    InstallStart.Started(2)
                }
            }
            coEvery { ApkDownloader.installApkStreaming(any(), any(), any(), any(), any(), any(), any()) } returns InstallStart.Started(2)

            viewModel.installApk()
            awaitOnMain { (viewModel.state.value as? UpdateState.Installing)?.sessionId == 2 }

            awaitCoVerify {
                coVerify(exactly = 1) { ApkDownloader.installApk(any(), any()) }
                coVerify(exactly = 1) { ApkDownloader.installApkStreaming(any(), any(), any(), any(), any(), any(), any()) }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }
}
