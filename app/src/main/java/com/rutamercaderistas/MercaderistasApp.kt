package com.rutamercaderistas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.rutamercaderistas.data.preferences.BrandPagesRepository
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.stock.StockSyncNotifier
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateNotifier
import com.rutamercaderistas.viewmodel.SyncViewModel
import com.rutamercaderistas.viewmodel.UpdateViewModel
import com.rutamercaderistas.worker.UpdateDownloadManager
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import io.sentry.android.timber.SentryTimberIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class MercaderistasApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var brandPagesRepository: BrandPagesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initSentry()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannels()
        cleanTempApk()
        runUpgradeCleanup()
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        try {
            SentryAndroid.init(this) { options ->
                options.dsn = dsn
                options.tracesSampleRate = 0.2
                options.environment = BuildConfig.BUILD_TYPE
                options.addIntegration(SentryTimberIntegration())
            }
        } catch (e: Exception) {
            Timber.w(e, "Sentry initialization failed")
        }
    }

    private fun cleanTempApk() {
        applicationScope.launch {
            val context = this@MercaderistasApp
            val pending = preferencesRepository.getPendingUpdate()
            val pendingNewer = pending != null && UpdateChecker.isPendingNewer(
                pending.versionTag,
                pending.versionCode,
                BuildConfig.VERSION_CODE,
            )
            val dir = ApkDownloader.apkDir(context)
            val apk = ApkDownloader.apkFile(context)
            val apkValid = apk.exists() &&
                ApkDownloader.readApkVersionCode(context, apk) > BuildConfig.VERSION_CODE

            // Se conserva el APK listo o el .part de una descarga en curso.
            if (apkValid || pendingNewer) {
                Timber.i("Se conserva la descarga pendiente de instalación")
                if (!apkValid && apk.exists()) apk.delete()
                dir.listFiles()
                    ?.filter { it.name != ApkDownloader.APK_NAME && it.name != ApkDownloader.PART_NAME }
                    ?.forEach { it.deleteRecursively() }
            } else {
                dir.deleteRecursively()
            }
            // Ruta legacy de versiones anteriores.
            File(cacheDir, "apk").deleteRecursively()
        }
    }

    private fun runUpgradeCleanup() {
        applicationScope.launch {
            val lastVersion = preferencesRepository.getLastVersionCode()
            if (lastVersion != BuildConfig.VERSION_CODE) {
                Timber.i("Actualización detectada: $lastVersion → ${BuildConfig.VERSION_CODE}. Limpiando caché…")
                performUpgradeCleanup(lastVersion)
            }
        }
    }

    private suspend fun performUpgradeCleanup(previousVersion: Int) {
        // Ya hay una versión instalada: el aviso, el APK y la descarga sobran.
        WorkManager.getInstance(this).cancelUniqueWork(UpdateDownloadManager.WORK_NAME)
        UpdateNotifier.cancelUpdate(this)
        File(filesDir, ApkDownloader.UPDATES_DIR).deleteRecursively()
        cacheDir.deleteRecursively()
        File(filesDir, "thumbnails").deleteRecursively()
        File(filesDir, com.rutamercaderistas.models.PdfDownloader.PDF_FILE_NAME).delete()
        brandPagesRepository.clearAll()
        preferencesRepository.clearPendingUpdate()
        preferencesRepository.setLastNotifiedUpdateTag("")
        preferencesRepository.setLastNotifiedUpdateCode(0)
        preferencesRepository.setUpdateSuppressedUntil(0)
        preferencesRepository.setLastVersionCode(BuildConfig.VERSION_CODE)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val promociones = NotificationChannel(
                "promociones",
                "Promociones",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notificaciones de promociones por vencer"
            }
            val actualizaciones = NotificationChannel(
                UpdateViewModel.UPDATE_CHANNEL_ID,
                "Actualizaciones",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificaciones de nuevas versiones disponibles"
            }
            val sincronizacion = NotificationChannel(
                SyncViewModel.SYNC_CHANNEL_ID,
                getString(R.string.notif_canal_sync_nombre),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notif_canal_sync_desc)
            }
            val stock = NotificationChannel(
                StockSyncNotifier.CANAL,
                "Actualización de stock",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progreso de la descarga de stock desde KPI One"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(promociones)
            manager.createNotificationChannel(actualizaciones)
            manager.createNotificationChannel(sincronizacion)
            manager.createNotificationChannel(stock)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
