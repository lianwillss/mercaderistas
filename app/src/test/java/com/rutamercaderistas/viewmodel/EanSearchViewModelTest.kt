package com.rutamercaderistas.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EanExcelParser
import com.rutamercaderistas.services.EAN_DATA_VERSION
import android.util.Log
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EanSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: EanProductDao
    private lateinit var parser: EanExcelParser
    private lateinit var prefs: PreferencesRepository
    private val createdViewModels = mutableListOf<EanSearchViewModel>()

    private class FakePagingSource(
        private val items: List<EanProductEntity>,
    ) : PagingSource<Int, EanProductEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EanProductEntity> =
            LoadResult.Page(data = items, prevKey = null, nextKey = null)

        override fun getRefreshKey(state: PagingState<Int, EanProductEntity>): Int? = null
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Paging 3 loguea vía android.util.Log (stub en JVM puro): mockearlo.
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns false
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        dao = mockk(relaxed = true)
        parser = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.getSearchHistoryFlow() } returns flowOf(emptyList())
        // Facetas y conteo: los chips y el total salen de SQL.
        coEvery { dao.brandFacets(any()) } returns emptyList()
        coEvery { dao.cajaFacets(any()) } returns emptyList()
        coEvery { dao.countForQuery(any()) } returns 0
        every { dao.browseSearch(any()) } answers { FakePagingSource(emptyList()) }
    }

    @After
    fun tearDown() {
        // Cancelar SIEMPRE (aunque el test falle antes de su cancel local):
        // un scope vivo con Paging deja hilos reales que envenenan la clase
        // siguiente con UncaughtExceptionsBeforeTest. Recién después, unmock.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun track(vm: EanSearchViewModel): EanSearchViewModel {
        createdViewModels.add(vm)
        return vm
    }

    private fun ready(vm: EanSearchViewModel): EanSearchUiState.Ready {
        assertTrue("debe quedar en Ready", vm.uiState.value is EanSearchUiState.Ready)
        return vm.uiState.value as EanSearchUiState.Ready
    }

    @Test
    fun `blank query browses catalog and import runs`() = runTest {
        val sample = listOf(
            EanProductEntity(eanPrincipal = "1234567890123", descripcionProducto = "A", marca = "X"),
            EanProductEntity(eanPrincipal = "2345678901234", descripcionProducto = "B", marca = "Y"),
        )
        coEvery { dao.count() } returns 0
        coEvery { dao.hasUnnormalized() } returns 0
        every { dao.browseSearch(any()) } answers { FakePagingSource(sample) }
        coEvery { dao.countForQuery(any()) } returns 2
        coEvery { parser.loadFromAssets() } returns Result.success(2)
        coEvery { parser.getEanDataVersion() } returns 0
        coEvery { parser.setEanDataVersion(any()) } returns Unit

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = ready(vm)
        assertEquals("", state.query)
        assertEquals(2, state.pagingFlow.asSnapshot().size)
        assertEquals(2, state.totalCount)

        // La importación debió ejecutarse al iniciar (count() == 0), y sin
        // texto de búsqueda la lista sale de browseSearch.
        coVerify(exactly = 1) { parser.loadFromAssets() }
        coVerify(exactly = 1) { parser.setEanDataVersion(EAN_DATA_VERSION) }
        verify(atLeast = 1) { dao.browseSearch(any()) }
    }

    @Test
    fun `search query uses rankedSearch`() = runTest {
        val found = listOf(
            EanProductEntity(eanPrincipal = "9999999999999", descripcionProducto = "FINDME", marca = "Z"),
        )
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        every { dao.rankedSearch(any()) } answers { FakePagingSource(found) }
        coEvery { dao.countForQuery(any()) } returns 1
        coEvery { parser.loadFromAssets() } returns Result.success(5)
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onQueryChange("FINDME")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = ready(vm)
        assertEquals("FINDME", state.query)
        assertEquals(1, state.pagingFlow.asSnapshot().size)
        assertEquals(1, state.totalCount)
        verify(atLeast = 1) { dao.rankedSearch(any()) }
    }

    @Test
    fun `stale data version forces reimport on existing installs`() = runTest {
        // Caso B.TAN → BWILD: el equipo ya importó (hay datos, hash igual),
        // pero con versión vieja; debe reimportar para aplicar alias/marcas.
        coEvery { dao.count() } returns 33
        coEvery { dao.hasUnnormalized() } returns 0
        every { parser.computeAssetsHash() } returns "abc123"
        every { parser.getEanAssetsHash() } returns "abc123"
        every { parser.getEanDataVersion() } returns EAN_DATA_VERSION - 1
        coEvery { parser.loadFromAssets() } returns Result.success(33)

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { parser.loadFromAssets() }
        coVerify(exactly = 1) { parser.setEanDataVersion(EAN_DATA_VERSION) }
        assertTrue(vm.uiState.value is EanSearchUiState.Ready)
    }

    @Test
    fun `token search matches across word order`() = runTest {
        val product = EanProductEntity(
            eanPrincipal = "111",
            descripcionProducto = "NAT ROMERO PISTACHO",
            marca = "NAT NATURAL",
            descripcionNorm = "nat romero pistacho",
            descripcionNormNospace = "natromeropistacho",
            marcaNorm = "nat natural",
            marcaNormNospace = "natnatural",
        )
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        // El ranking vive en SQL (buildRankedSearch); el VM solo cablea el factory.
        every { dao.rankedSearch(any()) } answers { FakePagingSource(listOf(product)) }
        coEvery { dao.countForQuery(any()) } returns 1
        coEvery { parser.loadFromAssets() } returns Result.success(5)
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onQueryChange("pistacho nat")
        testDispatcher.scheduler.advanceUntilIdle()

        val snapshot = ready(vm).pagingFlow.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("NAT ROMERO PISTACHO", snapshot.first().descripcionProducto)
    }

    @Test
    fun `brand chip filters in SQL and keeps the selected value`() = runTest {
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        coEvery { dao.countForQuery(any()) } returns 1
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onBrandFilter("KEYFOOD")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = ready(vm)
        assertEquals("KEYFOOD", state.brandFilter)
        // El filtro se aplica en el WHERE del conteo (y del pager), no en la UI.
        coVerify(atLeast = 1) { dao.countForQuery(match { it.sql.contains("p.marca = ?") }) }

        // Tocar el mismo chip lo quita.
        vm.onBrandFilter("KEYFOOD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(ready(vm).brandFilter)
    }

    @Test
    fun `scanning clears filters and normalizes upc a`() = runTest {
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onBrandFilter("KEYFOOD")
        vm.onCajaFilter("12")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onBarcodeScanned("658325654776")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = ready(vm)
        assertEquals("0658325654776", state.query)
        assertNull(state.brandFilter)
        assertNull(state.cajaFilter)
    }

    @Test
    fun `clearFilters resets brand and caja in one refresh`() = runTest {
        coEvery { dao.count() } returns 5
        coEvery { dao.hasUnnormalized() } returns 0
        coEvery { parser.getEanDataVersion() } returns EAN_DATA_VERSION

        val vm = track(EanSearchViewModel(dao, parser, prefs))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onBrandFilter("KEYFOOD")
        vm.onCajaFilter("12")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("KEYFOOD", ready(vm).brandFilter)
        assertEquals("12", ready(vm).cajaFilter)

        vm.clearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(ready(vm).brandFilter)
        assertNull(ready(vm).cajaFilter)
        coVerify(atLeast = 1) {
            dao.countForQuery(match { !it.sql.contains("p.marca = ?") && !it.sql.contains("p.conversion = ?") })
        }
    }
}
