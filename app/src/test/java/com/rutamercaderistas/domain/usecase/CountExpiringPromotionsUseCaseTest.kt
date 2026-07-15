package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountExpiringPromotionsUseCaseTest {

    private val useCase = CountExpiringPromotionsUseCase()

    @Test
    fun `countToday returns 0 when no promos end today`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = "2026-12-31"),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = "2026-01-01"),
        )
        assertEquals(0, useCase.countToday(promos))
    }

    @Test
    fun `countToday returns count for today date`() = runTest {
        val today = LocalDate.now().toString()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = "2026-12-31"),
        )
        assertEquals(1, useCase.countToday(promos))
    }

    @Test
    fun `countToday ignores blank endDate`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = ""),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = "  "),
        )
        assertEquals(0, useCase.countToday(promos))
    }

    @Test
    fun `countToday handles invalid date gracefully`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = "not-a-date"),
        )
        assertEquals(0, useCase.countToday(promos))
    }

    @Test
    fun `countTomorrow returns 1 for tomorrow date`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = tomorrow),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = "2026-12-31"),
        )
        assertEquals(1, useCase.countTomorrow(promos))
    }

    @Test
    fun `countTomorrow returns 0 for today date`() = runTest {
        val today = LocalDate.now().toString()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today),
        )
        assertEquals(0, useCase.countTomorrow(promos))
    }

    @Test
    fun `countTomorrow handles blank endDate`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = ""),
        )
        assertEquals(0, useCase.countTomorrow(promos))
    }

    @Test
    fun `getExpiringSoon returns promos within default 7 days`() = runTest {
        val today = LocalDate.now()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today.toString()),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = today.plusDays(3).toString()),
            PromotionEntity(brand = "C", chain = "", productName = "P3", price = "", endDate = today.plusDays(7).toString()),
        )
        val result = useCase.getExpiringSoon(promos)
        assertEquals(3, result.size)
    }

    @Test
    fun `getExpiringSoon excludes past promos`() = runTest {
        val today = LocalDate.now()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today.minusDays(1).toString()),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = today.plusDays(2).toString()),
        )
        val result = useCase.getExpiringSoon(promos)
        assertEquals(1, result.size)
        assertEquals("B", result[0].brand)
    }

    @Test
    fun `getExpiringSoon excludes promos beyond withinDays`() = runTest {
        val today = LocalDate.now()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today.plusDays(8).toString()),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = today.plusDays(14).toString()),
        )
        val result = useCase.getExpiringSoon(promos, withinDays = 7)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getExpiringSoon handles invalid dates`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = "not-a-date"),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = ""),
        )
        val result = useCase.getExpiringSoon(promos)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getExpiringSoon returns sorted by date`() = runTest {
        val today = LocalDate.now()
        val promos = listOf(
            PromotionEntity(brand = "C", chain = "", productName = "P3", price = "", endDate = today.plusDays(5).toString()),
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today.plusDays(1).toString()),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = today.plusDays(3).toString()),
        )
        val result = useCase.getExpiringSoon(promos)
        assertEquals(listOf("A", "B", "C"), result.map { it.brand })
    }

    @Test
    fun `getExpiringSoon respects custom withinDays`() = runTest {
        val today = LocalDate.now()
        val promos = listOf(
            PromotionEntity(brand = "A", chain = "", productName = "P1", price = "", endDate = today.plusDays(1).toString()),
            PromotionEntity(brand = "B", chain = "", productName = "P2", price = "", endDate = today.plusDays(3).toString()),
            PromotionEntity(brand = "C", chain = "", productName = "P3", price = "", endDate = today.plusDays(5).toString()),
        )
        val result = useCase.getExpiringSoon(promos, withinDays = 3)
        assertEquals(2, result.size)
    }

    @Test
    fun `getExpiringSoon handles empty list`() = runTest {
        assertTrue(useCase.getExpiringSoon(emptyList()).isEmpty())
    }
}
