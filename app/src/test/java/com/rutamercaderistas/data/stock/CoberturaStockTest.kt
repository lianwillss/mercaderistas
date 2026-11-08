package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La cobertura se calcula con la venta que manda KPI One en la misma respuesta
 * del stock: 28 días manda y 7 días es el respaldo.
 */
class CoberturaStockTest {

    private fun item(
        stock: Double,
        ventana7: Int? = null,
        ventana28: Int? = null,
    ) = StockItem(
        storeCode = "J503",
        storeName = "JUMBO MAIPU",
        brand = "MIEL TRAPENSE",
        product = "Miel 1 kg",
        sku = "1125185",
        stockUnits = stock,
        transitUnits = null,
        snapshotDate = "2026-10-30",
        quiebre = false,
        saleLastWeek = ventana7,
        saleLast4w = ventana28,
    )

    @Test
    fun laVentaDiariaUsaLaVentanaDe28Dias() {
        val fila = item(stock = 19.0, ventana7 = 1, ventana28 = 13)
        assertEquals(13.0 / 28.0, fila.ventaDiaria!!, 0.0001)
        assertEquals(19.0 / (13.0 / 28.0), fila.coberturaDias!!, 0.0001)
    }

    @Test
    fun sinVentanaMensualCaeALaSemanala() {
        val fila = item(stock = 14.0, ventana7 = 7, ventana28 = null)
        assertEquals(1.0, fila.ventaDiaria!!, 0.0001)
        assertEquals(14.0, fila.coberturaDias!!, 0.0001)
    }

    @Test
    fun sinVentaNoHayCobertura() {
        assertNull(item(stock = 10.0).coberturaDias)
        assertEquals(CoberturaTipo.SinDato, coberturaDe(item(stock = 10.0)))
    }

    @Test
    fun ventaCeroEsSinVentaYNoCoberturaInfinita() {
        val fila = item(stock = 10.0, ventana28 = 0)
        assertNull(fila.ventaDiaria)
        assertNull(fila.coberturaDias)
        assertEquals(CoberturaTipo.SinVenta, coberturaDe(fila))
    }

    @Test
    fun elStockNegativoEsDatoInvalidoYNoSeCalcula() {
        val fila = item(stock = -1.0, ventana28 = 57)
        assertNull(fila.coberturaDias)
        assertEquals(CoberturaTipo.SinDato, coberturaDe(fila))
    }

    @Test
    fun losTramosDeCoberturaSeCortanEnSieteCatorceYTreinta() {
        // 13 / 28 = 0,4643 unidades por día
        assertEquals(CoberturaTipo.Critico, coberturaDe(item(stock = 2.0, ventana28 = 13)))
        assertEquals(CoberturaTipo.Bajo, coberturaDe(item(stock = 5.0, ventana28 = 13)))
        assertEquals(CoberturaTipo.Sano, coberturaDe(item(stock = 10.0, ventana28 = 13)))
        assertEquals(CoberturaTipo.Sobrestock, coberturaDe(item(stock = 20.0, ventana28 = 13)))
    }
}
