package com.rutamercaderistas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.domain.model.normalizeChain
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.utils.cleanBrand
import com.rutamercaderistas.utils.filterLocales
import com.rutamercaderistas.utils.fuzzyMatches
import com.rutamercaderistas.utils.localeChain
import com.rutamercaderistas.utils.rankLocales
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    locales: List<LocalDelDia>,
    promotions: List<PromotionEntity>,
    onAddressClick: (String) -> Unit,
    onBrandClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Debounce: evita filtrar en cada tecla (listas grandes del rutero completo)
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300)
            .collect { debouncedQuery = it }
    }

    // Historial global centralizado en PreferencesRepository
    val prefsRepo = remember(context) { PreferencesRepository(context.applicationContext) }
    val searchHistory by prefsRepo.getLocalesSearchHistoryFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    // Guardar en historial tras debounce
    androidx.compose.runtime.LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            kotlinx.coroutines.delay(900)
            if (searchQuery.isNotBlank()) {
                scope.launch { prefsRepo.addLocalesSearchQuery(searchQuery) }
            }
        }
    }

    var selectedChain by remember { mutableStateOf<String?>(null) }
    var soloConPromos by remember { mutableStateOf(false) }
    val chains = remember(locales) {
        locales.map { localeChain(it) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val promoBrands = remember(promotions) {
        promotions.map { it.brand.cleanBrand() }.toSet()
    }

    val q = searchQuery.trim()
    var isSearching by remember { mutableStateOf(false) }
    val typing = searchQuery != debouncedQuery
    val filteredLocales by produceState(
        initialValue = emptyList<LocalDelDia>(),
        locales, debouncedQuery, selectedChain, soloConPromos, promoBrands,
    ) {
        if (debouncedQuery.isBlank()) {
            value = emptyList()
            return@produceState
        }
        isSearching = true
        value = withContext(Dispatchers.Default) {
            filterLocales(
                rankLocales(debouncedQuery, locales),
                selectedChain,
                soloConPromos,
                promoBrands,
            ).take(50)
        }
        isSearching = false
    }
    val filteredPromotions by produceState(
        initialValue = emptyList<PromotionEntity>(),
        promotions, debouncedQuery, selectedChain,
    ) {
        if (debouncedQuery.isBlank()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            promotions.filter {
                (selectedChain == null || normalizeChain(it.chain) == selectedChain) &&
                    (fuzzyMatches(debouncedQuery, "${it.productName} ${it.brand} ${it.chain}") ||
                        it.productName.lowercase().contains(debouncedQuery.lowercase().trim()) ||
                        it.brand.lowercase().contains(debouncedQuery.lowercase().trim()))
            }.take(50)
        }
    }

    var historyExpanded by remember { mutableStateOf(false) }
    val matchingHistory = remember(searchHistory, searchQuery) {
        if (searchQuery.isBlank()) searchHistory
        else searchHistory.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.busqueda_titulo),
            onBack = onBack,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacingXl, vertical = dimens.spacingMd),
        ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Buscar local por nombre o código en todo el rutero" }
                .onFocusChanged { historyExpanded = it.isFocused },
            label = { Text(stringResource(R.string.buscar_local_placeholder)) },
            placeholder = { Text(stringResource(R.string.busqueda_hint)) },
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
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(16.dp),
        )
        DropdownMenu(
            expanded = historyExpanded && matchingHistory.isNotEmpty(),
            onDismissRequest = { historyExpanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            matchingHistory.take(5).forEach { h ->
                DropdownMenuItem(
                    text = { Text(h, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        searchQuery = h
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
                    scope.launch { prefsRepo.clearLocalesSearchHistory() }
                    historyExpanded = false
                },
            )
        }
        }

        if (q.isNotBlank() && (typing || isSearching)) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingXl),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (q.isNotBlank() && (chains.isNotEmpty() || promoBrands.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingXl)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (promoBrands.isNotEmpty()) {
                    FilterChip(
                        selected = soloConPromos,
                        onClick = { soloConPromos = !soloConPromos },
                        label = { Text(stringResource(R.string.busqueda_solo_promos), style = MaterialTheme.typography.labelLarge) },
                    )
                }
                chains.forEach { chain ->
                    FilterChip(
                        selected = selectedChain == chain,
                        onClick = { selectedChain = if (selectedChain == chain) null else chain },
                        label = { Text(chain, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }

        if (q.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.busqueda_instruccion),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = dimens.spacingXl,
                    end = dimens.spacingXl,
                    top = dimens.spacingMd,
                    bottom = dimens.scrollBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
                modifier = Modifier.weight(1f),
            ) {
                if (filteredLocales.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(R.string.busqueda_locales))
                    }
                    items(filteredLocales, key = { it.codigo + it.local }, contentType = { "locale" }) { local ->
                        LocaleSearchRow(
                            local = local,
                            onAddressClick = onAddressClick,
                        )
                    }
                }
                if (filteredPromotions.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(R.string.busqueda_promociones))
                    }
                    items(filteredPromotions, key = { it.id }, contentType = { "promotion" }) { promo ->
                        PromotionSearchRow(
                            promo = promo,
                            onBrandClick = onBrandClick,
                        )
                    }
                }
                if (filteredLocales.isEmpty() && filteredPromotions.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.busqueda_sin_resultados),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = dimens.spacingLg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val dimens = LocalAppDimens.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingLg)
            .semantics { contentDescription = text },
    )
}

@Composable
private fun LocaleSearchRow(local: LocalDelDia, onAddressClick: (String) -> Unit) {
    val dimens = LocalAppDimens.current
    val address = local.direccion.ifBlank { stringResource(R.string.sin_direccion) }
    val navigateCd = stringResource(R.string.como_llegar_a, local.local)
    var expanded by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(dimens.spacingMd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = local.local,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (local.codigo.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.busqueda_codigo_label, local.codigo),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (local.rutero.isNotBlank()) {
                        Text(
                            text = local.rutero,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val expandCd = if (expanded) stringResource(R.string.busqueda_ocultar_detalle, local.local)
                else stringResource(R.string.busqueda_ver_detalle, local.local)
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { contentDescription = expandCd },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(dimens.spacingSm))
                    Text(
                        text = stringResource(R.string.share_marcas_count, local.totalClientes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    local.clientes.forEach { cliente ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = cliente.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (cliente.frecuenciaTexto.isNotBlank()) {
                                Text(
                                    text = cliente.frecuenciaTexto,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { onAddressClick(address) },
                        modifier = Modifier.semantics { contentDescription = navigateCd },
                    ) {
                        Text(text = stringResource(R.string.como_llegar))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionSearchRow(promo: PromotionEntity, onBrandClick: (String) -> Unit) {
    val dimens = LocalAppDimens.current
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (promo.brand.isNotBlank()) onBrandClick(promo.brand) }
                .padding(dimens.spacingMd),
        ) {
            Text(
                text = promo.productName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = promo.brand.ifBlank { promo.chain },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
