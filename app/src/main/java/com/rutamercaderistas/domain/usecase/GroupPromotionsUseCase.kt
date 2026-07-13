package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.utils.cleanBrand
import javax.inject.Inject

data class GroupedPromotions(
    val promotionsByBrand: Map<String, List<PromotionEntity>>,
    val marcasConPromo: Int,
    val totalPromosActivas: Int,
    val promosExpiringToday: Int,
    val promosExpiringTomorrow: Int,
)

class GroupPromotionsUseCase @Inject constructor(
    private val countExpiring: CountExpiringPromotionsUseCase,
) {
    operator fun invoke(promos: List<PromotionEntity>): GroupedPromotions {
        val byBrand = promos.groupBy { it.brand.cleanBrand() }
        return GroupedPromotions(
            promotionsByBrand = byBrand,
            marcasConPromo = byBrand.count { it.value.isNotEmpty() },
            totalPromosActivas = byBrand.values.sumOf { it.size },
            promosExpiringToday = countExpiring.countToday(promos),
            promosExpiringTomorrow = countExpiring.countTomorrow(promos),
        )
    }
}
