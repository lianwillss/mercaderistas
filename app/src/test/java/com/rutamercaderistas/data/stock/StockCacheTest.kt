package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCacheTest {

    private val storage = MemoryStockStorage()
    private val cache = StockCache(storage)

    @Test
    fun `guarda y recupera la ultima busqueda`() {
        val filtros = StockFilters(
            ruta = "EMU-3",
            marca = "CASO Y CIA",
            estado = EstadoTipo.Disponible,
            orden = StockSort.STOCK_ASC,
            modo = StockMode.POR_MARCA,
        )
        val resultado = StockResult(
            items = listOf(item("J503", -1.0), item("J502", 12.0)),
            source = StockSource.LIVE,
            fetchedAt = "2026-11-13T08:00:00Z",
            total = 2,
            conteoNegativos = 1,
            conteoDisponible = 1,
        )

        cache.save(filtros, resultado)
        val recuperado = cache.load()

        assertTrue(recuperado != null)
        assertEquals("EMU-3", recuperado!!.ruta)
        assertEquals("CASO Y CIA", recuperado.marca)
        assertEquals(2, recuperado.items.size)
        assertEquals(-1.0, recuperado.items.first().stockUnits, 0.0)
        assertEquals("2026-11-13T08:00:00Z", recuperado.fetchedAt)
        assertEquals(EstadoTipo.Disponible, recuperado.estado)
        assertEquals(StockSort.STOCK_ASC, recuperado.orden)
        assertEquals(StockMode.POR_MARCA, recuperado.modo)
        assertEquals(1, recuperado.conteoNegativos)
        assertTrue(recuperado.coincide(filtros))
    }

    @Test
    fun `no guarda resultados vacios ni pisa una cache buena`() {
        cache.save(StockFilters(local = "J503"), StockResult(items = listOf(item("J503", 5.0))))
        cache.save(StockFilters(local = "J503"), StockResult())

        val recuperado = cache.load()
        assertEquals(1, recuperado?.items?.size)
    }

    @Test
    fun `clear borra la cache`() {
        cache.save(StockFilters(local = "J503"), StockResult(items = listOf(item("J503", 5.0))))
        cache.clear()

        assertNull(cache.load())
    }

    @Test
    fun `guarda la OSA de los negativos y si el cruce termino`() {
        val filtros = StockFilters(
            local = "J503",
            soloNegativos = true,
            validacion = ValidacionNegativo.CONTRADICE,
        )
        val negativo = item("J503", -2.0).copy(
            storeId = 878,
            productId = 7165,
            osa = OsaRespuesta(
                estaEnGondola = true,
                fecha = "2026-11-14",
                visitaFecha = "2026-11-14",
                stockB2bVisita = 10.0,
            ),
        )
        val resultado = StockResult(
            items = listOf(negativo),
            source = StockSource.LIVE,
            fetchedAt = "2026-11-14T13:43:24Z",
            total = 1,
            conteoNegativos = 1,
            conteoContradicen = 1,
            osaConsultada = true,
        )

        cache.save(filtros, resultado)
        val recuperado = cache.load()

        assertTrue(recuperado != null)
        assertEquals(ValidacionNegativo.CONTRADICE, recuperado!!.validacion)
        assertTrue(recuperado.osaConsultada)
        assertEquals(1, recuperado.conteoContradicen)
        val fila = recuperado.items.single()
        assertEquals(878, fila.storeId)
        assertEquals(7165, fila.productId)
        assertTrue(fila.osa?.estaEnGondola == true)
        assertEquals("2026-11-14", fila.osa?.fecha)
        assertEquals(10.0, fila.osa?.stockB2bVisita ?: 0.0, 0.0)
        assertEquals(ValidacionNegativo.CONTRADICE, validacionNegativo(fila))
    }

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

    private class MemoryStockStorage : StockStorage {
        private val files = mutableMapOf<String, String>()
        override fun read(name: String): String? = files[name]
        override fun writeAtomic(name: String, text: String): Boolean {
            files[name] = text
            return true
        }
        override fun backup(name: String) = Unit
        override fun exists(name: String): Boolean = files.containsKey(name)
        override fun delete(name: String) { files.remove(name) }
    }
}