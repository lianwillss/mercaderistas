package com.rutamercaderistas.services

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.local.PromotionDao
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.data.network.parseCsvLine
import com.rutamercaderistas.data.network.parseDate
import com.rutamercaderistas.worker.PromotionRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromotionRepository @Inject constructor(
    private val promotionDao: PromotionDao,
    @ApplicationContext private val context: Context,
) {
    private val refreshing = AtomicBoolean(false)

    suspend fun refresh(): Boolean {
        if (!refreshing.compareAndSet(false, true)) {
            Timber.d("Promociones: refresh ya en curso — salteando")
            return true
        }
        return try {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    Timber.d("Promociones: descargando CSV desde %s", Constants.PROMOTIONS_CSV_URL)
                    downloadBytes(Constants.PROMOTIONS_CSV_URL).getOrElse {
                        Timber.w("Promociones: falló descarga CSV")
                        return@withContext null
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error descargando CSV")
                    return@withContext null
                }
            }

            if (bytes == null) return false

            withContext(Dispatchers.Default) {
                try {
                    val csv = bytes.decodeToString().removePrefix("\uFEFF")
                    Timber.d("Promociones: descargados %d bytes", bytes.size)

                    val lines = csv.lines()
                        .filter { it.isNotBlank() }
                        .toMutableList()
                    if (lines.isEmpty()) return@withContext true

                    val headers = parseCsvLine(lines.removeAt(0)) ?: emptyList()
                    val mapper = ColumnMapper()
                    mapper.map(headers)
                    val idxBrand = mapper.getIndex("MARCA", "BRAND", "PRODUCTO")
                    val idxChain = mapper.getIndex("CADENA", "CHAIN", "TIENDA")
                    val idxSubChain = mapper.getIndex("SUBCADENA", "SUBTIENDA")
                    val idxStart = mapper.getIndex("INICIO", "DESDE", "INICIAL")
                    val idxEnd = mapper.getIndex("FINAL", "HASTA", "FINALIZACION")
                    val idxProduct = mapper.getIndex("SKU", "NOMBRE", "PRODUCTO")
                    val idxPrice = mapper.findFirstContaining("PRECIO", "% PROMOCION", "OFERTA")
                    if (idxBrand == -1 || idxChain == -1 || idxStart == -1 || idxEnd == -1) {
                        Timber.w("Promociones: header no reconocido (%d cols): %s", headers.size, headers.joinToString(","))
                        return@withContext true
                    }
                    val maxIdx = maxOf(idxBrand, idxChain, idxSubChain, idxStart, idxEnd, idxProduct, idxPrice)

                    Timber.d("Promociones: %d líneas en CSV (sin header)", lines.size)

                    val today = java.time.LocalDate.now()
                    val todayStr = today.toString()
                    val now = System.currentTimeMillis()
                    var totalRead = 0
                    var filteredByDate = 0
                    val entities = lines.mapNotNull { line ->
                        val cols = parseCsvLine(line) ?: return@mapNotNull null
                        if (cols.size <= maxIdx) {
                            Timber.d("Promociones: línea saltada (solo %d columnas): %s", cols.size, line.take(80))
                            return@mapNotNull null
                        }

                        val brand = cols[idxBrand].trim()
                        val chainRaw = cols[idxChain].trim()
                        val subChainRaw = if (idxSubChain != -1 && idxSubChain < cols.size) cols[idxSubChain].trim() else ""
                        val chain = subChainRaw.takeIf { it.isNotBlank() } ?: chainRaw
                        val start = cols[idxStart].trim()
                        val end = cols[idxEnd].trim()
                        val product = if (idxProduct in cols.indices) cols[idxProduct].trim() else ""
                        val price = if (idxPrice in cols.indices) cols[idxPrice].trim() else ""

                        totalRead++

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
                            brand = brand, chain = chain, productName = product,
                            price = price, startDate = start, endDate = end,
                            lastUpdated = now,
                        )
                    }

                    Timber.d("Promociones: %d leídas, %d filtradas por fecha, %d vigentes",
                        totalRead, filteredByDate, entities.size)
                    val chainStats = entities.groupBy { it.chain }.map { (c, list) -> "$c=${list.size}" }
                    Timber.d("Promociones: vigentes por cadena: %s", chainStats.joinToString(", "))
                    val brandStats = entities.groupBy { it.brand }.map { (b, list) -> "$b=${list.size}" }.take(20)
                    Timber.d("Promociones: marcas vigentes (primeras 20): %s", brandStats.joinToString(", "))

                    val deleted = promotionDao.deleteExpired(todayStr)
                    if (deleted > 0) Timber.d("Promociones: %d vencidas eliminadas", deleted)

                    if (entities.isEmpty()) {
                        Timber.w("Promociones: 0 vigentes después de filtro — se conserva caché anterior")
                        return@withContext true
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
        } finally {
            refreshing.set(false)
        }
    }

    suspend fun getAllPromotions(): List<PromotionEntity> {
        return promotionDao.getAll()
    }

    suspend fun getLastUpdated(): Long {
        return promotionDao.getLastUpdated() ?: 0L
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "promotion_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
