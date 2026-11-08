package com.rutamercaderistas.data.stock

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rutamercaderistas.worker.StockSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Encola la descarga masiva y expone su progreso a la UI. */
@Singleton
class StockSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun start(request: StockSyncRequest): java.util.UUID {
        val datos = workDataOf(
            StockSyncWorker.KEY_FORCE to request.force,
            StockSyncWorker.KEY_RUTAS to request.rutas.joinToString(","),
            StockSyncWorker.KEY_LOCALES to request.locales.joinToString(","),
        )
        val trabajo = OneTimeWorkRequestBuilder<StockSyncWorker>()
            .setInputData(datos)
            // Es una acción manual del usuario: debe arrancar ya. El worker
            // reporta el error de red si no hay conexión.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(StockSyncWorker.TAG)
            .build()
        // REPLACE limpia un intento viejo que haya quedado ENQUEUED.
        workManager.enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.REPLACE, trabajo)
        return trabajo.id
    }

    fun cancel() {
        workManager.cancelUniqueWork(NOMBRE)
    }

    fun observar(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(NOMBRE)

    companion object {
        const val NOMBRE = "stock-sync"
    }
}
