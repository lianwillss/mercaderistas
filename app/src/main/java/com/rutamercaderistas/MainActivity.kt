package com.rutamercaderistas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.rutamercaderistas.util.openMaps
import com.rutamercaderistas.ui.screens.MainScreen
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import com.rutamercaderistas.viewmodel.RouteViewModel
import com.rutamercaderistas.viewmodel.SyncViewModel
import com.rutamercaderistas.viewmodel.UpdateUiState
import com.rutamercaderistas.viewmodel.UpdateViewModel
import com.rutamercaderistas.worker.DailyPromotionNotificationWorker
import com.rutamercaderistas.worker.SyncWorker
import com.rutamercaderistas.worker.UpdateCheckWorker
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
            schedulePeriodicUpdateCheck()
            if (intent.getBooleanExtra(UpdateViewModel.EXTRA_SHOW_UPDATE, false)) {
                updateViewModel.checkForUpdate(force = true, showFeedback = false)
            }
        }

        setContent {

            val updateState by updateViewModel.state.collectAsStateWithLifecycle()
            val routeUiState by routeViewModel.uiState.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.state.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }
            val ctx = LocalContext.current

            var transportMode by remember { mutableStateOf("transit") }
            LaunchedEffect(Unit) {
                transportMode = preferencesRepository.getTransportMode() ?: "transit"
            }

            LaunchedEffect(syncUiState.snackbarMessage) {
                syncUiState.snackbarMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    syncViewModel.clearSnackbar()
                }
            }

            LaunchedEffect(routeUiState.snackbarMessage) {
                routeUiState.snackbarMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    routeViewModel.clearSnackbar()
                }
            }

            LaunchedEffect(updateState) {
                val msg = (updateState as? UpdateUiState.Message)?.text ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(msg)
                updateViewModel.clearSnackbar()
            }

            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdate(showFeedback = false)
                routeViewModel.loadInitialData()
            }

            MercaderistasTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    MainScreen(
                        routeUiState = routeUiState,
                        syncUiState = syncUiState,
                        onCheckUpdate = { updateViewModel.checkForUpdate(force = true) },
                        onSetCurrentDay = { routeViewModel.setCurrentDay(it) },
                        onSelectRoute = { routeViewModel.selectRoute(it) },
                        onInitialSync = { syncViewModel.syncFromDriveWithRouteReload(null) },
                        onHeaderRefresh = {
                            routeViewModel.updateSyncLabel()
                            syncViewModel.syncFromDriveWithRouteReload(routeUiState.selectedRoute)
                            updateViewModel.checkForUpdate(force = true)
                        },
                        onPullRefresh = {
                            routeViewModel.refreshPromotions()
                            syncViewModel.syncFromDriveWithRouteReload(routeUiState.selectedRoute)
                            updateViewModel.checkForUpdate(force = true)
                        },
                        onRefreshPromotions = {
                            routeViewModel.refreshPromotions()
                            syncViewModel.syncFromDriveWithRouteReload(routeUiState.selectedRoute)
                        },
                        onExportRoute = { routeViewModel.exportRoute() },
                        onClearPromotionError = { routeViewModel.clearPromotionError() },
                        onBrandClick = { brandName -> brandReference.openPdfForBrand(ctx, brandName) },
                        onAddressClick = { address -> openMaps(ctx, address, transportMode) },
                        onShareLocal = { text ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.compartir_local)))
                        },
                        onSharePromo = { promo ->
                            val text = buildString {
                                appendLine("\uD83D\uDCE3 ${promo.productName}")
                                if (promo.price.isNotBlank()) appendLine("\uD83D\uDCB0 ${promo.price}")
                                if (promo.chain.isNotBlank()) appendLine("\uD83C\uDFEA ${promo.chain}")
                                if (promo.brand.isNotBlank()) appendLine("\uD83C\uDFF7 ${promo.brand}")
                                if (promo.startDate.isNotBlank() || promo.endDate.isNotBlank()) {
                                    append("\uD83D\uDCC5 ")
                                    if (promo.startDate.isNotBlank()) append("${promo.startDate} → ")
                                    appendLine(promo.endDate)
                                }
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text.trimEnd())
                            }
                            ctx.startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.compartir_promocion)))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                when (val state = updateState) {
                    is UpdateUiState.Dialog -> {
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }

                        IosModal(
                            visible = visible,
                            onDismiss = { updateViewModel.dismissDialog() },
                            title = stringResource(R.string.actualizacion_disponible),
                            confirmText = if (!state.downloading) stringResource(R.string.actualizar_btn) else null,
                            onConfirm = { updateViewModel.downloadAndInstall() },
                            dismissText = if (!state.downloading) stringResource(R.string.mas_tarde) else null,
                            onDismissAction = { updateViewModel.suppressUntilTomorrow() },
                        ) {
                            if (state.downloading) {
                                Text(
                                    text = stringResource(R.string.descargando_version, state.versionName),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.download_progress_percent, state.downloadProgress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.version_disponible, state.versionName),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> Unit
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

    private fun schedulePeriodicUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_update_check",
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
