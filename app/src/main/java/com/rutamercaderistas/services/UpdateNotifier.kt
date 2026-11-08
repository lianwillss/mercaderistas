package com.rutamercaderistas.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rutamercaderistas.MainActivity
import com.rutamercaderistas.R
import com.rutamercaderistas.worker.UpdateNotificationReceiver

/**
 * Canal único del actualizador. Concentra textos, intents y flags para que
 * el aviso pendiente, el progreso y el "lista para instalar" se comporten
 * igual vengan del ViewModel o del worker en segundo plano.
 */
object UpdateNotifier {

    const val CHANNEL_ID = "app_updates"
    const val PENDING_NOTIFICATION_ID = 2001
    const val PROGRESS_NOTIFICATION_ID = 2002

    const val EXTRA_SHOW_UPDATE = "show_update"
    const val EXTRA_INSTALL_UPDATE = "install_update"

    const val ACTION_CANCEL_DOWNLOAD = "com.rutamercaderistas.action.CANCEL_UPDATE_DOWNLOAD"

    private const val REQUEST_SHOW = 100
    private const val REQUEST_INSTALL = 101
    private const val REQUEST_CANCEL = 102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_notif_canal_nombre),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.update_notif_canal_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Aviso persistente: no se puede descartar y sobrevive hasta instalar. */
    fun postPending(context: Context, versionName: String) {
        ensureChannel(context)
        val showIntent = contentIntent(context, EXTRA_SHOW_UPDATE, REQUEST_SHOW)
        val notification = base(
            context,
            context.getString(R.string.update_notif_disponible),
            context.getString(R.string.update_notif_nueva, versionName),
        )
            .setContentIntent(showIntent)
            .addAction(
                R.drawable.ic_update,
                context.getString(R.string.actualizar_btn),
                showIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        notify(context, PENDING_NOTIFICATION_ID, notification)
    }

    fun postReady(context: Context, versionName: String) {
        ensureChannel(context)
        val installIntent = contentIntent(context, EXTRA_INSTALL_UPDATE, REQUEST_INSTALL)
        val text = if (versionName.isBlank()) {
            context.getString(R.string.update_notif_lista)
        } else {
            context.getString(R.string.update_notif_toca, versionName)
        }
        val notification = base(
            context,
            context.getString(R.string.update_notif_lista),
            text,
        )
            .setContentIntent(installIntent)
            .addAction(
                R.drawable.ic_update,
                context.getString(R.string.update_instalar),
                installIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        notify(context, PENDING_NOTIFICATION_ID, notification)
    }

    fun postDownloadError(context: Context, message: String) {
        ensureChannel(context)
        val showIntent = contentIntent(context, EXTRA_SHOW_UPDATE, REQUEST_SHOW)
        val text = message.ifBlank { context.getString(R.string.update_notif_error_texto) }
        val notification = base(
            context,
            context.getString(R.string.update_notif_error_titulo),
            text,
        )
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(showIntent)
            .addAction(
                R.drawable.ic_refresh,
                context.getString(R.string.update_reintentar),
                showIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        notify(context, PENDING_NOTIFICATION_ID, notification)
    }

    /** Notificación de foreground de la descarga (se actualiza en cada avance). */
    fun progressNotification(
        context: Context,
        versionName: String,
        percent: Int,
        totalBytes: Long,
    ): Notification {
        ensureChannel(context)
        val showIntent = contentIntent(context, EXTRA_SHOW_UPDATE, REQUEST_SHOW)
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CANCEL,
            Intent(context, UpdateNotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return base(
            context,
            context.getString(R.string.update_notif_descargando),
            context.getString(R.string.update_notif_progreso, percent),
        )
            .setProgress(100, percent.coerceIn(0, 100), totalBytes <= 0L)
            .setContentIntent(showIntent)
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.update_cancelar),
                cancelIntent,
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    fun cancelUpdate(context: Context) {
        cancel(context, PENDING_NOTIFICATION_ID)
        cancel(context, PROGRESS_NOTIFICATION_ID)
    }

    fun cancelProgress(context: Context) {
        cancel(context, PROGRESS_NOTIFICATION_ID)
    }

    private fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (!hasPermission(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun base(context: Context, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(false)

    private fun contentIntent(context: Context, extra: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(extra, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
