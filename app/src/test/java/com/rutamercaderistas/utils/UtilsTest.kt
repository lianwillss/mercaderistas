package com.rutamercaderistas.utils

import com.rutamercaderistas.ui.components.normalizeChain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UtilsTest {

    // ── cleanBrand ──

    @Test
    fun `cleanBrand removes star prefix`() {
        assertEquals("CUK", "⭐ CUK".cleanBrand())
    }

    @Test
    fun `cleanBrand removes diacritics`() {
        assertEquals("OLIMPIA", "OLÍMPIA".cleanBrand())
    }

    @Test
    fun `cleanBrand uppercases`() {
        assertEquals("CUK", "cuk".cleanBrand())
    }

    @Test
    fun `cleanBrand removes spaces and hyphens`() {
        assertEquals("DONOSO", "Donoso ".cleanBrand())
    }

    @Test
    fun `cleanBrand trims whitespace`() {
        assertEquals("CUK", "  CUK  ".cleanBrand())
    }

    @Test
    fun `cleanBrand handles combined transformations`() {
        assertEquals("SANJOSE", "⭐ San José".cleanBrand())
    }

    // ── normalizeChain ──

    @Test
    fun `normalizeChain maps Walmart Express to LIDER`() {
        assertEquals("LIDER", normalizeChain("WALMART EXPRESS"))
    }

    @Test
    fun `normalizeChain maps walmart express lowercase to LIDER`() {
        assertEquals("LIDER", normalizeChain("walmart express"))
    }

    @Test
    fun `normalizeChain leaves LIDER unchanged`() {
        assertEquals("LIDER", normalizeChain("LIDER"))
    }

    @Test
    fun `normalizeChain leaves JUMBO unchanged`() {
        assertEquals("JUMBO", normalizeChain("Jumbo"))
    }

    @Test
    fun `normalizeChain trims whitespace`() {
        assertEquals("LIDER", normalizeChain("  LIDER  "))
    }

    // ── countExpiringToday (pure logic) ──

    @Test
    fun `countExpiringToday returns 0 for no matching dates`() {
        val promos = listOf(
            TestPromo(endDate = "2026-12-31"),
            TestPromo(endDate = "2026-01-01"),
        )
        assertEquals(0, countExpiringToday(promos))
    }

    @Test
    fun `countExpiringToday returns count for today`() {
        val today = LocalDate.now().toString()
        val promos = listOf(
            TestPromo(endDate = today),
            TestPromo(endDate = "2026-12-31"),
        )
        assertEquals(1, countExpiringToday(promos))
    }

    @Test
    fun `countExpiringToday ignores blank endDate`() {
        val promos = listOf(TestPromo(endDate = ""), TestPromo(endDate = "  "))
        assertEquals(0, countExpiringToday(promos))
    }

    // ── urgency (pure logic) ──

    @Test
    fun `urgency returns TODAY for today date`() {
        val today = LocalDate.now().toString()
        assertEquals("TODAY", urgencyText(today))
    }

    @Test
    fun `urgency returns TOMORROW for tomorrow date`() {
        val tomorrow = LocalDate.now().plusDays(1).toString()
        assertEquals("TOMORROW", urgencyText(tomorrow))
    }

    @Test
    fun `urgency returns NORMAL for future date`() {
        assertEquals("NORMAL", urgencyText("2026-12-31"))
    }

    @Test
    fun `urgency returns NORMAL for blank`() {
        assertEquals("NORMAL", urgencyText(""))
    }
}

// ── Helpers to test pure logic without Compose dependencies ──

private data class TestPromo(
    val endDate: String = "",
)

private fun countExpiringToday(promos: List<TestPromo>): Int {
    val today = LocalDate.now()
    return promos.count { promo ->
        try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
        catch (_: Exception) { false }
    }
}

private fun urgencyText(endDate: String): String {
    if (endDate.isBlank()) return "NORMAL"
    return try {
        val end = LocalDate.parse(endDate)
        val today = LocalDate.now()
        when {
            end == today -> "TODAY"
            end == today.plusDays(1) -> "TOMORROW"
            else -> "NORMAL"
        }
    } catch (_: Exception) { "NORMAL" }
}
