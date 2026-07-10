package com.rutamercaderistas.services

import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuteroRepositoryTest {

    private lateinit var repository: RuteroRepository

    @Before
    fun setUp() {
        repository = RuteroRepository()
    }

    @Test
    fun `setEntries updates entriesFlow and stats`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
            localEntry("1", "Local A", "Cliente 2", lunes = true),
            localEntry("2", "Local B", "Cliente 1", lunes = true),
        )

        repository.setEntries(entries, "RUTA-1")

        val emitted = repository.entriesFlow.first()
        assertEquals(3, emitted.size)
        assertEquals("RUTA-1", repository.getActiveRuteroName())
        val stats = repository.getStats()
        assertEquals(2, stats.totalLocales)    // 2 distinct (codigo+local)
        assertEquals(2, stats.totalMarcas)     // 2 distinct clientes
        assertEquals(3, stats.visitasTotales)  // 3 total entries
    }

    @Test
    fun `getAllLocales size matches totalLocales`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
            localEntry("1", "Local A", "Cliente 2", lunes = true),
            localEntry("2", "Local B", "Cliente 3", martes = true),
        )
        repository.setEntries(entries, "RUTA-1")

        val allLocales = repository.getAllLocales()
        val stats = repository.getStats()

        assertEquals(stats.totalLocales, allLocales.size)
        assertEquals(2, allLocales.size)
    }

    @Test
    fun `getAllLocales groups brands per locale correctly`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
            localEntry("1", "Local A", "Cliente 2", lunes = true),
        )
        repository.setEntries(entries, "RUTA-1")

        val locales = repository.getAllLocales()
        assertEquals(1, locales.size)
        val local = locales.first()
        assertEquals(2, local.clientes.size)
        assertEquals("Cliente 1", local.clientes[0].nombre)
        assertEquals("Cliente 2", local.clientes[1].nombre)
    }

    @Test
    fun `getLocalesForDay filters by day`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
            localEntry("2", "Local B", "Cliente 2", martes = true),
            localEntry("3", "Local C", "Cliente 3", lunes = true),
        )
        repository.setEntries(entries, "RUTA-1")

        val mondayLocales = repository.getLocalesForDay(DiaSemana.LUNES)
        val tuesdayLocales = repository.getLocalesForDay(DiaSemana.MARTES)

        assertEquals(2, mondayLocales.size)
        assertEquals(1, tuesdayLocales.size)
    }

    @Test
    fun `hasAnyVisitOnDay returns true when entries exist for that day`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
        )
        repository.setEntries(entries, "RUTA-1")

        assertTrue(repository.hasAnyVisitOnDay(DiaSemana.LUNES))
    }

    @Test
    fun `clear resets all state`() = runTest {
        repository.setEntries(
            listOf(localEntry("1", "Local A", "Cliente 1")),
            "RUTA-1",
        )
        repository.clear()

        assertEquals(0, repository.getStats().totalLocales)
        assertEquals(0, repository.getStats().totalMarcas)
        assertEquals(0, repository.getStats().visitasTotales)
        assertNull(repository.getActiveRuteroName())
        assertTrue(repository.entriesFlow.first().isEmpty())
    }

    @Test
    fun `getActiveRuteroName returns null before setEntries`() {
        assertNull(repository.getActiveRuteroName())
    }

    @Test
    fun `getActiveRuteroName returns name after setEntries`() = runTest {
        repository.setEntries(
            listOf(localEntry("1", "Local A", "Cliente 1")),
            "EMU-2",
        )
        assertEquals("EMU-2", repository.getActiveRuteroName())
    }

    @Test
    fun `getAllLocales returns empty list when no entries`() = runTest {
        assertTrue(repository.getAllLocales().isEmpty())
    }

    @Test
    fun `getLocalesForDay returns empty list when no entries for that day`() = runTest {
        repository.setEntries(
            listOf(localEntry("1", "Local A", "Cliente 1", lunes = true)),
            "RUTA-1",
        )
        assertTrue(repository.getLocalesForDay(DiaSemana.DOMINGO).isEmpty())
    }

    @Test
    fun `stats compute correctly with duplicate brands`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente X", lunes = true),
            localEntry("1", "Local A", "Cliente X", martes = true),
            localEntry("2", "Local B", "Cliente Y", lunes = true),
        )
        repository.setEntries(entries, "RUTA-1")

        val stats = repository.getStats()
        assertEquals(2, stats.totalLocales)     // 2 distinct stores
        assertEquals(2, stats.totalMarcas)      // 2 distinct brands
        assertEquals(3, stats.visitasTotales)   // 3 total rows
    }

    @Test
    fun `getStats returns cached stats without recomputing`() = runTest {
        val entries = listOf(
            localEntry("1", "Local A", "Cliente 1", lunes = true),
        )
        repository.setEntries(entries, "RUTA-1")
        val first = repository.getStats()
        val second = repository.getStats()
        assertEquals(first, second)
    }

    // ── helpers ──────────────────────────────────────────

    private fun localEntry(
        codigo: String,
        local: String,
        cliente: String,
        lunes: Boolean = false,
        martes: Boolean = false,
        miercoles: Boolean = false,
        jueves: Boolean = false,
        viernes: Boolean = false,
        sabado: Boolean = false,
        domingo: Boolean = false,
    ) = EntradaRuta(
        reponedor = "",
        rutero = "RUTA-1",
        codigo = codigo,
        local = local,
        direccion = "",
        cliente = cliente,
        lunes = lunes,
        martes = martes,
        miercoles = miercoles,
        jueves = jueves,
        viernes = viernes,
        sabado = sabado,
        domingo = domingo,
    )
}
