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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.rutamercaderistas.R
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
import androidx.compose.ui.platform.LocalConfiguration
import com.rutamercaderistas.ui.components.BottomBarKey
import com.rutamercaderistas.ui.components.OnboardingOverlay
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.theme.AppWindowWidth
import com.rutamercaderistas.ui.theme.appWindowWidth

import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState
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
    pendingVersionName: String = "",
    onUpdateNow: () -> Unit = {},
    onUpdateLater: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    val isMainRoute = backStackEntry?.destination?.hasRoute<MainRoute>() ?: true
    SystemBarAppearance(
        lightIcons = !isMainRoute,
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
    val currentKey = when {
        dest?.hasRoute<PromotionsRoute>() == true -> BottomBarKey.MARCAS
        dest?.hasRoute<AllLocalesRoute>() == true -> BottomBarKey.LOCALES
        dest?.hasRoute<CodProvRoute>() == true -> BottomBarKey.CODPROV
        dest?.hasRoute<EanSearchRoute>() == true -> BottomBarKey.EAN
        else -> BottomBarKey.MAIN
    }

    val onBottomNav: (BottomBarKey) -> Unit = { key ->
        when (key) {
            BottomBarKey.MAIN -> navController.navigateTopLevel(MainRoute)
            BottomBarKey.MARCAS -> navController.navigateTopLevel(PromotionsRoute)
            BottomBarKey.LOCALES -> navController.navigateTopLevel(AllLocalesRoute())
            BottomBarKey.CODPROV -> navController.navigateTopLevel(CodProvRoute)
            BottomBarKey.EAN -> navController.navigateTopLevel(EanSearchRoute)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                item(
                    selected = currentKey == BottomBarKey.MAIN,
                    onClick = { onBottomNav(BottomBarKey.MAIN) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (syncUiState.syncChanges?.isEmpty == false) Badge()
                            },
                        ) {
                            Icon(
                                imageVector = if (currentKey == BottomBarKey.MAIN) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.rutero_title)) },
                )
                item(
                    selected = currentKey == BottomBarKey.MARCAS,
                    onClick = { onBottomNav(BottomBarKey.MARCAS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (routeUiState.marcasConPromo > 0) {
                                    Badge { Text(routeUiState.marcasConPromo.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (currentKey == BottomBarKey.MARCAS) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag,
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.stats_marcas_label)) },
                )
                item(
                    selected = currentKey == BottomBarKey.LOCALES,
                    onClick = { onBottomNav(BottomBarKey.LOCALES) },
                    icon = {
                        Icon(
                            imageVector = if (currentKey == BottomBarKey.LOCALES) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.stats_locales_label)) },
                )
                item(
                    selected = currentKey == BottomBarKey.CODPROV,
                    onClick = { onBottomNav(BottomBarKey.CODPROV) },
                    icon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                    label = { Text(stringResource(R.string.stats_cod_prov_label)) },
                )
                item(
                    selected = currentKey == BottomBarKey.EAN,
                    onClick = { onBottomNav(BottomBarKey.EAN) },
                    icon = { Icon(painterResource(R.drawable.ic_barcode), contentDescription = null) },
                    label = { Text(stringResource(R.string.stats_cod_ean_label)) },
                )
            },
            modifier = Modifier.fillMaxSize(),
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
                        pendingVersionName = pendingVersionName,
                        onUpdateNow = onUpdateNow,
                        onUpdateLater = onUpdateLater,
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
