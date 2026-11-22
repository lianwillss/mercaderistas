package com.rutamercaderistas.ui.screens.stock

import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockMode
import com.rutamercaderistas.data.stock.StockSort
import com.rutamercaderistas.data.stock.ordenarStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orden Prioridad y encabezados de la lista (Fase 3). */
class StockFilasTest {

    private fun item(
        store: String,
        name: String,
        product: String,
        sku: String,
        stock: Double,
        quiebre: Boolean = false,
    ) = StockItem(
        storeCode = store,
        storeName = name,
        brand = "CASO Y CIA",
        product = product,
        sku = sku,
        stockUnits = stock,
        transitUnits = null,
        snapshotDate = "2026-11-12",
        quiebre = quiebre,
    )

    @Test
    fun `prioridad manda negativos luego quiebres luego disponibles y A-Z dentro`() {
        val items = listOf(
            item("J503", "JUMBO", "Zumo", "1", 10.0),
            item("J503", "JUMBO", "Aceite", "2", -1.0),
            item("J503", "JUMBO", "Ketchup", "3", 0.0, quiebre = true),
            item("J503", "JUMBO", "Arroz", "4", 5.0),
            item("J503", "JUMBO", "Brocoli", "5", -2.0),
        )

        val ordenados = ordenarStock(items, StockSort.PRIORIDAD).map { it.sku }

        // Negativos A-Z (2 Aceite, 5 Brocoli), quiebre (3), disponibles A-Z (4 Arroz, 1 Zumo).
        assertEquals(listOf("2", "5", "3", "4", "1"), ordenados)
    }

    @Test
    fun `encabezados pegajosos con codigo nombre y conteos por local`() {
        val items = listOf(
            item("AL3083", "JUMBO NUNOA", "Zumo", "1", 10.0),
            item("AL3083", "JUMBO NUNOA", "Aceite", "2", -1.0),
            item("HI45", "LIDER MAIPU", "Ketchup", "3", 0.0, quiebre = true),
        )

        val cabeceras = construirFilas(items, StockMode.POR_LOCAL, alcancesMultiples = true)
            .filterIsInstance<FilaStock.Cabecera>()

        assertEquals(2, cabeceras.size)
        assertTrue(cabeceras.all { it.pegajosa })
        assertEquals("AL3083 · JUMBO NUNOA", cabeceras[0].titulo)
        assertEquals("1 negativo · 0 quiebres", cabeceras[0].detalle)
        assertEquals("HI45 · LIDER MAIPU", cabeceras[1].titulo)
        assertEquals("0 negativos · 1 quiebre", cabeceras[1].detalle)
    }

    @Test
    fun `en alcance local el encabezado sigue siendo el nombre y el total`() {
        val items = listOf(item("AL3083", "JUMBO NUNOA", "Zumo", "1", 10.0))

        val cabecera = construirFilas(items, StockMode.POR_LOCAL, alcancesMultiples = false)
            .filterIsInstance<FilaStock.Cabecera>()
            .single()

        assertEquals("JUMBO NUNOA", cabecera.titulo)
        assertEquals("1 productos", cabecera.detalle)
        assertFalse(cabecera.pegajosa)
    }

    @Test
    fun `el segmento sigue al alcance mientras el usuario no lo toca`() {
        assertTrue(!segmentoAlcance(StockFilters(local = "J503"), ultimo = null))
        assertTrue(segmentoAlcance(StockFilters(ruta = "EMU-3"), ultimo = null))
        assertTrue(!segmentoAlcance(StockFilters(), ultimo = null))
    }

    @Test
    fun `cuando el usuario toca el segmento manda su eleccion sin limpiar`() {
        // Con un local elegido se puede ir a Ruta sin limpiar.
        assertTrue(segmentoAlcance(StockFilters(local = "J503"), ultimo = true))
        // Y volver a Local aunque la ruta siga elegida.
        assertTrue(!segmentoAlcance(StockFilters(ruta = "EMU-3"), ultimo = false))
    }

    @Test
    fun `en modo por marca los encabezados son de marca y no pegajosos`() {
        val items = listOf(
            item("AL3083", "JUMBO NUNOA", "Zumo", "1", 10.0),
            item("HI45", "LIDER MAIPU", "Aceite", "2", -1.0),
        )

        val cabeceras = construirFilas(items, StockMode.POR_MARCA, alcancesMultiples = true)
            .filterIsInstance<FilaStock.Cabecera>()

        assertEquals(listOf("CASO Y CIA"), cabeceras.map { it.titulo })
        assertTrue(cabeceras.none { it.pegajosa })
    }
}
