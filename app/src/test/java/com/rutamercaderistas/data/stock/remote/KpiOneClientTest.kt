package com.rutamercaderistas.data.stock.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class KpiOneClientTest {

    @Test
    fun loginGuardaCookieYElCatalogoTraeCodigosMarcasYNombres() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(200, """{"storeCodes":["J503","J502"],"brands":["ZETA","ALFA"]}"""),
            response(
                200,
                """[{"storeCode":"J503","name":"JUMBO MAIPU"},{"storeCode":"J502","name":"JUMBO LA FLORIDA"}]""",
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)

            assertTrue(client.ensureSession())
            assertEquals("session=abc", session.cookie())

            val catalogo = client.catalog()
            assertEquals(listOf("J503", "J502"), catalogo.codes)
            assertEquals(listOf("ALFA", "ZETA"), catalogo.brands)
            assertEquals("JUMBO MAIPU", catalogo.names["J503"])
            assertNotNull(server.lastRequest())
            assertTrue(server.lastRequest()!!.startsWith("GET /api/stores"))
        }
    }

    @Test
    fun `un reporte ocupado se reintenta una sola vez`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(503, """{"error":"El reporte está temporalmente ocupado. Acota la consulta."}"""),
            response(503, """{"error":"El reporte está temporalmente ocupado. Acota la consulta."}"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            assertTrue(client.ensureSession())

            var error: Throwable? = null
            try {
                client.fetchInventory(listOf("J503"))
            } catch (e: Throwable) {
                error = e
            }

            assertTrue("Esperaba KpiHttpException: $error", error is KpiHttpException)
            assertEquals(503, (error as KpiHttpException).statusCode)
            // login + 2 intentos (antes eran 4 y la búsqueda quedaba ~90 s colgada)
            assertEquals(3, server.requestCount())
        }
    }

    @Test
    fun fetchInventoryPaginaYConvierteLosCamposDeKpiOne() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                """{
                  "items":[
                    {"store_code":"J503","store_name":"JUMBO MAIPU","brand_raw":"CASO Y CIA",
                     "product_desc_raw":"Nectar Jumex","sku_b2b_raw":"192829","stock_units":94,
                     "store_id":878,"product_id":1790,
                     "transit_units":0,"snapshot_date":"2026-10-28","quiebre":0,"days_of_inventory":10,
                     "sale_last_week":12,"sale_last_4w":48}
                  ],
                  "pagination":{"total":1}
                }""".trimIndent(),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            assertTrue(client.ensureSession())

            val pagina = client.fetchInventory(listOf("J503"))
            assertEquals(1, pagina.total)
            assertEquals(1, pagina.items.size)
            val fila = pagina.items.first()
            assertEquals("J503", fila.storeCode)
            assertEquals("CASO Y CIA", fila.brand)
            assertEquals(94.0, fila.stockUnits, 0.0)
            assertEquals("2026-10-28", fila.snapshotDate)
            assertEquals(10, fila.daysOfInventory)
            assertEquals(12, fila.saleLastWeek)
            assertEquals(48, fila.saleLast4w)
            assertEquals(878, fila.storeId)
            assertEquals(1790, fila.productId)
            assertEquals(48.0 / 28.0, fila.ventaDiaria!!, 0.0001)
            assertEquals(94.0 / (48.0 / 28.0), fila.coberturaDias!!, 0.0001)
            assertTrue(server.lastRequest()!!.contains("/api/b2b-reports/terreno/inventory-browse"))
            assertTrue(server.lastRequest()!!.contains("storeCodes=J503"))
        }
    }

    @Test
    fun `una respuesta gzip del inventario se descomprime y parsea`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/").toByteArray(),
            responseGzip(
                200,
                """{
                  "items":[
                    {"store_code":"J503","store_name":"JUMBO MAIPU","brand_raw":"CASO Y CIA",
                     "product_desc_raw":"Nectar Jumex","sku_b2b_raw":"192829","stock_units":94,
                     "snapshot_date":"2026-11-21"}
                  ],
                  "pagination":{"total":1}
                }""".trimIndent(),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            assertTrue(client.ensureSession())

            // KPI One comprime si el cliente pide gzip; OkHttp debe descomprimir
            // solo o json.parseToJsonElement recibe los bytes 1f 8b y la
            // búsqueda cae al snapshot.
            val pagina = client.fetchInventory(listOf("J503"))
            assertEquals(1, pagina.total)
            assertEquals(1, pagina.items.size)
            assertEquals(94.0, pagina.items.first().stockUnits, 0.0)
        }
    }

    @Test
    fun noEntraSoloSiElUsuarioDesconecto() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=nueva; Path=/"),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            session.borrarTodo()
            val client = client(server, session)

            // Hay credenciales en BuildConfig, pero "Desconectar" las bloquea.
            assertTrue(!client.ensureSession())

            // Un login manual vuelve a habilitar el auto-login.
            assertTrue(client.login("qa@retailtrust.cl", "secreto"))
            assertTrue(session.autoLoginPermitido())
        }
    }

    @Test
    fun htmlSeTrataComoSesionVencidaYSeReintentaUnaVez() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            // inventory: HTML sin sesión
            response(200, "<html>login</html>"),
            // re-login automático y reintento del mismo endpoint
            response(200, """{"ok":true}""", "Set-Cookie: session=nueva; Path=/"),
            response(200, """{"items":[],"pagination":{"total":0}}"""),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            assertTrue(client.ensureSession())

            val pagina = client.fetchInventory(listOf("J503"))
            assertEquals(0, pagina.items.size)
            assertEquals("session=nueva", session.cookie())
        }
    }

    @Test
    fun `fetchOsaRecienteLeeLaUltimaVisitaYSusRespuestas`() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                """{"records":[{"id":99,"date":"2026-11-14"}],"total":1}""",
            ),
            response(
                200,
                """{"osa":{"responses":[
                    {"productId":7165,"productSku":"17735507","answer":"no",
                     "respondedAt":"2026-11-14T13:43:24.056Z","b2bStock":-1},
                    {"productId":5080,"productSku":"80651320","answer":"si",
                     "respondedAt":"2026-11-14T13:43:52.140Z","b2bStock":10},
                    {"productId":1,"productSku":"sin-responder","answer":null}
                ]}}""".trimIndent(),
            ),
        ).use { server ->
            val session = InMemoryKpiSessionStore()
            val client = client(server, session)
            assertTrue(client.ensureSession())

            val respuestas = client.fetchOsaReciente(
                storeCode = "FA3741",
                brand = "CASO Y CIA",
                desde = java.time.LocalDate.of(2026, 9, 16),
                hasta = java.time.LocalDate.of(2026, 11, 16),
            )

            assertEquals(2, respuestas.size)
            val no = respuestas.first { it.sku == "17735507" }
            assertEquals(7165, no.productId)
            assertTrue(!no.respuesta)
            assertEquals("2026-11-14", no.visitaFecha)
            assertEquals(-1.0, no.stockB2bVisita ?: 0.0, 0.0)
            val si = respuestas.first { it.sku == "80651320" }
            assertTrue(si.respuesta)
            assertEquals(10.0, si.stockB2bVisita ?: 0.0, 0.0)
            assertTrue(server.lastRequest()!!.startsWith("GET /api/admin/photos/survey-data/99"))
        }
    }

    private fun client(server: QueueHttpServer, session: InMemoryKpiSessionStore) = KpiOneClient(
        session = session,
        baseUrl = server.url,
        http = KpiOneClient.defaultHttpClient(session),
        buildCredentials = { "qa@retailtrust.cl" to "secreto" },
    )

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

        /** Respuesta comprimida con gzip: KPI One la envía cuando el cliente la pide. */
        fun responseGzip(status: Int, body: String): ByteArray {
            val gzip = java.io.ByteArrayOutputStream().use { salida ->
                java.util.zip.GZIPOutputStream(salida).use { it.write(body.toByteArray()) }
                salida.toByteArray()
            }
            val texto = "HTTP/1.1 $status OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Encoding: gzip\r\n" +
                "Content-Length: " + gzip.size + "\r\n" +
                "Connection: close\r\n\r\n"
            return texto.toByteArray() + gzip
        }
    }
}

class InMemoryKpiSessionStore : KpiSessionStore {
    private val _hasSession = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val hasSession = _hasSession
    private var cookie: String? = null
    private var creds: Pair<String, String>? = null
    private var bloqueado = false

    override fun cookie(): String? = cookie
    override fun guardarCookie(cookie: String): Boolean {
        this.cookie = cookie
        bloqueado = false
        _hasSession.value = true
        return true
    }
    override fun borrarSesion() {
        cookie = null
        _hasSession.value = false
    }
    override fun hayCredenciales(): Boolean = creds != null
    override fun credenciales(): Pair<String, String>? = creds
    override fun guardarCredenciales(correo: String, clave: String) {
        creds = correo to clave
        bloqueado = false
    }
    override fun borrarTodo() {
        cookie = null
        creds = null
        bloqueado = true
        _hasSession.value = false
    }
    override fun autoLoginPermitido(): Boolean = !bloqueado
}

/** Servidor HTTP mínimo: entrega una respuesta de la cola por request. */
class QueueHttpServer private constructor(
    respuestasIniciales: List<ByteArray>,
) : Closeable {
    constructor() : this(emptyList())
    constructor(vararg responses: String) : this(responses.map { it.toByteArray() })
    constructor(vararg responses: ByteArray) : this(responses.toList())

    private val server = ServerSocket(0)
    private val cola = LinkedBlockingQueue<ByteArray>()
    private val pedidos = LinkedBlockingQueue<String>()
    @Volatile private var vivo = true

    private val hilo = thread(isDaemon = true) {
        respuestasIniciales.forEach { cola.offer(it) }
        while (vivo) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val primera = reader.readLine().orEmpty()
                    pedidos.offer(primera)
                    while (true) {
                        val linea = reader.readLine() ?: break
                        if (linea.isEmpty()) break
                    }
                    val respuesta = cola.poll(2, TimeUnit.SECONDS)
                    if (respuesta != null) {
                        socket.getOutputStream().apply {
                            write(respuesta)
                            flush()
                        }
                    }
                }
            } catch (_: Exception) {
                if (!vivo) return@thread
            }
        }
    }

    val url: String = "http://127.0.0.1:" + server.localPort

    fun lastRequest(): String? = pedidos.lastOrNull()

    fun requestCount(): Int = pedidos.size

    override fun close() {
        vivo = false
        server.close()
        hilo.join(1_000)
    }
}
