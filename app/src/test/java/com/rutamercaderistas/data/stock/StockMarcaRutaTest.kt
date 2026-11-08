package com.rutamercaderistas.data.stock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión: con una ruta elegida, escribir una marca a mano borraba el campo
 * en cada tecla porque la validación comparaba el texto exacto contra la lista
 * de marcas de la ruta ("caso" no es "CASO Y CIA"). Ahora sólo se limpia una
 * marca completa del catálogo que dejó de existir en la ruta.
 */
class StockMarcaRutaTest {

    private val catalogo = StockCatalog(
        locales = listOf(
            StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
            StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
        ),
        marcas = listOf("CASO Y CIA", "CUK", "SUK"),
        marcasPorRuta = mapOf(
            "EMU3" to listOf("CASO Y CIA"),
            "DMU5" to listOf("SUK", "CUK"),
        ),
    )

    @Test
    fun `marcas de la ruta salen del desglose por ruta`() {
        assertEquals(listOf("CASO Y CIA"), catalogo.marcasPara("EMU-3"))
        assertEquals(listOf("EMU3"), listOf(normalizarRuta("EMU-3")))
        assertEquals(listOf("SUK", "CUK"), catalogo.marcasPara("dmu 5"))
    }

    @Test
    fun `sin ruta o sin desglose se ofrece el catalogo completo`() {
        assertEquals(catalogo.marcas, catalogo.marcasPara(""))
        // Ruta sin marcas registradas (catálogo remoto): las 3 del catálogo.
        assertEquals(catalogo.marcas, catalogo.marcasPara("FMU-9"))
    }

    @Test
    fun `escribir una marca con ruta elegida no la borra`() {
        // Texto a medio escribir: es la búsqueda en curso, nunca se limpia.
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "caso"))
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "CASO"))
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "cas"))
        // Marca completa que sí existe en la ruta.
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "CASO Y CIA"))
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "caso y cia"))
    }

    @Test
    fun `marca completa que no existe en la ruta se limpia`() {
        assertTrue(catalogo.marcaFueraDeRuta("EMU-3", "CUK"))
        assertTrue(catalogo.marcaFueraDeRuta("EMU-3", "suk"))
    }

    @Test
    fun `sin ruta o sin marca no se limpia nada`() {
        assertFalse(catalogo.marcaFueraDeRuta("", "CUK"))
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", ""))
        assertFalse(catalogo.marcaFueraDeRuta("EMU-3", "   "))
    }

    @Test
    fun `ruta y marca combinan en la busqueda`() = runBlocking {
        val repo = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar", "1", 1.0, null, "2026-10-28", false),
                    StockItem("AL3083", "JUMBO NUNOA", "CUK", "Suspirito", "2", 2.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "CUK", "Falafel", "3", 3.0, null, "2026-10-28", false),
                ),
                locales = catalogo.locales,
            ),
        )
        val resultado = repo.search(StockFilters(ruta = "emu3", marca = "caso"))
        assertEquals(1, resultado.items.size)
        assertEquals("AL3083", resultado.items.first().storeCode)
        assertTrue(resultado.items.all { it.brand == "CASO Y CIA" })
    }

    @Test
    fun `ruta con marca y producto da lo mismo en cualquier orden`() = runBlocking {
        val repo = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar Jumex", "192829", 50.0, null, "2026-10-28", false),
                    StockItem("AL3083", "JUMBO NUNOA", "CUK", "Suspirito", "631111", 2.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "CASO Y CIA", "Nectar Jumex", "192829", 3.0, null, "2026-10-28", false),
                ),
                locales = catalogo.locales,
            ),
        )
        // Ruta → producto  y  producto → ruta  deben terminar en el mismo filtro.
        val rutaPrimero = StockFilters().conRuta("EMU-3").conMarca("caso").copy(sku = "jumex")
        val productoPrimero = StockFilters(sku = "jumex", marca = "caso").conRuta("EMU-3")

        assertEquals(productoPrimero.ruta, rutaPrimero.ruta)
        assertEquals(productoPrimero.marca, rutaPrimero.marca)
        assertEquals(productoPrimero.sku, rutaPrimero.sku)

        val a = repo.search(rutaPrimero).items.map { it.storeCode + "|" + it.sku }
        val b = repo.search(productoPrimero).items.map { it.storeCode + "|" + it.sku }
        assertEquals(a, b)
        assertEquals(listOf("AL3083|192829"), a)
    }
}
