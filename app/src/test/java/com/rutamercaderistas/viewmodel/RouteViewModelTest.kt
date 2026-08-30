package com.rutamercaderistas.viewmodel

import android.content.Context
import com.rutamercaderistas.R
import com.rutamercaderistas.data.export.RouteExporter
import com.rutamercaderistas.data.local.EanProductDao
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
    private lateinit var context: Context
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

        context = mockk<Context>(relaxed = true) {
            every { getString(any()) } returns "context_string"
            every { getString(R.string.ruta_selecciona_primero) } returns "Selecciona una ruta primero"
        }
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
        repository = RuteroRepository(mockk<EanProductDao>(relaxed = true) {
            coEvery { countValidEan() } returns 0
        })
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

    private fun awaitOnMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condición no cumplida dentro del timeout")
    }

    private suspend fun awaitCoVerify(timeoutMs: Long = 5000, verification: suspend () -> Unit) {
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

    private fun createViewModel(): RouteViewModel {
        val vm = RouteViewModel(
            context = context,
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
            defaultDispatcher = testDispatcher,
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
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.selectedRoute == "RUTA-1" }

        awaitCoVerify { coVerify { recentRoutesStore.addRoute("RUTA-1") } }
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.allLocales?.size == 1 }
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
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.selectedRoute == "EMU-2" }

        awaitCoVerify { coVerify { preferencesRepository.setSelectedRoute("EMU-2") } }
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
        awaitOnMain { viewModel.uiState.value.entries.isNotEmpty() }

        viewModel.setCurrentDay(DiaSemana.LUNES)
        awaitOnMain { viewModel.uiState.value.currentDayLocales.size == 1 }

        viewModel.setCurrentDay(DiaSemana.MARTES)
        awaitOnMain { viewModel.uiState.value.currentDayLocales.isEmpty() }
    }

    @Test
    fun `loadInitialData selects first route when no saved route`() = runTest(testDispatcher) {
        coEvery { ruteroManager.loadIndex() } returns listOf("RUTA-1", "RUTA-2")
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = createViewModel()
        viewModel.loadInitialData()
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.selectedRoute == "RUTA-1" }

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
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.selectedRoute == "RUTA-2" }

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
        awaitOnMain { (viewModel.uiState.value.route as? RouteDataState.Loaded)?.selectedRoute == "RUTA-1" }

        viewModel.exportRoute()
        awaitCoVerify { coVerify { routeExporter.exportAsImage("RUTA-1", entries, repository.getStats()) } }
    }

    @Test
    fun `exportRoute shows error when no route selected`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        awaitOnMain { viewModel.uiState.value.route is RouteDataState.Loaded }
        viewModel.exportRoute()
        awaitOnMain { viewModel.uiState.value.snackbarMessage != null }

        assertEquals("Selecciona una ruta primero", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `clearSnackbar resets snackbar message`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        awaitOnMain { viewModel.uiState.value.route is RouteDataState.Loaded }
        viewModel.exportRoute()
        awaitOnMain { viewModel.uiState.value.snackbarMessage != null }
        assertNotNull(viewModel.uiState.value.snackbarMessage)

        viewModel.clearSnackbar()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }
}
