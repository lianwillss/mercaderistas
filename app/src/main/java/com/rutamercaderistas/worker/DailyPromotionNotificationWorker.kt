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
import com.rutamercaderistas.domain.usecase.CountExpiringPromotionsUseCase
import com.rutamercaderistas.services.PromotionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyPromotionNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val promotionRepository: PromotionRepository,
    private val countExpiring: CountExpiringPromotionsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val allPromos = promotionRepository.getAllPromotions()
        val expiringToday = countExpiring.getExpiringToday(allPromos)
        val expiringTomorrow = countExpiring.getExpiringTomorrow(allPromos)

        if (expiringToday.isEmpty() && expiringTomorrow.isEmpty()) {
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (expiringToday.isNotEmpty()) {
            applicationContext.resources.getQuantityString(
                R.plurals.notif_promo_hoy, expiringToday.size, expiringToday.size,
            )
        } else {
            applicationContext.resources.getQuantityString(
                R.plurals.notif_promo_manana, expiringTomorrow.size, expiringTomorrow.size,
            )
        }

        val brands = expiringToday.map { it.brand }.distinct().sorted()
        val brandSummary = if (brands.size <= 3) {
            brands.joinToString(", ")
        } else {
            brands.take(3).joinToString(", ") + " " +
                applicationContext.getString(R.string.notif_y_mas, brands.size - 3)
        }

        val bigText = buildString {
            expiringToday.forEach { promo ->
                val detail = if (promo.price.isNotBlank()) {
                    applicationContext.getString(
                        R.string.notif_detalle_promo_precio,
                        promo.brand, promo.productName, promo.price,
                    )
                } else {
                    applicationContext.getString(
                        R.string.notif_detalle_promo, promo.brand, promo.productName,
                    )
                }
                append("• $detail\n")
            }
            if (expiringTomorrow.isNotEmpty()) {
                append(applicationContext.getString(R.string.notif_manana, expiringTomorrow.size))
            }
        }.trim()

        val notification = NotificationCompat.Builder(applicationContext, "promociones")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(brandSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
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

        return Result.success()
    }
}