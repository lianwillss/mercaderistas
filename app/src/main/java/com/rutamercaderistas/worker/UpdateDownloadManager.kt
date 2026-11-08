package com.rutamercaderistas.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rutamercaderistas.data.preferences.PendingUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Encapsula WorkManager para que el ViewModel sea testeable sin Android. */
@Singleton
class UpdateDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val WORK_NAME = "update_apk_download"
    }

    fun enqueue(pending: PendingUpdate) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_APK_URL to pending.apkUrl,
                    UpdateDownloadWorker.KEY_VERSION_NAME to pending.versionName,
                    UpdateDownloadWorker.KEY_VERSION_CODE to pending.versionCode,
                    UpdateDownloadWorker.KEY_VERSION_TAG to pending.versionTag,
                    UpdateDownloadWorker.KEY_APK_SIZE to pending.apkSize,
                    UpdateDownloadWorker.KEY_APK_SHA256 to pending.apkSha256,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun observe(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)
}
