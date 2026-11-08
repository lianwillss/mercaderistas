package com.rutamercaderistas.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El header no usa animateDpAsState: el alto sigue al dedo y cada píxel que
 * se encoge se descuenta del scroll. Estos tests fijan esa aritmética para
 * que la grilla no salte al colapsar o expandir.
 */
class HeaderCollapseStateTest {

    private val source = NestedScrollSource.UserInput

    @Test
    fun `bajar colapsa y consume el scroll`() {
        val state = HeaderCollapseState(expandedPx = 120f, collapsedPx = 60f)
        val consumed = state.nestedScrollConnection.onPreScroll(Offset(0f, -25f), source)
        assertEquals(-25f, consumed.y, 0.001f)
        assertEquals(95f, state.heightPx, 0.001f)
        assertEquals(25f / 60f, state.fraction, 0.001f)
    }

    @Test
    fun `no colapsa mas alla del minimo`() {
        val state = HeaderCollapseState(expandedPx = 120f, collapsedPx = 60f)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -200f), source)
        assertEquals(60f, state.heightPx, 0.001f)
        assertEquals(1f, state.fraction, 0.001f)
        val again = state.nestedScrollConnection.onPreScroll(Offset(0f, -10f), source)
        assertEquals(0f, again.y, 0.001f)
    }

    @Test
    fun `subir en el tope vuelve a expandir`() {
        val state = HeaderCollapseState(expandedPx = 120f, collapsedPx = 60f)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -200f), source)
        val consumed = state.nestedScrollConnection.onPostScroll(
            Offset.Zero,
            Offset(0f, 100f),
            source,
        )
        assertEquals(60f, consumed.y, 0.001f)
        assertEquals(120f, state.heightPx, 0.001f)
        assertEquals(0f, state.fraction, 0.001f)
    }

    @Test
    fun `reconfigurar respeta la posicion`() {
        val state = HeaderCollapseState(expandedPx = 120f, collapsedPx = 60f)
        state.nestedScrollConnection.onPreScroll(Offset(0f, -30f), source)
        state.reconfigurar(expanded = 160f, collapsed = 60f)
        assertEquals(90f, state.heightPx, 0.001f)
        state.colapsar()
        state.reconfigurar(expanded = 180f, collapsed = 60f)
        assertEquals(60f, state.heightPx, 0.001f)
        state.expandir()
        assertEquals(180f, state.heightPx, 0.001f)
    }
}
