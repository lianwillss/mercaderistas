package com.rutamercaderistas.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EanFtsTest {

    @Test
    fun `single token becomes quoted prefix`() {
        assertEquals("\"pistacho\"*", buildFtsMatch(listOf("pistacho")))
    }

    @Test
    fun `multiple tokens join with AND`() {
        assertEquals(
            "\"pistacho\"* AND \"nat\"*",
            buildFtsMatch(listOf("pistacho", "nat")),
        )
    }

    @Test
    fun `empty and blank tokens yield null`() {
        assertNull(buildFtsMatch(emptyList()))
        assertNull(buildFtsMatch(listOf("  ", "")))
    }

    @Test
    fun `blank tokens are dropped`() {
        assertEquals("\"nat\"*", buildFtsMatch(listOf("  ", "nat")))
    }
}
