package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDemoDataTest {

    @Test
    fun `parsea el formato compacto exportado de KPI One`() {
        val json = """
            {
              "locales":[{"c":"J503","n":"J503-JUMBO MAIPU"},{"c":"J502","n":"J502-JUMBO KENNEDY"}],
              "marcas":["CUK","SUK"],
              "items":[
                {"s":"J503","n":"J503-JUMBO MAIPU","b":"SUK","p":"CEREGLI SUK LIGTH","k":"1256098","u":5,"t":null,"d":"2026-10-28","q":null,"i":null},
                {"s":"J502","n":"J502-JUMBO KENNEDY","b":"CUK","p":"AGUA TONICA","k":"901122","u":0,"t":12,"d":"2026-10-27","q":1,"i":4}
              ]
            }
        """.trimIndent()

        val items = StockDemoData.parse(json).items

        assertEquals(2, items.size)
        assertEquals("J503", items[0].storeCode)
        assertEquals(5.0, items[0].stockUnits, 0.001)
        assertTrue(!items[0].quiebre)
        assertNull(items[0].transitUnits)

        assertEquals(0.0, items[1].stockUnits, 0.001)
        assertEquals(12.0, items[1].transitUnits!!, 0.001)
        assertTrue(items[1].quiebre)
        assertEquals(4, items[1].daysOfInventory)
    }

    @Test
    fun `el asset real de la Region Metropolitana parsea completo`() {
        val archivo = listOf(
            java.io.File("src/main/assets/stock/stock_demo.json"),
            java.io.File("app/src/main/assets/stock/stock_demo.json"),
        ).firstOrNull { it.exists() }
        org.junit.Assume.assumeTrue("asset no visible desde el test", archivo != null)

        val datos = StockDemoData.parse(archivo!!.readText())
        val items = datos.items

        assertTrue("esperaba filas", items.size > 1000)
        assertTrue("esperaba varios locales con stock", items.map { it.storeCode }.distinct().size > 100)
        assertTrue("esperaba catálogo", datos.locales.size > 100)
        assertTrue("esperaba marcas", datos.marcas.size >= 10)
        assertTrue(datos.locales.all { it.name.isNotBlank() })
        assertTrue(items.all { it.storeCode.isNotBlank() && it.product.isNotBlank() })
        assertTrue(items.all { it.snapshotDate.isNotBlank() })
    }

    @Test
    fun `presenta decimales y enteros sin perder formato`() {
        val entero = StockItem("J503", "J503-JUMBO MAIPU", "SUK", "P", "1", 5.0, null, "2026-10-28", false)
        assertEquals("5", entero.stockLabel)
        assertEquals("—", entero.transitLabel)

        val decimal = StockItem("J503", "J503-JUMBO MAIPU", "SUK", "P", "1", 6973.39, 0.0, "2026-10-28", false)
        assertTrue(decimal.stockLabel.contains("6.973"))
    }
}
