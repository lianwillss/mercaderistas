package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockScopeTest {

    @Test
    fun `el alcance por defecto son mis rutas y una ruta lo limita`() {
        val alcance = StockAlcance(StockScope.MIS_RUTAS, setOf("EMU-3"))
        assertTrue(alcance.limita)
        assertEquals("rutas:EMU3", alcance.claveSnapshot)

        val rm = StockAlcance(StockScope.RM_COMPLETA)
        assertFalse(rm.limita)
        assertEquals(StockAlcance.SCOPE_RM, rm.claveSnapshot)

        val sinRutero = StockAlcance(StockScope.MIS_RUTAS)
        assertFalse(sinRutero.limita)
        assertEquals(StockAlcance.SCOPE_RM, sinRutero.claveSnapshot)
    }

    @Test
    fun `la clave de rutas normaliza y ordena para comparar snapshots`() {
        assertEquals(
            "rutas:DMU5,EMU3",
            claveAlcanceRutas(listOf(" emu 3 ", "EMU-3", "dmu-5", "")),
        )
    }

    @Test
    fun `la persona suma las rutas que comparten sus gestores`() {
        val entries = listOf(
            entrada("EMU-1", gestores = "Ana / Pedro"),
            entrada("EMU-2", gestores = "Pedro"),
            entrada("DMU-5", gestores = "Otra"),
        )
        assertEquals(setOf("EMU-1", "EMU-2"), rutasDeLaPersona(entries, "EMU-1"))
    }

    @Test
    fun `sin gestores se usa el supervisor como respaldo`() {
        val entries = listOf(
            entrada("EMU-1", gestores = "", supervisor = "Carla"),
            entrada("EMU-2", gestores = "", supervisor = "carla"),
            entrada("DMU-5", gestores = "", supervisor = "Otro"),
        )
        assertEquals(setOf("EMU-1", "EMU-2"), rutasDeLaPersona(entries, "EMU-1"))
    }

    @Test
    fun `sin rutero activo no hay rutas que acoten`() {
        assertTrue(rutasDeLaPersona(emptyList(), "EMU-1").isEmpty())
        assertTrue(rutasDeLaPersona(listOf(entrada("EMU-1")), null).isEmpty())
    }

    private fun entrada(
        rutero: String,
        gestores: String = "Ana",
        supervisor: String = "Carla",
    ) = RouteEntryEntity(
        rutero = rutero,
        codigo = "J" + rutero.hashCode().toString().takeLast(3),
        local = "LOCAL",
        direccion = "",
        cliente = "",
        cadena = "",
        formato = "",
        region = "RM",
        comuna = "",
        supervisor = supervisor,
        gestores = gestores,
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
