package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.stock.remote.RemoteStockRepository
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