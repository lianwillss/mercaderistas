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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor

@Composable
fun AllLocalesScreen(
    locales: List<LocalDelDia>,
    onClose: () -> Unit,
    onAddressClick: (String) -> Unit,
    initialSearch: String = "",
) {
    var searchQuery by remember { mutableStateOf(initialSearch) }
    val s = rs()

    LaunchedEffect(initialSearch) {
        if (initialSearch.isNotBlank()) searchQuery = initialSearch
    }

    val filteredLocales by remember(locales, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) locales
            else {
                val q = searchQuery.lowercase().trim()
                locales.filter { local ->
                    local.local.lowercase().contains(q) ||
                    local.codigo.lowercase().contains(q) ||
                    local.direccion.lowercase().contains(q) ||
                    local.comuna.lowercase().contains(q) ||
                    local.clientes.any { it.nombre.lowercase().contains(q) }
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
        ScreenHeader(
            onBack = onClose,
            title = stringResource(R.string.todos_locales),
            verticalPadding = 12.dp * s,
        )

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.buscar_local_placeholder)) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.buscar_cd), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limpiar_cd), modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = ComponentShapes.textField,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp * s, vertical = 4.dp * s)
        )

        Text(
            text = stringResource(R.string.locales_count, filteredLocales.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp * s, vertical = 4.dp * s)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp * s, vertical = 4.dp * s),
            verticalArrangement = Arrangement.spacedBy(10.dp * s)
        ) {
            itemsIndexed(
                items = filteredLocales,
                key = { index, _ -> index }
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
                    shape = ComponentShapes.cardSmall,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp * s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp * s)
                                .clip(RoundedCornerShape(8.dp))
                                .background(storeSoftColor(local.local)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Store,
                                contentDescription = stringResource(R.string.cadena_cd),
                                tint = storeColor(local.local),
                                modifier = Modifier.size(14.dp * s)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp * s))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = local.local.ifBlank { stringResource(R.string.sin_numero) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = stringResource(R.string.direccion_cd),
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
