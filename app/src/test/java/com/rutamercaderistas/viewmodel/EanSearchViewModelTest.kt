package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.services.EanExcelParser
import com.rutamercaderistas.services.EAN_DATA_VERSION
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EanSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: EanProductDao
    private lateinit var parser: EanExcelParser

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mockk(relaxed = true)
        parser = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank query emits Ready with catalog products and import runs`() = runTest {
        val sample = listOf(
            EanProductEntity(eanPrincipal = "1234567890123", descripcionProducto = "A", marca = "X"),
            EanProductEntity(eanPrincipal = "2345678901234", descripcionProducto = "B", marca = "Y"),
        )
        coEvery { dao.count() } returns 0
        coEvery { dao.hasUnnormalized() } returns 0
        every { dao.getAll() } returns flowOf(sample)
        every { dao.searchAll(any()) } returns flowOf(emptyList())
        coEvery { parser.loadFromAssets() } returns Result.success(2)
        coEvery { parser.getEanDataVersion() } returns 0
        coEvery { parser.setEanDataVersion(any()) } returns Unit

        val vm = EanSearchViewModel(dao, parser)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("debe quedar en Ready", state is EanSearchUiState.Ready)
        val ready = state as EanSearchUiState.Ready
        assertEquals(2, ready.results.size)
        assertEquals("", ready.query)

        // La importación debió ejecutarse al iniciar (count() == 0)
        coVerify(exactly = 1) { parser.loadFromAssets() }
        coVerify(exactly = 1) { parser.setEanDataVersion(EAN_DATA_VERSION) }
    }

    @Test
    fun `search query uses searchAll`() = runTest {
        val found = listOf(
            EanProductEntity(eanPrincipal = "9999999999999", descripcionProducto = "FINDME", marca = "Z"),
        )
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        every { dao.getAll() } returns flowOf(emptyList())
        every { dao.searchAll(any()) } returns flowOf(found)
        coEvery { parser.loadFromAssets() } returns Result.success(5)
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = EanSearchViewModel(dao, parser)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onQueryChange("FINDME")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as EanSearchUiState.Ready
        assertEquals(1, state.results.size)
        assertEquals("FINDME", state.query)
    }
}
