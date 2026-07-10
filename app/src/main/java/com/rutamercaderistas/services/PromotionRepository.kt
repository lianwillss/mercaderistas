package com.rutamercaderistas.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.local.PromotionDao
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.worker.PromotionRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromotionRepository @Inject constructor(
    private val promotionDao: PromotionDao,
    @ApplicationContext private val context: Context,
) {

    suspend fun getPromotions(brand: String, chain: String): List<PromotionEntity> {
        return promotionDao.getPromotions(brand, chain)
    }

    suspend fun refreshIfNeeded() {
        val lastUpdated = promotionDao.getLastUpdated() ?: 0L
        if (System.currentTimeMillis() - lastUpdated > Constants.PROMOTION_REFRESH_INTERVAL_MS) {
            refresh()
        }
    }

    suspend fun refresh(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Promociones: descargando CSV desde %s", Constants.PROMOTIONS_CSV_URL)
                val bytes = downloadBytes(Constants.PROMOTIONS_CSV_URL)
                    .getOrElse {
                        Timber.w("Promociones: falló descarga CSV")
                        return@withContext false
                    }

                val csv = bytes.decodeToString()
                Timber.d("Promociones: descargados %d bytes", bytes.size)

                val lines = csv.lines()
                    .drop(1) // skip header
                    .filter { it.isNotBlank() }
                Timber.d("Promociones: %d líneas en CSV (sin header)", lines.size)

                val today = java.time.LocalDate.now()
                val now = System.currentTimeMillis()
                var totalRead = 0
                var filteredByDate = 0
                val entities = lines.mapNotNull { line ->
                    val cols = parseSemicolonLine(line) ?: return@mapNotNull null
                    if (cols.size < 6) {
                        Timber.d("Promociones: línea saltada (solo %d columnas): %s", cols.size, line.take(80))
                        return@mapNotNull null
                    }

                    val brand = cols[0].trim()
                    val chain = cols[1].trim()
                    val start = cols[2].trim()
                    val end = cols[3].trim()
                    val product = cols[4].trim()
                    val price = cols[5].trim()

                    totalRead++

                    // filter by date — only include active promotions
                    if (start.isNotEmpty() && end.isNotEmpty()) {
                        val startDate = parseDate(start) ?: return@mapNotNull null
                        val endDate = parseDate(end) ?: return@mapNotNull null
                        if (today.isBefore(startDate) || today.isAfter(endDate)) {
                            filteredByDate++
                            Timber.d("Promociones: filtrada por fecha [%s..%s] %s", start, end, brand)
                            return@mapNotNull null
                        }
                    }

                    PromotionEntity(
                        brand = brand,
                        chain = chain,
                        productName = product,
                        price = price,
                        startDate = start,
                        endDate = end,
                        lastUpdated = now,
                    )
                }

                Timber.d("Promociones: %d leídas, %d filtradas por fecha, %d vigentes",
                    totalRead, filteredByDate, entities.size)

                if (entities.isEmpty()) {
                    Timber.w("Promociones: 0 vigentes después de filtro — no se guarda nada")
                    return@withContext false
                }

                promotionDao.deleteAll()
                promotionDao.insertAll(entities)

                val savedCount = promotionDao.count()
                Timber.d("Promociones: %d guardadas en Room (verificación: %d)", entities.size, savedCount)
                true
            } catch (e: Exception) {
                Timber.w(e, "Error actualizando promociones")
                false
            }
        }
    }

    private fun parseSemicolonLine(line: String): List<String>? {
        if (line.isBlank()) return null
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ';' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun parseDate(dateStr: String): java.time.LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            java.time.LocalDate.parse(dateStr.trim())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAllPromotions(): List<PromotionEntity> {
        return promotionDao.getAll()
    }

    suspend fun getLastUpdated(): Long {
        return promotionDao.getLastUpdated() ?: 0L
    }

    suspend fun hasPromotions(brand: String): Boolean {
        return promotionDao.getPromotionsByBrand(brand).isNotEmpty()
    }

    fun schedulePeriodicRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PromotionRefreshWorker>(
            Constants.PROMOTION_REFRESH_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "promotion_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
