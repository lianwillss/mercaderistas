package com.rutamercaderistas.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun `escala cero significa movimiento reducido`() {
        assertTrue(isMotionReduced("0.0"))
        assertTrue(isMotionReduced("0"))
    }

    @Test
    fun `escala normal no es reducido`() {
        assertFalse(isMotionReduced("1.0"))
        assertFalse(isMotionReduced("0.5"))
    }

    @Test
    fun `nulo o ilegible no es reducido`() {
        assertFalse(isMotionReduced(null))
        assertFalse(isMotionReduced(""))
        assertFalse(isMotionReduced("x"))
    }
}
