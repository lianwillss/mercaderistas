package com.rutamercaderistas.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.rutamercaderistas.ui.theme.MotionSprings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import com.rutamercaderistas.ui.components.DropletToast
import com.rutamercaderistas.ui.components.MatchedBrandLine
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.delay
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.heading
import com.rutamercaderistas.models.DiaSemana
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.CodigoChip
import com.rutamercaderistas.ui.components.GlobalSearchAction
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.rutamercaderistas.utils.BrandSection
import com.rutamercaderistas.utils.brandSections
import com.rutamercaderistas.utils.matchedBrands
import com.rutamercaderistas.models.diasLabel
import com.rutamercaderistas.models.diasVisita
import com.rutamercaderistas.ui.components.ScreenHeader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.rutamercaderistas.ui.theme.AppDimens
import com.rutamercaderistas.ui.theme.AppWindowWidth
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.appWindowWidth
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.ui.theme.storeColor
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.ui.theme.storeSoftColor
import com.rutamercaderistas.utils.fuzzyMatches
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllLocalesScreen(
    locales: List<LocalDelDia>,
    onClose: () -> Unit,
    onAddressClick: (String) -> Unit,
    initialSearch: String = "",
    onGlobalSearch: () -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf(initialSearch) }
    val dimens = LocalAppDimens.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val prefsRepo = remember(context) { PreferencesRepository(context.applicationContext) }
    val searchHistory by prefsRepo.getLocalesSearchHistoryFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            kotlinx.coroutines.delay(800)
            if (searchQuery.isNotBlank()) {
                scope.launch { prefsRepo.addLocalesSearchQuery(searchQuery) }
            }
        }
    }

    LaunchedEffect(initialSearch) {
        if (initialSearch.isNotBlank()) searchQuery = initialSearch
    }

    val filteredLocales by remember(locales, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) locales
            else {
                locales.filter { local ->
                    fuzzyMatches(
                        searchQuery,
                        buildString {
                            append(local.local).append(' ')
                            append(local.codigo).append(' ')
                            append(local.direccion).append(' ')
                            append(local.comuna)
                            if (local.clientes.isNotEmpty()) {
                                append(' ')
                                append(local.clientes.joinToString(" ") { it.nombre })
                            }
                        },
                    )
                }
            }
        }
    }

    val isWide = appWindowWidth(LocalConfiguration.current.screenWidthDp.dp) == AppWindowWidth.Expanded

    // Toast-gota de copiado: hermano en Box (nunca hijo de un Column con peso).
    var copyToast by remember { mutableStateOf<String?>(null) }
    val copiedMessage = stringResource(R.string.direccion_copiada)
    Box(modifier = Modifier.fillMaxSize()) {
    if (isWide) {
        AllLocalesTwoPane(
            locales = filteredLocales,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onClose = onClose,
            onAddressClick = onAddressClick,
            onCopyAddress = { copyToast = copiedMessage },
            onBrandSearch = { searchQuery = it },
            onGlobalSearch = onGlobalSearch,
            searchHistory = searchHistory,
            onHistoryClick = { searchQuery = it },
        )
    } else {
        AllLocalesSinglePane(
            locales = filteredLocales,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onClose = onClose,
            onAddressClick = onAddressClick,
            onCopyAddress = { copyToast = copiedMessage },
            onBrandSearch = { searchQuery = it },
            onGlobalSearch = onGlobalSearch,
            scrollBehavior = scrollBehavior,
            searchHistory = searchHistory,
            onHistoryClick = { searchQuery = it },
        )
    }
    DropletToast(
        message = copyToast,
        onTimeout = { copyToast = null },
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllLocalesSinglePane(
    locales: List<LocalDelDia>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    onBrandSearch: (String) -> Unit = {},
    onGlobalSearch: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    searchHistory: List<String> = emptyList(),
    onHistoryClick: (String) -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        ScreenHeader(
            onBack = onClose,
            title = stringResource(R.string.todos_locales),
            scrollBehavior = scrollBehavior,
            trailingContent = { GlobalSearchAction(onGlobalSearch) },
        )
        SearchBarContent(
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            dimens = dimens,
            searchHistory = searchHistory,
            onHistoryClick = onHistoryClick,
        )
        CountAndGrid(
            locales = locales,
            searchQuery = searchQuery,
            onAddressClick = onAddressClick,
            onCopyAddress = onCopyAddress,
            onBrandSearch = onBrandSearch,
            dimens = dimens,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllLocalesTwoPane(
    locales: List<LocalDelDia>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    onBrandSearch: (String) -> Unit = {},
    onGlobalSearch: () -> Unit,
    searchHistory: List<String> = emptyList(),
    onHistoryClick: (String) -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    var selected by remember { mutableStateOf<LocalDelDia?>(null) }
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f)) {
            ScreenHeader(
                onBack = onClose,
                title = stringResource(R.string.todos_locales),
                trailingContent = { GlobalSearchAction(onGlobalSearch) },
            )
            SearchBarContent(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                dimens = dimens,
                searchHistory = searchHistory,
                onHistoryClick = onHistoryClick,
            )
            Text(
                text = stringResource(R.string.locales_count, locales.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
            )
            var selectedDay by remember { mutableStateOf<DiaSemana?>(null) }
            LaunchedEffect(searchQuery) { selectedDay = null }
            val sections = remember(searchQuery, locales, selectedDay) {
                brandSections(searchQuery, locales, selectedDay)
            }
            if (sections.isNotEmpty()) {
                DayFilterChips(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
            }
            if (locales.isEmpty() && searchQuery.isNotBlank()) {
                EmptyLocales(query = searchQuery, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = dimens.spacingMd,
                        end = dimens.spacingMd,
                        top = dimens.spacingXs,
                        bottom = dimens.scrollBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp * rs()),
                    modifier = Modifier.weight(1f),
                ) {
                    if (sections.isNotEmpty()) {
                        var sectionIndex = 0
                        sections.forEach { section ->
                            item(
                                key = "brand_${section.brand}",
                                contentType = "brand_header",
                            ) {
                                BrandSectionHeader(
                                    brand = section.brand,
                                    count = section.locales.size,
                                    days = section.locales
                                        .flatMap { it.marcasDias[section.brand].orEmpty() }
                                        .toSet(),
                                )
                            }
                            itemsIndexed(
                                section.locales,
                                key = { _, local -> "brand_${section.brand}|${local.codigo}|${local.local}" },
                                contentType = { _, _ -> "locale" },
                            ) { index, local ->
                                val animatedIndex = sectionIndex++
                                AnimatedLocaleCard(
                                    index = animatedIndex,
                                    local = local,
                                    selected = selected == local,
                                    onClick = { selected = local },
                                    onAddressClick = onAddressClick,
                                    onCopyAddress = onCopyAddress,
                                    searchQuery = searchQuery,
                                    onBrandSearch = onBrandSearch,
                                    focusBrand = section.brand,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            locales,
                            key = { _, local -> local.codigo + "|" + local.local },
                            contentType = { _, _ -> "locale" },
                        ) { index, local ->
                            AnimatedLocaleCard(
                                index = index,
                                local = local,
                                selected = selected == local,
                                onClick = { selected = local },
                                onAddressClick = onAddressClick,
                                onCopyAddress = onCopyAddress,
                                searchQuery = searchQuery,
                                onBrandSearch = onBrandSearch,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(DividerDefaults.Thickness),
            color = DividerDefaults.color,
        )
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f)) {
            key(selected?.codigo) {
                var detailVisible by remember { mutableStateOf(false) }
                val detailAlpha by animateFloatAsState(
                    targetValue = if (detailVisible) 1f else 0f,
                    animationSpec = MotionSprings.bouncy(),
                    label = "detailAlpha",
                )
                val detailOffsetX by animateDpAsState(
                    targetValue = if (detailVisible) 0.dp else 24.dp,
                    animationSpec = MotionSprings.bouncy(),
                    label = "detailOffset",
                )
                LaunchedEffect(Unit) { detailVisible = true }
                val density = LocalDensity.current
                selected?.let { local ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = detailAlpha
                                translationX = detailOffsetX.value * density.density
                            },
                    ) {
                        LocaleDetailPane(
                            local = local,
                            onAddressClick = onAddressClick,
                            onCopyAddress = onCopyAddress,
                            searchQuery = searchQuery,
                            onBrandSearch = onBrandSearch,
                        )
                    }
                }
            }
            if (selected == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.locale_detail_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarContent(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    dimens: AppDimens,
    searchHistory: List<String> = emptyList(),
    onHistoryClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val clearScope = rememberCoroutineScope()
    var historyExpanded by remember { mutableStateOf(false) }
    val matchingHistory = remember(searchHistory, searchQuery) {
        if (searchQuery.isBlank()) searchHistory
        else searchHistory.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    val buscarLocalesCd = stringResource(R.string.buscar_locales_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingXs),
    ) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text(stringResource(R.string.buscar_local_placeholder)) },
        placeholder = { Text(stringResource(R.string.buscar_local_placeholder)) },
        supportingText = {
            if (searchQuery.isNotEmpty() && searchQuery.length < 2) {
                Text(
                    text = "Escribe al menos 2 caracteres",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        isError = searchQuery.length == 1,
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.buscar_cd), modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                val limpiarBusquedaCd = stringResource(R.string.limpiar_busqueda_cd)
                IconButton(
                    onClick = { onSearchChange("") },
                    modifier = Modifier.semantics { contentDescription = limpiarBusquedaCd }
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = ComponentShapes.textField,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buscarLocalesCd
            }
            .onFocusChanged { historyExpanded = it.isFocused },
    )
    DropdownMenu(
        expanded = historyExpanded && matchingHistory.isNotEmpty(),
        onDismissRequest = { historyExpanded = false },
        modifier = Modifier.fillMaxWidth(),
    ) {
        matchingHistory.take(5).forEach { query ->
            DropdownMenuItem(
                text = { Text(query, maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Outlined.History, contentDescription = null)
                },
                onClick = {
                    onHistoryClick(query)
                    historyExpanded = false
                },
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.limpiar_historial),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = {
                clearScope.launch {
                    PreferencesRepository(context.applicationContext).clearLocalesSearchHistory()
                }
                historyExpanded = false
            },
        )
    }
    }
}

@Composable
private fun CountAndGrid(
    locales: List<LocalDelDia>,
    searchQuery: String,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    onBrandSearch: (String) -> Unit = {},
    dimens: AppDimens,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.locales_count, locales.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
        )
        var selectedDay by remember { mutableStateOf<DiaSemana?>(null) }
        LaunchedEffect(searchQuery) { selectedDay = null }
        val sections = remember(searchQuery, locales, selectedDay) {
            brandSections(searchQuery, locales, selectedDay)
        }
        if (sections.isNotEmpty()) {
            DayFilterChips(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
        }
        if (locales.isEmpty() && searchQuery.isNotBlank()) {
            EmptyLocales(query = searchQuery, modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(
                    start = dimens.spacingMd,
                    end = dimens.spacingMd,
                    top = dimens.spacingXs,
                    bottom = dimens.scrollBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp * rs()),
                horizontalArrangement = Arrangement.spacedBy(10.dp * rs()),
                modifier = Modifier.weight(1f),
            ) {
            if (sections.isNotEmpty()) {
                sections.forEach { section ->
                    item(
                        key = "brand_${section.brand}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "brand_header",
                    ) {
                        BrandSectionHeader(
                            brand = section.brand,
                            count = section.locales.size,
                            days = section.locales
                                .flatMap { it.marcasDias[section.brand].orEmpty() }
                                .toSet(),
                        )
                    }
                    itemsIndexed(
                        items = section.locales,
                        key = { _, local -> "brand_${section.brand}|${local.codigo}|${local.local}" },
                        contentType = { _, _ -> "locale" },
                    ) { _, local ->
                        Card(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            LocaleCardContent(
                                local = local,
                                onAddressClick = onAddressClick,
                                onCopyAddress = onCopyAddress,
                                searchQuery = searchQuery,
                                onBrandSearch = onBrandSearch,
                                focusBrand = section.brand,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = locales,
                    key = { _, local -> local.codigo + "|" + local.local },
                    contentType = { _, _ -> "locale" },
                ) { index, local ->
                    var visible by remember { mutableStateOf(false) }
                    val reducedMotion = LocalReducedMotionEnabled.current
                    val animAlpha by animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
                        label = "gridLocaleAlpha",
                    )
                    val animOffsetY by animateDpAsState(
                        targetValue = if (visible) 0.dp else 12.dp,
                        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
                        label = "gridLocaleOffset",
                    )
                    LaunchedEffect(Unit) {
                        if (!reducedMotion) delay(minOf(index, 8) * 40L)
                        visible = true
                    }
                    val density = LocalDensity.current

                    Card(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = animAlpha
                                translationY = animOffsetY.value * density.density
                            },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        LocaleCardContent(
                            local = local,
                            onAddressClick = onAddressClick,
                            onCopyAddress = onCopyAddress,
                            searchQuery = searchQuery,
                            onBrandSearch = onBrandSearch,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun EmptyLocales(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Store,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sin_resultados_para, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ean_no_results_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AnimatedLocaleCard(
    index: Int,
    local: LocalDelDia,
    selected: Boolean,
    onClick: () -> Unit,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    searchQuery: String = "",
    onBrandSearch: (String) -> Unit = {},
    focusBrand: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotionEnabled.current
    val animAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
        label = "localeCardAlpha",
    )
    val animOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 12.dp,
        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
        label = "localeCardOffset",
    )
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(minOf(index, 8) * 40L)
        visible = true
    }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animAlpha
                translationY = animOffsetY.value * density.density
            },
    ) {
        LocaleCard(
            local = local,
            selected = selected,
            onClick = onClick,
            onAddressClick = onAddressClick,
            onCopyAddress = onCopyAddress,
            searchQuery = searchQuery,
            onBrandSearch = onBrandSearch,
            focusBrand = focusBrand,
        )
    }
}

@Composable
private fun LocaleCard(
    local: LocalDelDia,
    selected: Boolean,
    onClick: () -> Unit,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    searchQuery: String = "",
    onBrandSearch: (String) -> Unit = {},
    focusBrand: String? = null,
) {
    val localSeleccionadoCd = if (selected) stringResource(R.string.local_seleccionado_cd, local.local.ifBlank { stringResource(R.string.sin_numero) }) else ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                stateDescription = localSeleccionadoCd
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        LocaleCardContent(
            local = local,
            onAddressClick = onAddressClick,
            onCopyAddress = onCopyAddress,
            searchQuery = searchQuery,
            onBrandSearch = onBrandSearch,
            focusBrand = focusBrand,
        )
    }
}

/** Encabezado de sección de marca: nombre + locales + días (union). */
@Composable
private fun BrandSectionHeader(
    brand: String,
    count: Int,
    days: Set<DiaSemana>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$brand · en $count ${if (count == 1) "local" else "locales"}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        if (days.isNotEmpty()) {
            Text(
                text = diasLabel(days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Filtro "¿dónde la veo el día X?": Todos + 7 días. */
@Composable
private fun DayFilterChips(
    selectedDay: DiaSemana?,
    onDaySelected: (DiaSemana?) -> Unit,
) {
    val filtrarPorDiaCd = stringResource(R.string.filtrar_por_dia_cd)
    val todasLosDiasCd = stringResource(R.string.todas_los_dias_cd)
    val todasCd = stringResource(R.string.todas_cd)
    val diaSeleccionadoCd = stringResource(R.string.dia_seleccionado_cd)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "day_all") {
            Box(modifier = Modifier.heightIn(min = 48.dp)) {
                FilterChip(
                    selected = selectedDay == null,
                    onClick = { onDaySelected(null) },
                    label = { Text(stringResource(R.string.todas)) },
                    modifier = Modifier
                        .semantics {
                            contentDescription = filtrarPorDiaCd
                            stateDescription = if (selectedDay == null) todasLosDiasCd else todasCd
                        }
                )
            }
        }
        items(DiaSemana.todos(), key = { it.name }) { dia ->
            val diaSeleccionadoStr = stringResource(R.string.dia_seleccionado_cd, dia.nombreCompleto)
            Box(modifier = Modifier.heightIn(min = 48.dp)) {
                FilterChip(
                    selected = selectedDay == dia,
                    onClick = { onDaySelected(if (selectedDay == dia) null else dia) },
                    label = { Text(dia.abreviacion) },
                    modifier = Modifier
                        .semantics {
                            contentDescription = filtrarPorDiaCd
                            stateDescription = if (selectedDay == dia) diaSeleccionadoStr else dia.nombreCompleto
                        }
                )
            }
        }
    }
}

/** Copia "dirección, comuna" al portapapeles (sin toast: lo pone el llamador). */
private fun copyAddressToClipboard(context: android.content.Context, local: LocalDelDia) {
    val addressText = buildString {
        append(local.direccion)
        if (local.comuna.isNotBlank()) append(", ${local.comuna}")
    }
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("Dirección", addressText))
}

@Composable
private fun LocaleCardContent(
    local: LocalDelDia,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    searchQuery: String = "",
    onBrandSearch: (String) -> Unit = {},
    focusBrand: String? = null,
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val matched = remember(local, searchQuery, focusBrand) {
        val all = matchedBrands(local, searchQuery)
        if (focusBrand != null) all.filter { it.nombre == focusBrand } else all
    }
    val localTitle = local.local.ifBlank { stringResource(R.string.sin_numero) }
    val dias = remember(local) { local.diasVisita() }
    val diasTexto = diasLabel(dias)
    val seVisitaCd = if (dias.isNotEmpty()) stringResource(R.string.locale_se_visita, diasTexto) else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacingMd)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(localTitle)
                    if (local.codigo.isNotBlank()) append(", código ${local.codigo}")
                    if (seVisitaCd.isNotEmpty()) append(". $seVisitaCd")
                    if (local.direccion.isNotBlank()) append(", ${local.direccion}")
                    if (local.comuna.isNotBlank()) append(", ${local.comuna}")
                    if (matched.isNotEmpty()) {
                        append(". Marcas: ")
                        append(matched.joinToString(", ") { it.nombre })
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dimens.iconLg)
                .clip(RoundedCornerShape(8.dp))
                .background(storeSoftColor(local.local)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Store,
                contentDescription = null,
                tint = storeColor(local.local),
                modifier = Modifier.size(14.dp * rs())
            )
        }

        Spacer(modifier = Modifier.width(10.dp * rs()))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = local.local.ifBlank { stringResource(R.string.sin_numero) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (local.codigo.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                CodigoChip(codigo = local.codigo)
            }
            // Días en que se visita el local dentro de la ruta actual
            // (unión de los días de sus marcas; vacío si no hay datos).
            if (dias.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = diasTexto,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            matched.forEach { cliente ->
                MatchedBrandLine(
                    cliente = cliente,
                    daysLabel = diasLabel(local.marcasDias[cliente.nombre].orEmpty()),
                    query = searchQuery,
                    onBrandSearch = onBrandSearch,
                )
            }
            if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val addressPress = rememberPressInteractionSource()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .heightIn(min = 48.dp)
                        .pressScale(addressPress)
                        .combinedClickable(
                            interactionSource = addressPress,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = "Abrir ${local.direccion} en Maps",
                            onLongClickLabel = stringResource(R.string.compartir_copiar_direccion),
                            onLongClick = {
                                copyAddressToClipboard(context, local)
                                onCopyAddress()
                            },
                            onClick = { onAddressClick(local.direccion) },
                        )
                        .semantics {
                            val addr = buildString {
                                if (local.direccion.isNotBlank()) append(local.direccion)
                                if (local.comuna.isNotBlank()) {
                                    if (isNotEmpty()) append(", ")
                                    append(local.comuna)
                                }
                            }
                            contentDescription = "Abrir $addr en Maps"
                        }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buildString {
                            if (local.direccion.isNotBlank()) append(local.direccion)
                            if (local.comuna.isNotBlank()) {
                                if (isNotEmpty()) append(", ")
                                append(local.comuna)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocaleDetailPane(
    local: LocalDelDia,
    onAddressClick: (String) -> Unit,
    onCopyAddress: () -> Unit = {},
    searchQuery: String = "",
    onBrandSearch: (String) -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val detailPress = rememberPressInteractionSource()
    val matched = remember(local, searchQuery) { matchedBrands(local, searchQuery) }
    val localTitle = local.local.ifBlank { stringResource(R.string.sin_numero) }
    val dias = remember(local) { local.diasVisita() }
    val diasTexto = diasLabel(dias)
    val seVisitaCd = if (dias.isNotEmpty()) stringResource(R.string.locale_se_visita, diasTexto) else ""
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingLg)
            .verticalScroll(rememberScrollState())
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Detalle del local ")
                    append(localTitle)
                    if (local.codigo.isNotBlank()) append(", código ${local.codigo}")
                    if (seVisitaCd.isNotEmpty()) append(". $seVisitaCd")
                    if (matched.isNotEmpty()) {
                        append(". Marcas: ")
                        append(matched.joinToString(", ") { it.nombre })
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
    ) {
        Text(
            text = local.local.ifBlank { stringResource(R.string.sin_numero) },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (local.codigo.isNotBlank()) {
            CodigoChip(codigo = local.codigo)
        }
        if (dias.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = diasTexto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        matched.forEach { cliente ->
            MatchedBrandLine(
                cliente = cliente,
                daysLabel = diasLabel(local.marcasDias[cliente.nombre].orEmpty()),
                query = searchQuery,
                onBrandSearch = onBrandSearch,
            )
        }
        if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pressScale(detailPress)
                    .combinedClickable(
                        interactionSource = detailPress,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = "Abrir ${local.direccion} en Maps",
                        onLongClickLabel = stringResource(R.string.compartir_copiar_direccion),
                        onLongClick = {
                            copyAddressToClipboard(context, local)
                            onCopyAddress()
                        },
                        onClick = { onAddressClick(local.direccion) },
                    )
                    .semantics {
                        val addr = buildString {
                            if (local.direccion.isNotBlank()) append(local.direccion)
                            if (local.comuna.isNotBlank()) {
                                if (isNotEmpty()) append(", ")
                                append(local.comuna)
                            }
                        }
                        contentDescription = "Abrir $addr en Maps"
                    }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildString {
                        if (local.direccion.isNotBlank()) append(local.direccion)
                        if (local.comuna.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(local.comuna)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (local.clientes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.locale_clientes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            local.clientes.forEach { cliente ->
                Text(
                    text = cliente.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AllLocalesScreenPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            AllLocalesScreen(
                locales = emptyList(),
                onClose = {},
                onAddressClick = {},
            )
        }
    }
}
