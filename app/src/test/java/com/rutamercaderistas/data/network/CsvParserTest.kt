package com.rutamercaderistas.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvParserTest {

    @Test
    fun `parseCsvLine splits comma-separated values`() {
        val line = "CUK,Jumbo,2026-07-01,2026-07-31,Chocolate 70%,\\$2.990"
        val result = parseCsvLine(line)
        assertNotNull(result)
        assertEquals(6, result!!.size)
        assertEquals("CUK", result[0])
        assertEquals("Jumbo", result[1])
        assertEquals("2026-07-01", result[2])
        assertEquals("2026-07-31", result[3])
        assertEquals("Chocolate 70%", result[4])
        assertEquals("\\$2.990", result[5])
    }

    @Test
    fun `parseCsvLine handles quoted fields with commas`() {
        val line = "CUK,Jumbo,2026-07-01,2026-07-31,\"Chocolate 70%, oferta\",\\$2.990"
        val result = parseCsvLine(line)
        assertNotNull(result)
        assertEquals(6, result!!.size)
        assertEquals("Chocolate 70%, oferta", result[4])
    }

    @Test
    fun `parseCsvLine handles quoted fields with internal quotes`() {
        val line = "MARCA,X,2026-07-01,2026-07-31,\"Producto \"\"especial\"\"\",\\$100"
        val result = parseCsvLine(line)
        assertNotNull(result)
        assertEquals(6, result!!.size)
        assertEquals("Producto \"especial\"", result[4])
    }

    @Test
    fun `parseCsvLine returns null for blank line`() {
        assertNull(parseCsvLine(""))
        assertNull(parseCsvLine("  "))
    }

    @Test
    fun `parseCsvLine handles single field`() {
        val result = parseCsvLine("onlyfield")
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("onlyfield", result[0])
    }

    @Test
    fun `parseCsvLine handles empty fields`() {
        val result = parseCsvLine("a,,c")
        assertNotNull(result)
        assertEquals(3, result!!.size)
        assertEquals("a", result[0])
        assertEquals("", result[1])
        assertEquals("c", result[2])
    }

    @Test
    fun `parseDate parses ISO date`() {
        assertEquals(LocalDate.of(2026, 7, 11), parseDate("2026-07-11"))
    }

    @Test
    fun `parseDate returns null for blank input`() {
        assertNull(parseDate(""))
        assertNull(parseDate("  "))
    }

    @Test
    fun `parseDate returns null for invalid format`() {
        assertNull(parseDate("11-07-2026"))
        assertNull(parseDate("not-a-date"))
    }
}
