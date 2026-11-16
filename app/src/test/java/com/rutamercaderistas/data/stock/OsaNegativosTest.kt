package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cruce de stock negativo con OSA: la OSA sólo decide si el negativo se
 * confirma o si hay que revisar el dato (producto exhibido, stock viejo).
 */
class OsaNegativosTest {

    @Test
    fun `un negativo con OSA si esta, se manda a verificar`() {
        val item = item(stock = -2.0, osa = osa(estaEnGondola = true))

        assertEquals(ValidacionNegativo.CONTRADICE, validacionNegativo(item))
    }

    @Test
    fun `un negativo con OSA no queda confirmado`() {
        val item = item(stock = -3.0, osa = osa(estaEnGondola = false))

        assertEquals(ValidacionNegativo.CONFIRMADO, validacionNegativo(item))
    }

    @Test
    fun `un negativo sin OSA no se puede validar`() {
        assertEquals(
            ValidacionNegativo.SIN_DATO,
            validacionNegativo(item(stock = -1.0, osa = null)),
        )
    }

    @Test
    fun `un positivo nunca se cruza aunque tenga OSA`() {
        val item = item(stock = 10.0, osa = osa(estaEnGondola = true))

        assertEquals(ValidacionNegativo.SIN_DATO, validacionNegativo(item))
    }

    @Test
    fun `cuenta por fila y filtra por validacion`() {
        val base = listOf(
            item(sku = "1", stock = -1.0, osa = osa(estaEnGondola = true)),
            item(sku = "2", stock = -2.0, osa = osa(estaEnGondola = false)),
            item(sku = "3", stock = -3.0, osa = null),
            item(sku = "4", stock = 10.0),
        )

        val todos = construirStockResult(
            base = base,
            filtros = StockFilters(soloNegativos = true),
            osaConsultada = true,
        )

        assertEquals(3, todos.items.size)
        assertEquals(1, todos.conteoConfirmados)
        assertEquals(1, todos.conteoContradicen)
        assertEquals(1, todos.conteoSinOsa)
        assertEquals(1, todos.porVerificar)

        val porVerificar = construirStockResult(
            base = base,
            filtros = StockFilters(
                soloNegativos = true,
                validacion = ValidacionNegativo.CONTRADICE,
            ),
            osaConsultada = true,
        )
        assertEquals(listOf("1"), porVerificar.items.map { it.sku })

        val sinOsa = construirStockResult(
            base = base,
            filtros = StockFilters(
                soloNegativos = true,
                validacion = ValidacionNegativo.SIN_DATO,
            ),
            osaConsultada = true,
        )
        assertEquals(listOf("3"), sinOsa.items.map { it.sku })
    }

    @Test
    fun `sin cruce terminado no inventa sin OSA`() {
        val base = listOf(item(sku = "1", stock = -1.0, osa = null))

        val antes = construirStockResult(base, StockFilters(soloNegativos = true))

        assertFalse(antes.osaConsultada)
        assertEquals(0, antes.conteoSinOsa)
    }

    @Test
    fun `por gravedad primero van los negativos que hay que verificar`() {
        val base = listOf(
            item(sku = "confirmado", stock = -9.0, osa = osa(estaEnGondola = false)),
            item(sku = "verificar", stock = -1.0, osa = osa(estaEnGondola = true)),
            item(sku = "positivo", stock = 10.0),
        )

        val resultado = construirStockResult(base, StockFilters(orden = StockSort.GRAVEDAD))

        assertEquals("verificar", resultado.items.first().sku)
    }

    @Test
    fun `limpiar local o ruta apaga el filtro de validacion`() {
        val filtros = StockFilters(
            local = "J503",
            soloNegativos = true,
            validacion = ValidacionNegativo.CONTRADICE,
        )

        // Vaciar el buscador no borra el cruce: la pantalla queda en blanco.
        assertEquals(ValidacionNegativo.CONTRADICE, filtros.conLocal("").validacion)
        assertEquals(ValidacionNegativo.CONTRADICE, filtros.conRuta("").validacion)
        // Una búsqueda nueva sí lo apaga (es otro corte de negativos).
        assertEquals(null, filtros.conLocal("J502").validacion)
        assertEquals(null, filtros.conRuta("EMU-3").validacion)
    }

    private fun osa(estaEnGondola: Boolean) = OsaRespuesta(
        estaEnGondola = estaEnGondola,
        fecha = "2026-11-14",
        visitaFecha = "2026-11-14",
        stockB2bVisita = if (estaEnGondola) 10.0 else -1.0,
    )

    private fun item(
        storeCode: String = "J503",
        sku: String = "192829",
        stock: Double,
        osa: OsaRespuesta? = null,
    ) = StockItem(
        storeCode = storeCode,
        storeName = "JUMBO MAIPU",
        brand = "CASO Y CIA",
        product = "Producto " + sku,
        sku = sku,
        stockUnits = stock,
        transitUnits = null,
        snapshotDate = "2026-11-15",
        quiebre = stock <= 0.0,
        storeId = 878,
        productId = sku.toIntOrNull(),
        osa = osa,
    )
}
