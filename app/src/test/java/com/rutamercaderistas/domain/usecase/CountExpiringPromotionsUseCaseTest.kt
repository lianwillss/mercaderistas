package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
