package com.rutamercaderistas.ui.screens

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.preferences.prefsDataStore
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.utils.fuzzyMatches
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

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
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var searchHistory by remember { mutableStateOf(emptyList<String>()) }

    // Historial global (reusa locales history para coherencia)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        context.prefsDataStore.data.map { prefs ->
            prefs[PreferencesRepository.KEY_LOCALES_SEARCH_HISTORY]?.let { raw ->
                runCatching { org.json.JSONArray(raw) }.getOrElse { org.json.JSONArray() }
                    .let { arr -> List(arr.length()) { i -> arr.getString(i) } }
            } ?: emptyList()
        }.collect { searchHistory = it }
    }
    // Guardar en historial tras debounce
    androidx.compose.runtime.LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            kotlinx.coroutines.delay(900)
            if (searchQuery.isNotBlank()) {
                scope.launch {
                    val prefs = context.prefsDataStore.data.first()
                    val existing = prefs[PreferencesRepository.KEY_LOCALES_SEARCH_HISTORY]
                        ?.let { runCatching { org.json.JSONArray(it) }.getOrNull() } ?: org.json.JSONArray()
                    val list = (0 until existing.length()).map { existing.getString(it) }.toMutableList()
                    list.remove(searchQuery.trim())
                    list.add(0, searchQuery.trim())
                    val trimmed = list.take(8)
                    val next = org.json.JSONArray()
                    trimmed.forEach { next.put(it) }
                    context.prefsDataStore.edit { it[PreferencesRepository.KEY_LOCALES_SEARCH_HISTORY] = next.toString() }
                }
            }
        }
    }

    val q = searchQuery.trim()
    val filteredLocales = remember(locales, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            // Prioriza locales: búsqueda difusa por nombre y código (tolerante a tildes, typos y ceros)
            val ranked = locales.mapNotNull { local ->
                val haystack = buildString {
                    append(local.local).append(' ')
                    append(local.codigo).append(' ')
                    append(local.direccion).append(' ')
                    append(local.comuna).append(' ')
                    append(local.cadena)
                    if (local.clientes.isNotEmpty()) {
                        append(' ')
                        append(local.clientes.joinToString(" ") { it.nombre })
                    }
                }
                if (!fuzzyMatches(searchQuery, haystack)) return@mapNotNull null
                // score: código exacto > nombre exacto > difuso
                val codeHit = local.codigo.lowercase().trimStart('0').contains(searchQuery.lowercase().trimStart('0')) ||
                    local.codigo.lowercase().contains(searchQuery.lowercase())
                val nameHit = local.local.lowercase().contains(searchQuery.lowercase())
                val score = when {
                    codeHit && searchQuery.any { it.isDigit() } -> 0
                    nameHit -> 1
                    else -> 2
                }
                score to local
            }.sortedBy { it.first }.map { it.second }
            ranked
        }
    }
    val filteredPromotions = remember(promotions, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else promotions.filter {
            fuzzyMatches(searchQuery, "${it.productName} ${it.brand} ${it.chain}") ||
                it.productName.lowercase().contains(searchQuery.lowercase().trim()) ||
                it.brand.lowercase().contains(searchQuery.lowercase().trim())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.busqueda_titulo),
            onBack = onBack,
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacingXl, vertical = dimens.spacingMd),
            placeholder = { Text(stringResource(R.string.busqueda_hint)) },
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

        if (q.isBlank()) {
            if (searchHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.spacingXl, vertical = dimens.spacingMd),
                ) {
                    Text(
                        text = "Búsquedas recientes",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        searchHistory.take(5).forEach { query ->
                            androidx.compose.material3.AssistChip(
                                onClick = { searchQuery = query },
                                label = { Text(query, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.busqueda_instruccion),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
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
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = dimens.spacingXl,
                    vertical = dimens.spacingMd,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
            ) {
                if (filteredLocales.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(R.string.busqueda_locales))
                    }
                    items(filteredLocales, key = { it.codigo + it.local }) { local ->
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
                    items(filteredPromotions, key = { it.id }) { promo ->
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
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddressClick(address) }
                .padding(dimens.spacingMd),
        ) {
            Text(
                text = local.local,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
