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
    fun `filtra por sku exacto y por nombre de producto`() = runBlocking {
        val porSku = repo.search(StockFilters(sku = "2088850"))
        assertEquals(1, porSku.items.size)

        val porNombre = repo.search(StockFilters(sku = "jumex"))
        assertEquals(2, porNombre.items.size)
        assertTrue(porNombre.items.all { it.product.contains("Jumex", ignoreCase = true) })
    }

    @Test
    fun `filtra por marca parcial`() = runBlocking {
        val porMarca = repo.search(StockFilters(marca = "caso"))
        assertEquals(2, porMarca.items.size)
        assertTrue(porMarca.items.all { it.brand == "CASO Y CIA" })
    }

    @Test
    fun `filtra local por nombre parcial`() = runBlocking {
        val porNombre = repo.search(StockFilters(local = "serena"))
        assertEquals(2, porNombre.uniqueLocales)
        assertTrue(porNombre.items.all { it.storeName.contains("SERENA") })
    }

    @Test
    fun `filtra local por codigo parcial`() = runBlocking {
        val resultado = repo.search(StockFilters(local = "J5"))
        assertTrue(resultado.items.isNotEmpty())
        assertTrue(resultado.items.all { it.storeCode == "J521" })
    }

    @Test
    fun `normaliza tildes y mayusculas`() = runBlocking {
        val resultado = repo.search(StockFilters(local = "nunoa"))
        assertEquals(1, resultado.items.size)
        assertEquals("UN335", resultado.items.first().storeCode)
        assertEquals("NUNOA", normalizarStock("Ñuñoa"))
    }

    @Test
    fun `etiqueta con separador sigue filtrando por codigo`() = runBlocking {
        val resultado = repo.search(StockFilters(local = "J521 · JUMBO LA SERENA"))
        assertEquals(3, resultado.items.size)
        assertTrue(resultado.items.all { it.storeCode == "J521" })
    }

    @Test
    fun `solo quiebre devuelve solo filas en quiebre`() = runBlocking {
        val quiebres = repo.search(StockFilters(soloQuiebre = true))
        assertEquals(2, quiebres.items.size)
        assertTrue(quiebres.items.all { it.quiebre })
    }

    @Test
    fun `un negativo no se cuenta ni se filtra como quiebre`() = runBlocking {
        val conNegativo = DemoStockRepository(
            DemoStockData(
                listOf(
                    StockItem(
                        "J521", "JUMBO LA SERENA", "CASO Y CIA",
                        "Toallas Everskin", "2052322", -13.0, null,
                        "2026-10-28", true,
                    ),
                ),
            ),
        )

        val resultado = conNegativo.search(StockFilters(local = "J521"))
        assertEquals(1, resultado.items.size)
        assertEquals(EstadoTipo.Negativo, estadoDe(resultado.items.first()))
        assertEquals(1, resultado.conteoNegativos)
        assertEquals(0, resultado.conteoQuiebre)
        assertEquals(0, resultado.quiebres)

        val quiebres = conNegativo.search(StockFilters(soloQuiebre = true))
        assertEquals(0, quiebres.items.size)
    }

    @Test
    fun `ordena por stock ascendente`() = runBlocking {
        val stocks = repo.search(StockFilters(orden = StockSort.STOCK_ASC)).items.map { it.stockUnits }
        assertEquals(stocks.sorted(), stocks)
    }

    @Test
    fun `elimina filas repetidas del mismo local y sku`() = runBlocking {
        val conDuplicados = DemoStockRepository(
            DemoStockData(
                listOf(
                    StockItem("UN333", "UNIMARC LOS LEONES", "CUK", "SUSPIRO 140 GR", "631111", 11.0, null, "2026-10-28", false),
                    StockItem("UN333", "UNIMARC LOS LEONES", "CUK", "SUSPIRO FRAMBUESA CUK 140 GR", "631111", 11.0, null, "2026-10-28", false, 7),
                ),
            ),
        )
        val resultado = conDuplicados.search(StockFilters(local = "UN333"))
        assertEquals(1, resultado.items.size)
        assertEquals(7, resultado.items.first().daysOfInventory)
    }

    @Test
    fun `busca por ruta tolerando guiones y mayusculas`() = runBlocking {
        val conRutas = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CUK", "SUSPIRO 140 GR", "631111", 11.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "SUK", "FALAFEL 200 GR", "554137", 4.0, null, "2026-10-28", false),
                ),
                locales = listOf(
                    StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
                    StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5", "EMU-5 (PT)")),
                ),
            ),
        )
        assertEquals(1, conRutas.search(StockFilters(ruta = "emu3")).items.size)
        assertEquals(1, conRutas.search(StockFilters(ruta = "EMU 3")).items.size)
        assertEquals(2, conRutas.search(StockFilters(ruta = "EMU")).items.size)
        assertEquals(0, conRutas.search(StockFilters(ruta = "DMU-9")).items.size)
    }
    @Test
    fun `ordena por gravedad`() = runBlocking {
        val repo = DemoStockRepository(
            DemoStockData(
                listOf(
                    StockItem("AAA", "A", "M", "Normal", "1", 10.0, null, "2026-10-28", false),
                    StockItem("BBB", "B", "M", "Negativo", "2", -3.0, null, "2026-10-28", false),
                    StockItem("CCC", "C", "M", "Sin stock", "3", 0.0, null, "2026-10-28", false),
                ),
            ),
        )
        val orden = repo.search(StockFilters(orden = StockSort.GRAVEDAD)).items
            .map { it.stockUnits }
        assertEquals(listOf(-3.0, 0.0, 10.0), orden)
    }

    @Test
    fun `filtra por estado y mantiene los conteos`() = runBlocking {
        val sinFiltro = repo.search(StockFilters(local = "J521"))
        val disponibles = repo.search(StockFilters(local = "J521", estado = EstadoTipo.Disponible))

        assertTrue(disponibles.items.isNotEmpty())
        assertTrue(disponibles.items.all { estadoDe(it) == EstadoTipo.Disponible })
        // Los conteos salen de la búsqueda sin filtrar: no se apagan al elegir.
        assertEquals(sinFiltro.conteoDisponible, disponibles.conteoDisponible)
        assertEquals(sinFiltro.conteoQuiebre, disponibles.conteoQuiebre)
        assertEquals(sinFiltro.conteoNegativos, disponibles.conteoNegativos)

        val quiebres = repo.search(StockFilters(local = "J521", estado = EstadoTipo.Quiebre))
        assertTrue(quiebres.items.all { estadoDe(it) == EstadoTipo.Quiebre })
    }

    @Test
    fun `buscar local o marca suelta la ruta`() {
        val base = StockFilters(
            ruta = "EMU-3",
            local = "J502",
            marca = "CUK",
            sku = "2088850",
            estado = EstadoTipo.Quiebre,
            soloNegativos = true,
        )

        val conLocal = base.conLocal("J503")
        assertEquals("J503", conLocal.local)
        assertEquals("", conLocal.ruta)
        assertEquals(EstadoTipo.Disponible, conLocal.estado)
        assertFalse(conLocal.soloNegativos)
        assertFalse(conLocal.soloQuiebre)

        val conMarca = base.conMarca("SUK")
        assertEquals("SUK", conMarca.marca)
        assertEquals("EMU-3", conMarca.ruta)

        val conRuta = base.conRuta("DMU-5")
        assertEquals("DMU-5", conRuta.ruta)
        // Sólo el local (camino alternativo) se descarta; marca y producto se
        // conservan porque la ruta los acota.
        assertEquals("", conRuta.local)
        assertEquals("CUK", conRuta.marca)
        assertEquals("2088850", conRuta.sku)
        assertEquals(EstadoTipo.Disponible, conRuta.estado)
        assertFalse(conRuta.soloNegativos)
        assertFalse(conRuta.soloQuiebre)
    }

    @Test
    fun `elegir local no borra la marca ni el producto`() {
        val base = StockFilters(ruta = "EMU-3", marca = "CUK", sku = "2088850")
        val conLocal = base.conLocal("J503")
        assertEquals("J503", conLocal.local)
        assertEquals("", conLocal.ruta)
        assertEquals("CUK", conLocal.marca)
        assertEquals("2088850", conLocal.sku)
    }

    @Test
    fun `sin criterio de busqueda la pantalla no muestra nada`() {
        assertTrue(!StockFilters().hayCriterio)
        assertTrue(!StockFilters(soloQuiebre = true).hayCriterio)
        assertTrue(StockFilters(local = "J5").hayCriterio)
        assertTrue(StockFilters(marca = "CUK").hayCriterio)
        assertTrue(StockFilters(sku = "20").hayCriterio)
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
            ),
        )
        val catalogo = conRutas.catalog()
        assertEquals(listOf("CASO Y CIA", "KOMBUCHA"), catalogo.marcasPorRuta["EMU3"] ?: emptyList<String>())
        assertEquals(listOf("SUK"), catalogo.marcasPorRuta["DMU5"] ?: emptyList<String>())
        // combinacion ruta + marca filtra correctamente
        val resultado = conRutas.search(StockFilters(ruta = "EMU-3", marca = "KOMBUCHA"))
        assertEquals(1, resultado.items.size)
        assertTrue(resultado.items.all { it.brand == "KOMBUCHA" && it.storeCode == "AL3083" })
    }

    @Test
    fun `el alcance de mis rutas limita catalogo, marcas y busqueda`() = runBlocking {
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
                marcas = listOf("CASO Y CIA", "SUK", "SIN STOCK EN LA RUTA"),
            ),
        )

        conRutas.fijarAlcance(setOf("emu 3"))

        val catalogo = conRutas.catalog()
        assertEquals(listOf("AL3083"), catalogo.locales.map { it.code })
        assertEquals(listOf("EMU-3"), catalogo.rutas)
        assertEquals(listOf("CASO Y CIA"), catalogo.marcas)
        assertEquals(listOf("CASO Y CIA"), catalogo.marcasPorRuta["EMU3"])
        assertEquals(1, conRutas.search(StockFilters()).items.size)
        assertEquals(1, conRutas.search(StockFilters(sku = "nectar")).items.size)
        assertEquals(0, conRutas.search(StockFilters(ruta = "DMU-5")).items.size)

        // Vacío vuelve a significar "sin límite" (RM completa).
        conRutas.fijarAlcance(emptySet())
        assertEquals(2, conRutas.catalog().locales.size)
        assertEquals(2, conRutas.search(StockFilters()).items.size)
    }

    @Test
    fun `marca dada de baja no aparece en catalogo, busqueda ni conteos`() = runBlocking {
        // La fuente escribe la marca de las dos formas: "BERRYSUR" en stock y
        // "BERRY SUR" en el catálogo EAN. Ninguna debe sobrevivir al filtro.
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
        assertEquals(listOf("PET KITCHEN"), catalogo.marcas)
        assertEquals(emptyList<String>(), catalogo.marcasPorRuta.values.flatten().filter { it.contains("BERRY") })
        assertTrue(catalogo.marcasPorRuta.values.flatten().all { it == "PET KITCHEN" })

        assertEquals(0, conBaja.search(StockFilters(marca = "berry")).items.size)
        assertEquals(0, conBaja.search(StockFilters(sku = "1990127")).items.size)
        val todo = conBaja.search(StockFilters())
        assertEquals(1, todo.items.size)
        // El quiebre de la marca dada de baja tampoco cuenta en las tarjetas.
        assertEquals(0, todo.conteoQuiebre)
        assertEquals("PET KITCHEN", todo.items.single().brand)
    }

    @Test
    fun `localesPorRutaMarca cuenta locales por ruta y marca`() = runBlocking {
        // Cobertura del bloque que se reescribió para dejar de recorrer todos
        // los items por cada local (296 x 13.081): el resultado debe ser el mismo.
        val repoRutas = DemoStockRepository(
            DemoStockData(
                items = listOf(
                    StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar", "1", 1.0, null, "2026-10-28", false),
                    StockItem("AL3084", "JUMBO MAIPU", "CASO Y CIA", "Nectar", "1", 2.0, null, "2026-10-28", false),
                    StockItem("AL3084", "JUMBO MAIPU", "SUK", "Falafel", "2", 3.0, null, "2026-10-28", false),
                    StockItem("HI45", "LIDER MAIPU", "SUK", "Falafel", "2", 0.0, null, "2026-10-28", true),
                ),
                locales = listOf(
                    StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
                    StockLocal("AL3084", "JUMBO MAIPU", listOf("EMU-3")),
                    StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
                ),
            ),
        )
        val catalogo = repoRutas.catalog()
        val emu3 = catalogo.localesPorRutaMarca["EMU3"].orEmpty()
        val dmu5 = catalogo.localesPorRutaMarca["DMU5"].orEmpty()
        // Dos locales distintos con CASO Y CIA en EMU-3; SUK solo en uno.
        assertEquals(2, emu3["CASO Y CIA"])
        assertEquals(1, emu3["SUK"])
        // DMU-5 tiene un solo local con SUK.
        assertEquals(1, dmu5["SUK"])
        assertEquals(null, dmu5["CASO Y CIA"])
        // marcasPorRuta sigue saliendo del mismo agrupado.
        assertEquals(listOf("CASO Y CIA", "SUK"), catalogo.marcasPorRuta["EMU3"])
        assertEquals(listOf("SUK"), catalogo.marcasPorRuta["DMU5"])
    }
}
