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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.components.DaySelector
import com.rutamercaderistas.ui.components.HeaderSection
import com.rutamercaderistas.ui.components.RouteSearchBar
import com.rutamercaderistas.ui.components.ShimmerDaySelector
import com.rutamercaderistas.ui.components.ShimmerLoadingContent
import com.rutamercaderistas.ui.components.ShimmerStatsCards
import com.rutamercaderistas.ui.components.StatsCards
import com.rutamercaderistas.ui.components.StoreCard
import com.rutamercaderistas.util.openMaps
import com.rutamercaderistas.viewmodel.PromotionDiagnostic
import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.RouteViewModel
import com.rutamercaderistas.viewmodel.SyncUiState
import com.rutamercaderistas.viewmodel.SyncViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MainScreen(
    routeViewModel: RouteViewModel,
    syncViewModel: SyncViewModel,
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit = {},
) {
    val routeState by routeViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()
    val ctx = LocalContext.current
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier.fillMaxSize(),
    ) {
        composable("main") {
            MainRoute(
                routeState = routeState,
                syncState = syncState,
                routeViewModel = routeViewModel,
                syncViewModel = syncViewModel,
                onCheckUpdate = onCheckUpdate,
                onNavigateToAllLocales = {
                    navController.navigate("all_locales") {
                        launchSingleTop = true
                    }
                },
                onNavigateToPromotions = {
                    navController.navigate("promotions") {
                        launchSingleTop = true
                    }
                },
                onNavigateToManual = {
                    navController.navigate("manual") {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            "all_locales",
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
            popEnterTransition = { slideInVertically { -it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            AllLocalesScreen(
                locales = routeState.allLocales,
                onClose = { navController.popBackStack() },
                onAddressClick = { address -> openMaps(ctx, address) },
            )
        }
        composable(
            "promotions",
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
            popEnterTransition = { slideInVertically { -it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            PromotionsOverviewScreen(
                promotionsByBrand = routeState.promotionsByBrand,
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            "manual",
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
    routeViewModel: RouteViewModel,
    syncViewModel: SyncViewModel,
    onCheckUpdate: () -> Unit,
    onNavigateToAllLocales: () -> Unit,
    onNavigateToPromotions: () -> Unit,
    onNavigateToManual: () -> Unit,
) {
    val entries = routeState.entries
    val selectedRoute = routeState.selectedRoute
    val routes = routeState.routes
    val stats = routeState.stats
    val activeDays = routeState.activeDays
    val recentRoutes = routeState.recentRoutes
    val isDataLoaded = routeState.isDataLoaded
    val isSyncing = syncState.isSyncing
    val ctx = LocalContext.current

    var showPromoDiagnostic by remember { mutableStateOf(false) }
    val diagnostic = routeState.promotionDiagnostic

    val activeDayNumbers by remember(activeDays) {
        derivedStateOf { activeDays.map { day -> diaDelMes(day) } }
    }

    val pagerState = rememberPagerState(pageCount = { activeDays.size.coerceAtLeast(1) })
    val currentDay = activeDays.getOrNull(pagerState.currentPage) ?: activeDays.firstOrNull()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentDay) {
        routeViewModel.setCurrentDay(currentDay)
    }

    LaunchedEffect(entries) {
        if (entries.isNotEmpty() && routeState.selectedRoute == null) {
            val lastRoute = routeState.routes.firstOrNull()
            if (lastRoute != null) routeViewModel.selectRoute(lastRoute)
        }
    }

    LaunchedEffect(routeState.needsInitialLoad) {
        if (routeState.needsInitialLoad) {
            syncViewModel.syncFromDriveWithRouteReload(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HeaderSection(
                isOnline = syncState.isOnline,
                lastSyncRelative = routeState.lastSyncRelative,
                onRefresh = {
                    routeViewModel.updateSyncLabel()
                    syncViewModel.syncFromDriveWithRouteReload(selectedRoute)
                    onCheckUpdate()
                },
                onOpenManual = onNavigateToManual,
                onShare = { routeViewModel.exportRoute() },
                onCheckUpdate = onCheckUpdate,
                onOpenPromoDiagnostic = {
                    routeViewModel.loadPromotionDiagnostic()
                    showPromoDiagnostic = true
                },
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
                onRouteSelected = { route -> routeViewModel.selectRoute(route) },
            )

            if (isDataLoaded && recentRoutes.isNotEmpty()) {
                RecentRoutesRow(
                    routes = recentRoutes.take(5),
                    selectedRoute = selectedRoute,
                    onRouteSelected = { route -> routeViewModel.selectRoute(route) },
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
                modifier = Modifier.fillMaxSize().weight(1f),
            ) {
                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = {
                        syncViewModel.syncFromDriveWithRouteReload(selectedRoute)
                        onCheckUpdate()
                    },
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
                                        text = "Sin visitas este día",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
            itemsIndexed(
                items = locales,
                key = { _, local -> local.codigo + local.local },
            ) { index, local ->
                StoreCard(
                    local = local,
                    index = index,
                    marcaResaltada = null,
                    promotionsByBrand = routeState.promotionsByBrand,
                    onBrandClick = { brandName ->
                        BrandReference.openPdfForBrand(ctx, brandName)
                    },
                    onAddressClick = { address -> openMaps(ctx, address) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
                        }
                    }
                }
            }
        } else if (!isDataLoaded) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
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

    if (showPromoDiagnostic && diagnostic != null) {
        PromotionDiagnosticDialog(
            diagnostic = diagnostic,
            onDismiss = {
                showPromoDiagnostic = false
                routeViewModel.clearDiagnostic()
            },
        )
    }
}

@Composable
private fun PromotionDiagnosticDialog(
    diagnostic: PromotionDiagnostic,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Diagnóstico de Promociones")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = "Última descarga: ${diagnostic.lastUpdated}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Total promociones: ${diagnostic.totalPromos}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Marcas con promos: ${diagnostic.distinctBrands}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Primeras 20 promociones:",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = diagnostic.first20,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cruce con local actual:",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = diagnostic.currentLocaleResult.ifEmpty { "(selecciona un local primero)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
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
            val bg = if (isSelected) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            val textColor = if (isSelected) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onRouteSelected(route) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = route,
                    style = MaterialTheme.typography.labelMedium,
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


