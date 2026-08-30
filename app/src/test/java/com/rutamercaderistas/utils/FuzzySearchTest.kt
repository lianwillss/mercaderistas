package com.rutamercaderistas.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(fuzzyMatches("", "cualquier texto"))
    }

    @Test
    fun exactMatch() {
        assertTrue(fuzzyMatches("By Maria", "BY MARIA"))
    }

    @Test
    fun accentsTolerant() {
        assertTrue(fuzzyMatches("jose", "JOSÉ"))
        assertTrue(fuzzyMatches("ñandu", "NANDU"))
    }

    @Test
    fun typoTolerant() {
        assertTrue(fuzzyMatches("supermercado", "supermercadoz"))
        assertTrue(fuzzyMatches("farmacity", "farmacia"))
    }

    @Test
    fun tokenAllMustMatch() {
        assertTrue(fuzzyMatches("comercio sz", "COMERCIO SZ"))
        assertFalse(fuzzyMatches("comercio xyz", "COMERCIO SZ"))
    }

    @Test
    fun noFalsePositiveOnUnrelated() {
        assertFalse(fuzzyMatches("zeus", "panaderia del barrio"))
    }

    @Test
    fun levenshteinBasic() {
        assertTrue(levenshtein("kit", "cit") <= 1)
        assertTrue(levenshtein("casa", "calle") >= 2)
    }
}
