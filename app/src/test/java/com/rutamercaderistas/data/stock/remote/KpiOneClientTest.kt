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
    fun fetchInventoryPaginaYConvierteLosCamposDeKpiOne() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(
                200,
                """{
                  "items":[
                    {"store_code":"J503","store_name":"JUMBO MAIPU","brand_raw":"CASO Y CIA",
                     "product_desc_raw":"Nectar Jumex","sku_b2b_raw":"192829","stock_units":94,
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
            assertEquals(48.0 / 28.0, fila.ventaDiaria!!, 0.0001)
            assertEquals(94.0 / (48.0 / 28.0), fila.coberturaDias!!, 0.0001)
            assertTrue(server.lastRequest()!!.contains("/api/b2b-reports/terreno/inventory-browse"))
            assertTrue(server.lastRequest()!!.contains("storeCodes=J503"))
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
class QueueHttpServer(vararg responses: String) : Closeable {
    private val server = ServerSocket(0)
    private val cola = LinkedBlockingQueue<String>()
    private val pedidos = LinkedBlockingQueue<String>()
    @Volatile private var vivo = true

    private val hilo = thread(isDaemon = true) {
        responses.forEach { cola.offer(it) }
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
                            write(respuesta.toByteArray())
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

    override fun close() {
        vivo = false
        server.close()
        hilo.join(1_000)
    }
}
