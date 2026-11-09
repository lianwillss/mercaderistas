package com.rutamercaderistas.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.preferences.PreferencesRepository
import androidx.datastore.preferences.preferencesDataStore
import com.rutamercaderistas.ui.navigation.AllLocalesRoute
import com.rutamercaderistas.ui.navigation.CodProvRoute
import com.rutamercaderistas.ui.navigation.EanSearchRoute
import com.rutamercaderistas.ui.navigation.GlobalSearchRoute
import com.rutamercaderistas.ui.navigation.MainRoute
import com.rutamercaderistas.ui.navigation.ManualRoute
import com.rutamercaderistas.ui.navigation.PromotionsRoute
import com.rutamercaderistas.ui.navigation.SettingsRoute
import com.rutamercaderistas.ui.navigation.StockRoute
import com.rutamercaderistas.ui.screens.stock.StockScreen
import androidx.compose.ui.platform.LocalConfiguration
import com.rutamercaderistas.ui.components.BottomBarKey
import com.rutamercaderistas.ui.components.LiquidGlassNavigationScaffold
import com.rutamercaderistas.ui.components.OnboardingOverlay
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.theme.AppWindowWidth
import com.rutamercaderistas.ui.theme.appWindowWidth

import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState
import com.rutamercaderistas.viewmodel.UpdateCardUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences


private val slideUpEnter: EnterTransition = slideInVertically { it }
private val slideDownEnter: EnterTransition = slideInVertically { -it }
private val slideDownExit: ExitTransition = slideOutVertically { it }

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
    }
}

private fun topLevelRoute(key: BottomBarKey): Any = when (key) {
    BottomBarKey.MAIN -> MainRoute
    BottomBarKey.MARCAS -> PromotionsRoute
    BottomBarKey.LOCALES -> AllLocalesRoute()
    BottomBarKey.STOCK -> StockRoute
    BottomBarKey.CODPROV -> CodProvRoute
    BottomBarKey.EAN -> EanSearchRoute
}

