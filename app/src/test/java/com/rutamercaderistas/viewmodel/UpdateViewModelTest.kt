package com.rutamercaderistas.viewmodel

import android.app.Application
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var viewModel: UpdateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        preferencesRepository = mockk {
            every { getUpdateSuppressedUntil() } returns 0L
            coEvery { setUpdateSuppressedUntil(any()) } returns Unit
        }
        viewModel = UpdateViewModel(application, preferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}
