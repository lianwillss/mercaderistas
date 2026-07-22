package com.rutamercaderistas.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.components.BrandCard
import com.rutamercaderistas.ui.components.ScreenHeader
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.UrgencyOrange
import timber.log.Timber
import com.rutamercaderistas.ui.components.ShimmerPromotionsContent
import com.rutamercaderistas.ui.components.urgency
import com.rutamercaderistas.domain.model.normalizeChain
import java.time.LocalDate

private val BottomPadding = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsOverviewScreen(
    promotionsByBrand: Map<String, List<PromotionEntity>>,
    chainToLocales: Map<String, String> = emptyMap(),
    onClose: () -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    onPromoClick: (brand: String) -> Unit = {},
    onSharePromo: (PromotionEntity) -> Unit = {},
    promotionErrorMessage: String? = null,
    onDismissError: () -> Unit = {},
    routeBrands: Set<String> = emptySet(),
    routeChains: Set<String> = emptySet(),
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var soloMisMarcas by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(promotionErrorMessage) {
        if (promotionErrorMessage != null) {
            kotlinx.coroutines.delay(5000)
            onDismissError()
        }
    }

    val allChains = remember(promotionsByBrand) {
        promotionsByBrand.values.flatten()
            .map { normalizeChain(it.chain) }
            .filter { it.isNotBlank() }
            .distinct().sorted()
    }

    var selectedChain by rememberSaveable { mutableStateOf("Todas") }
    var soloHoy by rememberSaveable { mutableStateOf(false) }

    fun brandUrgencyTier(promos: List<PromotionEntity>): Int {
        val max = promos.maxOfOrNull { urgency(it.endDate).ordinal } ?: 0
        return max // TODAY=2, TOMORROW=1, NORMAL=0 — descending
    }

    val sortedEntries = remember(promotionsByBrand) {
        promotionsByBrand.entries
            .map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, List<PromotionEntity>>> { (_, p) -> brandUrgencyTier(p) }
                .thenBy { (brand, _) -> brand })
    }

    val brandFiltered = remember(sortedEntries, soloMisMarcas, routeBrands, routeChains) {
        if (soloMisMarcas && routeBrands.isNotEmpty()) {
            sortedEntries.map { (brand, promos) ->
                brand to if (routeChains.isNotEmpty()) {
                    promos.filter { normalizeChain(it.chain) in routeChains }
                } else promos
            }.filter { (brand, _) ->
                brand.lowercase() in routeBrands.map { it.lowercase() }
            }.filter { (_, promos) -> promos.isNotEmpty() }
        } else sortedEntries
    }

    val chainFiltered = remember(brandFiltered, selectedChain) {
        if (selectedChain == "Todas") {
            brandFiltered
        } else {
            brandFiltered.map { (brand, promos) ->
                brand to promos.filter { normalizeChain(it.chain) == selectedChain }
            }.filter { it.second.isNotEmpty() }
        }
    }

    val today = remember { LocalDate.now() }

    val hoyFiltered = remember(chainFiltered, soloHoy) {
        if (!soloHoy) chainFiltered
        else chainFiltered.map { (brand, promos) ->
            brand to promos.filter { promo ->
                try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
                catch (_: Exception) {
                    Timber.w("Error parseando endDate '%s' en filtro hoy", promo.endDate)
                    false
                }
            }
        }.filter { it.second.isNotEmpty() }
    }

    val filteredEntries by remember(hoyFiltered, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) hoyFiltered
            else {
                val q = searchQuery.lowercase().trim()
                hoyFiltered.filter { (brand, promos) ->
                    brand.lowercase().contains(q) ||
                    promos.any { promo ->
                        promo.productName.lowercase().contains(q) ||
                        promo.chain.lowercase().contains(q)
                    }
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
            title = stringResource(R.string.promociones_title),
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            verticalPadding = 16.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            val tfColors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            )
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.buscar_marca_producto),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.buscar_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.limpiar_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = ComponentShapes.textField,
                colors = tfColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.marcas_con_promociones, filteredEntries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (routeBrands.isNotEmpty()) {
                    FilterChip(
                        selected = soloMisMarcas,
                        onClick = { soloMisMarcas = !soloMisMarcas },
                        label = { Text(stringResource(R.string.solo_mis_marcas), style = MaterialTheme.typography.labelLarge) },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                FilterChip(
                    selected = soloHoy,
                    onClick = { soloHoy = !soloHoy },
                    label = { Text(stringResource(R.string.solo_hoy), style = MaterialTheme.typography.labelLarge) },
                )
                Spacer(modifier = Modifier.width(4.dp))
                allChains.forEach { chain ->
                    FilterChip(
                        selected = selectedChain == chain,
                        onClick = {
                            selectedChain = if (selectedChain == chain) "Todas" else chain
                        },
                        label = {
                            Text(
                                text = chain,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        promotionErrorMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                    .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = UrgencyOrange,
                )
                IconButton(onClick = onDismissError) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.descartar_cd),
                        tint = UrgencyOrange,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isRefreshing && promotionsByBrand.isEmpty()) {
                ShimmerPromotionsContent(modifier = Modifier.padding(top = 4.dp))
            } else if (filteredEntries.isEmpty()) {
                    val emptyMsg = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.sin_resultados_para, searchQuery)
                        selectedChain != "Todas" -> stringResource(R.string.sin_promociones_para, selectedChain)
                        else -> stringResource(R.string.sin_promociones_activas)
                    }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = emptyMsg,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BottomPadding),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(
                        items = filteredEntries,
                        key = { _, entry -> entry.first },
                    ) { index, (brand, promos) ->
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

                        Box(
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer(alpha = animAlpha)
                                .offset(y = animOffsetY)
                        ) {
                            BrandCard(
                                brand = brand,
                                promos = promos,
                                chainToLocales = chainToLocales,
                                onPromoClick = onPromoClick,
                                onSharePromo = onSharePromo,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PromotionsOverviewScreenPreview() {
    if (BuildConfig.DEBUG) {
        val testData = mapOf(
            "CUK" to listOf(
                PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Arroz 1 kg", price = "$1.990", endDate = "2026-07-31"),
                PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Fideos 500 g", price = "2x$1.500", endDate = "2026-07-31"),
            ),
            "OLIMPIA" to listOf(
                PromotionEntity(brand = "OLIMPIA", chain = "Lider", productName = "Té Verde 20 un.", price = "$1.490"),
            ),
        )
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            PromotionsOverviewScreen(
                promotionsByBrand = testData,
                onClose = {},
            )
        }
    }
}