@Composable
fun MainScreen(
    routeUiState: RouteUiState,
    syncUiState: SyncUiState,
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit = {},
    onSetCurrentDay: (DiaSemana?) -> Unit,
    onSelectRoute: (String) -> Unit,
    onInitialSync: () -> Unit,
    onHeaderRefresh: () -> Unit,
    onPullRefresh: () -> Unit,
    onRefreshPromotions: () -> Unit,
    onExportRoute: () -> Unit,
    onClearPromotionError: () -> Unit,
    onBrandClick: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    onShareLocal: (String) -> Unit,
    onSharePromo: (PromotionEntity) -> Unit,
    onDismissSyncChanges: () -> Unit = {},
    showUpdateBanner: Boolean = false,
    updateCardState: UpdateCardUiState? = null,
    onUpdateNow: () -> Unit = {},
    onUpdateLater: () -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onRetryUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    /** Pestaña a restaurar tras una actualización; null en un arranque normal. */
    restoreTab: BottomBarKey? = null,
    onTabSelected: (BottomBarKey) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    val isMainRoute = backStackEntry?.destination?.hasRoute<MainRoute>() ?: true
    // Stock pinta su banner morado hasta el borde superior: iconos claros.
    val isStockRoute = backStackEntry?.destination?.hasRoute<StockRoute>() == true
    SystemBarAppearance(
        // Header/stock llevan fondo oscuro: iconos claros.
        lightIcons = !isMainRoute && !isStockRoute,
        lightNavIcons = true,
    )

    val onGlobalSearch = { navController.navigate(GlobalSearchRoute) }

    var showOnboarding by remember { mutableStateOf(false) }
    var refreshCenter by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(Unit) {
        val repo = com.rutamercaderistas.data.preferences.PreferencesRepository(context)
        val done = repo.isOnboardingDone()
        if (!done) {
            showOnboarding = true
        }
    }

    val dest = backStackEntry?.destination
    val isEanRoute = dest?.hasRoute<EanSearchRoute>() == true
    val currentKey = when {
        dest?.hasRoute<PromotionsRoute>() == true -> BottomBarKey.MARCAS
        dest?.hasRoute<AllLocalesRoute>() == true -> BottomBarKey.LOCALES
        dest?.hasRoute<StockRoute>() == true -> BottomBarKey.STOCK
        dest?.hasRoute<CodProvRoute>() == true -> BottomBarKey.CODPROV
        dest?.hasRoute<EanSearchRoute>() == true -> BottomBarKey.EAN
        else -> BottomBarKey.MAIN
    }

    var userNavigated by rememberSaveable { mutableStateOf(false) }
    val onBottomNav: (BottomBarKey) -> Unit = { key ->
        userNavigated = true
        navController.navigateTopLevel(topLevelRoute(key))
        onTabSelected(key)
    }

    // Solo en el arranque posterior a una actualización se vuelve a la
    // pestaña que el usuario tenía. Mantiene [MainRoute] abajo, así Atrás
    // sigue volviendo a Rutero y no cierra la app desde una pestaña. Si el
    // usuario ya tocó otra pestaña, su elección manda.
    var restoredInitialTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(restoreTab) {
        val key = restoreTab ?: return@LaunchedEffect
        if (restoredInitialTab || userNavigated) return@LaunchedEffect
        restoredInitialTab = true
        if (key != BottomBarKey.MAIN) {
            navController.navigateTopLevel(topLevelRoute(key))
        }
    }

    // El catálogo EAN se abre como pantalla completa (tiene su propio título y
    // botón volver): el vidrio inferior se oculta para darle todo el alto a la
    // lista. El NavHost no se condiciona a la visibilidad de la navegación.
    val navigationVisible = !isEanRoute

    Box(modifier = modifier.fillMaxSize()) {
        LiquidGlassNavigationScaffold(
            selectedKey = currentKey,
            onNavigate = onBottomNav,
            navigationVisible = navigationVisible,
            marcasConPromo = routeUiState.marcasConPromo,
            hasPlanillaChanges = syncUiState.syncChanges?.isEmpty == false,
            modifier = Modifier.fillMaxSize(),
            // Cada entrada de navegación devuelve la barra: si quedó escondida
            // por scroll en una ruta, la siguiente no hereda ese estado.
            navigationResetKey = backStackEntry?.id,
        ) {
            NavHost(
                navController = navController,
                startDestination = MainRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            ) {
                composable<MainRoute> {
                    MainRouteContent(
                        routeState = routeUiState,
                        syncState = syncUiState,
                        onCheckUpdate = onCheckUpdate,
                        onNavigateToManual = {
                            navController.navigate(ManualRoute) {
                                launchSingleTop = true
                            }
                        },
                        onSetCurrentDay = onSetCurrentDay,
                        onSelectRoute = onSelectRoute,
                        onInitialSync = onInitialSync,
                        onHeaderRefresh = onHeaderRefresh,
                        onPullRefresh = onPullRefresh,
                        onExportRoute = onExportRoute,
                        onClearPromotionError = onClearPromotionError,
                        onBrandClick = onBrandClick,
                        onAddressClick = onAddressClick,
                        onShareLocal = onShareLocal,
                        onGlobalSearch = onGlobalSearch,
                         onDismissSyncChanges = onDismissSyncChanges,
                         onOpenSettings = { navController.navigate(SettingsRoute) },
                        onRefreshPositioned = { refreshCenter = it },
                        showUpdateBanner = showUpdateBanner,
                        updateCardState = updateCardState,
                        onUpdateNow = onUpdateNow,
                        onUpdateLater = onUpdateLater,
                        onCancelUpdate = onCancelUpdate,
                        onRetryUpdate = onRetryUpdate,
                        onInstallUpdate = onInstallUpdate,
                    )
                }
                composable<StockRoute> {
                    StockScreen(
                        onAbrirAjustes = {
                            navController.navigate(SettingsRoute) { launchSingleTop = true }
                        },
                    )
                }
                composable<AllLocalesRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) { backStackEntry ->
                    val args: AllLocalesRoute = backStackEntry.toRoute()
                    AllLocalesScreen(
                        locales = routeUiState.allLocales,
                        onClose = { navController.popBackStack() },
                        onAddressClick = onAddressClick,
                        initialSearch = args.brand,
                        onGlobalSearch = onGlobalSearch,
                        routeName = routeUiState.selectedRoute,
                    )
                }
                composable<PromotionsRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    BoxWithConstraints {
                         val isWide = appWindowWidth(maxWidth) != AppWindowWidth.Compact
                        if (isWide) {
                            PromotionsListDetailScreen(
                                promotionsByBrand = routeUiState.promotionsByBrand,
                                allLocales = routeUiState.allLocales,
                                chainToLocales = routeUiState.chainToLocales,
                                onAddressClick = onAddressClick,
                                onSharePromo = onSharePromo,
                                onRefresh = onRefreshPromotions,
                                isRefreshing = routeUiState.isPromotionsLoading,
                                promotionErrorMessage = routeUiState.promotionErrorMessage,
                                onDismissError = onClearPromotionError,
                                routeBrands = routeUiState.routeBrands,
                                routeChains = routeUiState.routeChains,
                                onGlobalSearch = onGlobalSearch,
                            )
                        } else {
                            PromotionsOverviewScreen(
                                promotionsByBrand = routeUiState.promotionsByBrand,
                                chainToLocales = routeUiState.chainToLocales,
                                onClose = { navController.popBackStack() },
                                onRefresh = onRefreshPromotions,
                                isRefreshing = routeUiState.isPromotionsLoading,
                                onPromoClick = { brandName ->
                                    navController.navigate(AllLocalesRoute(brand = brandName)) {
                                        popUpTo<MainRoute> { inclusive = false }
                                    }
                                },
                                promotionErrorMessage = routeUiState.promotionErrorMessage,
                                onDismissError = onClearPromotionError,
                                routeBrands = routeUiState.routeBrands,
                                routeChains = routeUiState.routeChains,
                                onSharePromo = onSharePromo,
                                onGlobalSearch = onGlobalSearch,
                            )
                        }
                    }
                }
                composable<ManualRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    ManualScreen(onClose = { navController.popBackStack() })
                }
                composable<SettingsRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable<EanSearchRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    EanSearchScreen(onBack = { navController.popBackStack() })
                }
                composable<CodProvRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    CodProvScreen(onBack = { navController.popBackStack() })
                }
                composable<GlobalSearchRoute>(
                    enterTransition = { slideUpEnter },
                    exitTransition = { slideDownExit },
                    popEnterTransition = { slideDownEnter },
                    popExitTransition = { slideDownExit },
                ) {
                    GlobalSearchScreen(
                        locales = (routeUiState.allRuteroLocales + routeUiState.allLocales)
                            .distinctBy { it.codigo.uppercase() + it.local.uppercase() },
                        promotions = routeUiState.promotionsByBrand.values.flatten(),
                        onAddressClick = onAddressClick,
                        onBrandClick = onBrandClick,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        }
        if (showOnboarding && isMainRoute) {
            OnboardingOverlay(
                visible = true,
                onDismiss = {
                    showOnboarding = false
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.rutamercaderistas.data.preferences.PreferencesRepository(context)
                            .setOnboardingDone()
                    }
                },
                syncTargetCenter = refreshCenter,
            )
        }
    }

@Composable
private fun SystemBarAppearance(
    lightIcons: Boolean,
    lightNavIcons: Boolean,
) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(lightIcons, lightNavIcons) {
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightNavIcons
        onDispose {}
    }
}
