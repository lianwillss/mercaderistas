package com.rutamercaderistas.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija la aritmética de la barra que se esconde al hacer scroll: el signo de
 * nested scroll (negativo = el contenido avanza), los umbrales con histéresis
 * y que la conexión observe sin consumir nada del gesto.
 */
class BottomBarAutoHideStateTest {

    private val source = NestedScrollSource.UserInput

    private fun state() = BottomBarAutoHideState(hideAfterPx = 40f, revealAfterPx = 12f)

    @Test
    fun `bajar no aparta la barra antes del umbral`() {
        val state = state()
        state.onScrollDelta(-20f)
        assertFalse(state.hidden)
        state.onScrollDelta(-19f)
        assertFalse(state.hidden)
        state.onScrollDelta(-1f)
        assertTrue(state.hidden)
    }

    @Test
    fun `subir la devuelve con menos recorrido`() {
        val state = state()
        state.onScrollDelta(-40f)
        assertTrue(state.hidden)
        state.onScrollDelta(5f)
        assertTrue(state.hidden)
        state.onScrollDelta(7f)
        assertFalse(state.hidden)
    }

    @Test
    fun `avanzar cancela lo que se habia retrocedido`() {
        val state = state()
        state.onScrollDelta(-40f)
        assertTrue(state.hidden)
        state.onScrollDelta(10f)   // casi vuelve...
        state.onScrollDelta(-10f)  // ...pero sigue bajando
        assertTrue(state.hidden)
        state.onScrollDelta(11f)
        assertTrue(state.hidden)
        state.onScrollDelta(1f)
        assertFalse(state.hidden)
    }

    @Test
    fun `la cuenta es neta entre micro correcciones`() {
        val state = state()
        state.onScrollDelta(-30f)
        state.onScrollDelta(10f)
        assertFalse(state.hidden)
        state.onScrollDelta(-20f)
        assertTrue(state.hidden)
    }

    @Test
    fun `show devuelve la barra y reinicia los umbrales`() {
        val state = state()
        state.onScrollDelta(-30f)
        state.show()
        assertFalse(state.hidden)
        // El avance anterior no cuenta: hace falta recorrer el umbral de nuevo.
        state.onScrollDelta(-20f)
        assertFalse(state.hidden)
    }

    @Test
    fun `la conexion no consume el gesto`() {
        val state = state()
        val consumed = state.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, -50f),
            available = Offset(0f, -10f),
            source = source,
        )
        assertEquals(Offset.Zero, consumed)
        assertTrue(state.hidden)

        val back = state.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 12f),
            available = Offset.Zero,
            source = source,
        )
        assertEquals(Offset.Zero, back)
        assertFalse(state.hidden)
    }

    @Test
    fun `el scroll horizontal no mueve la barra`() {
        val state = state()
        state.nestedScrollConnection.onPostScroll(
            consumed = Offset(-200f, 0f),
            available = Offset.Zero,
            source = source,
        )
        assertFalse(state.hidden)
    }
}
