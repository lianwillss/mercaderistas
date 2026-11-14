package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockSource
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
    fun `una ruta de 124 locales entra completa en una sola consulta`() = runBlocking {
        // PITUTO es la ruta más grande del rutero; con el tope viejo de 60
        // locales se perdían más de 2.000 filas.
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(total = 0)),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns (1..124).map {
                entrada("S" + it.toString().padStart(3, '0'), "PITUTO")
            }
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(ruta = "PITUTO"))

            assertFalse(resultado.truncated)
            assertTrue(server.lastRequest()!!.contains("S124"))
        }
    }

    @Test
    fun `una ruta con mas de 150 locales se marca como parcial`() = runBlocking {
        QueueHttpServer(
            respuestaLogin(),
            response(200, inventario(total = 0)),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            val dao = mockk<RouteEntryDao>()
            coEvery { dao.getAllEntries() } returns (1..151).map {
                entrada("S" + it.toString().padStart(3, '0'), "PITUTO")
            }
            val repo = RemoteStockRepository(client, session, dao)

            assertTrue(repo.ensureSession())
            val resultado = repo.search(StockFilters(ruta = "PITUTO"))

            assertTrue(resultado.truncated)
            assertTrue(!server.lastRequest()!!.contains("S151"))
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
    ): String = """{"store_code":"$storeCode","store_name":"$storeName","brand_raw":"$brand",
        "product_desc_raw":"Producto $sku","sku_b2b_raw":"$sku","stock_units":$stock,
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