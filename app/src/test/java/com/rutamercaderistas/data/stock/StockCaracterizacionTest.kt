package com.rutamercaderistas.data.stock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Caracterización de la semántica de filtros que el rediseño de Stock no debe
 * cambiar: Local, Ruta y Marca son dimensiones que se combinan con AND; Local
 * y Ruta son caminos alternativos (elegir uno limpia el otro) y conservan
 * Marca y Producto; los conteos de las tarjetas salen de la base sin filtrar
 * por estado.
 */
class StockCaracterizacionTest {

    private val catalogo = StockCatalog(
        locales = listOf(
            StockLocal("AL3083", "JUMBO NUNOA", listOf("EMU-3")),
            StockLocal("HI45", "LIDER MAIPU", listOf("DMU-5")),
        ),
        marcas = listOf("CASO Y CIA", "CUK", "SUK"),
    )

    private val repo = DemoStockRepository(
        DemoStockData(
            items = listOf(
                StockItem("AL3083", "JUMBO NUNOA", "CASO Y CIA", "Nectar Jumex", "192829", 50.0, null, "2026-10-28", false),
                StockItem("AL3083", "JUMBO NUNOA", "CUK", "Suspirito", "631111", 0.0, 2.0, "2026-10-28", true),
                StockItem("AL3083", "JUMBO NUNOA", "CUK", "Zumo", "631112", -2.0, null, "2026-10-28", false),
                StockItem("HI45", "LIDER MAIPU", "CASO Y CIA", "Nectar Jumex", "192829", 3.0, null, "2026-10-28", false),
                StockItem("HI45", "LIDER MAIPU", "SUK", "Falafel", "555001", 12.0, null, "2026-10-28", false),
            ),
            locales = catalogo.locales,
            marcas = catalogo.marcas,
        ),
    )

    private suspend fun claves(filtros: StockFilters): List<String> =
        repo.search(filtros).items.map { it.storeCode + "|" + it.sku }.sorted()

    @Test
    fun `local y marca combinan con AND`() = runBlocking {
        assertEquals(listOf("AL3083|631111", "AL3083|631112"), claves(StockFilters(local = "AL3083", marca = "CUK")))
    }

    @Test
    fun `local y producto combinan con AND`() = runBlocking {
        assertEquals(listOf("AL3083|192829"), claves(StockFilters(local = "AL3083", sku = "jumex")))
        assertEquals(listOf("HI45|555001"), claves(StockFilters(local = "HI45", sku = "falafel")))
    }

    @Test
    fun `ruta y marca combinan con AND`() = runBlocking {
        assertEquals(listOf("AL3083|192829"), claves(StockFilters(ruta = "EMU-3", marca = "CASO Y CIA")))
        assertEquals(listOf("HI45|555001"), claves(StockFilters(ruta = "DMU-5", marca = "SUK")))
    }

    @Test
    fun `ruta y producto combinan con AND`() = runBlocking {
        assertEquals(listOf("AL3083|192829"), claves(StockFilters(ruta = "EMU-3", sku = "jumex")))
        // El mismo SKU existe en las dos rutas: la ruta acota el local.
        assertEquals(listOf("HI45|192829"), claves(StockFilters(ruta = "DMU-5", sku = "192829")))
    }

    @Test
    fun `local y ruta son alternativos y no coexisten`() {
        val rutaPrimero = StockFilters().conRuta("EMU-3").conLocal("AL3083")
        assertEquals("AL3083", rutaPrimero.local)
        assertEquals("", rutaPrimero.ruta)

        val localPrimero = StockFilters().conLocal("AL3083").conRuta("EMU-3")
        assertEquals("EMU-3", localPrimero.ruta)
        assertEquals("", localPrimero.local)
    }

    @Test
    fun `elegir local o ruta conserva marca y producto`() {
        val base = StockFilters(marca = "CUK", sku = "jumex")
        assertEquals("CUK", base.conLocal("AL3083").marca)
        assertEquals("jumex", base.conLocal("AL3083").sku)
        assertEquals("CUK", base.conRuta("EMU-3").marca)
        assertEquals("jumex", base.conRuta("EMU-3").sku)
    }

