package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rutamercaderistas.data.stock.DemoStockRepository
import com.rutamercaderistas.data.stock.StockBulkSync
import com.rutamercaderistas.data.stock.StockOrigin
import com.rutamercaderistas.data.stock.StockSyncNotifier
import com.rutamercaderistas.data.stock.StockSyncOutcome
import com.rutamercaderistas.data.stock.StockSyncPhase
import com.rutamercaderistas.data.stock.StockSyncProgress
import com.rutamercaderistas.data.stock.StockSyncRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Descarga el stock completo desde KPI One. Corre como trabajo único con
 * foreground service para sobrevivir a la pantalla apagada; publica el
 * progreso en WorkInfo y en la notificación.
 */
@HiltWorker
class StockSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: StockBulkSync,
    private val repository: DemoStockRepository,
    private val notifier: StockSyncNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val request = StockSyncRequest(
            rutas = inputData.getString(KEY_RUTAS).orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            locales = inputData.getString(KEY_LOCALES).orEmpty()
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet(),
            force = inputData.getBoolean(KEY_FORCE, false),
        )

        Timber.i(
            "StockSyncWorker inició: rutas=%s locales=%s force=%s",
            request.rutas.joinToString(","),
            request.locales.joinToString(","),
            request.force,
        )

        // Si el sistema no deja mostrar la notificación, la descarga sigue:
        // perder el foreground no debe abortar la actualización.
        try {
            setForeground(
                notifier.foreground(
                    StockSyncProgress(
                        StockSyncPhase.CONNECTING,
                        startedAt = System.currentTimeMillis(),
                    ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "StockSyncWorker: no se pudo iniciar foreground")
        }

        return try {
            val outcome = sync.run(request) { progreso ->
                try {
                    setProgress(progreso.toWorkData())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // El progreso es best-effort; no debe cortar la descarga.
                }
                runCatching { notifier.update(progreso) }
            }
            when (outcome) {
                is StockSyncOutcome.Success -> {
                    repository.actualizarDatos(outcome.data, StockOrigin.KPI_ONE)
                    notifier.cancel()
                    Result.success(
                        workDataOf(
                            KEY_ROWS to outcome.meta.rows,
                            KEY_LOCALES to outcome.meta.locales,
                            KEY_FAILED to outcome.meta.failed.size,
                            KEY_DURATION to outcome.meta.durationMs,
                        ),
                    )
                }
                is StockSyncOutcome.Blocked -> {
                    Timber.w("StockSyncWorker bloqueado: %s", outcome.message)
                    notifier.cancel()
                    Result.failure(
                        workDataOf(
                            KEY_ERROR to outcome.message,
                            KEY_BLOCKED to true,
                            KEY_ROWS to outcome.rows,
                            KEY_LOCALES to outcome.locales,
                        ),
                    )
                }
                is StockSyncOutcome.Failure -> {
                    Timber.w("StockSyncWorker falló: %s", outcome.message)
                    notifier.cancel()
                    Result.failure(
                        workDataOf(
                            KEY_ERROR to (outcome.message.ifBlank { "No se pudo actualizar el stock" }),
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            notifier.cancel()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "StockSyncWorker falló")
            notifier.cancel()
            Result.failure(
                workDataOf(KEY_ERROR to (e.message ?: "No se pudo actualizar el stock")),
            )
        }
    }

    companion object {
        const val TAG = "stock-sync"
        const val KEY_FORCE = "force"
        const val KEY_RUTAS = "rutas"
        const val KEY_LOCALES = "locales"

        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ROWS = "rows"
        const val KEY_LOCALES_DONE = "locales_done"
        const val KEY_CURRENT = "current"
        const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"
        const val KEY_BLOCKED = "blocked"
        const val KEY_DURATION = "duration"
    }
}

fun StockSyncProgress.toWorkData(): Data = workDataOf(
    StockSyncWorker.KEY_PHASE to phase.name,
    StockSyncWorker.KEY_DONE to done,
    StockSyncWorker.KEY_TOTAL to total,
    StockSyncWorker.KEY_ROWS to rows,
    StockSyncWorker.KEY_CURRENT to current.take(40),
    StockSyncWorker.KEY_FAILED to failed,
)
