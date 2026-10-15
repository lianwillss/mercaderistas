package com.rutamercaderistas.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodProvFilterTest {

    private val items = listOf(
        CodProvItem(1, "13710"),
        CodProvItem(2, "7088"),
        CodProvItem(3, "1000339140"),
    )
    private val names = mapOf(items[0] to "NAT NATURAL", items[1] to "SUK", items[2] to "FERMENTISTA")

    @Test
    fun `blank query returns all`() {
        assertEquals(3, filterCodProvItems(items, "") { names.getValue(it) }.size)
        assertEquals(3, filterCodProvItems(items, "   ") { names.getValue(it) }.size)
    }

    @Test
    fun `matches by brand name case insensitive`() {
        val filtered = filterCodProvItems(items, "suk") { names.getValue(it) }
        assertEquals(1, filtered.size)
        assertEquals("7088", filtered[0].code)
    }

    @Test
    fun `matches by code`() {
        val filtered = filterCodProvItems(items, "13710") { names.getValue(it) }
        assertEquals(1, filtered.size)
        assertEquals("NAT NATURAL", names.getValue(filtered[0]))
    }

    @Test
    fun `multi token AND`() {
        val filtered = filterCodProvItems(items, "FERMENTISTA 1000339140") { names.getValue(it) }
        assertEquals(1, filtered.size)
        assertTrue(filterCodProvItems(items, "FERMENTISTA 13710") { names.getValue(it) }.isEmpty())
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(filterCodProvItems(items, "zzz") { names.getValue(it) }.isEmpty())
    }
}
