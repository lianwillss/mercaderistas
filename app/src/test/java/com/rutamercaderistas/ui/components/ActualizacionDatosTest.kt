package com.rutamercaderistas.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ActualizacionDatosTest {

    private val hoy = LocalDate.of(2026, 10, 29)

    @Test
    fun `hoy pinta verde y muestra la hora`() {
        val datos = ActualizacionDatos(hoy, LocalTime.of(7, 21))
        assertEquals(FrescuraDatos.Hoy, datos.frescura(hoy))
        assertEquals("Actualizado hoy 07:21", datos.etiqueta(hoy))
    }

    @Test
    fun `ayer pinta ámbar y conserva la hora`() {
        val datos = ActualizacionDatos(hoy.minusDays(1), LocalTime.of(18, 5))
        assertEquals(FrescuraDatos.Ayer, datos.frescura(hoy))
        assertEquals("Actualizado ayer 18:05", datos.etiqueta(hoy))
    }

    @Test
    fun `dos días o más pinta rojo`() {
        val dos = ActualizacionDatos(hoy.minusDays(2), LocalTime.NOON)
        assertEquals(FrescuraDatos.Atrasado, dos.frescura(hoy))
        assertEquals("Actualizado hace 2 días", dos.etiqueta(hoy))

        val cinco = ActualizacionDatos(hoy.minusDays(5), null)
        assertEquals(FrescuraDatos.Atrasado, cinco.frescura(hoy))
        assertEquals("Actualizado hace 5 días", cinco.etiqueta(hoy))
    }

    @Test
    fun `sin fecha no inventa frescura`() {
        val datos = ActualizacionDatos(null, null)
        assertEquals(FrescuraDatos.SinFecha, datos.frescura(hoy))
        assertEquals("Sin fecha de datos", datos.etiqueta(hoy))
    }

    @Test
    fun `un reloj adelantado se lee como hoy`() {
        val datos = ActualizacionDatos(hoy.plusDays(1), LocalTime.of(9, 0))
        assertEquals(FrescuraDatos.Hoy, datos.frescura(hoy))
    }

    @Test
    fun `fecha corta para el mensaje de error`() {
        assertEquals(
            "29-10 07:21",
            ActualizacionDatos(hoy, LocalTime.of(7, 21)).fechaHoraCorta(hoy),
        )
        assertEquals("28-10", ActualizacionDatos(hoy.minusDays(1), null).fechaHoraCorta(hoy))
        // Otro año sí lleva año para no confundir snapshots viejos.
        assertEquals(
            "29-10-2025",
            ActualizacionDatos(LocalDate.of(2025, 10, 29), null).fechaHoraCorta(hoy),
        )
        assertEquals("sin fecha", ActualizacionDatos(null, null).fechaHoraCorta(hoy))
    }

    @Test
    fun `desdeIso toma fecha y hora generada`() {
        val instante = Instant.parse("2026-10-29T12:21:00Z")
        val local = instante.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val datos = ActualizacionDatos.desdeIso(local.toLocalDate().toString(), instante.toString())
        assertEquals(local.toLocalDate(), datos?.fecha)
        assertEquals(local.toLocalTime(), datos?.hora)
    }

    @Test
    fun `desdeIso descarta la hora si no corresponde al snapshot`() {
        val instante = Instant.parse("2026-10-30T12:00:00Z")
        val local = instante.atZone(ZoneId.systemDefault()).toLocalDateTime()
        // Snapshot del día anterior: la hora del archivo no es su hora.
        val datos = ActualizacionDatos.desdeIso(
            local.toLocalDate().minusDays(1).toString(),
            instante.toString(),
        )
        assertEquals(local.toLocalDate().minusDays(1), datos?.fecha)
        assertNull(datos?.hora)
    }

    @Test
    fun `desdeIso sin datos devuelve null`() {
        assertNull(ActualizacionDatos.desdeIso(null, null))
        assertNull(ActualizacionDatos.desdeIso("", ""))
        assertNull(ActualizacionDatos.desdeIso("no-es-fecha", "no-es-instant"))
    }
}
