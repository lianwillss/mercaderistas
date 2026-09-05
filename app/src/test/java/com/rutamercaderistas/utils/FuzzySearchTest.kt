package com.rutamercaderistas.utils

import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import org.junit.Assert.assertEquals
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

    @Test
    fun compactMatchIgnoresSpaces() {
        assertTrue(fuzzyMatches("supermercado", "super mercado"))
        assertTrue(fuzzyMatches("bymaria", "by maria"))
    }

    @Test
    fun numericCodesTolerant() {
        assertTrue(fuzzyMatches("13710", "13710"))
        assertFalse(fuzzyMatches("99999", "13710"))
    }

    @Test
    fun singleTokenDoesNotMatchUnrelatedMultiToken() {
        assertFalse(fuzzyMatches("zzz", "panaderia del barrio supermercado"))
    }

    private fun local(
        codigo: String,
        nombre: String,
        rutero: String = "",
        cadena: String = "",
        formato: String = "",
    ) = LocalDelDia(
        codigo = codigo,
        local = nombre,
        direccion = "Dir $nombre",
        rutero = rutero,
        cadena = cadena,
        formato = formato,
        clientes = listOf(ClienteInfo("Marca", false, 1)),
    )

    @Test
    fun rankLocales_blankReturnsEmpty() {
        assertTrue(rankLocales("", listOf(local("1", "Jumbo"))).isEmpty())
    }

    @Test
    fun rankLocales_exactCodeFirst() {
        val locales = listOf(
            local("12", "Jumbo Temuco", "AMU-1"),
            local("112", "Santa Isabel", "AMU-2"),
        )
        val ranked = rankLocales("12", locales)
        assertEquals(2, ranked.size)
        assertEquals("12", ranked[0].codigo)
    }

    @Test
    fun rankLocales_leadingZerosTolerant() {
        val locales = listOf(local("0012", "Jumbo", "AMU-1"))
        assertEquals(1, rankLocales("12", locales).size)
    }

    @Test
    fun rankLocales_nameBeatsFuzzy() {
        val locales = listOf(
            local("1", "Panadería Barrio"),
            local("2", "Jumbo Temuco"),
        )
        val ranked = rankLocales("jumbo", locales)
        assertEquals(1, ranked.size)
        assertEquals("Jumbo Temuco", ranked[0].local)
    }

    @Test
    fun rankLocales_accentsTolerant() {
        val locales = listOf(local("1", "José Martínez"))
        assertEquals(1, rankLocales("jose", locales).size)
    }

    @Test
    fun rankLocales_keepsRutero() {
        val locales = listOf(local("1", "Jumbo Temuco", "AMU-1"))
        assertEquals("AMU-1", rankLocales("jumbo", locales)[0].rutero)
    }

    @Test
    fun rankLocales_matchesChainByFormatoCode() {
        val locales = listOf(
            local("1", "Temuco Av. Alemania", "AMU-1", cadena = "CENCOSUD", formato = "J"),
            local("2", "Barrio Panadería", "AMU-2", cadena = "UNIMARC"),
        )
        val ranked = rankLocales("jumbo", locales)
        assertEquals(1, ranked.size)
        assertEquals("1", ranked[0].codigo)
    }

    @Test
    fun rankLocales_sisaFindsSantaIsabel() {
        val locales = listOf(
            local("1", "Temuco Centro", "AMU-1", cadena = "CENCOSUD", formato = "N"),
            local("2", "Barrio Panadería", "AMU-2", cadena = "UNIMARC"),
        )
        assertEquals(1, rankLocales("sisa", locales).size)
        assertEquals(1, rankLocales("santa isabel", locales).size)
    }

    @Test
    fun rankLocales_walmartFindsLider() {
        val locales = listOf(
            local("1", "Local Centro", "AMU-1", cadena = "", formato = "EX"),
            local("2", "Barrio Panadería", "AMU-2", cadena = "UNIMARC"),
        )
        assertEquals(1, rankLocales("walmart", locales).size)
        assertEquals(1, rankLocales("lider", locales).size)
    }

    @Test
    fun rankLocales_unimarcAndAlvi() {
        val locales = listOf(
            local("1", "Local Centro", "AMU-1", cadena = "UNIMARC"),
            local("2", "Local Norte", "AMU-2", cadena = "ALVI"),
        )
        assertEquals("1", rankLocales("unimarc", locales)[0].codigo)
        assertEquals("2", rankLocales("alvi", locales)[0].codigo)
    }

    @Test
    fun rankLocales_alphanumericCodeFindsStore() {
        val locales = listOf(
            LocalDelDia(
                codigo = "J513",
                local = "Jumbo El Llano Subercaseaux",
                direccion = "El Llano Subercaseaux 3519, San Miguel",
                rutero = "DMU-6",
                comuna = "San Miguel",
                cadena = "CENCOSUD",
                formato = "JUMBO",
                clientes = listOf(ClienteInfo("CUK", false, 1)),
            ),
            local("J506", "Jumbo Temuco", "AMU-1", cadena = "CENCOSUD", formato = "JUMBO"),
        )
        val ranked = rankLocales("j513", locales)
        assertEquals(1, ranked.size)
        assertEquals("J513", ranked[0].codigo)
    }
}
