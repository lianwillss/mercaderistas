package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateInfo
import com.rutamercaderistas.services.UpdateNotifier
import com.rutamercaderistas.worker.UpdateDownloadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
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
    private lateinit var downloadManager: UpdateDownloadManager
    private lateinit var apkFile: File
    private lateinit var viewModel: UpdateViewModel
    private val createdViewModels = mutableListOf<UpdateViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        apkFile = File.createTempFile("update-test", ".apk").apply { delete() }

        preferencesRepository = mockk {
            coEvery { getUpdateSuppressedUntil() } returns 0L
            coEvery { setUpdateSuppressedUntil(any()) } returns Unit
            coEvery { getPendingUpdate() } returns null
            coEvery { setPendingUpdate(any()) } returns Unit
            coEvery { clearPendingUpdate() } returns Unit
            coEvery { getLastNotifiedUpdateCode() } returns 0
            coEvery { setLastNotifiedUpdateCode(any()) } returns Unit
            coEvery { getLastNotifiedUpdateTag() } returns ""
            coEvery { setLastNotifiedUpdateTag(any()) } returns Unit
            coEvery { wasUpdateNotifAsked() } returns false
            coEvery { setUpdateNotifAsked() } returns Unit
            coEvery { getLastUpdateCheck() } returns 0L
            coEvery { setLastUpdateCheck(any()) } returns Unit
        }

        downloadManager = mockk(relaxed = true)
        every { downloadManager.observe() } returns emptyFlow()
        every { downloadManager.enqueue(any()) } returns Unit
        every { downloadManager.cancel() } returns Unit

        mockkObject(ApkDownloader)
        every { ApkDownloader.apkFile(any()) } returns apkFile
        every { ApkDownloader.partialFile(any()) } returns File(apkFile.parentFile, "update.apk.part")
        every { ApkDownloader.readApkVersionCode(any(), any()) } returns 0
        every { ApkDownloader.readApkInfo(any(), any()) } returns
            com.rutamercaderistas.services.ApkInfo(
                packageName = "com.rutamercaderistas",
                versionCode = BuildConfig.VERSION_CODE + 1,
                versionName = "12.42",
            )
        every { ApkDownloader.canInstallPackages(any()) } returns true
        every { ApkDownloader.installApk(any(), any()) } returns null
        every { ApkDownloader.openInstallPermissionSettings(any()) } returns true
        every { ApkDownloader.clearDownloadedApk(any()) } returns Unit
        every { ApkDownloader.clearPartial(any()) } returns Unit

        mockkObject(UpdateNotifier)
        every { UpdateNotifier.hasPermission(any()) } returns false
        every { UpdateNotifier.postPending(any(), any()) } returns Unit
        every { UpdateNotifier.postReady(any(), any()) } returns Unit
        every { UpdateNotifier.postDownloadError(any(), any()) } returns Unit
        every { UpdateNotifier.cancelUpdate(any()) } returns Unit
        every { UpdateNotifier.cancelProgress(any()) } returns Unit
        every { UpdateNotifier.ensureChannel(any()) } returns Unit

        viewModel = track(UpdateViewModel(application, preferencesRepository, downloadManager))
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
        runCatching { unmockkObject(UpdateNotifier) }
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

    /**
     * Espera la verificación misma (no un estado proxy): varios awaits de
     * estado se cumplen trivialmente antes de que la coroutine bajo test
     * llegue al mock, y el coVerify directo pierde la carrera en suite.
     */
    private fun awaitVerify(timeoutMs: Long = 10000, verification: () -> Unit) {
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
        throw lastError ?: AssertionError("verify no cumplido dentro del timeout")
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

    private fun mockAvailableUpdate(code: Int = 12002, name: String = "12.02", tag: String = "v12.02") {
        mockkObject(UpdateChecker)
        coEvery {
            UpdateChecker.check(any())
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
        assertFalse(viewModel.showUpdateSheet.value)
    }

    @Test
    fun `available persists pending y muestra la tarjeta`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value == PendingUpdate("12.02", 12002, "http://x.apk", "v12.02")
            }
            awaitCoVerify {
                coVerify {
                    preferencesRepository.setPendingUpdate(
                        PendingUpdate("12.02", 12002, "http://x.apk", "v12.02"),
                    )
                }
            }
            awaitOnMain { viewModel.showUpdateBanner.value }
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Available }
            assertEquals("12.02", (viewModel.card.value as UpdateCardUiState.Available).versionName)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `second check same version does not re-notify`() = runTest {
        mockAvailableUpdate()
        try {
            coEvery { preferencesRepository.getLastNotifiedUpdateTag() } returns "" andThen "v12.02"
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value == PendingUpdate("12.02", 12002, "http://x.apk", "v12.02")
            }
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            awaitVerify { verify(exactly = 1) { UpdateNotifier.postPending(any(), "12.02") } }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `patch version notifies again after minor notified`() = runTest {
        mockkObject(UpdateChecker)
        try {
            coEvery {
                UpdateChecker.check(any())
            } returns UpdateInfo(
                available = true,
                versionCode = 12020,
                versionName = "12.20.1",
                apkUrl = "http://x.apk",
                tag = "v12.20.1",
            )
            coEvery { preferencesRepository.getLastNotifiedUpdateTag() } returns "v12.20"
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value == PendingUpdate("12.20.1", 12020, "http://x.apk", "v12.20.1")
            }
            awaitCoVerify {
                coVerify { preferencesRepository.setLastNotifiedUpdateTag("v12.20.1") }
            }
            awaitVerify { verify { UpdateNotifier.postPending(any(), "12.20.1") } }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `restore shows banner for stored newer pending`() = runTest {
        val newer = PendingUpdate("99.99", BuildConfig.VERSION_CODE + 1, "http://x.apk")
        coEvery { preferencesRepository.getPendingUpdate() } returns newer
        val restored = track(UpdateViewModel(application, preferencesRepository, downloadManager))
        awaitOnMain { restored.pendingUpdate.value == newer }
        awaitOnMain { restored.showUpdateBanner.value }
        awaitOnMain { restored.card.value is UpdateCardUiState.Available }
    }

    @Test
    fun `stale stored pending is cleared`() = runTest {
        coEvery { preferencesRepository.getPendingUpdate() } returns
            PendingUpdate("11.00", 11000, "http://old.apk")
        val restored = track(UpdateViewModel(application, preferencesRepository, downloadManager))
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
                PendingUpdate("12.02", 12002, "http://x.apk", "v12.02"),
                viewModel.pendingUpdate.value,
            )
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `showPendingDialog reopens the sheet without network`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.dismissSheet()
            assertFalse(viewModel.showUpdateSheet.value)
            viewModel.showPendingDialog()
            assertTrue(viewModel.showUpdateSheet.value)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `startUpdate enqueues and shows downloading card`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.startUpdate()
            assertTrue(viewModel.showUpdateSheet.value)
            awaitOnMain { viewModel.card.value is UpdateCardUiState.Downloading }
            awaitVerify {
                verify(exactly = 1) {
                    downloadManager.enqueue(
                        PendingUpdate("12.02", 12002, "http://x.apk", "v12.02"),
                    )
                }
            }
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
                UpdateChecker.check(any())
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
    fun `up to date clears pending and notification`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            coEvery {
                UpdateChecker.check(any())
            } returns UpdateInfo(available = false, versionCode = 0, versionName = "", apkUrl = "")
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value == null }
            awaitVerify { verify { UpdateNotifier.cancelUpdate(any()) } }
            awaitCoVerify { coVerify { preferencesRepository.clearPendingUpdate() } }
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
            awaitVerify { verify { ApkDownloader.openInstallPermissionSettings(any()) } }
            assertTrue(viewModel.showUpdateSheet.value)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }
}
