package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

class CountExpiringPromotionsUseCase @Inject constructor() {

    fun countToday(promos: List<PromotionEntity>): Int {
        val today = LocalDate.now()
        return promos.count { promo ->
            try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
            catch (_: Exception) {
                Timber.w("Error parseando endDate '%s' en countToday", promo.endDate)
                false
            }
        }
    }

    fun countTomorrow(promos: List<PromotionEntity>): Int {
        val tomorrow = LocalDate.now().plusDays(1)
        return promos.count { promo ->
            try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == tomorrow }
            catch (_: Exception) {
                Timber.w("Error parseando endDate '%s' en countTomorrow", promo.endDate)
                false
            }
        }
    }

    fun getExpiringSoon(promos: List<PromotionEntity>, withinDays: Long = 7): List<PromotionEntity> {
        val today = LocalDate.now()
        val limit = today.plusDays(withinDays)
        return promos
            .filter { promo ->
                try {
                    promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate).let { end ->
                        end >= today && end <= limit
                    }
                } catch (_: Exception) {
                    Timber.w("Error parseando endDate '%s' en getExpiringSoon filter", promo.endDate)
                    false
                }
            }
            .sortedBy { promo ->
                try { LocalDate.parse(promo.endDate) }
                catch (_: Exception) {
                    Timber.w("Error parseando endDate '%s' en getExpiringSoon sort", promo.endDate)
                    LocalDate.MAX
                }
            }
    }
}
