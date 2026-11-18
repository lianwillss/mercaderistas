package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanSkuLookup
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.stock.CachedStock
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockCache
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.OsaRespuesta
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.StockSyncScheduler
import com.rutamercaderistas.data.stock.StockSyncRequest
import com.rutamercaderistas.data.stock.StockUpdater
import com.rutamercaderistas.data.stock.conLocal
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import com.rutamercaderistas.services.EanCatalogSyncer
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
    private val eanDao = mockk<EanProductDao>(relaxed = true)
    private val eanSyncer = mockk<EanCatalogSyncer>(relaxed = true)
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
        coEvery { eanSyncer.ensure() } returns Result.success(1)
        every { cache.load() } returns null
        every { cache.save(any(), any(), any()) } returns Unit
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

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)

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

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)
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

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)
        vm!!.onFilter { it.conLocal("J503") }
        await { it.result.items.isNotEmpty() }

        vm!!.onFilter { it.conLocal("J503") }
        delay(900)

        coVerify(exactly = 1) { repo.searchLive(any(), any(), any()) }
    }

    @Test
    fun `cruza los negativos con OSA despues de la busqueda en vivo`() = runBlocking {
        val negativo = item("J503", -2.0)
        coEvery { repo.searchLive(any(), any(), any()) } returns StockResult(
            items = listOf(negativo),
            source = StockSource.LIVE,
            conteoNegativos = 1,
        )
        val cruzado = StockResult(
            items = listOf(
                negativo.copy(
                    osa = OsaRespuesta(
                        estaEnGondola = true,
                        fecha = "2026-11-14",
                        visitaFecha = "2026-11-14",
                    ),
                ),
            ),
            source = StockSource.LIVE,
            conteoNegativos = 1,
            conteoContradicen = 1,
            osaConsultada = true,
        )
        coEvery { repo.completarOsa(any(), any()) } returns cruzado

        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)
        vm!!.onFilter { it.conLocal("J503") }

        val estado = await { it.result.osaConsultada }
        assertEquals(1, estado.result.conteoContradicen)
        assertTrue(estado.result.items.single().osa?.estaEnGondola == true)
        assertTrue(!estado.osaEnCurso)
    }


    @Test
    fun eanParaSkuEnlazaElCodigoDelCatalogoEan() = runBlocking {
        every { eanDao.eanPorSkuFlow("1986305") } returns flowOf("859547004701")
        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)

        val emisiones = vm!!.eanParaSku("1986305").toList()

        assertEquals(EanSkuLookup.Cargando, emisiones.first())
        assertEquals(
            "859547004701",
            (emisiones.last() as EanSkuLookup.Resultado).ean,
        )
        // El detalle asegura el catalogo antes de consultar.
        coVerify(exactly = 1) { eanSyncer.ensure() }
    }

    @Test
    fun eanParaSkuSinCodigoQuedaEnBlanco() = runBlocking {
        every { eanDao.eanPorSkuFlow(any()) } returns flowOf(null)
        vm = StockViewModel(repo, cookies, updater, prefs, scheduler, client, cache, eanDao, eanSyncer)

        val emisiones = vm!!.eanParaSku("999999").toList()

        assertEquals(EanSkuLookup.Cargando, emisiones.first())
        assertEquals(null, (emisiones.last() as EanSkuLookup.Resultado).ean)
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