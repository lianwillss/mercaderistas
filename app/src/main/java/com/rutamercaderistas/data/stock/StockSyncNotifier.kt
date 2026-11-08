package com.rutamercaderistas.data.stock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.rutamercaderistas.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificación de la descarga de stock. Es la misma que mantiene vivo el
 * worker con la pantalla apagada; el texto se actualiza con el progreso.
 */
@Singleton
class StockSyncNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        crearCanal()
    }

    fun foreground(progress: StockSyncProgress): ForegroundInfo {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICACION_ID, construir(progress), tipo)
    }

    fun update(progress: StockSyncProgress) {
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICACION_ID, construir(progress))
        }
    }

    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICACION_ID) }
    }

    private fun construir(progress: StockSyncProgress): android.app.Notification {
        val abrir = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendiente = PendingIntent.getActivity(
            context,
            0,
            abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val indeterminada = progress.phase != StockSyncPhase.DOWNLOADING
        return NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Actualizando stock")
            .setContentText(progress.descripcion)
            .setContentIntent(pendiente)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, (progress.fraction * 100).toInt(), indeterminada)
            .build()
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            "Actualización de stock",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progreso de la descarga de stock desde KPI One"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    companion object {
        const val CANAL = "stock_sync"
        const val NOTIFICACION_ID = 7401
    }
}
