package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Historial de selecciones de Stock: upsert (usos + ultimoUso), orden por
 * recencia, topes por tipo y total, y el mapa de usos para el desempate.
 * Lógica pura, sin Android ni DataStore.
 */
class StockBusquedasTest {

    @Test
    fun `registrar dos veces suma usos y no duplica`() {
        var stock = StockBusquedas()
        stock = registrarSeleccion(stock, TipoBusquedaStock.LOCAL, "J503", ahora = 100)
        stock = registrarSeleccion(stock, TipoBusquedaStock.LOCAL, "J503", ahora = 200)

        val lista = stock.locales
        assertEquals(1, lista.size)
        assertEquals(2, lista.single().usos)
        assertEquals(200L, lista.single().ultimoUso)
    }

    @Test
    fun `ordena por recencia de lo mas reciente a lo mas viejo`() {
        var stock = StockBusquedas()
        stock = registrarSeleccion(stock, TipoBusquedaStock.RUTA, "EMU-3", ahora = 100)
        stock = registrarSeleccion(stock, TipoBusquedaStock.RUTA, "FMU-1", ahora = 200)
        stock = registrarSeleccion(stock, TipoBusquedaStock.RUTA, "DMU-5", ahora = 300)

        assertEquals(listOf("DMU-5", "FMU-1", "EMU-3"), stock.rutas.map { it.valor })
    }

    @Test
    fun `el tope por tipo conserva solo las ultimas`() {
        var stock = StockBusquedas()
        for (i in 1..10) {
            stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "MARCA " + i, ahora = i.toLong())
        }
        assertEquals(8, stock.marcas.size)
        assertEquals("MARCA 10", stock.marcas.first().valor)
        assertTrue(stock.marcas.none { it.valor == "MARCA 1" })
        assertTrue(stock.marcas.none { it.valor == "MARCA 2" })
    }

    @Test
    fun `el valor en blanco no registra nada`() {
        val stock = registrarSeleccion(StockBusquedas(), TipoBusquedaStock.LOCAL, "   ", ahora = 1)
        assertEquals(StockBusquedas(), stock)
    }

    @Test
    fun `cada tipo lleva su propia lista sin mezclarse`() {
        var stock = StockBusquedas()
        stock = registrarSeleccion(stock, TipoBusquedaStock.LOCAL, "J503", ahora = 1)
        stock = registrarSeleccion(stock, TipoBusquedaStock.RUTA, "EMU-3", ahora = 2)
        stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "CASO Y CIA", ahora = 3)

        assertEquals(listOf("J503"), stock.locales.map { it.valor })
        assertEquals(listOf("EMU-3"), stock.rutas.map { it.valor })
        assertEquals(listOf("CASO Y CIA"), stock.marcas.map { it.valor })
    }

    @Test
    fun `usosPorTipo mapea valor a usos para el desempate`() {
        var stock = StockBusquedas()
        stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "CASO Y CIA", ahora = 1)
        stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "CASO Y CIA", ahora = 2)
        stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "SUK", ahora = 3)

        val usos = usosPorTipo(stock, TipoBusquedaStock.MARCA)
        assertEquals(2, usos["CASO Y CIA"])
        assertEquals(1, usos["SUK"])
    }

    @Test
    fun `el tope total evacua las selecciones mas viejas entre tipos`() {
        var stock = StockBusquedas()
        for (i in 1..3) {
            stock = registrarSeleccion(stock, TipoBusquedaStock.LOCAL, "L" + i, ahora = i.toLong(), topePorTipo = 2, topeTotal = 4)
            stock = registrarSeleccion(stock, TipoBusquedaStock.RUTA, "R" + i, ahora = (10 + i).toLong(), topePorTipo = 2, topeTotal = 4)
            stock = registrarSeleccion(stock, TipoBusquedaStock.MARCA, "M" + i, ahora = (20 + i).toLong(), topePorTipo = 2, topeTotal = 4)
        }
        assertTrue(stock.total <= 4)
    }

    @Test
    fun `recientes devuelve a lo sumo el limite pedido`() {
        var stock = StockBusquedas()
        for (i in 1..8) {
            stock = registrarSeleccion(stock, TipoBusquedaStock.LOCAL, "J" + i, ahora = i.toLong())
        }
        val recientes = recientesBusqueda(stock, TipoBusquedaStock.LOCAL, limite = 5)
        assertEquals(5, recientes.size)
        assertEquals("J8", recientes.first().valor)
    }
}
