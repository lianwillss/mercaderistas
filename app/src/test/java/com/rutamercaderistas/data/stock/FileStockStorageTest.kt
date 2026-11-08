package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStockStorageTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun escribeConReemplazoAtomicoYGuardaBackup() {
        val storage = FileStockStorage(temporal.newFolder("stock"))

        assertTrue(storage.writeAtomic("stock.json", "PRIMERO"))
        assertEquals("PRIMERO", storage.read("stock.json"))
        assertTrue(storage.exists("stock.json"))

        storage.backup("stock.json")
        assertTrue(storage.writeAtomic("stock.json", "SEGUNDO"))

        assertEquals("SEGUNDO", storage.read("stock.json"))
        assertEquals("PRIMERO", storage.read("stock.json.bak"))

        storage.delete("stock.json")
        assertFalse(storage.exists("stock.json"))
    }

    @Test
    fun elCodecConservaFilasLocalesRutasYMetadatos() {
        val datos = DemoStockData(
            items = listOf(
                StockItem(
                    storeCode = "J503",
                    storeName = "JUMBO MAIPU",
                    brand = "CASO Y CIA",
                    product = "Nectar Jumex",
                    sku = "192829",
                    stockUnits = 94.0,
                    transitUnits = 0.0,
                    snapshotDate = "2026-10-28",
                    quiebre = false,
                    daysOfInventory = 10,
                ),
            ),
            locales = listOf(StockLocal("J503", "JUMBO MAIPU", listOf("EMU-3"))),
            marcas = listOf("CASO Y CIA"),
        )

        val texto = StockFileCodec.encode(datos, "2026-10-29T00:00:00Z", "kpi-one")
        val decodificado = StockFileCodec.decode(texto)

        assertEquals(1, decodificado.items.size)
        assertEquals("J503", decodificado.items.first().storeCode)
        assertEquals(94.0, decodificado.items.first().stockUnits, 0.0)
        assertEquals(listOf("EMU-3"), decodificado.locales.first().rutas)
        assertEquals(listOf("CASO Y CIA"), decodificado.marcas)

        val meta = StockMeta(rows = 1, locales = 1, maxSnapshot = "2026-10-28")
        val metaLeida = StockFileCodec.decodeMeta(StockFileCodec.encodeMeta(meta))
        assertEquals(1, metaLeida!!.rows)
        assertEquals("2026-10-28", metaLeida.maxSnapshot)
    }
}
