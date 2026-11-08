package com.rutamercaderistas.ui.screens.stock

import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompartirNegativosTest {

    private val negativos = listOf(
        StockItem("J521", "JUMBO LA SERENA", "CASO Y CIA", "Néctar Jumex", "192829", -3.0, 0.0, "2026-10-29", false),
        StockItem("J521", "JUMBO LA SERENA", "CASO Y CIA", "Néctar Piña", "192830", -1.0, 0.0, "2026-10-29", false),
        StockItem("UN335", "UNIMARC ÑUÑOA", "MAILEMU", "Miel de Ulmo", "553311", -7.0, 0.0, "2026-10-29", false),
    )

    @Test
    fun `con negativos activos el texto habla solo de negativos`() {
        val texto = mensajeCompartir(
            result = StockResult(items = negativos),
            filtros = StockFilters(soloNegativos = true),
            titulo = "J514",
        )

        assertTrue(texto.contains("Stock J514"))
        assertTrue(texto.contains("3 negativos en 2 locales"))
        assertTrue(texto.contains("Locales con más negativos:"))
        // Ordenado por cantidad de negativos.
        assertTrue(texto.contains("• J521 · JUMBO LA SERENA: 2 negativos"))
        assertTrue(texto.contains("• UN335 · UNIMARC ÑUÑOA: 1 negativo"))
        assertFalse(texto.contains("quiebre"))
        assertFalse(texto.contains("registros"))
        assertFalse(texto.contains("Locales con más problemas:"))
    }

    @Test
    fun `sin el filtro el resumen sigue mostrando quiebres`() {
        val conQuiebre = negativos + StockItem(
            "J521", "JUMBO LA SERENA", "CASO Y CIA", "Sin stock", "1", 0.0, null, "2026-10-29", true,
        )
        val texto = mensajeCompartir(
            result = StockResult(items = conQuiebre),
            filtros = StockFilters(),
            titulo = null,
        )

        assertTrue(texto.contains("Stock RM"))
        assertTrue(texto.contains("4 registros · 1 quiebre · 3 negativos"))
        assertTrue(texto.contains("Locales con más problemas:"))
        assertTrue(texto.contains("2 negativos"))
    }

    @Test
    fun `no repite el codigo cuando el local ya lo trae en el nombre`() {
        val item = StockItem(
            "J514", "J514-JUMBO LA DEHESA", "CUK", "Producto", "1",
            -2.0, null, "2026-10-29", false,
        )
        val texto = mensajeCompartir(
            StockResult(items = listOf(item)),
            StockFilters(soloNegativos = true),
            "J514",
        )

        assertTrue(texto.contains("• J514-JUMBO LA DEHESA: 1 negativo"))
        assertFalse(texto.contains("J514 J514-JUMBO"))
    }

    @Test
    fun `la fecha del snapshot se mantiene en ambos modos`() {
        val negativo = mensajeCompartir(
            StockResult(items = negativos),
            StockFilters(soloNegativos = true),
            "J514",
        )
        val normal = mensajeCompartir(StockResult(items = negativos), StockFilters(), "J514")

        assertTrue(negativo.contains("Datos de KPI One al 29-10-2026"))
        assertTrue(normal.contains("Datos de KPI One al 29-10-2026"))
    }
}
