package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.stock.remote.KpiHttpException
import com.rutamercaderistas.data.stock.remote.RemoteStockRepository
import com.rutamercaderistas.data.stock.remote.SessionExpiredException
import com.rutamercaderistas.data.stock.remote.StockQueryException
import com.rutamercaderistas.data.stock.remote.StockQueryTooLargeException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StockRepositoryRouterTest {

    private val local = mockk<DemoStockRepository>(relaxed = true)
    private val remote = mockk<RemoteStockRepository>(relaxed = true)
    private val cache = mockk<StockCache>(relaxed = true)
    private var online = true
    private val network = object : NetworkMonitor {
        override fun online(): Boolean = online
    }
    private val router = StockRepositoryRouter(local, remote, network, cache)

    @Test
    fun `con red usa la consulta en vivo`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } returns StockResult(
            items = listOf(item("J503")),
            source = StockSource.LIVE,
            fetchedAt = "2026-11-13T08:00:00Z",
        )

        val resultado = router.search(StockFilters(local = "J503"))

        assertEquals(StockSource.LIVE, resultado.source)
        coVerify(exactly = 1) { remote.search(any(), any(), any()) }
    }

    @Test
    fun `sin red usa el snapshot y avisa`() = runBlocking {
        online = false
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(local = "J503"))

        assertEquals(StockSource.SNAPSHOT, resultado.source)
        assertTrue(resultado.aviso != null)
        coVerify(exactly = 0) { remote.search(any(), any(), any()) }
    }

    @Test
    fun `si KPI One falla cae al snapshot`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws IOException("sin red")
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(local = "J503"))

        assertEquals(StockSource.SNAPSHOT, resultado.source)
        assertTrue(resultado.aviso != null)
    }

    @Test
    fun `una marca sola no dispara la consulta en vivo`() = runBlocking {
        online = true
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(marca = "CASO"))

        assertEquals(StockSource.SNAPSHOT, resultado.source)
        coVerify(exactly = 0) { remote.search(any(), any(), any()) }
    }

    @Test
    fun `un SKU de tres caracteres si consulta en vivo`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } returns StockResult(
            items = listOf(item("J503")),
            source = StockSource.LIVE,
        )

        val resultado = router.search(StockFilters(sku = "192"))

        assertEquals(StockSource.LIVE, resultado.source)
    }

    @Test
    fun `una consulta de mas de 60 locales avisa que usa datos guardados`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws StockQueryTooLargeException(131)
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(ruta = "PITUTO"))

        assertEquals(StockSource.SNAPSHOT, resultado.source)
        assertTrue(resultado.aviso!!.contains("131"))
        assertTrue(resultado.aviso!!.contains("guardados", ignoreCase = true))
    }

    @Test
    fun `un 503 de KPI One avisa que el reporte esta ocupado`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws
            KpiHttpException(503, "El reporte está temporalmente ocupado")
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(local = "J503"))

        assertTrue(resultado.aviso!!.contains("ocupado", ignoreCase = true))
        assertEquals(StockSource.SNAPSHOT, resultado.source)
    }

    @Test
    fun `una sesion vencida avisa que hay que reconectar en Ajustes`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws SessionExpiredException()
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(local = "J503"))

        assertTrue(resultado.aviso!!.contains("Ajustes", ignoreCase = true))
    }

    @Test
    fun `un error de red avisa que no se pudo conectar`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws IOException("timeout")
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        val resultado = router.search(StockFilters(local = "J503"))

        assertTrue(resultado.aviso!!.contains("conectar", ignoreCase = true))
    }

    @Test
    fun `un local sin coincidencias cae al snapshot sin banner de error`() = runBlocking {
        online = true
        coEvery { remote.search(any(), any(), any()) } throws
            StockQueryException("Sin locales para la búsqueda")
        coEvery { local.search(any()) } returns StockResult(items = emptyList())

        val resultado = router.search(StockFilters(local = "JUMBOMAIPU"))

        assertEquals(StockSource.SNAPSHOT, resultado.source)
        // No es un problema de red: no debe mostrar "No se pudo consultar KPI One".
        assertTrue(resultado.aviso == null)
    }

    @Test
    fun `un local de un caracter no dispara la consulta en vivo`() = runBlocking {
        online = true
        coEvery { local.search(any()) } returns StockResult(items = listOf(item("J503")))

        router.search(StockFilters(local = "J"))

        coVerify(exactly = 0) { remote.search(any(), any(), any()) }
    }

    @Test
    fun `ensureSession sigue vivo con snapshot aunque KPI One no responda`() = runBlocking {
        online = true
        every { local.tieneDatos } returns true
        coEvery { remote.ensureSession() } returns false

        assertTrue(router.ensureSession())
    }

    private fun item(store: String) = StockItem(
        storeCode = store,
        storeName = "JUMBO MAIPU",
        brand = "CASO Y CIA",
        product = "Nectar",
        sku = "192829",
        stockUnits = 10.0,
        transitUnits = null,
        snapshotDate = "2026-11-12",
        quiebre = false,
    )
}