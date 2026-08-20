package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.data.local.PromotionEntity
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

class CountExpiringPromotionsUseCase @Inject constructor() {

    fun countToday(promos: List<PromotionEntity>): Int =
        getExpiringOn(promos, LocalDate.now()).size

    fun countTomorrow(promos: List<PromotionEntity>): Int =
        getExpiringOn(promos, LocalDate.now().plusDays(1)).size

    fun getExpiringToday(promos: List<PromotionEntity>): List<PromotionEntity> =
        getExpiringOn(promos, LocalDate.now())

    fun getExpiringTomorrow(promos: List<PromotionEntity>): List<PromotionEntity> =
        getExpiringOn(promos, LocalDate.now().plusDays(1))

    private fun getExpiringOn(
        promos: List<PromotionEntity>,
        date: LocalDate,
    ): List<PromotionEntity> = promos.filter { promo ->
        try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == date }
        catch (_: Exception) {
            Timber.w("Error parseando endDate '%s'", promo.endDate)
            false
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
