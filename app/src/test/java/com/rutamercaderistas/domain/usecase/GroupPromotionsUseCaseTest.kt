package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupPromotionsUseCaseTest {

    private val countExpiring = CountExpiringPromotionsUseCase()
    private val useCase = GroupPromotionsUseCase(countExpiring)

    @Test
    fun `groups promotions by brand using cleanBrand`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "CUK", chain = "", productName = "Leche", price = "1000"),
            PromotionEntity(brand = "CUK", chain = "", productName = "Yogurt", price = "500"),
            PromotionEntity(brand = "OLIMPIA", chain = "", productName = "Arroz", price = "2000"),
        )
        val result = useCase(promos)

        assertEquals(2, result.marcasConPromo)
        assertEquals(3, result.totalPromosActivas)
        assertEquals(2, result.promotionsByBrand["CUK"]?.size)
        assertEquals(1, result.promotionsByBrand["OLIMPIA"]?.size)
    }

    @Test
    fun `deduplicates brands with diacritics`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "OLÍMPIA", chain = "", productName = "Arroz", price = "2000"),
            PromotionEntity(brand = "OLIMPIA", chain = "", productName = "Fideos", price = "1500"),
        )
        val result = useCase(promos)

        assertEquals(1, result.marcasConPromo)
        assertEquals(2, result.totalPromosActivas)
    }

    @Test
    fun `handles empty list`() = runTest {
        val result = useCase(emptyList())

        assertEquals(0, result.marcasConPromo)
        assertEquals(0, result.totalPromosActivas)
        assertEquals(0, result.promosExpiringToday)
        assertEquals(0, result.promosExpiringTomorrow)
    }

    @Test
    fun `handles single promotion`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "CUK", chain = "", productName = "Leche", price = "1000"),
        )
        val result = useCase(promos)

        assertEquals(1, result.marcasConPromo)
        assertEquals(1, result.totalPromosActivas)
        assertEquals(1, result.promotionsByBrand.size)
    }

    @Test
    fun `groups brands correctly with star prefix`() = runTest {
        val promos = listOf(
            PromotionEntity(brand = "⭐ CUK", chain = "", productName = "Leche", price = "1000"),
            PromotionEntity(brand = "CUK", chain = "", productName = "Yogurt", price = "500"),
        )
        val result = useCase(promos)

        assertEquals(1, result.marcasConPromo)
        assertEquals(2, result.promotionsByBrand["CUK"]?.size)
    }
}
