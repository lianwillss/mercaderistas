package com.rutamercaderistas.ui.screens

import android.app.Activity
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
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
import com.rutamercaderistas.ui.navigation.AllLocalesRoute
import com.rutamercaderistas.ui.navigation.CodProvRoute
import com.rutamercaderistas.ui.navigation.EanSearchRoute
import com.rutamercaderistas.ui.navigation.GlobalSearchRoute
import com.rutamercaderistas.ui.navigation.MainRoute
import com.rutamercaderistas.ui.navigation.ManualRoute
import com.rutamercaderistas.ui.navigation.PromotionsRoute
import com.rutamercaderistas.ui.navigation.SettingsRoute
import androidx.compose.ui.platform.LocalConfiguration
import com.rutamercaderistas.ui.components.AppBottomBar
import com.rutamercaderistas.ui.components.AppNavigationRail
import com.rutamercaderistas.ui.components.BottomBarKey
import com.rutamercaderistas.models.DiaSemana

import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState

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
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val isMainRoute = backStackEntry?.destination?.hasRoute<MainRoute>() ?: true
    SystemBarAppearance(
        lightIcons = !isMainRoute,
        lightNavIcons = true,
    )

    val onGlobalSearch = { navController.navigate(GlobalSearchRoute) }

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

    val isCompactWidth = LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
        bottomBar = if (isCompactWidth) {
            {
                AppBottomBar(
                    selectedKey = currentKey,
                    onNavigate = onBottomNav,
                    stats = routeUiState.stats,
                    marcasConPromo = routeUiState.marcasConPromo,
                    promosExpiringToday = routeUiState.promosExpiringToday,
                    hasPlanillaChanges = syncUiState.syncChanges?.isEmpty == false,
                )
            }
        } else {
            {}
        },
        contentWindowInsets = if (isCompactWidth) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        } else {
            WindowInsets(0.dp)
        },
    ) { scaffoldPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompactWidth) {
                AppNavigationRail(
                    selectedKey = currentKey,
                    onNavigate = onBottomNav,
                    stats = routeUiState.stats,
                    marcasConPromo = routeUiState.marcasConPromo,
                    promosExpiringToday = routeUiState.promosExpiringToday,
                    hasPlanillaChanges = syncUiState.syncChanges?.isEmpty == false,
                )
            }
            NavHost(
                navController = navController,
                startDestination = MainRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(scaffoldPadding),
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
                val isWide = maxWidth >= 600.dp
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
                locales = routeUiState.allLocales,
                promotions = routeUiState.promotionsByBrand.values.flatten(),
                onAddressClick = onAddressClick,
                onBrandClick = onBrandClick,
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
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
