package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.LocalAppDimens

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

    val q = searchQuery.lowercase().trim()
    val filteredLocales = remember(locales, q) {
        if (q.isBlank()) emptyList()
        else locales.filter {
            it.local.lowercase().contains(q) ||
                it.direccion.lowercase().contains(q) ||
                it.codigo.lowercase().contains(q) ||
                it.comuna.lowercase().contains(q) ||
                it.cadena.lowercase().contains(q)
        }
    }
    val filteredPromotions = remember(promotions, q) {
        if (q.isBlank()) emptyList()
        else promotions.filter {
            it.productName.lowercase().contains(q) ||
                it.brand.lowercase().contains(q) ||
                it.chain.lowercase().contains(q)
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
