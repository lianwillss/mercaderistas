package com.rutamercaderistas.ui.screens

import android.app.Activity
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import androidx.navigation.toRoute
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.navigation.AllLocalesRoute
import com.rutamercaderistas.ui.navigation.MainRoute
import com.rutamercaderistas.ui.navigation.ManualRoute
import com.rutamercaderistas.ui.navigation.PromotionsRoute
import com.rutamercaderistas.models.DiaSemana

import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState

private val slideUpEnter: EnterTransition = slideInVertically { it }
private val slideDownEnter: EnterTransition = slideInVertically { -it }
private val slideDownExit: ExitTransition = slideOutVertically { it }

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
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val isMainRoute = backStackEntry?.destination?.hasRoute<MainRoute>() ?: true
    SystemBarAppearance(
        lightIcons = !isMainRoute,
        lightNavIcons = true,
    )

    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<MainRoute> {
            MainRouteContent(
                routeState = routeUiState,
                syncState = syncUiState,
                onCheckUpdate = onCheckUpdate,
                onNavigateToAllLocales = {
                    navController.navigate(AllLocalesRoute()) {
                        launchSingleTop = true
                    }
                },
                onNavigateToPromotions = {
                    navController.navigate(PromotionsRoute) {
                        launchSingleTop = true
                    }
                },
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
            )
        }
        composable<PromotionsRoute>(
            enterTransition = { slideUpEnter },
            exitTransition = { slideDownExit },
            popEnterTransition = { slideDownEnter },
            popExitTransition = { slideDownExit },
        ) {
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
            )
        }
        composable<ManualRoute>(
            enterTransition = { slideUpEnter },
            exitTransition = { slideDownExit },
            popEnterTransition = { slideDownEnter },
            popExitTransition = { slideDownExit },
        ) {
            ManualScreen(onClose = { navController.popBackStack() })
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
