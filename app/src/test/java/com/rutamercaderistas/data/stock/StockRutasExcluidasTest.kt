package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PITUTO y PROPAL están dados de baja del módulo Stock: no aparecen en el
 * catálogo, no resuelven consulta (ni por ruta ni por local) y sus locales
 * quedan fuera del selector, también los compartidos con otra ruta.
 */
class StockRutasExcluidasTest {

    private fun item(local: String) = StockItem(
        local, "LOCAL $local", "CASO Y CIA", "Producto", "SKU$local",
        10.0, 0.0, "2026-11-21", false,
    )

    private val data = DemoStockData(
        locales = listOf(
            StockLocal("J503", "JUMBO MAIPU", listOf("EMU-3")),
            StockLocal("AL3071", "ALVI PUENTE ALTO", listOf("PITUTO")),
            StockLocal("PR100", "PROPAL CENTRO", listOf("PROPAL")),
            StockLocal("SH1", "COMPARTIDO", listOf("PITUTO", "EMU-3")),
        ),
        marcas = listOf("CASO Y CIA"),
        items = listOf(item("J503"), item("AL3071"), item("PR100"), item("SH1")),
    )
    private val repo = DemoStockRepository(data)

    @Test
    fun `catalog sin rutas ni locales de PITUTO y PROPAL`() = runBlocking {
        val catalogo = repo.catalog()
        assertFalse(catalogo.rutas.contains("PITUTO"))
        assertFalse(catalogo.rutas.contains("PROPAL"))
        assertEquals(listOf("J503", "SH1"), catalogo.locales.map { it.code })
        // El compartido con EMU-3 se mantiene: no es exclusivo de las rutas
        // dadas de baja y los supervisores lo ven por su ruta.
        assertFalse(catalogo.marcasPorRuta.keys.any { it == "PITUTO" })
        assertFalse(catalogo.marcasPorRuta.keys.any { it == "PROPAL" })
        assertTrue(catalogo.rutas.contains("EMU-3"))
        assertTrue(catalogo.marcasPara("PITUTO").isEmpty())
        assertTrue(catalogo.marcasPara("PROPAL").isEmpty())
        assertTrue(catalogo.marcasPara("EMU-3").isNotEmpty())
    }

    @Test
    fun `buscar por la ruta o un local excluido da vacio`() = runBlocking {
        assertTrue(repo.search(StockFilters(ruta = "PITUTO")).items.isEmpty())
        assertTrue(repo.search(StockFilters(ruta = "PROPAL")).items.isEmpty())
        assertTrue(repo.search(StockFilters(local = "AL3071")).items.isEmpty())
        // El compartido con EMU-3 sigue visible por su ruta de la RM.
        assertEquals(1, repo.search(StockFilters(local = "SH1")).items.size)
        assertEquals(1, repo.search(StockFilters(local = "J503")).items.size)
        // CASO Y CIA está en J503 y en el compartido SH1.
        assertEquals(2, repo.search(StockFilters(marca = "CASO Y CIA")).items.size)
    }

    @Test
    fun `codigosDeRuta no resuelve rutas dadas de baja`() {
        val entradas = listOf(entrada("AL3071", "PITUTO"), entrada("J503", "EMU-3"))
        assertTrue(codigosDeRuta(entradas, "PITUTO").isEmpty())
        assertTrue(codigosDeRuta(entradas, "pituto").isEmpty())
        assertEquals(listOf("J503"), codigosDeRuta(entradas, "EMU-3"))

        val locales = listOf(
            StockLocal("AL3071", "ALVI PUENTE ALTO", listOf("PITUTO")),
            StockLocal("J503", "JUMBO MAIPU", listOf("EMU-3")),
        )
        assertTrue(codigosDeRuta(locales, "PROPAL").isEmpty())
        assertEquals(setOf("J503"), codigosDeRuta(locales, "EMU-3"))
    }

    @Test
    fun `el asset real queda sin PITUTO ni PROPAL`() = runBlocking {
        val archivo = listOf(
            java.io.File("src/main/assets/stock/stock_demo.json"),
            java.io.File("app/src/main/assets/stock/stock_demo.json"),
        ).firstOrNull { it.exists() }
        org.junit.Assume.assumeTrue("asset no visible desde el test", archivo != null)

        val catalogo = DemoStockRepository(StockDemoData.parse(archivo!!.readText())).catalog()
        assertFalse(catalogo.rutas.any { it.equals("PITUTO", true) || it.equals("PROPAL", true) })
        assertTrue(catalogo.locales.none { localExcluido(it.rutas) })
        // 296 menos los 88 exclusivos de PITUTO/PROPAL: los 41 compartidos
        // siguen por su ruta de la RM y ninguna ruta queda vacía.
        assertEquals(208, catalogo.locales.size)
        val br14 = catalogo.rutas.firstOrNull { it.startsWith("BR-14") }
        assertNotNull(br14)
        assertTrue(catalogo.marcasPorRuta.getValue(normalizarRuta(br14!!)).isNotEmpty())
    }

    private fun entrada(codigo: String, rutero: String) = RouteEntryEntity(
        rutero = rutero,
        codigo = codigo,
        local = "LOCAL $codigo",
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
}
