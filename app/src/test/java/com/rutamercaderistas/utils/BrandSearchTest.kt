package com.rutamercaderistas.utils

import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.models.brandVisitDays
import com.rutamercaderistas.models.diasDeVisita
import com.rutamercaderistas.models.diasLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandSearchTest {

    private fun entrada(cliente: String, vararg dias: DiaSemana) = EntradaRuta(
        reponedor = "R",
        rutero = "AMU-1",
        codigo = "1",
        local = "Local",
        direccion = "Dir",
        cliente = cliente,
        lunes = DiaSemana.LUNES in dias,
        martes = DiaSemana.MARTES in dias,
        miercoles = DiaSemana.MIERCOLES in dias,
        jueves = DiaSemana.JUEVES in dias,
        viernes = DiaSemana.VIERNES in dias,
        sabado = DiaSemana.SABADO in dias,
        domingo = DiaSemana.DOMINGO in dias,
    )

    private fun localCon(vararg marcas: String) = LocalDelDia(
        codigo = "1",
        local = "Local",
        direccion = "Dir",
        clientes = marcas.map { ClienteInfo(it, false, 3) },
    )

    @Test
    fun `diasDeVisita une booleanos`() {
        val dias = diasDeVisita(entrada("BIGU", DiaSemana.LUNES, DiaSemana.MIERCOLES, DiaSemana.VIERNES))
        assertEquals(setOf(DiaSemana.LUNES, DiaSemana.MIERCOLES, DiaSemana.VIERNES), dias)
    }

    @Test
    fun `brandVisitDays une dias por marca`() {
        val map = brandVisitDays(
            listOf(
                entrada("BIGU", DiaSemana.LUNES, DiaSemana.MIERCOLES),
                entrada("BIGU", DiaSemana.VIERNES),
                entrada("CUK", DiaSemana.MARTES),
            )
        )
        assertEquals(
            setOf(DiaSemana.LUNES, DiaSemana.MIERCOLES, DiaSemana.VIERNES),
            map["BIGU"],
        )
        assertEquals(setOf(DiaSemana.MARTES), map["CUK"])
    }

    @Test
    fun `diasLabel ordena lun a dom`() {
        assertEquals(
            "LUN, MIE, VIE",
            diasLabel(setOf(DiaSemana.VIERNES, DiaSemana.LUNES, DiaSemana.MIERCOLES)),
        )
    }

    @Test
    fun `matchedBrands encuentra marca difusa e ignora cortas`() {
        val local = localCon("BIGU", "CUK")
        assertEquals(listOf("BIGU"), matchedBrands(local, "bigu").map { it.nombre })
        assertEquals(listOf("CUK"), matchedBrands(local, "cuk").map { it.nombre })
        assertTrue(matchedBrands(local, "").isEmpty())
        assertTrue(matchedBrands(local, "x").isEmpty())
    }

    @Test
    fun `matchedBrands limita a 2`() {
        val local = localCon("BIGU UNO", "BIGU DOS", "BIGU TRES")
        assertEquals(2, matchedBrands(local, "bigu").size)
    }

    private fun localConDias(
        codigo: String,
        marca: String,
        vararg dias: DiaSemana,
    ): LocalDelDia {
        val base = entrada(marca, *dias)
        return LocalDelDia(
            codigo = codigo,
            local = "Local $codigo",
            direccion = "Dir",
            clientes = listOf(ClienteInfo(marca, false, dias.size)),
            marcasDias = brandVisitDays(listOf(base)),
        )
    }

    @Test
    fun `brandSections agrupa por marca y ordena`() {
        val locales = listOf(
            localConDias("1", "CUK", DiaSemana.LUNES),
            localConDias("2", "BIGU", DiaSemana.LUNES),
            localConDias("3", "BIGU", DiaSemana.MARTES),
        )
        // "bigu cuk" no pega como frase; se prueba por marca separada abajo.
        val byBigu = brandSections("bigu", locales)
        assertEquals(1, byBigu.size)
        assertEquals("BIGU", byBigu[0].brand)
        assertEquals(listOf("2", "3"), byBigu[0].locales.map { it.codigo })
    }

    @Test
    fun `brandSections filtra por dia`() {
        val locales = listOf(
            localConDias("2", "BIGU", DiaSemana.LUNES),
            localConDias("3", "BIGU", DiaSemana.MARTES),
        )
        val lunes = brandSections("bigu", locales, DiaSemana.LUNES)
        assertEquals(1, lunes.size)
        assertEquals(listOf("2"), lunes[0].locales.map { it.codigo })
        val domingo = brandSections("bigu", locales, DiaSemana.DOMINGO)
        assertTrue(domingo.isEmpty())
    }

    @Test
    fun `brandSections vacio sin match de marca`() {
        val locales = listOf(localConDias("1", "CUK", DiaSemana.LUNES))
        // "jumbo" no es marca de este local → plano, sin secciones.
        assertTrue(brandSections("jumbo", locales).isEmpty())
        assertTrue(brandSections("x", locales).isEmpty())
    }
}
