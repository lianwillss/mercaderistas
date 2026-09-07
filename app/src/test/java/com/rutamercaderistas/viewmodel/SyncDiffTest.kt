package com.rutamercaderistas.viewmodel

import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import org.junit.Assert.*
import org.junit.Test

class SyncDiffTest {

    private fun entry(
        codigo: String,
        local: String,
        rutero: String = "AMU-1",
        direccion: String = "Dir 123",
        cliente: String = "CUK",
        lunes: Boolean = true,
        martes: Boolean = false,
    ) = EntradaRuta(
        reponedor = "",
        rutero = rutero,
        codigo = codigo,
        local = local,
        direccion = direccion,
        cliente = cliente,
        lunes = lunes,
        martes = martes,
    )

    @Test
    fun emptyDiffWhenSame() {
        val entries = listOf(entry("J1", "Local A"), entry("J2", "Local B"))
        val changes = diffPlanilla(entries, entries, "AMU-1", DiaSemana.LUNES)
        assertTrue(changes.isEmpty)
    }

    @Test
    fun detectsAddedAndRemoved() {
        val old = listOf(entry("J1", "Local A"))
        val new = listOf(entry("J2", "Local B"))
        val changes = diffPlanilla(old, new)
        assertEquals(listOf("Local B"), changes.added)
        assertEquals(listOf("Local A"), changes.removed)
        assertTrue(changes.moved.isEmpty())
    }

    @Test
    fun detectsDayMoveWithRealDays() {
        val old = listOf(entry("J1", "Local A", lunes = true, martes = false))
        val new = listOf(entry("J1", "Local A", lunes = false, martes = true))
        val changes = diffPlanilla(old, new)
        assertEquals(1, changes.moved.size)
        assertEquals("LUN", changes.moved[0].fromDays)
        assertEquals("MAR", changes.moved[0].toDays)
        assertEquals("AMU-1", changes.moved[0].fromRoute)
    }

    @Test
    fun detectsRouteChange() {
        val old = listOf(entry("J1", "Local A", rutero = "AMU-1"))
        val new = listOf(entry("J1", "Local A", rutero = "DMU-6"))
        val changes = diffPlanilla(old, new)
        assertEquals(1, changes.moved.size)
        assertEquals("AMU-1", changes.moved[0].fromRoute)
        assertEquals("DMU-6", changes.moved[0].toRoute)
    }

    @Test
    fun detectsAddressChange() {
        val old = listOf(entry("J1", "Local A", direccion = "Dir vieja 1"))
        val new = listOf(entry("J1", "Local A", direccion = "Dir nueva 2"))
        val changes = diffPlanilla(old, new)
        assertEquals(1, changes.changedAddress.size)
        assertEquals("Dir vieja 1", changes.changedAddress[0].oldAddress)
        assertEquals("Dir nueva 2", changes.changedAddress[0].newAddress)
    }

    @Test
    fun detectsBrandChanges() {
        val old = listOf(entry("J1", "Local A", cliente = "CUK"))
        val new = listOf(
            entry("J1", "Local A", cliente = "CUK"),
            entry("J1", "Local A", cliente = "SUK"),
        )
        val changes = diffPlanilla(old, new)
        assertEquals(1, changes.changedBrands.size)
        assertEquals(listOf("SUK"), changes.changedBrands[0].added)
        assertTrue(changes.changedBrands[0].removed.isEmpty())
    }

    @Test
    fun affectsTodayOnRemove() {
        val old = listOf(entry("J1", "Local A", rutero = "AMU-1", lunes = true))
        val changes = diffPlanilla(old, emptyList(), "AMU-1", DiaSemana.LUNES)
        assertEquals(listOf("Local A"), changes.affectsToday)
    }

    @Test
    fun noAffectsTodayOnOtherDay() {
        val old = listOf(entry("J1", "Local A", rutero = "AMU-1", lunes = true))
        val changes = diffPlanilla(old, emptyList(), "AMU-1", DiaSemana.MARTES)
        assertTrue(changes.affectsToday.isEmpty())
        assertEquals(listOf("Local A"), changes.removed)
    }

    @Test
    fun affectsTodayOnMoveOut() {
        val old = listOf(entry("J1", "Local A", rutero = "AMU-1", lunes = true))
        val new = listOf(entry("J1", "Local A", rutero = "AMU-1", lunes = false, martes = true))
        val changes = diffPlanilla(old, new, "AMU-1", DiaSemana.LUNES)
        assertEquals(listOf("Local A"), changes.affectsToday)
    }
}
