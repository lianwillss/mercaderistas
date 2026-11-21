package com.rutamercaderistas.data.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking de las sugerencias de los combos: lo más probable primero, total de
 * coincidencias y tolerancia a errores de tipeo. Así el combo no esconde una
 * opción por orden alfabético ni muestra cosas que la búsqueda no encuentra.
 */
class StockSuggestionsTest {

    private val locales = listOf(
        StockLocal("J503", "J503-JUMBO MAIPU", listOf("EMU-3")),
        StockLocal("J501", "J501-JUMBO BILBAO", listOf("EMU-3")),
        StockLocal("AL3071", "ALVI PUENTE ALTO", listOf("PITUTO")),
        StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
    )
    private val opciones = opcionesDeLocales(locales)

    @Test
    fun `el codigo exacto va primero`() {
        assertEquals("J503", sugerenciasStock("J503", opciones).items.first().valor)
    }

    @Test
    fun `el prefijo de codigo va antes que cualquier nombre`() {
        val resultado = sugerenciasStock("J50", opciones)

        assertEquals(listOf("J501", "J503"), resultado.items.take(2).map { it.valor })
        assertEquals(2, resultado.total)
    }

    @Test
    fun `nombre compacto con o sin espacios encuentra el mismo local`() {
        assertEquals("J503", sugerenciasStock("jumbomaipu", opciones).items.first().valor)
        assertEquals("J503", sugerenciasStock("JUMBO MAIPU", opciones).items.first().valor)
        assertEquals("J503", sugerenciasStock("jumbo-maipu", opciones).items.first().valor)
    }

    @Test
    fun `el total cuenta todas las coincidencias aunque se muestren menos`() {
        val resultado = sugerenciasStock("jumbo", opciones, limit = 1)

        assertEquals(1, resultado.items.size)
        assertEquals(2, resultado.total)
    }

    @Test
    fun `tolera errores de tipeo en las sugerencias`() {
        val marcas = opcionesDeMarcas(listOf("CASO Y CIA", "SUK", "CORRALES DEL SUR"))

        assertEquals("CASO Y CIA", sugerenciasStock("casa y cia", marcas).items.first().valor)
        assertEquals(
            "CORRALES DEL SUR",
            sugerenciasStock("corralez del sur", marcas).items.first().valor,
        )
        // Sin espacios también (la búsqueda ya compara compacto).
        assertEquals(
            "THE POWER OF FOOD",
            sugerenciasStock(
                "thepoweroffood",
                opcionesDeMarcas(listOf("THE POWER OF FOOD")),
            ).items.first().valor,
        )
    }

    @Test
    fun `el nombre corto quita el codigo del inicio`() {
        assertEquals("JUMBO MAIPU", locales[0].nombreCorto)
        assertEquals("ALVI PUENTE ALTO", locales[2].nombreCorto)
    }

    @Test
    fun `sin texto devuelve el orden recibido y el total completo`() {
        val resultado = sugerenciasStock("", opciones, limit = 2)

        assertEquals(listOf("J503", "J501"), resultado.items.map { it.valor })
        assertEquals(4, resultado.total)
        assertTrue(resultado.items.isNotEmpty())
    }

    @Test
    fun `el desempate por uso ordena dentro del mismo tier`() {
        val marcas = opcionesDeMarcas(listOf("CASO Y CIA", "CUK"))
        // "c" es prefijo de ambas: mismo tier, el mas usado gana.
        val conUsos = sugerenciasStock("c", marcas, usos = mapOf("CUK" to 10))
        assertEquals("CUK", conUsos.items.first().valor)
        // Sin usos, el orden alfabetico devuelve "CASO Y CIA" primero.
        val sinUsos = sugerenciasStock("c", marcas)
        assertEquals("CASO Y CIA", sinUsos.items.first().valor)
    }

    @Test
    fun `el desempate por uso no pisa la relevancia`() {
        val marcas = opcionesDeMarcas(listOf("CUK", "CUKY"))
        // "cuk" es exacto para CUK (tier 0) y prefijo para CUKY (tier 2):
        // aunque CUKY tenga mas usos, el exacto sigue primero.
        val resultado = sugerenciasStock("cuk", marcas, usos = mapOf("CUKY" to 100))
        assertEquals("CUK", resultado.items.first().valor)
    }

    @Test
    fun `el prefijo encuentra la marca sin importar mayusculas ni tildes`() {
        val marcas = opcionesDeMarcas(listOf("KHATAR", "CASO Y CIA"))
        assertEquals(listOf("KHATAR"), sugerenciasStock("kh", marcas).items.map { it.valor })
        assertEquals(listOf("KHATAR"), sugerenciasStock("Kh", marcas).items.map { it.valor })
        assertEquals(listOf("AGUILA"), sugerenciasStock("águila", opcionesDeMarcas(listOf("AGUILA"))).items.map { it.valor })
    }

    @Test
    fun `la busqueda de marca tambien es por contiene`() {
        val marcas = opcionesDeMarcas(listOf("DISTRIBUIDORA KHATAR SA"))
        assertEquals(
            listOf("DISTRIBUIDORA KHATAR SA"),
            sugerenciasStock("khatar", marcas).items.map { it.valor },
        )
    }

    @Test
    fun `una marca que solo esta en el listado tambien se ofrece`() {
        val catalogo = StockCatalog(
            marcas = listOf("CASO Y CIA"),
            rutas = listOf("EMU-3"),
            marcasPorRuta = mapOf("EMU-3" to listOf("CASO Y CIA")),
        )
        // El snapshot viejo no trae KHATAR, pero el listado en vivo sí.
        assertEquals(
            listOf("CASO Y CIA", "KHATAR"),
            marcasParaSelector(catalogo, "", listOf("KHATAR", "CASO Y CIA")),
        )
        // Con ruta se respetan las marcas de esa ruta y se suman las visibles.
        assertEquals(
            listOf("CASO Y CIA", "KHATAR"),
            marcasParaSelector(catalogo, "EMU-3", listOf("KHATAR")),
        )
        // Una ruta dada de baja no ofrece nada, ni con marcas visibles.
        assertTrue(marcasParaSelector(catalogo, "PITUTO", listOf("KHATAR")).isEmpty())
        // Las dadas de baja nunca se ofrecen.
        assertFalse(marcasParaSelector(catalogo, "", listOf("BERRYSUR")).contains("BERRYSUR"))
    }
}
