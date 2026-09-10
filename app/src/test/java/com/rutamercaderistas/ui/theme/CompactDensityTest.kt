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

    @Test
    fun `common density profiles never exceed the compact cap`() {
        val profiles = listOf(1.75f, 2f, 2.625f, 3f, 3.5f, 4f)

        profiles.forEach { density ->
            assertEquals(minOf(density, COMPACT_DENSITY), compactDensity(density), 0f)
        }
    }
}
