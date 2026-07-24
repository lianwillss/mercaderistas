package com.rutamercaderistas.ui.screens

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
import com.rutamercaderistas.R
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.components.DaySelector
import com.rutamercaderistas.ui.components.HeaderSection
import com.rutamercaderistas.ui.components.PromoExpiringSoonModal
import com.rutamercaderistas.ui.components.RouteSearchBar
import com.rutamercaderistas.ui.components.ShimmerDaySelector
import com.rutamercaderistas.ui.components.ShimmerLoadingContent
import com.rutamercaderistas.ui.components.ShimmerStatsCards
import com.rutamercaderistas.ui.components.StatsCards
import com.rutamercaderistas.ui.components.StoreCard
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRouteContent(
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
    val s = rs()
    val dimens = LocalAppDimens.current

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
                            .padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs),
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingXs))

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
                Spacer(modifier = Modifier.height(dimens.spacingMd))
                StatsCards(
                    stats = stats,
                    onLocalesClick = onNavigateToAllLocales,
                    onMarcasClick = onNavigateToPromotions,
                    marcasConPromo = routeState.marcasConPromo,
                )
                Spacer(modifier = Modifier.height(dimens.spacingXxl))
                if (activeDays.isNotEmpty()) {
                    DaySelector(
                        days = activeDays,
                        dayNumbers = activeDayNumbers,
                        selectedIndex = pagerState.currentPage,
                        onDaySelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                    )
                    Spacer(modifier = Modifier.height(dimens.spacingSm))
                }
            } else {
                Spacer(modifier = Modifier.height(dimens.spacingMd))
                ShimmerStatsCards()
                Spacer(modifier = Modifier.height(dimens.spacingXxl))
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
                        contentPadding = PaddingValues(bottom = dimens.contentPaddingBottom),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val locales = routeState.currentDayLocales
                        if (locales.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(dimens.spacingSection),
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
                                            .padding(horizontal = dimens.spacingXxl),
                                    ) {
                                        val sinConexionCd = stringResource(R.string.sin_conexion_cd)
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.extraSmall)
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
                key = { index, _ -> index },
            ) { index, local ->
                StoreCard(
                    local = local,
                    index = index,
                    marcaResaltada = null,
                    promotionsByBrand = routeState.promotionsByBrand,
                    onBrandClick = onBrandClick,
                    onAddressClick = onAddressClick,
                    onShareLocal = onShareLocal,
                    modifier = Modifier.animateItem().padding(horizontal = dimens.cardPaddingH),
                )
            }
                        }
                    }
                }
            }
        } else if (!isDataLoaded) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                        contentPadding = PaddingValues(bottom = dimens.contentPaddingBottom),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
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
fun RecentRoutesRow(
    routes: List<String>,
    selectedRoute: String?,
    onRouteSelected: (String) -> Unit,
) {
    val dimens = LocalAppDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingLg, vertical = 6.dp * rs())
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
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
                    .clickable(
                        onClick = { onRouteSelected(route) },
                        role = androidx.compose.ui.semantics.Role.Button,
                    )
                    .semantics { contentDescription = route }
                    .padding(horizontal = 14.dp * rs(), vertical = 8.dp * rs()),
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

fun diaDelMes(dia: DiaSemana): Int {
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
