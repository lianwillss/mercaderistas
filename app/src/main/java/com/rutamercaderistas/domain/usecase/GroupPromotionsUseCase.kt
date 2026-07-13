package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.utils.cleanBrand
import com.rutamercaderistas.utils.normalizeBrand
import timber.log.Timber
import javax.inject.Inject

data class GroupedPromotions(
    val promotionsByBrand: Map<String, List<PromotionEntity>>,
    val marcasConPromo: Int,
    val totalPromosActivas: Int,
    val promosExpiringToday: Int,
    val promosExpiringTomorrow: Int,
    val promosExpiringSoon: List<PromotionEntity>,
)

class GroupPromotionsUseCase @Inject constructor(
    private val countExpiring: CountExpiringPromotionsUseCase,
) {
    operator fun invoke(promos: List<PromotionEntity>): GroupedPromotions {
        val byBrand = promos.groupBy { normalizeBrand(it.brand).cleanBrand() }
        Timber.d("GROUP: %d promos agrupadas en %d marcas", promos.size, byBrand.size)
        byBrand.entries.take(20).forEach { (k, v) ->
            Timber.d("GROUP: \"%s\" → %d promos (chains: %s)", k, v.size,
                v.map { it.chain }.distinct().joinToString(","))
        }
        return GroupedPromotions(
            promotionsByBrand = byBrand,
            marcasConPromo = byBrand.count { it.value.isNotEmpty() },
            totalPromosActivas = byBrand.values.sumOf { it.size },
            promosExpiringToday = countExpiring.countToday(promos),
            promosExpiringTomorrow = countExpiring.countTomorrow(promos),
            promosExpiringSoon = countExpiring.getExpiringSoon(promos),
        )
    }
}
