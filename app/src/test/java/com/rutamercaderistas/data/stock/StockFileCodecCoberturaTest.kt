package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockFileCodecCoberturaTest {

    @Test
    fun laVentaSobreviveAlEncodeYDecode() {
        val datos = DemoStockData(
            items = listOf(
                StockItem(
                    storeCode = "J503",
                    storeName = "JUMBO MAIPU",
                    brand = "MIEL TRAPENSE",
                    product = "Miel 1 kg",
                    sku = "1125185",
                    stockUnits = 19.0,
                    transitUnits = null,
                    snapshotDate = "2026-10-30",
                    quiebre = false,
                    saleLastWeek = 1,
                    saleLast4w = 13,
                ),
            ),
        )

        val leido = StockFileCodec.decode(StockFileCodec.encode(datos)).items.first()

        assertEquals(1, leido.saleLastWeek)
        assertEquals(13, leido.saleLast4w)
        assertEquals(19.0 / (13.0 / 28.0), leido.coberturaDias!!, 0.0001)
    }

    @Test
    fun unArchivoViejoSinVentaSeLeeComoSinDato() {
        val v2 = """{"v":2,"items":[{"s":"J503","n":"JUMBO MAIPU","b":"SUK","p":"Ceregli","k":"1256098","u":20,"t":0,"d":"2026-10-28","q":0,"i":10}]}"""

        val item = StockFileCodec.decode(v2).items.first()

        assertEquals("1256098", item.sku)
        assertNull(item.saleLastWeek)
        assertNull(item.saleLast4w)
        assertNull(item.coberturaDias)
        assertEquals(CoberturaTipo.SinDato, coberturaDe(item))
    }
}
