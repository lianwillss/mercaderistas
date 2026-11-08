package com.rutamercaderistas.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Acción "Cancelar" de la notificación de progreso. */
class UpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateNotifier.ACTION_CANCEL_DOWNLOAD) return
        val pendingResult = goAsync()
        WorkManager.getInstance(context).cancelUniqueWork(UpdateDownloadManager.WORK_NAME)
        UpdateNotifier.cancelProgress(context)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ApkDownloader.clearPartial(context) }
            pendingResult.finish()
        }
    }
}
