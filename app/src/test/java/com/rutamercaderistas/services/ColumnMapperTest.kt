package com.rutamercaderistas.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMapperTest {

    @Test
    fun `maps promo CSV header with SUBCADENA column`() {
        val headers = listOf("MARCA", "CADENA", "SUBCADENA", "INICIO", "FINAL", "SKU", "PRECIO - % PROMOCION")
        val mapper = ColumnMapper()
        mapper.map(headers)

        assertEquals(0, mapper.getIndex("MARCA"))
        assertEquals(1, mapper.getIndex("CADENA"))
        assertEquals(3, mapper.getIndex("INICIO"))
        assertEquals(4, mapper.getIndex("FINAL"))
        assertEquals(5, mapper.getIndex("SKU"))
        assertEquals(6, mapper.findFirstContaining("PRECIO"))
    }

    @Test
    fun `maps legacy 6-column promo CSV header`() {
        val headers = listOf("MARCA", "CADENA", "INICIO", "FINAL", "SKU", "PRECIO")
        val mapper = ColumnMapper()
        mapper.map(headers)

        assertEquals(0, mapper.getIndex("MARCA"))
        assertEquals(1, mapper.getIndex("CADENA"))
        assertEquals(2, mapper.getIndex("INICIO"))
        assertEquals(3, mapper.getIndex("FINAL"))
        assertEquals(4, mapper.getIndex("SKU"))
        assertEquals(5, mapper.getIndex("PRECIO"))
    }

    @Test
    fun `normalize handles case accents and spacing`() {
        assertEquals("MARCA", ColumnMapper.normalize("  Marca "))
        assertEquals("PROMOCION", ColumnMapper.normalize("Promoción"))
        assertEquals("PRECIO - % PROMOCION", ColumnMapper.normalize("PRECIO - % PROMOCION"))
    }

    @Test
    fun `findFirstContaining matches substring of header`() {
        val mapper = ColumnMapper()
        mapper.map(listOf("MARCA", "PRECIO - % PROMOCION"))
        assertTrue(mapper.findFirstContaining("PRECIO") != -1)
        assertEquals(1, mapper.findFirstContaining("PRECIO"))
    }

    @Test
    fun `maps header with UTF-8 BOM prefix`() {
        val headers = listOf("\uFEFFMARCA", "CADENA", "SUBCADENA", "INICIO", "FINAL", "SKU", "PRECIO - % PROMOCION")
        val mapper = ColumnMapper()
        mapper.map(headers)

        assertEquals(0, mapper.getIndex("MARCA", "BRAND", "PRODUCTO"))
        assertEquals(1, mapper.getIndex("CADENA", "CHAIN", "TIENDA", "SUBCADENA"))
        assertEquals(3, mapper.getIndex("INICIO", "DESDE", "INICIAL"))
        assertEquals(4, mapper.getIndex("FINAL", "HASTA", "FINALIZACION"))
        assertEquals(5, mapper.getIndex("SKU", "NOMBRE", "PRODUCTO"))
        assertEquals(6, mapper.findFirstContaining("PRECIO", "% PROMOCION", "OFERTA"))
    }
}