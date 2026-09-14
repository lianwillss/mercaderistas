package com.rutamercaderistas.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizeTest {

    @Test
    fun `narrow phones are compact`() {
        assertEquals(AppWindowWidth.Compact, appWindowWidth(360.dp))
        assertEquals(AppWindowWidth.Compact, appWindowWidth(599.dp))
    }

    @Test
    fun `tablets reach medium and expanded`() {
        assertEquals(AppWindowWidth.Medium, appWindowWidth(600.dp))
        assertEquals(AppWindowWidth.Medium, appWindowWidth(839.dp))
        assertEquals(AppWindowWidth.Expanded, appWindowWidth(840.dp))
    }
}
