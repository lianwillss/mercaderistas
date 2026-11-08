package com.rutamercaderistas

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.rutamercaderistas.R
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
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
import com.rutamercaderistas.ui.components.UpdateModal
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import com.rutamercaderistas.viewmodel.RouteViewModel
import com.rutamercaderistas.viewmodel.SyncViewModel
import com.rutamercaderistas.viewmodel.UpdateViewModel
import com.rutamercaderistas.worker.DailyPromotionNotificationWorker
import com.rutamercaderistas.worker.SyncWorker
import com.rutamercaderistas.worker.UpdateCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.time.Duration
import java.time.ZonedDateTime
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

    private var firstResumeHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            schedulePeriodicSync()
            promotionRepository.schedulePeriodicRefresh()
            scheduleDailyPromotionNotification()
            schedulePeriodicUpdateCheck()
        }
        handleUpdateIntent(intent, checkNetwork = false)

        setContent {

            val showUpdateBanner by updateViewModel.showUpdateBanner.collectAsStateWithLifecycle()
            val updateCard by updateViewModel.card.collectAsStateWithLifecycle()
            val showUpdateSheet by updateViewModel.showUpdateSheet.collectAsStateWithLifecycle()
            val updateMessage by updateViewModel.message.collectAsStateWithLifecycle()
            val routeUiState by routeViewModel.uiState.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.state.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }
            val ctx = LocalContext.current

            val transportMode by preferencesRepository.getTransportModeFlow()
                .map { it ?: "transit" }
                .collectAsStateWithLifecycle(initialValue = "transit")

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

            LaunchedEffect(syncUiState.isSyncing) {
                if (!syncUiState.isSyncing) {
                    routeViewModel.updateSyncLabel()
                }
            }

            LaunchedEffect(updateMessage) {
                val msg = updateMessage ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(msg)
                updateViewModel.clearMessage()
            }

            LaunchedEffect(Unit) {
                // Al abrir la app: consulta obligatoria y, si hay versión
                // nueva, descarga e instala de inmediato.
                updateViewModel.checkForUpdateOnLaunch()
                routeViewModel.loadInitialData()
            }

            // Si hay update visible y falta el permiso de notificaciones,
            // pedirlo una sola vez: es el canal del aviso en 2do plano.
            // Si lo niega, igual quedan banner + diálogo dentro de la app.
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                updateViewModel.onNotifPermissionResult(granted)
            }
            LaunchedEffect(showUpdateBanner) {
                if (showUpdateBanner && updateViewModel.shouldAskNotifPermission()) {
                    updateViewModel.markNotifAsked()
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Auto-sync del rutero al abrir: una sola vez, cuando ya hay ruta
            // seleccionada. Silencioso si no hay cambios (Idle sin snackbar).
            var autoSyncLaunched by remember { mutableStateOf(false) }
            LaunchedEffect(routeUiState.selectedRoute) {
                val route = routeUiState.selectedRoute
                if (!autoSyncLaunched && route != null) {
                    autoSyncLaunched = true
                    syncViewModel.autoSyncIfStale(route)
                }
            }

            MercaderistasTheme {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            )
                        )
                    },
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                ) { innerPadding ->
                    MainScreen(
                        routeUiState = routeUiState,
                        syncUiState = syncUiState,
                        showUpdateBanner = showUpdateBanner,
                        updateCardState = updateCard,
                        onUpdateNow = { updateViewModel.startUpdate() },
                        onUpdateLater = { updateViewModel.suppressUntilTomorrow() },
                        onCancelUpdate = { updateViewModel.cancelDownload() },
                        onRetryUpdate = { updateViewModel.retryDownload() },
                        onInstallUpdate = { updateViewModel.installApk() },
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
                         onDismissSyncChanges = { syncViewModel.clearChanges() },
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

                UpdateModal(
                    visible = showUpdateSheet,
                    state = updateCard,
                    onDismiss = { updateViewModel.dismissSheet() },
                    onUpdate = { updateViewModel.startUpdate() },
                    onLater = { updateViewModel.suppressUntilTomorrow() },
                    onCancel = { updateViewModel.cancelDownload() },
                    onRetry = { updateViewModel.retryDownload() },
                    onInstall = { updateViewModel.installApk() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent, checkNetwork = true)
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.onActivityResumed()
        // El arranque frío ya consulta en LaunchedEffect(Unit); al volver
        // desde segundo plano se revisa con throttle de 15 min.
        if (firstResumeHandled) {
            updateViewModel.checkForUpdateIfStale()
        } else {
            firstResumeHandled = true
        }
    }

    override fun onPause() {
        updateViewModel.onActivityPaused()
        super.onPause()
    }

    private fun handleUpdateIntent(intent: Intent?, checkNetwork: Boolean) {
        if (intent == null) return
        val showUpdate = intent.getBooleanExtra(UpdateViewModel.EXTRA_SHOW_UPDATE, false)
        val installUpdate = intent.getBooleanExtra(UpdateViewModel.EXTRA_INSTALL_UPDATE, false)
        intent.removeExtra(UpdateViewModel.EXTRA_SHOW_UPDATE)
        intent.removeExtra(UpdateViewModel.EXTRA_INSTALL_UPDATE)
        when {
            installUpdate -> updateViewModel.onInstallRequested()
            showUpdate -> {
                updateViewModel.showPendingDialog()
                // En arranque frío LaunchedEffect(Unit) ya consulta; al
                // recibir el intent con la app abierta sí hace falta refrescar.
                if (checkNetwork) {
                    updateViewModel.checkForUpdate(force = true, showFeedback = false)
                }
            }
        }
    }

    private fun scheduleDailyPromotionNotification() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyPromotionNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextMorning(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_promotion_notification",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun delayUntilNextMorning(targetHour: Int = 7): Long {
        val now = ZonedDateTime.now()
        var nextRun = now.toLocalDate().atTime(targetHour, 0).atZone(now.zone)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        return Duration.between(now, nextRun).toMillis()
    }

    private fun schedulePeriodicUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_update_check",
            ExistingPeriodicWorkPolicy.UPDATE,
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
