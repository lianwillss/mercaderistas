package com.rutamercaderistas.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.services.PromotionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PromotionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val promotionRepository: PromotionRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (promotionRepository.refresh()) Result.success() else Result.retry()
    }
}
