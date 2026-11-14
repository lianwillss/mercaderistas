package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.stock.CachedStock
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockAlcance
import com.rutamercaderistas.data.stock.StockCache
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockScope
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockScopeStore
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.StockSyncScheduler
import com.rutamercaderistas.data.stock.StockSyncRequest
import com.rutamercaderistas.data.stock.StockUpdater
import com.rutamercaderistas.data.stock.conLocal
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import androidx.lifecycle.viewModelScope
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModelTest {

    private val repo = mockk<StockRepository>(relaxed = true)
    private val cookies = mockk<KpiSessionStore>(relaxed = true)
    private val updater = mockk<StockUpdater>(relaxed = true)
    private val prefs = mockk<PreferencesRepository>(relaxed = true)
    private val scheduler = mockk<StockSyncScheduler>(relaxed = true)
    private val client = mockk<KpiOneClient>(relaxed = true)
    private val cache = mockk<StockCache>(relaxed = true)
    private val scopeStore = mockk<StockScopeStore>(relaxed = true)
    private var vm: StockViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        every { cookies.hasSession } returns MutableStateFlow(false)
        every { scheduler.observar() } returns emptyFlow()
        coEvery { prefs.getStockLocal() } returns ""
        coEvery { prefs.getStockMarca() } returns ""
        coEvery { prefs.getStockRuta() } returns ""
        coEvery { prefs.getStockSku() } returns ""
        coEvery { prefs.setStockFilters(any(), any(), any(), any()) } returns Unit
        coEvery { repo.ensureSession() } returns true
        coEvery { repo.searchLocal(any()) } returns StockResult()
        coEvery { repo.searchLive(any(), any(), any()) } returns null
        coEvery { repo.catalog() } returns StockCatalog(
            locales = listOf(StockLocal("J503", "JUMBO MAIPU", listOf("EMU-3"))),
            marcas = listOf("CASO Y CIA"),
            rutas = listOf("EMU-3"),
        )
        every { cache.load() } returns null
        every { cache.save(any(), any()) } returns Unit
        coEvery { scopeStore.cargar() } returns StockAlcance()
        every { scopeStore.snapshotCubre(any()) } returns true
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `pinta la cache y despues la reemplaza con la consulta en vivo`() = runBlocking {
        coEvery { prefs.getStockLocal() } returns "J503"
        every { cache.load() } returns CachedStock(
            local = "J503", ruta = "", marca = "", sku = "",
            fetchedAt = "2026-11-13T08:00:00Z", total = 1, truncated = false,
            items = listOf(item("J503", 10.0)),
            estado = EstadoTipo.Disponible,
            conteoNegativos = 1,
        )
        coEvery { repo.searchLive(any(), any(), any()) } coAnswers {
            delay(700)
            StockResult(items = listOf(item("J503", 20.0)), source = StockSource.LIVE)
        }

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, scopeStore)

        val cacheState = await { it.fuente == StockSource.CACHE }
        assertEquals(10.0, cacheState.result.items.single().stockUnits, 0.0)
        assertEquals(EstadoTipo.Disponible, vm!!.filters.value.estado)
        assertEquals(1, cacheState.result.conteoNegativos)
        assertTrue(cacheState.refrescando)

        val liveState = await { it.fuente == StockSource.LIVE }
        assertEquals(20.0, liveState.result.items.single().stockUnits, 0.0)
        assertTrue(!liveState.refrescando)
        assertEquals(1f, liveState.progresoCarga ?: 0f, 0.001f)
        assertTrue(liveState.progresoCompleto)
    }

    @Test
    fun `elegir un local dispara la busqueda sin tocar recargar`() = runBlocking {
        coEvery { repo.searchLocal(any()) } returns StockResult(
            items = listOf(item("J503", 8.0)),
            source = StockSource.SNAPSHOT,
        )
        coEvery { repo.searchLive(any(), any(), any()) } returns StockResult(
            items = listOf(item("J503", 8.0)),
            source = StockSource.LIVE,
        )

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, scopeStore)
        await { it.status == com.rutamercaderistas.data.stock.StockStatus.Idle }

        vm!!.onFilter { it.conLocal("J503") }

        val estado = await { it.fuente == StockSource.LIVE }
        assertEquals(StockSource.LIVE, estado.fuente)
        coVerify(exactly = 1) { repo.searchLive(any(), any(), any()) }
    }

    @Test
    fun `la misma seleccion no repite la consulta`() = runBlocking {
        coEvery { repo.searchLive(any(), any(), any()) } returns StockResult(
            items = listOf(item("J503", 8.0)),
            source = StockSource.LIVE,
        )

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, scopeStore)
        vm!!.onFilter { it.conLocal("J503") }
        await { it.result.items.isNotEmpty() }

        vm!!.onFilter { it.conLocal("J503") }
        delay(900)

        coVerify(exactly = 1) { repo.searchLive(any(), any(), any()) }
    }

    @Test
    fun `cambiar a RM completa quita el limite y pide el snapshot que falta`() = runBlocking {
        val scoped = StockAlcance(StockScope.MIS_RUTAS, setOf("EMU-3"))
        coEvery { scopeStore.cargar() } returns scoped
        coEvery { scopeStore.cambiar(StockScope.RM_COMPLETA) } returns StockAlcance(StockScope.RM_COMPLETA)
        // Al abrir, el snapshot ya cubre "Mis rutas"; al cambiar a RM, no.
        every { scopeStore.snapshotCubre(any()) } returnsMany listOf(true, false)
        every { client.puedeEntrar() } returns true
        every { scheduler.start(any()) } returns java.util.UUID.randomUUID()

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, scopeStore)
        await { it.alcance.limita }
        verify { repo.fijarAlcance(setOf("EMU-3")) }

        vm!!.cambiarAlcance(StockScope.RM_COMPLETA)
        await { it.alcance.scope == StockScope.RM_COMPLETA }

        verify { repo.fijarAlcance(emptySet()) }
        verify {
            scheduler.start(
                match {
                    it.rutas.isEmpty() &&
                        !it.force
                },
            )
        }
    }

    private suspend fun await(predicado: (StockUiState) -> Boolean): StockUiState =
        withTimeout(5_000) { vm!!.state.first(predicado) }

    private fun item(store: String, stock: Double) = StockItem(
        storeCode = store,
        storeName = "JUMBO MAIPU",
        brand = "CASO Y CIA",
        product = "Nectar",
        sku = "192829",
        stockUnits = stock,
        transitUnits = null,
        snapshotDate = "2026-11-12",
        quiebre = stock <= 0.0,
    )
}