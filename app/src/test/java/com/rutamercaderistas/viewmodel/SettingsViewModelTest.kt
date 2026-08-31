package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EanExcelParser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prefs: PreferencesRepository
    private lateinit var parser: EanExcelParser

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true)
        parser = mockk(relaxed = true)
        every { prefs.getFontScaleFlow() } returns flowOf(1f)
        every { prefs.getTransportModeFlow() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setFontScale delegates to repository`() = runTest {
        val vm = SettingsViewModel(prefs, parser)
        vm.setFontScale(1.2f)
        advanceUntilIdle()
        coVerify { prefs.setFontScale(1.2f) }
    }

    @Test
    fun `setTransportMode delegates to repository`() = runTest {
        val vm = SettingsViewModel(prefs, parser)
        vm.setTransportMode("drive")
        advanceUntilIdle()
        coVerify { prefs.setTransportMode("drive") }
    }

    @Test
    fun `clearEanCache sets version 0`() = runTest {
        val vm = SettingsViewModel(prefs, parser)
        vm.clearEanCache()
        advanceUntilIdle()
        coVerify { parser.setEanDataVersion(0) }
    }

    @Test
    fun `clearSearchHistory delegates to repository`() = runTest {
        val vm = SettingsViewModel(prefs, parser)
        vm.clearSearchHistory()
        advanceUntilIdle()
        coVerify { prefs.clearSearchHistory() }
    }

    @Test
    fun `versionName is not blank`() = runTest {
        val vm = SettingsViewModel(prefs, parser)
        assertFalse(vm.versionName.isBlank())
    }
}
