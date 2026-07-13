package com.rutamercaderistas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        if (savedInstanceState == null) {
            schedulePeriodicSync()
            promotionRepository.schedulePeriodicRefresh()
            scheduleDailyPromotionNotification()
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
                    AlertDialog(
                        onDismissRequest = { updateViewModel.dismissDialog() },
                        title = {
                            Text(
                                text = "Actualización disponible",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        },
                        text = {
                            if (updateState.downloading) {
                                Column {
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
                                }
                            } else {
                                Text(
                                    text = "Versión ${updateState.versionName} disponible. ¿Quieres actualizar ahora?",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        confirmButton = {
                            if (!updateState.downloading) {
                                Button(
                                    onClick = { updateViewModel.downloadAndInstall() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text("Actualizar", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        },
                        dismissButton = {
                            if (!updateState.downloading) {
                                TextButton(onClick = { updateViewModel.suppressUntilTomorrow() }) {
                                    Text("Más tarde")
                                }
                            }
                        },
                    )
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
