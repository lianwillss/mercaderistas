package com.rutamercaderistas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.services.PromotionRepository
import com.rutamercaderistas.ui.screens.MainScreen
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import com.rutamercaderistas.viewmodel.RouteViewModel
import com.rutamercaderistas.viewmodel.SyncViewModel
import com.rutamercaderistas.viewmodel.UpdateViewModel
import com.rutamercaderistas.worker.DailyPromotionNotificationWorker
import com.rutamercaderistas.worker.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val updateViewModel: UpdateViewModel by viewModels()
    private val routeViewModel: RouteViewModel by viewModels()
    private val syncViewModel: SyncViewModel by viewModels()

    @Inject
    lateinit var promotionRepository: PromotionRepository

    @Inject
    lateinit var brandReference: BrandReference

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createUpdateNotificationChannel()
        if (savedInstanceState == null) {
            schedulePeriodicSync()
            promotionRepository.schedulePeriodicRefresh()
            scheduleDailyPromotionNotification()
            if (intent.getBooleanExtra(UpdateViewModel.EXTRA_SHOW_UPDATE, false)) {
                updateViewModel.checkForUpdate(force = true, showFeedback = false)
            }
        }

        setContent {

            val updateState by updateViewModel.state.collectAsStateWithLifecycle()
            val routeState by routeViewModel.uiState.collectAsStateWithLifecycle()
            val syncState by syncViewModel.state.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(syncState.snackbarMessage) {
                syncState.snackbarMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    syncViewModel.clearSnackbar()
                }
            }

            LaunchedEffect(routeState.snackbarMessage) {
                routeState.snackbarMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    routeViewModel.clearSnackbar()
                }
            }

            LaunchedEffect(updateState.snackbarMessage) {
                updateState.snackbarMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    updateViewModel.clearSnackbar()
                }
            }

            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdate(showFeedback = false)
                routeViewModel.loadInitialData()
            }

            MercaderistasTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) {
                    MainScreen(
                        routeViewModel = routeViewModel,
                        syncViewModel = syncViewModel,
                        brandReference = brandReference,
                        preferencesRepository = preferencesRepository,
                        onCheckUpdate = { updateViewModel.checkForUpdate(force = true) },
                        modifier = Modifier,
                    )
                }

                if (updateState.showDialog) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }

                    IosModal(
                        visible = visible,
                        onDismiss = { updateViewModel.dismissDialog() },
                        title = "Actualización disponible",
                        confirmText = if (!updateState.downloading) "Actualizar" else null,
                        onConfirm = { updateViewModel.downloadAndInstall() },
                        dismissText = if (!updateState.downloading) "Más tarde" else null,
                        onDismissAction = { updateViewModel.suppressUntilTomorrow() },
                    ) {
                        if (updateState.downloading) {
                            Text(
                                text = "Descargando versión ${updateState.versionName}…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { updateState.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${updateState.downloadProgress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Versión ${updateState.versionName} disponible. ¿Quieres actualizar ahora?",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDailyPromotionNotification() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyPromotionNotificationWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_promotion_notification",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createUpdateNotificationChannel() {
        val channel = NotificationChannel(
            UpdateViewModel.UPDATE_CHANNEL_ID,
            "Actualizaciones",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notificaciones de nuevas versiones disponibles"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

}
