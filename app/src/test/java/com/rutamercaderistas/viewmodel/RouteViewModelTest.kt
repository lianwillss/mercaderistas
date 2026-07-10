package com.rutamercaderistas.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.export.RouteExporter
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
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
import org.junit.Before
import org.junit.Test
import java.io.File

class RouteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var ruteroManager: RuteroManager
    private lateinit var recentRoutesStore: RecentRoutesStore
    private lateinit var routeExporter: RouteExporter
    private lateinit var repository: RuteroRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        prefsEditor = mockk(relaxed = true)
        prefs = mockk {
            every { edit() } returns prefsEditor
            every { getString(any(), any()) } returns null
        }
        context = mockk(relaxed = true) {
            every { getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
            every { filesDir } returns File(System.getProperty("java.io.tmpdir"))
        }
        ruteroManager = mockk(relaxed = true)
        recentRoutesStore = mockk(relaxed = true) {
            every { recentRoutesFlow } returns MutableStateFlow(emptyList())
        }
        routeExporter = mockk(relaxed = true)
        repository = RuteroRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectRoute loads route and updates repository`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1"),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.selectRoute("RUTA-1")

        testDispatcher.scheduler.advanceUntilIdle()

        verify { recentRoutesStore.addRoute("RUTA-1") }
        assertEquals("RUTA-1", repository.getActiveRuteroName())
        assertEquals(1, repository.getStats().totalLocales)
    }

    @Test
    fun `selectRoute saves route to prefs`() = runTest(testDispatcher) {
        coEvery { ruteroManager.loadRoute("EMU-2") } returns listOf(
            EntradaRuta("", "EMU-2", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.selectRoute("EMU-2")

        testDispatcher.scheduler.advanceUntilIdle()

        verify { prefsEditor.putString(Constants.KEY_RUTERO, "EMU-2") }
        verify { prefsEditor.apply() }
    }

    @Test
    fun `setCurrentDay updates currentDayLocales in uiState`() = runTest(testDispatcher) {
        val entries = listOf(
            EntradaRuta("", "RUTA-1", "1", "Local A", "", "Cliente 1",
                lunes = true),
        )
        coEvery { ruteroManager.loadRoute("RUTA-1") } returns entries

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
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

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.loadInitialData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("RUTA-1", repository.getActiveRuteroName())
    }

    @Test
    fun `loadInitialData selects saved route when available`() = runTest(testDispatcher) {
        every { prefs.getString(Constants.KEY_RUTERO, null) } returns "RUTA-2"
        coEvery { ruteroManager.loadIndex() } returns listOf("RUTA-1", "RUTA-2")
        coEvery { ruteroManager.loadRoute("RUTA-2") } returns listOf(
            EntradaRuta("", "RUTA-2", "1", "Local A", "", "Cliente 1"),
        )

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
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

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.selectRoute("RUTA-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportRoute()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { routeExporter.exportAsImage("RUTA-1", entries, repository.getStats()) }
    }

    @Test
    fun `exportRoute shows error when no route selected`() = runTest(testDispatcher) {
        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
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

        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.selectRoute("RUTA-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setCurrentDay(DiaSemana.LUNES)
        val text = viewModel.getShareText()

        assertTrue(text.contains("RUTA-1"))
        assertTrue(text.contains("COD1"))
    }

    @Test
    fun `clearSnackbar resets snackbar message`() = runTest(testDispatcher) {
        val viewModel = RouteViewModel(context, ruteroManager, recentRoutesStore, routeExporter, repository)
        viewModel.exportRoute()
        assertNotNull(viewModel.uiState.value.snackbarMessage)

        viewModel.clearSnackbar()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }
}
