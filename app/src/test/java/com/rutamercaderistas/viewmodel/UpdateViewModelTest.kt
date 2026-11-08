package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.ApkValidation
import com.rutamercaderistas.services.DownloadResult
import com.rutamercaderistas.services.InstallStart
import com.rutamercaderistas.services.UpdateChecker
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
        // PackageManager relajado devuelve valores vacíos: InstalledVersion
        // cae a BuildConfig, que es la versión real de esta compilación.

        apkFile = File.createTempFile("update-test", ".apk").apply { delete() }

        preferencesRepository = mockk {
            coEvery { getUpdateSuppressedUntil() } returns 0L
            coEvery { setUpdateSuppressedUntil(any()) } returns Unit
            coEvery { getPendingUpdate() } returns null
            coEvery { setPendingUpdate(any()) } returns Unit
            coEvery { clearPendingUpdate() } returns Unit
            coEvery { getRejectedUpdateTag() } returns ""
            coEvery { setRejectedUpdateTag(any()) } returns Unit
            coEvery { getLastUpdateCheck() } returns 0L
            coEvery { setLastUpdateCheck(any()) } returns Unit
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
        every { ApkDownloader.installApk(any(), any()) } returns InstallStart.Started
        every { ApkDownloader.openInstallPermissionSettings(any()) } returns true
        every { ApkDownloader.clearDownloadedApk(any()) } returns Unit
        every { ApkDownloader.clearPartial(any()) } returns Unit
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
        // 10s: en suite completa el Main global se congestiona (hilos reales
        // de Paging de otras clases) y 5s expiraba en flake.
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
        assertNull(viewModel.card.value)
        assertNull(viewModel.pendingUpdate.value)
    }

    @Test
    fun `available persists pending y muestra la tarjeta`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value ==
                    PendingUpdate("12.53", 12053, "http://x.apk", "v12.53")
            }
            awaitCoVerify {
                coVerify {
                    preferencesRepository.setPendingUpdate(
                        PendingUpdate("12.53", 12053, "http://x.apk", "v12.53"),
                    )
                }
            }
            awaitOnMain { viewModel.showUpdateBanner.value }
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Available }
            assertEquals(
                "12.53",
                (viewModel.card.value as UpdateCardUiState.Available).versionName,
            )
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `second check same version does not restart the download`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Available }
            coVerify(exactly = 0) {
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
        awaitOnMain { restored.showUpdateBanner.value }
        awaitOnMain { restored.card.value is UpdateCardUiState.Available }
    }

    @Test
    fun `stale stored pending is cleared`() = runTest {
        coEvery { preferencesRepository.getPendingUpdate() } returns
            PendingUpdate("11.00", 11000, "http://old.apk")
        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitCoVerify { coVerify { preferencesRepository.clearPendingUpdate() } }
        awaitOnMain { restored.pendingUpdate.value == null }
        awaitOnMain { !restored.showUpdateBanner.value }
    }

    @Test
    fun `suppress hides banner but keeps pending`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.showUpdateBanner.value }
            viewModel.suppressUntilTomorrow()
            awaitOnMain { !viewModel.showUpdateBanner.value }
            assertEquals(
                PendingUpdate("12.53", 12053, "http://x.apk", "v12.53"),
                viewModel.pendingUpdate.value,
            )
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `download in foreground starts install automatically`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.onActivityResumed()
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            // Simula el APK que deja el descargador real.
            apkFile.writeBytes(ByteArray(2048))
            viewModel.startUpdate()
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Installing }
            awaitCoVerify {
                coVerify(exactly = 1) {
                    ApkDownloader.download(
                        any(),
                        "http://x.apk",
                        any(),
                        any(),
                        "12.53",
                        12053,
                        any(),
                    )
                }
            }
            awaitCoVerify {
                coVerify(exactly = 1) { ApkDownloader.installApk(any(), any()) }
            }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `download in background waits for resume to install`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            apkFile.writeBytes(ByteArray(2048))
            viewModel.startUpdate()
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Ready }
            // Sin foreground no se abre la confirmación del sistema.
            verify(exactly = 0) { ApkDownloader.installApk(any(), any()) }

            viewModel.onActivityResumed()
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Installing }
            awaitCoVerify {
                coVerify(exactly = 1) { ApkDownloader.installApk(any(), any()) }
            }
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
                (viewModel.card.value as? UpdateCardUiState.Failed)?.message == "Sin conexión"
            }
            assertTrue((viewModel.card.value as UpdateCardUiState.Failed).retryable)
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
                (viewModel.card.value as? UpdateCardUiState.Failed)?.retryable == false
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
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Available }
            awaitCoVerify { coVerify { ApkDownloader.clearPartial(any()) } }
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
            viewModel.checkForUpdate(
                force = true,
                showFeedback = false,
                autoStartDownload = true,
            )
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Ready }
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
                (viewModel.card.value as? UpdateCardUiState.Failed)?.message == message
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
            (restored.card.value as? UpdateCardUiState.Failed)?.message?.contains("SHA-256") == true
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
}
