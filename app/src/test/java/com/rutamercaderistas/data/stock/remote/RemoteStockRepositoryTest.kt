package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.marcasPara
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStockRepositoryTest {

    @Test
    fun `busca por ruta con los codigos del rutero en Room`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                inventario(
                    fila("J503", "JUMBO MAIPU", "CASO Y CIA", "192829", 94.0),
                    fila("J502", "JUMBO LA FLORIDA", "CASO Y CIA", "192829", 0.0),
                    total = 2,
                ),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503", "EMU-3"),
                entrada("J502", "EMU-3"),
            )
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(ruta = "EMU-3"))

            assertEquals(2, resultado.items.size)
            assertEquals(StockSource.LIVE, resultado.source)
            assertNotNull(resultado.fetchedAt)
            assertTrue(server.lastRequest()!!.contains("storeCodes=J503%2CJ502"))
        }
    }

    @Test
    fun `reporta el avance real mientras pagina`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                inventario(
                    fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
                    fila("J502", "JUMBO LA FLORIDA", "CASO Y CIA", "2", 20.0),
                    total = 2,
                ),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503", "EMU-3"),
                entrada("J502", "EMU-3"),
            )
            val repo = RemoteStockRepository(client, session, dao)
            val avances = mutableListOf<Pair<Int, Int>>()

            assertTrue(repo.ensureSession())
            repo.search(
                filters = StockFilters(ruta = "EMU-3"),
                force = false,
                onProgress = { cargados, total -> avances += cargados to total },
            )

            assertEquals(listOf(2 to 2), avances)
        }
    }

    @Test
    fun `filtra marca parcial en memoria y no la manda a la API`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                inventario(
                    fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
                    fila("J503", "JUMBO MAIPU", "SUK", "2", 20.0),
                    total = 2,
                ),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(local = "J503", marca = "caso"))

            assertEquals(1, resultado.items.size)
            assertEquals("CASO Y CIA", resultado.items.single().brand)
            assertTrue(!server.lastRequest()!!.contains("brands="))
        }
    }

    @Test
    fun `la segunda busqueda igual usa la cache y no vuelve a la API`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                inventario(fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0), total = 1),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            repo.search(StockFilters(local = "J503"))
            repo.search(StockFilters(local = "J503"))

            // login + una sola consulta de inventario
            assertEquals(2, server.requestCount())
        }
    }

    /**
     * Regresión: la clave de caché no incluía la ruta, así que dos rutas
     * distintas compartían la entrada "|" y la segunda búsqueda mostraba las
     * filas de la primera durante los 60 s de la caché.
     */
    @Test
    fun `cambiar de ruta no reusa la cache de otra ruta`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0), total = 1)),
            response(200, inventario(fila("EX603", "EXPRESS LA REINA", "CASO Y CIA", "2", 10.0), total = 1)),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503", "EMU-3"),
                entrada("EX603", "FMU-1"),
            )
            val repo = RemoteStockRepository(client, session, dao)
            assertTrue(repo.ensureSession())

            val a = repo.search(StockFilters(ruta = "EMU-3"))
            val b = repo.search(StockFilters(ruta = "FMU-1"))

            assertEquals(listOf("J503"), a.items.map { it.storeCode })
            assertEquals(listOf("EX603"), b.items.map { it.storeCode })
            // login + una consulta por ruta
            assertEquals(3, server.requestCount())
        }
    }

    /**
     * Una ruta real trae más de 1.500 filas (el tope viejo de 3 páginas):
     * la descarga debe seguir hasta el total que informa la API.
     */
    @Test
    fun `descarga todas las paginas y cuenta sobre la base completa`() = runBlocking {
        val respuestas = listOf(respuestaLogin()) + paginasDeFilas(total = 1_800)
        QueueHttpServer(*respuestas.toTypedArray()).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(local = "J503"))

            assertEquals(1_800, resultado.items.size)
            // Las tarjetas se calculan sobre TODAS las filas, no sobre 1.500.
            assertEquals(900, resultado.conteoQuiebre)
            assertEquals(900, resultado.conteoDisponible)
            assertEquals(1_800, resultado.total)
            assertFalse(resultado.truncated)
            // login + 500 + 500 + 500 + 300
            assertEquals(5, server.requestCount())
        }
    }

    @Test
    fun `corta en el tope de filas y marca el resultado como truncado`() = runBlocking {
        val pagina = inventario(
            fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
            fila("J503", "JUMBO MAIPU", "CASO Y CIA", "2", 20.0),
            total = 100_000,
        )
        // 10.000 filas / 500 por página = 20 páginas; después se corta.
        val respuestas = listOf(respuestaLogin()) + List(20) { response(200, pagina) }
        QueueHttpServer(*respuestas.toTypedArray()).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(local = "J503"))

            assertTrue(resultado.truncated)
            assertEquals(100_000, resultado.total)
            // login + una consulta por cada una de las 20 páginas
            assertEquals(21, server.requestCount())
        }
    }

    @Test
    fun `una ruta de 60 locales entra completa en una sola consulta`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(total = 0)),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns (1..60).map {
                entrada("S" + it.toString().padStart(3, '0'), "EMU-3")
            }
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(ruta = "EMU-3"))

            assertFalse(resultado.truncated)
            assertTrue(server.lastRequest()!!.contains("S060"))
        }
    }

    /**
     * KPI One se cae (503 "reporte ocupado", ~30 s) con consultas de más de
     * ~100 locales: PITUTO tiene 131. En vez de colgar la búsqueda, se resuelve
     * con el snapshot sin hacer el request.
     */
    @Test
    fun `una ruta de mas de 60 locales no consulta en vivo`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            // Ruta sintética: PITUTO (la real con >60) está excluida del módulo.
            coEvery { dao.getAllEntries() } returns (1..61).map {
                entrada("S" + it.toString().padStart(3, '0'), "ZETA-9")
            }
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            var error: Throwable? = null
            try {
                repo.search(StockFilters(ruta = "ZETA-9"))
            } catch (e: Throwable) {
                error = e
            }

            assertTrue("Esperaba StockQueryTooLargeException: $error",
                error is StockQueryTooLargeException)
            assertEquals(61, (error as StockQueryTooLargeException).locales)
            // Sólo el login: la consulta en vivo no se hizo.
            assertEquals(1, server.requestCount())
        }
    }

    @Test
    fun `PITUTO y PROPAL no consultan en vivo ni por local`() = runBlocking {
        QueueHttpServer(respuestaLogin()).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("S001", "PITUTO"),
                entrada("S002", "PROPAL"),
                entrada("J503", "EMU-3"),
            )
            val repo = RemoteStockRepository(client, session, dao)
            assertTrue(repo.ensureSession())

            var errorRuta: Throwable? = null
            try {
                repo.search(StockFilters(ruta = "PITUTO"))
            } catch (e: Throwable) {
                errorRuta = e
            }
            assertTrue("Esperaba StockQueryException: $errorRuta",
                errorRuta is StockQueryException)

            var errorLocal: Throwable? = null
            try {
                repo.search(StockFilters(local = "S001"))
            } catch (e: Throwable) {
                errorLocal = e
            }
            assertTrue("El local de PITUTO tampoco consulta: $errorLocal",
                errorLocal is StockQueryException)

            // Sólo el login: ninguna consulta en vivo llegó a KPI One.
            assertEquals(1, server.requestCount())
        }
    }

    /**
     * La marca no viaja a KPI One: cambiarla no debe volver a paginar la ruta.
     */
    @Test
    fun `cambiar solo la marca reusa la descarga y filtra en memoria`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(
                200,
                inventario(
                    fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
                    fila("J503", "JUMBO MAIPU", "SUK", "2", 20.0),
                    total = 2,
                ),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            repo.search(StockFilters(local = "J503"))
            val conMarca = repo.search(StockFilters(local = "J503", marca = "caso"))

            assertEquals(1, conMarca.items.size)
            assertEquals("CASO Y CIA", conMarca.items.single().brand)
            // login + una sola consulta de inventario
            assertEquals(2, server.requestCount())
        }
    }

    @Test
    fun `el nombre compacto del local resuelve la consulta en vivo`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(
                200,
                inventario(fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0), total = 1),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503").copy(local = "JUMBO MAIPU"),
            )
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            // El combo ya sugería "JUMBO MAIPU" al escribir "jumbomaipu";
            // ahora la consulta en vivo también lo resuelve.
            val resultado = repo.search(StockFilters(local = "jumbomaipu"))

            assertEquals(StockSource.LIVE, resultado.source)
            assertEquals(listOf("J503"), resultado.items.map { it.storeCode })
            assertTrue(server.lastRequest()!!.contains("storeCodes=J503"))
        }
    }

    @Test
    fun `una ruta sin locales en Room no inventa una consulta en vivo`() = runBlocking {
        QueueHttpServer().use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns emptyList()
            val repo = RemoteStockRepository(client, session, dao)

            var error: Throwable? = null
            try {
                repo.search(StockFilters(ruta = "NO-EXISTE"))
            } catch (e: Throwable) {
                error = e
            }

            assertTrue(error is StockQueryException)
        }
    }

    @Test
    fun `el catalogo arma las rutas desde Room`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(200, """{"storeCodes":["J503"],"brands":["CASO Y CIA"]}"""),
            response(200, """[{"storeCode":"J503","name":"JUMBO MAIPU"}]"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503", "EMU-3"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val catalogo = repo.catalog()

            assertEquals(listOf("J503"), catalogo.locales.map { it.code })
            assertEquals(listOf("EMU-3"), catalogo.rutas)
            assertEquals(listOf("CASO Y CIA"), catalogo.marcas)
        }
    }

    @Test
    fun `completa los negativos con la OSA de la ultima visita`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(
                200,
                inventario(
                    fila(
                        "J503", "JUMBO MAIPU", "CASO Y CIA", "192829", -1.0,
                        storeId = 878, productId = 7165,
                    ),
                    fila(
                        "J503", "JUMBO MAIPU", "CASO Y CIA", "192830", 5.0,
                        storeId = 878, productId = 5000,
                    ),
                    total = 2,
                ),
            ),
            // Browse: última visita del local+marca.
            response(
                200,
                """{"records":[{"id":99,"date":"2026-11-14"}],"total":1}""",
            ),
            // Survey de esa visita: la OSA del producto negativo.
            response(
                200,
                """{"osa":{"responses":[{"productId":7165,"productSku":"192829",
                   "answer":"si","respondedAt":"2026-11-14T13:43:24.056Z",
                   "b2bStock":10}]}}""".trimIndent(),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503"))
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(local = "J503"))
            assertEquals(1, resultado.items.count { it.stockUnits < 0.0 })

            val cruzado = repo.completarOsa(StockFilters(local = "J503"), resultado)

            assertNotNull(cruzado)
            val negativo = cruzado!!.items.first { it.stockUnits < 0.0 }
            assertTrue(negativo.osa?.estaEnGondola == true)
            assertEquals("2026-11-14", negativo.osa?.fecha)
            assertEquals(1, cruzado.conteoContradicen)
            assertEquals(0, cruzado.conteoConfirmados)
            assertTrue(cruzado.osaConsultada)
            assertTrue(server.lastRequest()!!.startsWith("GET /api/admin/photos/survey-data/99"))
        }
    }

    /**
     * Tarea A: KPI One no trae marcas por ruta, así que el catálogo remoto las
     * aprende de las búsquedas en vivo. Tras buscar una ruta, el selector de
     * marca se acota y trae conteos por local.
     */
    @Test
    fun `tras una busqueda en vivo de una ruta el catalogo acota sus marcas`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(
                fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
                fila("J502", "JUMBO LA FLORIDA", "SUK", "2", 20.0),
                total = 2,
            )),
            response(200, """{"storeCodes":["J503","J502"],"brands":["CASO Y CIA","SUK","CUK"]}"""),
            response(200, """[{"storeCode":"J503","name":"JUMBO MAIPU"},{"storeCode":"J502","name":"JUMBO LA FLORIDA"}]"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503", "EMU-3"),
                entrada("J502", "EMU-3"),
            )
            val repo = RemoteStockRepository(client, session, dao)
            assertTrue(repo.ensureSession())

            val resultado = repo.search(StockFilters(ruta = "EMU-3"))
            assertEquals(2, resultado.items.size)

            val catalogo = repo.catalog()
            // Sólo las marcas vistas en la búsqueda, no el catálogo completo.
            assertEquals(listOf("CASO Y CIA", "SUK"), catalogo.marcasPara("EMU-3"))
            // Conteo de locales por ruta+marca: una por cada marca.
            assertEquals(1, catalogo.localesPorRutaMarca["EMU3"]?.get("CASO Y CIA"))
            assertEquals(1, catalogo.localesPorRutaMarca["EMU3"]?.get("SUK"))
            assertEquals(null, catalogo.localesPorRutaMarca["EMU3"]?.get("CUK"))
        }
    }

    /** Una ruta nunca buscada no inventa marcas: conserva el fallback completo. */
    @Test
    fun `una ruta sin datos conserva el fallback de la lista completa`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(
                fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0),
                total = 1,
            )),
            response(200, """{"storeCodes":["J503"],"brands":["CASO Y CIA","CUK","SUK"]}"""),
            response(200, """[{"storeCode":"J503","name":"JUMBO MAIPU"}]"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(entrada("J503", "EMU-3"))
            val repo = RemoteStockRepository(client, session, dao)
            assertTrue(repo.ensureSession())

            repo.search(StockFilters(ruta = "EMU-3"))
            val catalogo = repo.catalog()

            // EMU-3 aprendió "CASO Y CIA"; FMU-9 no tiene datos → lista completa.
            assertEquals(listOf("CASO Y CIA"), catalogo.marcasPara("EMU-3"))
            assertEquals(catalogo.marcas, catalogo.marcasPara("FMU-9"))
        }
    }

    /** Aprender una ruta no contamina lo aprendido de otra. */
    @Test
    fun `cambiar de ruta no mezcla las marcas aprendidas`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(fila("J503", "JUMBO MAIPU", "CASO Y CIA", "1", 10.0), total = 1)),
            response(200, inventario(fila("EX603", "EXPRESS LA REINA", "SUK", "2", 10.0), total = 1)),
            response(200, """{"storeCodes":["J503","EX603"],"brands":["CASO Y CIA","SUK"]}"""),
            response(200, """[{"storeCode":"J503","name":"JUMBO MAIPU"},{"storeCode":"EX603","name":"EXPRESS LA REINA"}]"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns listOf(
                entrada("J503", "EMU-3"),
                entrada("EX603", "FMU-1"),
            )
            val repo = RemoteStockRepository(client, session, dao)
            assertTrue(repo.ensureSession())

            repo.search(StockFilters(ruta = "EMU-3"))
            repo.search(StockFilters(ruta = "FMU-1"))
            val catalogo = repo.catalog()

            assertEquals(listOf("CASO Y CIA"), catalogo.marcasPara("EMU-3"))
            assertEquals(listOf("SUK"), catalogo.marcasPara("FMU-1"))
            assertTrue(!catalogo.marcasPara("EMU-3").contains("SUK"))
            assertTrue(!catalogo.marcasPara("FMU-1").contains("CASO Y CIA"))
        }
    }

    private fun client(server: QueueHttpServer, session: InMemoryKpiSessionStore) = KpiOneClient(
        session = session,
        baseUrl = server.url,
        http = KpiOneClient.defaultHttpClient(session),
        buildCredentials = { "qa@retailtrust.cl" to "secreto" },
    )

    private fun entrada(codigo: String, rutero: String = "EMU-3") = RouteEntryEntity(
        rutero = rutero,
        codigo = codigo,
        local = "LOCAL " + codigo,
        direccion = "",
        cliente = "",
        cadena = "",
        formato = "",
        region = "RM",
        comuna = "",
        supervisor = "",
        gestores = "",
        modalidad = "",
        equipo = "",
        reponedor = "",
        lunes = false,
        martes = false,
        miercoles = false,
        jueves = false,
        viernes = false,
        sabado = false,
        domingo = false,
    )

    private fun fila(
        storeCode: String,
        storeName: String,
        brand: String,
        sku: String,
        stock: Double,
        quiebre: Int = 0,
        storeId: Int? = null,
        productId: Int? = null,
    ): String = """{"store_code":"$storeCode","store_name":"$storeName","brand_raw":"$brand",
        "product_desc_raw":"Producto $sku","sku_b2b_raw":"$sku","stock_units":$stock,
        "store_id":$storeId,"product_id":$productId,
        "transit_units":0,"snapshot_date":"2026-11-12","quiebre":$quiebre,
        "days_of_inventory":10,"sale_last_week":5,"sale_last_4w":20}""".trimIndent()

    private fun inventario(vararg filas: String, total: Int): String =
        """{"items":[${filas.joinToString(",")}],"pagination":{"total":$total}}"""

    /** Respuesta válida de inventario paginada en bloques de 500 filas. */
    private fun paginasDeFilas(total: Int): List<String> =
        (1..total)
            .map { n ->
                fila(
                    storeCode = "J503",
                    storeName = "JUMBO MAIPU",
                    brand = "CASO Y CIA",
                    sku = "SKU" + n,
                    stock = 10.0,
                    quiebre = if (n % 2 == 0) 1 else 0,
                )
            }
            .chunked(KpiOneClient.LIMIT)
            .map { bloque -> response(200, inventario(*bloque.toTypedArray(), total = total)) }

    private fun respuestaLogin(): String =
        response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/")

    private companion object {
        fun response(status: Int, body: String, extraHeaders: String = ""): String {
            val cabeceras = if (extraHeaders.isBlank()) "" else extraHeaders + "\r\n"
            return "HTTP/1.1 $status OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.toByteArray().size + "\r\n" +
                "Connection: close\r\n" +
                cabeceras +
                "\r\n" +
                body
        }
    }
}