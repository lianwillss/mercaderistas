package com.rutamercaderistas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rutamercaderistas.data.preferences.BrandPagesRepository
import com.rutamercaderistas.data.preferences.PreferencesRepository
import dagger.hilt.android.HiltAndroidApp
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
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannel()
        runUpgradeCleanup()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "promociones",
                "Promociones",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notificaciones de promociones por vencer"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
