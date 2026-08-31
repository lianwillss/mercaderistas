package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.geometry.Offset
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
import com.rutamercaderistas.ui.components.StoreCard
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.viewmodel.RouteUiState
import com.rutamercaderistas.viewmodel.SyncUiState
import com.rutamercaderistas.viewmodel.PlanillaChanges
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRouteContent(
    routeState: RouteUiState,
    syncState: SyncUiState,
    onCheckUpdate: () -> Unit,
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
    onGlobalSearch: () -> Unit = {},
    onDismissSyncChanges: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRefreshPositioned: (Offset) -> Unit = {},
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
    val scope = rememberCoroutineScope()
    val s = rs()
    val dimens = LocalAppDimens.current

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 600.dp
        var selectedDayIndex by remember { mutableStateOf(0) }
        val currentDay = if (isWide) {
            activeDays.getOrNull(selectedDayIndex) ?: activeDays.firstOrNull()
        } else {
            activeDays.getOrNull(pagerState.currentPage) ?: activeDays.firstOrNull()
        }

        LaunchedEffect(currentDay) {
            onSetCurrentDay(currentDay)
        }

        Column(modifier = Modifier.fillMaxSize()) {
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
                onGlobalSearch = onGlobalSearch,
                onOpenSettings = onOpenSettings,
                onRefreshPositioned = onRefreshPositioned,
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

            if (syncState.syncError != null && !isSyncing) {
                SyncErrorBanner(
                    message = syncState.syncError!!,
                    onRetry = onHeaderRefresh,
                )
            }

            if (syncState.syncChanges != null && !syncState.syncChanges!!.isEmpty && !isSyncing) {
                SyncChangesBanner(
                    changes = syncState.syncChanges!!,
                    onDismiss = onDismissSyncChanges,
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacingXs))

            if (isDataLoaded) {
                RouteSearchBar(
                    routes = routes,
                    recentRoutes = recentRoutes,
                    selectedRoute = selectedRoute,
                    onRouteSelected = onSelectRoute,
                    onSearchActiveChanged = { searchActive = it },
                )
            }

            if (isDataLoaded && recentRoutes.isNotEmpty() && !searchActive) {
                RecentRoutesRow(
                    routes = recentRoutes.take(5),
                    selectedRoute = selectedRoute,
                    onRouteSelected = onSelectRoute,
                )
            }

            if (isDataLoaded) {
                if (!isWide && activeDays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(dimens.spacingMd))
                    DaySelector(
                        days = activeDays,
                        dayNumbers = activeDayNumbers,
                        selectedIndex = pagerState.currentPage,
                        onDaySelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                    )
                    Spacer(modifier = Modifier.height(dimens.spacingSm))
                }
            } else if (isSyncing) {
                Spacer(modifier = Modifier.height(dimens.spacingMd))
                ShimmerDaySelector()
            }
        }

        if (isDataLoaded && activeDays.isNotEmpty()) {
            if (isWide) {
                Row(modifier = Modifier.weight(1f)) {
                    DayList(
                        days = activeDays,
                        dayNumbers = activeDayNumbers,
                        selectedIndex = selectedDayIndex,
                        onDaySelected = { selectedDayIndex = it },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        DayContent(
                            routeState = routeState,
                            syncState = syncState,
                            isSyncing = isSyncing,
                            onPullRefresh = onPullRefresh,
                            onBrandClick = onBrandClick,
                            onAddressClick = onAddressClick,
                            onShareLocal = onShareLocal,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.weight(1f),
                ) {
                    DayContent(
                        routeState = routeState,
                        syncState = syncState,
                        isSyncing = isSyncing,
                        onPullRefresh = onPullRefresh,
                        onBrandClick = onBrandClick,
                        onAddressClick = onAddressClick,
                        onShareLocal = onShareLocal,
                    )
                }
            }
        } else if (!isDataLoaded) {
            Box(modifier = Modifier.weight(1f)) {
                if (isSyncing) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = dimens.contentPaddingBottom),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item { ShimmerLoadingContent() }
                    }
                } else {
                    FirstLaunchEmptyState(
                        isOffline = !syncState.isOnline,
                        onSync = onInitialSync,
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncErrorBanner(message: String, onRetry: () -> Unit) {
    val dimens = LocalAppDimens.current
    val cd = stringResource(R.string.sync_error_banner_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .semantics { contentDescription = "$cd: $message" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.reintentar),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SyncChangesBanner(changes: PlanillaChanges, onDismiss: () -> Unit) {
    val dimens = LocalAppDimens.current
    val cd = stringResource(R.string.sync_cambios_cd)
    val summary = buildList {
        if (changes.added.isNotEmpty()) add(stringResource(R.string.sync_cambios_agregados, changes.added.size))
        if (changes.removed.isNotEmpty()) add(stringResource(R.string.sync_cambios_eliminados, changes.removed.size))
        if (changes.moved.isNotEmpty()) add(stringResource(R.string.sync_cambios_movidos, changes.moved.size))
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = "$cd: $summary" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sync_cambios_titulo),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cerrar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirstLaunchEmptyState(isOffline: Boolean, onSync: () -> Unit) {
    val dimens = LocalAppDimens.current
    val cd = stringResource(R.string.empty_state_cd)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingLg)
            .semantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Text(
            text = stringResource(R.string.empty_state_titulo),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(dimens.spacingXs))
        Text(
            text = if (isOffline) stringResource(R.string.empty_state_offline) else stringResource(R.string.empty_state_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(dimens.spacingLg))
        Button(onClick = onSync) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.empty_state_accion))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayContent(
    routeState: RouteUiState,
    syncState: SyncUiState,
    isSyncing: Boolean,
    onPullRefresh: () -> Unit,
    onBrandClick: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    onShareLocal: (String) -> Unit,
) {
    val dimens = LocalAppDimens.current
    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onPullRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 380.dp),
            contentPadding = PaddingValues(bottom = dimens.contentPaddingBottom),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingLg),
            modifier = Modifier.fillMaxSize(),
        ) {
            val locales = routeState.currentDayLocales
            if (locales.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(key = "offline_badge", span = { GridItemSpan(maxLineSpan) }) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.sin_conexion_datos),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { contentDescription = sinConexionCd },
                                    )
                                }
                            }
                        }
                    }
                }
                items(
                    count = locales.size,
                    key = { index -> "${locales[index].codigo}|${locales[index].local}" },
                ) { index ->
                    val local = locales[index]
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

@Composable
private fun DayList(
    days: List<DiaSemana>,
    dayNumbers: List<Int>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
) {
    val dimens = LocalAppDimens.current
    LazyColumn(
        contentPadding = PaddingValues(dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        modifier = Modifier
            .fillMaxHeight()
            .width(180.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        items(days.size) { index ->
            val dia = days[index]
            val num = dayNumbers.getOrNull(index) ?: 0
            val isSelected = index == selectedIndex
            DayListChip(
                dia = dia,
                num = num,
                isSelected = isSelected,
                onClick = { onDaySelected(index) },
            )
        }
    }
}

@Composable
private fun DayListChip(
    dia: DiaSemana,
    num: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
            .semantics { contentDescription = "${dia.nombreCompleto} $num" }
            .padding(dimens.spacingMd),
    ) {
        Column {
            Text(
                text = dia.abreviacion,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
            Text(
                text = num.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
            )
        }
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
