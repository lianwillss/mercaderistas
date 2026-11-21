package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los filtros de local/ruta son caminos alternativos entre sí, pero no deben
 * pisar las tarjetas de estado que el usuario ya eligió.
 */
class StockFiltersTest {

    @Test
    fun `buscar local pone Disponible como default solo si no hay estado elegido`() {
        assertEquals(EstadoTipo.Disponible, StockFilters().conLocal("J503").estado)
        assertEquals(EstadoTipo.Disponible, StockFilters(estado = null).conRuta("EMU-3").estado)
    }

    @Test
    fun `buscar local conserva quiebres negativos y cobertura`() {
        assertEquals(
            EstadoTipo.Quiebre,
            StockFilters(estado = EstadoTipo.Quiebre).conLocal("J503").estado,
        )
        assertTrue(StockFilters(soloNegativos = true).conLocal("J503").soloNegativos)
        assertEquals(
            CoberturaTipo.Critico,
            StockFilters(cobertura = CoberturaTipo.Critico).conLocal("J503").cobertura,
        )
        assertEquals(
            ValidacionNegativo.CONTRADICE,
            StockFilters(validacion = ValidacionNegativo.CONTRADICE).conLocal("J503").validacion,
        )
    }

    @Test
    fun `buscar ruta conserva el estado elegido y limpia solo el local`() {
        val filtros = StockFilters(
            local = "J503",
            marca = "CASO Y CIA",
            sku = "1928",
            estado = EstadoTipo.Negativo,
        ).conRuta("EMU-3")

        assertEquals("", filtros.local)
        assertEquals("EMU-3", filtros.ruta)
        assertEquals("CASO Y CIA", filtros.marca)
        assertEquals("1928", filtros.sku)
        assertEquals(EstadoTipo.Negativo, filtros.estado)
    }

    @Test
    fun `buscar local limpia la ruta pero conserva marca y producto`() {
        val filtros = StockFilters(
            ruta = "EMU-3",
            marca = "CUK",
            sku = "111",
        ).conLocal("J503")

        assertEquals("J503", filtros.local)
        assertEquals("", filtros.ruta)
        assertEquals("CUK", filtros.marca)
        assertEquals("111", filtros.sku)
    }

    @Test
    fun `consulta de stock compara normal y compacto`() {
        val consulta = ConsultaStock("vegmonkey")
        assertTrue(consulta.coincide("VEG MONKEY"))
        assertTrue(ConsultaStock("VEG MONKEY").coincide("Veg Monkey"))
        assertTrue(ConsultaStock("ñuñoa").coincide("NUNOA"))
        assertTrue(ConsultaStock("jumbo-maipu").coincide("JUMBO MAIPU"))
        assertTrue(!ConsultaStock("veggie").coincide("VEG MONKEY"))
    }
}