    @Test
    fun `elegir local o ruta pone Disponible como estado por defecto`() {
        assertEquals(EstadoTipo.Disponible, StockFilters().conLocal("AL3083").estado)
        assertEquals(EstadoTipo.Disponible, StockFilters().conRuta("EMU-3").estado)
        // Un estado elegido a mano no se pisa.
        assertEquals(
            EstadoTipo.Negativo,
            StockFilters(estado = EstadoTipo.Negativo).conLocal("AL3083").estado,
        )
    }

    @Test
    fun `los conteos de tarjetas salen de la base sin filtrar por estado`() = runBlocking {
        val base = repo.search(StockFilters(local = "AL3083"))
        assertEquals(3, base.items.size)
        assertEquals(1, base.conteoDisponible)
        assertEquals(1, base.conteoQuiebre)
        assertEquals(1, base.conteoNegativos)

        val soloQuiebres = repo.search(StockFilters(local = "AL3083", estado = EstadoTipo.Quiebre))
        assertEquals(1, soloQuiebres.items.size)
        assertEquals("631111", soloQuiebres.items.single().sku)
        // La tarjeta sigue mostrando el total del corte aunque esté seleccionada.
        assertEquals(1, soloQuiebres.conteoDisponible)
        assertEquals(1, soloQuiebres.conteoQuiebre)
        assertEquals(1, soloQuiebres.conteoNegativos)
    }

    @Test
    fun `el orden no cambia el conjunto de filas`() = runBlocking {
        val base = StockFilters(local = "AL3083")
        val az = repo.search(base.copy(orden = StockSort.PRODUCTO_AZ)).items.map { it.sku }
        val gravedad = repo.search(base.copy(orden = StockSort.GRAVEDAD)).items.map { it.sku }
        assertEquals(az.sorted(), gravedad.sorted())
        assertEquals(
            listOf("Nectar Jumex", "Suspirito", "Zumo"),
            repo.search(base.copy(orden = StockSort.PRODUCTO_AZ)).items.map { it.product },
        )
        // La gravedad manda el negativo primero.
        assertEquals("631112", gravedad.first())
    }

    @Test
    fun `el modo de agrupacion no cambia las filas`() = runBlocking {
        val porLocal = repo.search(StockFilters(local = "AL3083"))
        val porMarca = repo.search(StockFilters(local = "AL3083", modo = StockMode.POR_MARCA))
        assertEquals(
            porLocal.items.map { it.sku }.sorted(),
            porMarca.items.map { it.sku }.sorted(),
        )
    }

    @Test
    fun `sin criterio no se busca`() {
        // El repo no se auto-limita: el ViewModel es el que bloquea la
        // búsqueda sin criterio (hayCriterio).
        assertTrue(!StockFilters().hayCriterio)
        assertTrue(!StockFilters(local = "A").hayCriterio)
        assertTrue(!StockFilters(sku = "1").hayCriterio)
        assertTrue(StockFilters(local = "AL3083").hayCriterio)
    }

    @Test
    fun `activos cuenta los filtros encendidos`() {
        assertEquals(0, StockFilters().activos)
        assertEquals(1, StockFilters(local = "AL3083").activos)
        assertEquals(2, StockFilters(local = "AL3083", estado = EstadoTipo.Quiebre).activos)
        assertEquals(3, StockFilters(ruta = "EMU-3", soloNegativos = true, sku = "192829").activos)
    }

    @Test
    fun `limpiar conserva orden y modo`() {
        val limpio = StockFilters(
            local = "AL3083",
            marca = "CUK",
            sku = "192829",
            estado = EstadoTipo.Quiebre,
            cobertura = CoberturaTipo.Critico,
            validacion = ValidacionNegativo.CONFIRMADO,
            soloQuiebre = true,
            soloNegativos = true,
            orden = StockSort.GRAVEDAD,
            modo = StockMode.POR_MARCA,
        ).limpiar()

        assertEquals(
            StockFilters(orden = StockSort.GRAVEDAD, modo = StockMode.POR_MARCA),
            limpio,
        )
        assertEquals(0, limpio.activos)
    }
}
