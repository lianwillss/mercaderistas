package com.rutamercaderistas.data.stock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoStockRepositoryTest {

    private val muestra = listOf(
        StockItem("J521", "JUMBO LA SERENA", "CASO Y CIA", "Néctar Jumex 335 cc Durazno", "192829", 94.0, 0.0, "2026-10-23", false),
        StockItem("J521", "JUMBO LA SERENA", "CASO Y CIA", "Néctar Jumex 335 cc Piña", "192830", 104.0, 0.0, "2026-10-23", false),
        StockItem("J521", "JUMBO LA SERENA", "KOMBUCHA", "Kombucha Jengibre 473 ml", "331045", 0.0, 24.0, "2026-10-27", true),
        StockItem("FA1400", "FALABELLA LA SERENA", "JAPI JANE", "Aceite Corporal 120 ml", "2088850", 9.0, 0.0, "2026-10-27", false),
        StockItem("UN335", "UNIMARC ÑUÑOA", "MAILEMU MIEL", "Miel de Ulmo 500 g", "553311", 12.0, 6.0, "2026-10-27", false),
        StockItem("HI89", "89 - LIDER BIO BIO", "GRANA", "Chocolate 70% Cacao 100 g", "778201", 0.0, 0.0, "2026-09-19", true),
    )

    private val repo = DemoStockRepository(DemoStockData(muestra))

    @Test
    fun `catalogo trae locales y marcas sin duplicados`() = runBlocking {
        val catalogo = repo.catalog()
        assertEquals(4, catalogo.locales.size)
        assertEquals(5, catalogo.marcas.size)
        assertEquals(catalogo.locales.size, catalogo.locales.distinctBy { it.code }.size)
    }

    @Test
    fun `hay criterio con sku, local, ruta o marca`() = runBlocking {
        assertTrue(StockFilters(sku = "123").hayCriterio)
        assertTrue(StockFilters(local = "J521").hayCriterio)
        assertTrue(StockFilters(ruta = "EMU-3").hayCriterio)
        assertTrue(StockFilters(marca = "CASO Y CIA").hayCriterio)
        assertFalse(StockFilters().hayCriterio)
        // Un solo caracter no alcanza: con "1" el filtro por subcadena
        // devolvía cientos de locales y disparaba consultas enormes.
        assertFalse(StockFilters(local = "J").hayCriterio)
        assertFalse(StockFilters(sku = "1").hayCriterio)
        assertFalse(StockFilters(marca = "C").hayCriterio)
        assertFalse(StockFilters(ruta = "E").hayCriterio)
    }

    @Test
    fun `marca sin espacios encuentra la marca con espacios`() = runBlocking {
        val repoMonkey = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem(
                        "J503", "JUMBO MAIPU", "VEG MONKEY", "Snack", "1",
                        5.0, null, "2026-11-12", false,
                    ),
                ),
            ),
        )
        // La sugerencia del combo ya aceptaba "VEGMONKEY": la búsqueda también.
        assertEquals(1, repoMonkey.search(StockFilters(marca = "VEGMONKEY")).items.size)
        assertEquals(1, repoMonkey.search(StockFilters(marca = "veg-monkey")).items.size)
        assertEquals(1, repoMonkey.search(StockFilters(marca = "VEG MONKEY")).items.size)
    }

    @Test
    fun `local compacto y sufijo de codigo encuentran el local`() = runBlocking {
        val repoLocal = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem(
                        "J503", "JUMBO MAIPU", "CASO Y CIA", "Nectar", "1",
                        5.0, null, "2026-11-12", false,
                    ),
                ),
            ),
        )
        assertEquals(1, repoLocal.search(StockFilters(local = "jumbomaipu")).items.size)
        assertEquals(1, repoLocal.search(StockFilters(local = "503")).items.size)
    }

    @Test
    fun `sin resultados devuelve lista vacia`() = runBlocking {
        val resultado = repo.search(StockFilters(sku = "no-existe-123"))
        assertTrue(resultado.items.isEmpty())
        assertEquals(0, resultado.quiebres)
    }

    @Test
    fun `marcas por ruta se limitan a las presentes en los locales de la ruta`() = runBlocking {
        val conRutas = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar", "1", 1.0, null, "2026-10-28", false),
                    StockItem("AL3083", "JUMBO NUNOA", "KOMBUCHA", "Kombucha", "2", 2.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "SUK", "Falafel", "3", 3.0, null, "2026-10-28", false),
                ),
                locales = listOf(
                    StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
                    StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
                ),
                marcas = listOf("CASO Y CIA", "SUK"),
            ),
        )
        val catalogo = conRutas.catalog()
        assertEquals(listOf("CASO Y CIA", "KOMBUCHA"), catalogo.marcasPorRuta["EMU3"] ?: emptyList<String>())
        assertEquals(listOf("SUK"), catalogo.marcasPorRuta["DMU5"] ?: emptyList<String>())
        // Combinacion ruta + marca filtra correctamente
        val resultado = conRutas.search(StockFilters(ruta = "EMU-3", marca = "KOMBUCHA"))
        assertEquals(1, resultado.items.size)
        assertTrue(resultado.items.all { it.brand == "KOMBUCHA" && it.storeCode == "AL3083" })
    }

    @Test
    fun `el catalogo y la busqueda siempre muestran todas las rutas`() = runBlocking {
        val conRutas = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar", "1", 1.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "SUK", "Falafel", "2", 2.0, null, "2026-10-28", false),
                ),
                locales = listOf(
                    StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
                    StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
                ),
                marcas = listOf("CASO Y CIA", "SUK"),
            ),
        )

        // Sin alcance, el catalogo muestra todos los locales y rutas
        val catalogo = conRutas.catalog()
        assertEquals(listOf("AL3083", "HI45"), catalogo.locales.map { it.code })
        assertEquals(listOf("DMU-5", "EMU-3"), catalogo.rutas)
        assertEquals(listOf("CASO Y CIA", "SUK"), catalogo.marcas)
        assertEquals(2, conRutas.search(StockFilters()).items.size)
        assertEquals(1, conRutas.search(StockFilters(sku = "nectar")).items.size)
        // Busqueda por ruta devuelve solo los locales de esa ruta
        val resultado = conRutas.search(StockFilters(ruta = "EMU-3", marca = "KOMBUCHA"))
        assertEquals(0, resultado.items.size)
        val resultado2 = conRutas.search(StockFilters(ruta = "EMU-3"))
        assertEquals(1, resultado2.items.size)
        assertTrue(resultado2.items.all { it.storeCode == "AL3083" })
    }

    @Test
    fun `marca dada de baja no aparece en catalogo, busqueda ni conteos`() = runBlocking {
        // La fuente escribe la marca de las dos formas: "BERRYSUR" en stock y
        // "BERRY SUR" en el catalogo EAN. Ninguna debe sobrevivir al filtro.
        val conBaja = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("J521", "JUMBO LA SERENA", "BERRYSUR", "Batido Berry Sur Avena 300 ml", "1990127", 5.0, 0.0, "2026-10-28", false),
                    StockItem("J521", "JUMBO LA SERENA", "BERRY SUR", "Agua Fort Cranfra 500 ml", "2037262", 0.0, 0.0, "2026-10-28", true),
                    StockItem("J521", "JUMBO LA SERENA", "PET KITCHEN", "Snack Bite Jumbo 2un", "2094116", 8.0, 0.0, "2026-10-28", false),
                ),
                marcas = listOf("BERRY SUR", "BERRYSUR", "PET KITCHEN"),
            ),
        )

        val catalogo = conBaja.catalog()
        assertFalse(catalogo.marcas.any { it.contains("BERRY", ignoreCase = true) })
        assertEquals(1, conBaja.catalog().marcas.size)
        assertEquals("PET KITCHEN", conBaja.catalog().marcas.single())

        val resultado = conBaja.search(StockFilters())
        assertTrue(resultado.items.all { !it.brand.contains("BERRY", ignoreCase = true) })
    }
}
