package com.rutamercaderistas.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactDensityTest {

    @Test
    fun `density below compact cap is preserved`() {
        assertEquals(2.75f, compactDensity(2.75f), 0f)
    }

    @Test
    fun `density above compact cap is reduced to 480 dpi equivalent`() {
        assertEquals(3f, compactDensity(4f), 0f)
    }
}
