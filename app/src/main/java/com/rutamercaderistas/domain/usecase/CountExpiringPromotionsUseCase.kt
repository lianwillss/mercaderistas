package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import java.time.LocalDate
import javax.inject.Inject

class CountExpiringPromotionsUseCase @Inject constructor() {

    fun countToday(promos: List<PromotionEntity>): Int {
        val today = LocalDate.now()
        return promos.count { promo ->
            try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
            catch (_: Exception) { false }
        }
    }

    fun countTomorrow(promos: List<PromotionEntity>): Int {
        val tomorrow = LocalDate.now().plusDays(1)
        return promos.count { promo ->
            try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == tomorrow }
            catch (_: Exception) { false }
        }
    }
}
