package com.rutamercaderistas.viewmodel

import android.app.Application
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

class UpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var viewModel: UpdateViewModel
    private val createdViewModels = mutableListOf<UpdateViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        preferencesRepository = mockk {
            coEvery { getUpdateSuppressedUntil() } returns 0L
            coEvery { setUpdateSuppressedUntil(any()) } returns Unit
            coEvery { getPendingUpdate() } returns null
            coEvery { setPendingUpdate(any()) } returns Unit
            coEvery { clearPendingUpdate() } returns Unit
            coEvery { getLastNotifiedUpdateCode() } returns 0
            coEvery { setLastNotifiedUpdateCode(any()) } returns Unit
        }
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
        Dispatchers.resetMain()
    }

    private fun awaitOnMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condición no cumplida dentro del timeout")
    }

    private fun mockAvailableUpdate(code: Int = 12002, name: String = "12.02") {
        mockkObject(UpdateChecker)
        coEvery {
            UpdateChecker.check(any())
        } returns UpdateInfo(available = true, versionCode = code, versionName = name, apkUrl = "http://x.apk")
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(UpdateUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `checkForUpdate transitions through states`() = runTest {
        viewModel.checkForUpdate(force = true, showFeedback = true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is UpdateUiState.Checking)
    }

    @Test
    fun `state survives after Idle`() = runTest {
        viewModel.checkForUpdate(force = true, showFeedback = false)
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertNotNull(state)
    }

    @Test
    fun `available persists pending and shows banner`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value == PendingUpdate("12.02", 12002, "http://x.apk")
            }
            coVerify { preferencesRepository.setPendingUpdate(PendingUpdate("12.02", 12002, "http://x.apk")) }
            awaitOnMain { viewModel.showUpdateBanner.value }
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `second check same version does not re-notify`() = runTest {
        mockAvailableUpdate()
        try {
            coEvery { preferencesRepository.getLastNotifiedUpdateCode() } returns 0 andThen 12002
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain {
                viewModel.pendingUpdate.value == PendingUpdate("12.02", 12002, "http://x.apk")
            }
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { preferencesRepository.setLastNotifiedUpdateCode(12002) }
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
    }

    @Test
    fun `stale stored pending is cleared`() = runTest {
        coEvery { preferencesRepository.getPendingUpdate() } returns
            PendingUpdate("11.00", 11000, "http://old.apk")
        val restored = track(UpdateViewModel(application, preferencesRepository))
        awaitOnMain { restored.pendingUpdate.value == null }
        awaitOnMain { !restored.showUpdateBanner.value }
        coVerify { preferencesRepository.clearPendingUpdate() }
    }

    @Test
    fun `suppress hides banner but keeps pending`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.showUpdateBanner.value }
            viewModel.suppressUntilTomorrow()
            awaitOnMain { !viewModel.showUpdateBanner.value }
            assertEquals(PendingUpdate("12.02", 12002, "http://x.apk"), viewModel.pendingUpdate.value)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }

    @Test
    fun `showPendingDialog reopens dialog without network`() = runTest {
        mockAvailableUpdate()
        try {
            viewModel.checkForUpdate(force = true, showFeedback = false)
            awaitOnMain { viewModel.pendingUpdate.value != null }
            viewModel.dismissDialog()
            viewModel.showPendingDialog()
            val state = viewModel.state.value
            assertTrue(state is UpdateUiState.Dialog)
            assertEquals("12.02", (state as UpdateUiState.Dialog).versionName)
        } finally {
            unmockkObject(UpdateChecker)
        }
    }
}
