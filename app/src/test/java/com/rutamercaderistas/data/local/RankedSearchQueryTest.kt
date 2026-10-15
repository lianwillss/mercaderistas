package com.rutamercaderistas.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedSearchQueryTest {

    @Test
    fun `null on blank tokens`() {
        assertNull(buildRankedSearch("", emptyList()))
        assertNull(buildRankedSearch("   ", listOf("  ", "")))
    }

    @Test
    fun `single token AND group covers codes and norms`() {
        val q = checkNotNull(buildRankedSearch("bacio", listOf("bacio")))
        assertTrue(q.sql.contains("eanPrincipal LIKE ?"))
        assertTrue(q.sql.contains("descripcion_norm LIKE ?"))
        assertTrue(q.sql.contains("marca_norm_nospace LIKE ?"))
        assertTrue(q.sql.contains("ltrim(p.eanPrincipal,'0') LIKE ?"))
        // 10 LIKE + 4 ltrim por token en WHERE; 2 brandHits; 13 en score.
        assertEquals(14 + 2 + 13, q.args.size)
    }

    @Test
    fun `multi token joins groups with AND`() {
        val q = checkNotNull(buildRankedSearch("pistacho nat", listOf("pistacho", "nat")))
        // 2 grupos WHERE unidos por AND + ORDER BY (el score usa + y CASE, no AND).
        val where = q.sql.substringBefore(" ORDER BY ")
        assertEquals(1, where.split(" AND ").size - 1)
        assertTrue(q.sql.contains("ORDER BY"))
    }

    @Test
    fun `score weights preserve lexicographic order`() {
        val q = checkNotNull(buildRankedSearch("7804690890292", listOf("7804690890292")))
        val units = listOf(300000, 20000, 1000, 200, 20)
        units.forEach { assertTrue("falta peso $it", q.sql.contains(it.toString())) }
        // Cada peso supera la suma máxima de todos los tiers menores (+2 del tier 4).
        units.forEachIndexed { i, u ->
            val lowerMax = units.drop(i + 1).sumOf { it * 2 } + 2
            assertTrue("peso $u no domina ($lowerMax)", u > lowerMax)
        }
    }

    @Test
    fun `exact zero trimmed ean scores max`() {
        val q = checkNotNull(buildRankedSearch("658325654776", listOf("658325654776")))
        assertTrue(q.sql.contains("ltrim(p.eanPrincipal,'0') = ?"))
        assertTrue(q.sql.contains("ltrim(p.codigoBarra,'0') = ?"))
        // WHERE (14) + brandHits (2): el tier 1 exacto arranca en args[16] con q crudo.
        assertEquals("658325654776", q.args[16])
    }

    @Test
    fun `brand hits tier counts all tokens`() {
        val q = checkNotNull(buildRankedSearch("pistacho nat", listOf("pistacho", "nat")))
        // Suma de 2 CASEs y comparación contra N=2.
        assertTrue(q.sql.contains(") = 2 THEN 2"))
    }

    @Test
    fun `tiebreak marca then descripcion`() {
        val q = checkNotNull(buildRankedSearch("x", listOf("x")))
        assertTrue(q.sql.endsWith(") DESC, p.marca ASC, p.descripcionProducto ASC"))
    }

    @Test
    fun `args bind in placeholder order`() {
        val q = checkNotNull(buildRankedSearch("Bacio 500", listOf("bacio", "500")))
        // WHERE: 14 args por token × 2 = 28; brandHits: 4; score: 4+2+3+2+2 = 13.
        assertEquals(28 + 4 + 13, q.args.size)
        // Primeros args del WHERE del token 1.
        assertEquals("%bacio%", q.args[0])
        // ltrim del token 1 (offset 10).
        assertEquals("%bacio%", q.args[10])
        // WHERE del token 2 arranca en 14.
        assertEquals("%500%", q.args[14])
    }

    private fun checkNotNull(q: RankedQuery?): RankedQuery {
        assertNotNull(q)
        return q!!
    }
}
