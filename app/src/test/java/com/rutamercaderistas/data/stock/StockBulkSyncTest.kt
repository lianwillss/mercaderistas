package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.remote.InMemoryKpiSessionStore
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.QueueHttpServer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockBulkSyncTest {

    @Test
    fun generaElSnapshotYLoGuardaConMetadatos() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(200, """{"storeCodes":["J503","J502"],"brands":["CASO Y CIA"]}"""),
            response(
                200,
                """[{"storeCode":"J503","name":"JUMBO MAIPU"},{"storeCode":"J502","name":"JUMBO LA FLORIDA"}]""",
            ),
            response(200, inventarioDosLocales()),
        ).use { server ->
            val storage = InMemoryStockStorage()
            val sync = sync(server, storage, listOf(entrada("J503"), entrada("J502")))
            val progreso = mutableListOf<StockSyncProgress>()

            val resultado = sync.run(StockSyncRequest()) { progreso += it }

            assertTrue(resultado is StockSyncOutcome.Success)
            val exito = resultado as StockSyncOutcome.Success
            assertEquals(2, exito.meta.rows)
            assertEquals(2, exito.meta.locales)
            assertEquals(2, exito.data.items.size)
            assertEquals("EMU-3", exito.data.locales.first { it.code == "J503" }.rutas.first())
            assertTrue(storage.exists(StockBulkSync.STOCK_FILE))
            assertTrue(storage.exists(StockBulkSync.META_FILE))

            val guardado = StockFileCodec.decode(storage.read(StockBulkSync.STOCK_FILE)!!)
            assertEquals(2, guardado.items.size)
            val meta = StockFileCodec.decodeMeta(storage.read(StockBulkSync.META_FILE)!!)
            assertEquals(2, meta!!.rows)
            assertEquals("2026-10-28", meta.maxSnapshot)
            assertTrue(progreso.any { it.phase == StockSyncPhase.DOWNLOADING })
            assertTrue(progreso.any { it.phase == StockSyncPhase.SAVING })
            assertTrue(progreso.any { it.phase == StockSyncPhase.DONE })
        }
    }

    @Test
    fun siLasFilasCaenMasDeDiezPorCientoNoPisaElSnapshotAnterior() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(200, """{"storeCodes":["J503","J502"],"brands":[]}"""),
            response(
                200,
                """[{"storeCode":"J503","name":"JUMBO MAIPU"},{"storeCode":"J502","name":"JUMBO LA FLORIDA"}]""",
            ),
            response(200, inventarioDosLocales()),
        ).use { server ->
            val storage = InMemoryStockStorage()
            storage.files[StockBulkSync.STOCK_FILE] = "ANTERIOR"
            storage.files[StockBulkSync.META_FILE] = StockFileCodec.encodeMeta(
                StockMeta(rows = 100, locales = 10, maxSnapshot = "2026-10-27"),
            )
            val sync = sync(server, storage, listOf(entrada("J503"), entrada("J502")))

            val resultado = sync.run(StockSyncRequest())

            assertTrue(resultado is StockSyncOutcome.Blocked)
            assertEquals("ANTERIOR", storage.read(StockBulkSync.STOCK_FILE))
            assertEquals(100, StockFileCodec.decodeMeta(storage.read(StockBulkSync.META_FILE)!!)!!.rows)
        }
    }

    @Test
    fun losLocalesQueNoEstanEnKpiOneQuedanComoFallidos() = runBlocking {
        QueueHttpServer(
            response(200, """{"ok":true}""", "Set-Cookie: session=abc; Path=/"),
            response(200, """{"storeCodes":["J503"],"brands":[]}"""),
            response(200, """[{"storeCode":"J503","name":"JUMBO MAIPU"}]"""),
            response(200, inventarioDosLocales()),
        ).use { server ->
            val storage = InMemoryStockStorage()
            val sync = sync(server, storage, listOf(entrada("J503"), entrada("NO-EXISTE")))

            val resultado = sync.run(StockSyncRequest())

            assertTrue(resultado is StockSyncOutcome.Success)
            val meta = (resultado as StockSyncOutcome.Success).meta
            assertEquals(listOf("NO-EXISTE"), meta.failed)
            assertEquals(1, meta.locales)
        }
    }

    private fun sync(
        server: QueueHttpServer,
        storage: StockStorage,
        entradas: List<RouteEntryEntity>,
    ): StockBulkSync {
        val session = InMemoryKpiSessionStore()
        val client = KpiOneClient(
            session = session,
            baseUrl = server.url,
            http = KpiOneClient.defaultHttpClient(session),
            buildCredentials = { "qa@retailtrust.cl" to "secreto" },
        )
        val dao = mockk<RouteEntryDao>()
        coEvery { dao.getAllEntries() } returns entradas
        return StockBulkSync(client, dao, storage, RmCodeProvider { emptySet() })
    }

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

    private fun inventarioDosLocales(): String = """{
      "items":[
        {"store_code":"J503","store_name":"JUMBO MAIPU","brand_raw":"CASO Y CIA",
         "product_desc_raw":"Nectar Jumex","sku_b2b_raw":"192829","stock_units":94,
         "transit_units":0,"snapshot_date":"2026-10-28","quiebre":0,"days_of_inventory":10},
        {"store_code":"J502","store_name":"JUMBO LA FLORIDA","brand_raw":"CASO Y CIA",
         "product_desc_raw":"Nectar Jumex","sku_b2b_raw":"192829","stock_units":0,
         "transit_units":12,"snapshot_date":"2026-10-28","quiebre":1,"days_of_inventory":0}
      ],
      "pagination":{"total":2}
    }""".trimIndent()

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

class InMemoryStockStorage : StockStorage {
    val files = mutableMapOf<String, String>()

    override fun read(name: String): String? = files[name]
    override fun writeAtomic(name: String, text: String): Boolean {
        files[name] = text
        return true
    }
    override fun backup(name: String) {
        files[name]?.let { files[name + ".bak"] = it }
    }
    override fun exists(name: String): Boolean = files.containsKey(name)
    override fun delete(name: String) {
        files.remove(name)
    }
}
