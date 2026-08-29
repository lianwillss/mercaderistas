package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.theme.LocalAppDimens

@Composable
fun PromotionsListDetailScreen(
    promotionsByBrand: Map<String, List<PromotionEntity>>,
    allLocales: List<LocalDelDia>,
    chainToLocales: Map<String, String> = emptyMap(),
    onAddressClick: (String) -> Unit = {},
    onSharePromo: (PromotionEntity) -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    promotionErrorMessage: String? = null,
    onDismissError: () -> Unit = {},
    routeBrands: Set<String> = emptySet(),
    routeChains: Set<String> = emptySet(),
    onGlobalSearch: () -> Unit = {},
) {
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    val dimens = LocalAppDimens.current

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        ) {
            PromotionsOverviewScreen(
                promotionsByBrand = promotionsByBrand,
                chainToLocales = chainToLocales,
                showBack = false,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                onPromoClick = { selectedBrand = it },
                onSharePromo = onSharePromo,
                promotionErrorMessage = promotionErrorMessage,
                onDismissError = onDismissError,
                routeBrands = routeBrands,
                routeChains = routeChains,
                onGlobalSearch = onGlobalSearch,
            )
        }
        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
        ) {
            if (selectedBrand != null) {
                AllLocalesScreen(
                    locales = allLocales,
                    onClose = { selectedBrand = null },
                    onAddressClick = onAddressClick,
                    initialSearch = selectedBrand!!,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(dimens.spacingSection),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.selecciona_marca_detalle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
