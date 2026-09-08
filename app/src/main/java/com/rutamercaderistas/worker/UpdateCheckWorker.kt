package com.rutamercaderistas.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.MainActivity
import com.rutamercaderistas.R
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.viewmodel.UpdateViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val info = UpdateChecker.check(BuildConfig.VERSION_CODE)
            if (info.available) {
                if (preferencesRepository.getLastNotifiedUpdateCode() == info.versionCode) {
                    Timber.i("Update %s ya notificado, se omite", info.versionName)
                    return Result.success()
                }
                preferencesRepository.setLastNotifiedUpdateCode(info.versionCode)
                Timber.i("Update disponible en segundo plano: %s", info.versionName)

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(UpdateViewModel.EXTRA_SHOW_UPDATE, true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val notification = NotificationCompat.Builder(applicationContext, UpdateViewModel.UPDATE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(applicationContext.getString(R.string.update_notif_disponible))
                    .setContentText(applicationContext.getString(R.string.update_notif_nueva, info.versionName))
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(applicationContext.getString(R.string.update_notif_nueva, info.versionName))
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(applicationContext)
                    .notify(UpdateViewModel.UPDATE_NOTIFICATION_ID, notification)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error en UpdateCheckWorker")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
