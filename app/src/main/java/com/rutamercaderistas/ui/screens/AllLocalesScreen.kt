package com.rutamercaderistas.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.GlobalSearchAction
import com.rutamercaderistas.ui.components.ScreenHeader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rutamercaderistas.ui.theme.AppDimens
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor
import com.rutamercaderistas.utils.fuzzyMatches

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

    val isWide = LocalConfiguration.current.screenWidthDp >= 840

    if (isWide) {
        AllLocalesTwoPane(
            locales = filteredLocales,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onClose = onClose,
            onAddressClick = onAddressClick,
            onGlobalSearch = onGlobalSearch,
        )
    } else {
        AllLocalesSinglePane(
            locales = filteredLocales,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onClose = onClose,
            onAddressClick = onAddressClick,
            onGlobalSearch = onGlobalSearch,
            scrollBehavior = scrollBehavior,
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
    onGlobalSearch: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
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
        )
        CountAndGrid(
            locales = locales,
            searchQuery = searchQuery,
            onAddressClick = onAddressClick,
            dimens = dimens,
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
    onGlobalSearch: () -> Unit,
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
            )
            Text(
                text = stringResource(R.string.locales_count, locales.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
            )
            if (locales.isEmpty() && searchQuery.isNotBlank()) {
                EmptyLocales(query = searchQuery)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(10.dp * rs()),
                ) {
                    items(locales, key = { it.codigo }) { local ->
                        LocaleCard(
                            local = local,
                            selected = selected == local,
                            onClick = { selected = local },
                            onAddressClick = onAddressClick,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(DividerDefaults.Thickness),
            color = DividerDefaults.color,
        )
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f)) {
            selected?.let { local ->
                LocaleDetailPane(local = local, onAddressClick = onAddressClick)
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.locale_detail_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SearchBarContent(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    dimens: AppDimens,
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text(stringResource(R.string.buscar_local_placeholder)) },
        placeholder = { Text(stringResource(R.string.buscar_local_placeholder)) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.buscar_cd), modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limpiar_cd), modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = ComponentShapes.textField,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Buscar local por nombre, código o dirección. Escribe para filtrar la lista." }
            .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingXs)
    )
}

@Composable
private fun CountAndGrid(
    locales: List<LocalDelDia>,
    searchQuery: String,
    onAddressClick: (String) -> Unit,
    dimens: AppDimens,
) {
    Text(
        text = stringResource(R.string.locales_count, locales.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = dimens.spacingLg, vertical = dimens.spacingXs)
    )
    if (locales.isEmpty() && searchQuery.isNotBlank()) {
        EmptyLocales(query = searchQuery)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(10.dp * rs()),
            horizontalArrangement = Arrangement.spacedBy(10.dp * rs()),
        ) {
            itemsIndexed(
                items = locales,
                key = { _, local -> local.codigo }
            ) { index, local ->
                var visible by remember { mutableStateOf(false) }
                val animAlpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(250, delayMillis = index * 50),
                )
                val animOffsetY by animateDpAsState(
                    targetValue = if (visible) 0.dp else 12.dp,
                    animationSpec = tween(250, delayMillis = index * 50),
                )
                LaunchedEffect(Unit) { visible = true }

                Card(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .graphicsLayer(alpha = animAlpha)
                        .offset(y = animOffsetY),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    LocaleCardContent(local = local, onAddressClick = onAddressClick)
                }
            }
        }
    }
}

@Composable
private fun EmptyLocales(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sin_resultados_para, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun LocaleCard(
    local: LocalDelDia,
    selected: Boolean,
    onClick: () -> Unit,
    onAddressClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        LocaleCardContent(local = local, onAddressClick = onAddressClick)
    }
}

@Composable
private fun LocaleCardContent(
    local: LocalDelDia,
    onAddressClick: (String) -> Unit,
) {
    val dimens = LocalAppDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacingMd),
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
                Text(
                    text = local.codigo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 24.dp)
                        .clickable(
                            onClick = { onAddressClick(local.direccion) },
                            role = Role.Button,
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
                        .padding(vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingLg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
    ) {
        Text(
            text = local.local.ifBlank { stringResource(R.string.sin_numero) },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (local.codigo.isNotBlank()) {
            Text(local.codigo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClick = { onAddressClick(local.direccion) },
                        role = Role.Button,
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
