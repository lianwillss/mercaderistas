package com.rutamercaderistas.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rutamercaderistas.MainActivity
import com.rutamercaderistas.R
import com.rutamercaderistas.services.PromotionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class DailyPromotionNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val promotionRepository: PromotionRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val allPromos = promotionRepository.getAllPromotions()
        val today = LocalDate.now()
        val expiringToday = allPromos.filter { promo ->
            try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
            catch (_: Exception) { false }
        }

        if (expiringToday.isNotEmpty()) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val brands = expiringToday.map { it.brand }.distinct().sorted()
            val brandSummary = if (brands.size <= 3) brands.joinToString(", ")
            else brands.take(3).joinToString(", ") + " y ${brands.size - 3} más"

            val notification = NotificationCompat.Builder(applicationContext, "promociones")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle("${
                    if (expiringToday.size == 1) "\uD83D\uDD25 1 promo hoy"
                    else "\uD83D\uDD25 ${expiringToday.size} promos hoy"
                }")
                .setContentText(brandSummary)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(expiringToday.joinToString("\n") {
                            "\u2022 ${it.brand} — ${it.productName}${if (it.price.isNotBlank()) " (${it.price})" else ""}"
                        })
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(applicationContext)
                    .notify(1001, notification)
            }
        }

        return Result.success()
    }
}
