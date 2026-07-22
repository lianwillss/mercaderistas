package com.rutamercaderistas.ui.screens

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.navigation.AllLocalesRoute
import com.rutamercaderistas.ui.navigation.MainRoute
import com.rutamercaderistas.ui.navigation.ManualRoute
import com.rutamercaderistas.ui.navigation.PromotionsRoute
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.components.DaySelector
import com.rutamercaderistas.ui.components.HeaderSection
import com.rutamercaderistas.ui.components.RouteSearchBar
import com.rutamercaderistas.ui.components.ShimmerDaySelector
import com.rutamercaderistas.ui.components.ShimmerLoadingContent
import com.rutamercaderistas.ui.components.ShimmerStatsCards
import com.rutamercaderistas.ui.components.StatsCards
import com.rutamercaderistas.ui.components.StoreCard
import com.rutamercaderistas.ui.components.PromoExpiringSoonModal
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<MainRoute> {
            MainRoute(
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
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
            popEnterTransition = { slideInVertically { -it } },
            popExitTransition = { slideOutVertically { it } },
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
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
            popEnterTransition = { slideInVertically { -it } },
            popExitTransition = { slideOutVertically { it } },
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
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
            popEnterTransition = { slideInVertically { -it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            ManualScreen(onClose = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainRoute(
    routeState: RouteUiState,
    syncState: SyncUiState,
    onCheckUpdate: () -> Unit,
    onNavigateToAllLocales: () -> Unit,
    onNavigateToPromotions: () -> Unit,
    onNavigateToManual: () -> Unit,
    onSetCurrentDay: (DiaSemana?) -> Unit,
    onSelectRoute: (String) -> Unit,
    onInitialSync: () -> Unit,
    onHeaderRefresh: () -> Unit,
    onPullRefresh: () -> Unit,
    onExportRoute: () -> Unit,
    onClearPromotionError: () -> Unit,
    onBrandClick: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    onShareLocal: (String) -> Unit,
) {
    val entries = routeState.entries
    val selectedRoute = routeState.selectedRoute
    val routes = routeState.routes
    val stats = routeState.stats
    val activeDays = routeState.activeDays
    val recentRoutes = routeState.recentRoutes
    val isDataLoaded = routeState.isDataLoaded
    val isSyncing = syncState.isSyncing

    var searchActive by remember { mutableStateOf(false) }
    var showExpiringSoon by remember { mutableStateOf(false) }

    val activeDayNumbers by remember(activeDays) {
        derivedStateOf { activeDays.map { day -> diaDelMes(day) } }
    }

    val pagerState = rememberPagerState(pageCount = { activeDays.size.coerceAtLeast(1) })
    val currentDay = activeDays.getOrNull(pagerState.currentPage) ?: activeDays.firstOrNull()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentDay) {
        onSetCurrentDay(currentDay)
    }

    LaunchedEffect(entries) {
        if (entries.isNotEmpty() && routeState.selectedRoute == null) {
            val lastRoute = routeState.routes.firstOrNull()
            if (lastRoute != null) onSelectRoute(lastRoute)
        }
    }

    LaunchedEffect(routeState.needsInitialLoad) {
        if (routeState.needsInitialLoad) {
            onInitialSync()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HeaderSection(
                isOnline = syncState.isOnline,
                lastSyncRelative = routeState.lastSyncRelative,
                onRefresh = onHeaderRefresh,
                onOpenManual = onNavigateToManual,
                onShare = onExportRoute,
                onCheckUpdate = onCheckUpdate,
                promosExpiringSoon = routeState.promosExpiringSoon,
                onExpiringSoonClick = { showExpiringSoon = true },
            )

            if (isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                syncState.syncPhase?.let { phase ->
                    Text(
                        text = phase,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            RouteSearchBar(
                routes = routes,
                recentRoutes = recentRoutes,
                selectedRoute = selectedRoute,
                onRouteSelected = onSelectRoute,
                onSearchActiveChanged = { searchActive = it },
            )

            if (isDataLoaded && recentRoutes.isNotEmpty() && !searchActive) {
                RecentRoutesRow(
                    routes = recentRoutes.take(5),
                    selectedRoute = selectedRoute,
                    onRouteSelected = onSelectRoute,
                )
            }

            if (isDataLoaded) {
                Spacer(modifier = Modifier.height(12.dp))
                StatsCards(
                    stats = stats,
                    onLocalesClick = onNavigateToAllLocales,
                    onMarcasClick = onNavigateToPromotions,
                    marcasConPromo = routeState.marcasConPromo,
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (activeDays.isNotEmpty()) {
                    DaySelector(
                        days = activeDays,
                        dayNumbers = activeDayNumbers,
                        selectedIndex = pagerState.currentPage,
                        onDaySelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerStatsCards()
                Spacer(modifier = Modifier.height(20.dp))
                ShimmerDaySelector()
            }
        }

        if (isDataLoaded && activeDays.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) {
                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = onPullRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val locales = routeState.currentDayLocales
                        if (locales.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.sin_visitas_dia),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            if (!syncState.isOnline) {
                                item(key = "offline_badge") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val sinConexionCd = stringResource(R.string.sin_conexion_cd)
                                        Box(
                                            modifier = Modifier
                .clip(ComponentShapes.badge)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        ) {
                                            Text(
                                                text = "\uD83D\uDCE6 " + stringResource(R.string.sin_conexion_datos),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.semantics { contentDescription = sinConexionCd },
                                            )
                                        }
                                    }
                                }
                            }
                            itemsIndexed(
                items = locales,
                key = { _, local -> "${local.codigo}|${local.local}|${local.cadena}|${local.formato}" },
            ) { index, local ->
                StoreCard(
                    local = local,
                    index = index,
                    marcaResaltada = null,
                    promotionsByBrand = routeState.promotionsByBrand,
                    onBrandClick = onBrandClick,
                    onAddressClick = onAddressClick,
                    onShareLocal = onShareLocal,
                    modifier = Modifier.animateItem().padding(horizontal = 20.dp),
                )
            }
                        }
                    }
                }
            }
        } else if (!isDataLoaded) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { ShimmerLoadingContent() }
                }
            }
        }
    }

    if (showExpiringSoon && routeState.promosExpiringSoon.isNotEmpty()) {
        PromoExpiringSoonModal(
            promos = routeState.promosExpiringSoon,
            onDismiss = { showExpiringSoon = false },
        )
    }
}

@Composable
private fun RecentRoutesRow(
    routes: List<String>,
    selectedRoute: String?,
    onRouteSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routes.forEach { route ->
            val isSelected = route == selectedRoute
            val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .clip(ComponentShapes.pill)
                    .background(bg)
                    .border(1.dp, borderColor, ComponentShapes.pill)
                    .clickable { onRouteSelected(route) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                text = route,
                style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
            }
        }
    }
}

private fun diaDelMes(dia: DiaSemana): Int {
    val today = LocalDate.now()
    val target = when (dia) {
        DiaSemana.LUNES -> java.time.DayOfWeek.MONDAY
        DiaSemana.MARTES -> java.time.DayOfWeek.TUESDAY
        DiaSemana.MIERCOLES -> java.time.DayOfWeek.WEDNESDAY
        DiaSemana.JUEVES -> java.time.DayOfWeek.THURSDAY
        DiaSemana.VIERNES -> java.time.DayOfWeek.FRIDAY
        DiaSemana.SABADO -> java.time.DayOfWeek.SATURDAY
        DiaSemana.DOMINGO -> java.time.DayOfWeek.SUNDAY
    }
    val diff = target.value - today.dayOfWeek.value
    return today.plusDays(diff.toLong()).dayOfMonth
}


