package com.rutamercaderistas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rutamercaderistas.data.preferences.BrandPagesRepository
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.viewmodel.UpdateViewModel
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
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
            }
        } catch (e: Exception) {
            Timber.w(e, "Sentry initialization failed")
        }
    }

    private fun cleanTempApk() {
        applicationScope.launch {
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
        cacheDir.deleteRecursively()
        File(filesDir, "thumbnails").deleteRecursively()
        brandPagesRepository.clearAll()
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(promociones)
            manager.createNotificationChannel(actualizaciones)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
