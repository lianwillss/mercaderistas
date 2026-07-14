package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.data.export.RouteExporter
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.preferences.FileRepository
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.domain.usecase.ComputeChainToLocalesUseCase
import com.rutamercaderistas.domain.usecase.ComputeRouteBrandsUseCase
import com.rutamercaderistas.domain.usecase.CountExpiringPromotionsUseCase
import com.rutamercaderistas.domain.usecase.GroupPromotionsUseCase
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.services.PromotionRepository
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RouteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fileRepository: FileRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var ruteroManager: RuteroManager
    private lateinit var recentRoutesStore: RecentRoutesStore
    private lateinit var routeExporter: RouteExporter
    private lateinit var repository: RuteroRepository
    private lateinit var promotionRepository: PromotionRepository
    private lateinit var groupPromotions: GroupPromotionsUseCase
    private lateinit var computeChainToLocales: ComputeChainToLocalesUseCase
    private lateinit var computeRouteBrands: ComputeRouteBrandsUseCase
    private var createdViewModels = mutableListOf<RouteViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        createdViewModels.clear()

        fileRepository = mockk(relaxed = true) {
            every { excelExists() } returns true
        }
        preferencesRepository = mockk(relaxed = true) {
            coEvery { getSelectedRoute() } returns null
        }
        ruteroManager = mockk(relaxed = true)
        recentRoutesStore = mockk(relaxed = true) {
            every { recentRoutesFlow } returns MutableStateFlow(emptyList())
        }
        routeExporter = mockk(relaxed = true)
        repository = RuteroRepository()
        promotionRepository = mockk(relaxed = true) {
            coEvery { getAllPromotions() } returns emptyList()
            coEvery { refresh() } returns true
        }
        groupPromotions = GroupPromotionsUseCase(CountExpiringPromotionsUseCase())
        computeChainToLocales = ComputeChainToLocalesUseCase()
        computeRouteBrands = ComputeRouteBrandsUseCase()
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RouteViewModel {
        val vm = RouteViewModel(
            fileRepository = fileRepository,
            preferencesRepository = preferencesRepository,
            ruteroManager = ruteroManager,
            recentRoutesStore = recentRoutesStore,
            routeExporter = routeExporter,
            repository = repository,
            promotionRepository = promotionRepository,
            groupPromotions = groupPromotions,
            computeChainToLocales = computeChainToLocales,
            computeRouteBrands = computeRouteBrands,
        )
        createdViewModels.add(vm)
        return vm
    }

    @Test
    fun `selectRoute loads route and updates repository`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1"),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries

        val viewModel = createViewModel()
        viewModel.selectRoute("RUTA-1")

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { recentRoutesStore.addRoute("RUTA-1") }
        assertEquals("RUTA-1", repository.getActiveRuteroName())
        assertEquals(1, repository.getStats().totalLocales)
    }

    @Test
    fun `selectRoute saves route to prefs`() = runTest(testDispatcher) {
        coEvery { ruteroManager.loadRoute("EMU-2") } returns listOf(
            EntradaRuta("", "EMU-2", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = createViewModel()
        viewModel.selectRoute("EMU-2")

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setSelectedRoute("EMU-2") }
    }

    @Test
    fun `setCurrentDay updates currentDayLocales in uiState`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1",
                lunes = true),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries

        val viewModel = createViewModel()
        viewModel.selectRoute("RUTA-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setCurrentDay(DiaSemana.LUNES)
        assertEquals(1, viewModel.uiState.value.currentDayLocales.size)

        viewModel.setCurrentDay(DiaSemana.MARTES)
        assertEquals(0, viewModel.uiState.value.currentDayLocales.size)
    }

    @Test
    fun `loadInitialData selects first route when no saved route`() = runTest(testDispatcher) {
        coEvery { ruteroManager.loadIndex() } returns listOf("RUTA-1", "RUTA-2")
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = createViewModel()
        viewModel.loadInitialData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("RUTA-1", repository.getActiveRuteroName())
    }

    @Test
    fun `loadInitialData selects saved route when available`() = runTest(testDispatcher) {
        coEvery { preferencesRepository.getSelectedRoute() } returns "RUTA-2"
        coEvery { ruteroManager.loadIndex() } returns listOf("RUTA-1", "RUTA-2")
        coEvery { ruteroManager.loadRoute("RUTA-2") } returns listOf(
            EntradaRuta("", "RUTA-2", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = createViewModel()
        viewModel.loadInitialData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("RUTA-2", repository.getActiveRuteroName())
    }

    @Test
    fun `exportRoute calls routeExporter with correct params`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1", lunes = true),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries
        coEvery { routeExporter.exportAsImage(any(), any(), any()) } returns mockk()

        val viewModel = createViewModel()
        viewModel.selectRoute("RUTA-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportRoute()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { routeExporter.exportAsImage("RUTA-1", entries, repository.getStats()) }
    }

    @Test
    fun `exportRoute shows error when no route selected`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.exportRoute()

        assertEquals("Selecciona una ruta primero", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `getShareText returns formatted route text`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "COD1", "Local Uno", "Dir 123", "Cliente A",
                lunes = true),
            EntradaRuta("", "RUTA-1", "COD1", "Local Uno", "Dir 123", "Cliente B",
                lunes = true),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries

        val viewModel = createViewModel()
        viewModel.selectRoute("RUTA-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setCurrentDay(DiaSemana.LUNES)
        val text = viewModel.getShareText()

        assertTrue(text.contains("RUTA-1"))
        assertTrue(text.contains("COD1"))
    }

    @Test
    fun `clearSnackbar resets snackbar message`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.exportRoute()
        assertNotNull(viewModel.uiState.value.snackbarMessage)

        viewModel.clearSnackbar()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }
}
