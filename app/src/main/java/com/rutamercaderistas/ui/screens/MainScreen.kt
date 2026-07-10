package com.rutamercaderistas.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.DaySelector
import com.rutamercaderistas.ui.components.HeaderSection
import com.rutamercaderistas.ui.components.RouteSearchBar
import com.rutamercaderistas.ui.components.ShimmerDaySelector
import com.rutamercaderistas.ui.components.ShimmerLoadingContent
import com.rutamercaderistas.ui.components.ShimmerStatsCards
import com.rutamercaderistas.ui.components.StatsCards
import com.rutamercaderistas.ui.components.StoreCard
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor
import com.rutamercaderistas.viewmodel.RouteViewModel
import com.rutamercaderistas.viewmodel.SyncViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    routeViewModel: RouteViewModel,
    syncViewModel: SyncViewModel,
    onOpenFilePicker: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val routeState by routeViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()

    val entries = routeState.entries
    val selectedRoute = routeState.selectedRoute
    val routes = routeState.routes
    val stats = routeState.stats
    val activeDays = routeState.activeDays
    val recentRoutes = routeState.recentRoutes
    val isDataLoaded = routeState.isDataLoaded
    val isSyncing = syncState.isSyncing
    val ctx = LocalContext.current

    val activeDayNumbers by remember(activeDays) {
        derivedStateOf { activeDays.map { day -> diaDelMes(day) } }
    }

    val pagerState = rememberPagerState(pageCount = { activeDays.size.coerceAtLeast(1) })
    val currentDay = activeDays.getOrNull(pagerState.currentPage) ?: activeDays.firstOrNull()
    val scope = rememberCoroutineScope()

    var showAllLocalesScreen by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

    LaunchedEffect(currentDay) {
        routeViewModel.setCurrentDay(currentDay)
    }

    LaunchedEffect(entries) {
        if (entries.isNotEmpty() && routeState.selectedRoute == null) {
            val lastRoute = routeState.routes.firstOrNull()
            if (lastRoute != null) routeViewModel.selectRoute(lastRoute)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HeaderSection(
                    lastSyncRelative = routeState.lastSyncRelative,
                    onRefresh = {
                        routeViewModel.updateSyncLabel()
                        syncViewModel.syncFromDriveWithRouteReload(selectedRoute)
                        onCheckUpdate()
                    },
                    onOpenManual = { showManual = true },
                    onShare = { routeViewModel.exportRoute() },
                )

                AnimatedVisibility(visible = isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                        onLocalesClick = { showAllLocalesScreen = true },
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

        AnimatedVisibility(
            visible = showAllLocalesScreen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            AllLocalesScreen(
                locales = routeState.allLocales,
                onClose = { showAllLocalesScreen = false },
                onAddressClick = { address -> openMaps(ctx, address) },
            )
        }

        AnimatedVisibility(
            visible = showManual,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ManualScreen(onClose = { showManual = false })
        }
    }
}

@Composable
private fun RecentRoutesRow(
    routes: List<String>,
    selectedRoute: String?,
    onRouteSelected: (String) -> Unit,
) {
    val activeBg = Color(0xFFFCE4EC)
    val activeText = Color(0xFFC62828)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routes.forEach { route ->
            val isSelected = route == selectedRoute
            val bg = if (isSelected) activeBg else Color(0x1A000000)
            val textColor = if (isSelected) activeText else Color(0xFF8E8E93)
            val borderColor = if (isSelected) activeText.copy(alpha = 0.3f) else Color(0xFFE5E5EA)

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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
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

private fun openMaps(context: android.content.Context, address: String) {
    val encoded = android.net.Uri.encode(address)
    val mode = context.getSharedPreferences("mercaderistas_prefs", android.content.Context.MODE_PRIVATE)
        .getString("transport_mode", "transit") ?: "transit"
    val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=$mode"
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(mapsUrl))
    intent.setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(mapsUrl)))
    }
}

@Composable
private fun AllLocalesScreen(
    locales: List<LocalDelDia>,
    onClose: () -> Unit,
    onAddressClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredLocales by remember(locales, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) locales
            else {
                val q = searchQuery.lowercase().trim()
                locales.filter { local ->
                    local.local.lowercase().contains(q) ||
                    local.codigo.lowercase().contains(q) ||
                    local.direccion.lowercase().contains(q) ||
                    local.comuna.lowercase().contains(q)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2190",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Todos los locales",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar local\u2026", fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Text(
            text = "${filteredLocales.size} locales",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = filteredLocales,
                key = { _, local -> local.codigo + local.local }
            ) { _, local ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(storeSoftColor(local.local)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Store,
                                contentDescription = null,
                                tint = storeColor(local.local),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = local.local.ifBlank { "S/N" },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (local.codigo.isNotBlank()) {
                                Text(
                                    text = local.codigo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = buildString {
                                            if (local.direccion.isNotBlank()) append(local.direccion)
                                            if (local.comuna.isNotBlank()) {
                                                if (isNotEmpty()) append(", ")
                                                append(local.comuna)
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = AccentBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onAddressClick(local.direccion) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
