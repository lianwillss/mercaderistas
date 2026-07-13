package com.rutamercaderistas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.DateFormatters
import com.rutamercaderistas.ui.components.ShimmerPromotionsContent
import com.rutamercaderistas.ui.components.chainColor
import com.rutamercaderistas.ui.components.normalizeChain
import java.time.LocalDate

private fun formatDate(iso: String): String = DateFormatters.formatFull(iso)

private fun dateText(promo: PromotionEntity): String {
    val start = if (promo.startDate.isNotBlank()) formatDate(promo.startDate) else null
    val end = if (promo.endDate.isNotBlank()) formatDate(promo.endDate) else null
    return when {
        start != null && end != null -> "$start → $end"
        start != null -> "Desde $start"
        end != null -> "Hasta $end"
        else -> ""
    }
}

private enum class Urgency { NORMAL, TOMORROW, TODAY }

private fun urgency(endDate: String): Urgency {
    return try {
        if (endDate.isBlank()) return Urgency.NORMAL
        val end = LocalDate.parse(endDate)
        val today = LocalDate.now()
        when {
            end == today -> Urgency.TODAY
            end == today.plusDays(1) -> Urgency.TOMORROW
            else -> Urgency.NORMAL
        }
    } catch (_: Exception) { Urgency.NORMAL }
}

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

    val brandFiltered = remember(sortedEntries, soloMisMarcas, routeBrands) {
        if (soloMisMarcas && routeBrands.isNotEmpty()) {
            sortedEntries.filter { (brand, _) ->
                brand.lowercase() in routeBrands.map { it.lowercase() }
            }
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
                catch (_: Exception) { false }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Promociones",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Buscar marca, producto\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Buscar",
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
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${filteredEntries.size} marcas con promociones",
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
                        label = { Text("Solo mis marcas", style = MaterialTheme.typography.labelMedium) },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                FilterChip(
                    selected = soloHoy,
                    onClick = { soloHoy = !soloHoy },
                    label = { Text("Solo hoy", style = MaterialTheme.typography.labelMedium) },
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
                                style = MaterialTheme.typography.labelMedium,
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
                    color = Color(0xFFD97706),
                )
                IconButton(onClick = onDismissError) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Descartar",
                        tint = Color(0xFFD97706),
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
                    searchQuery.isNotBlank() -> "Sin resultados para \"$searchQuery\""
                    selectedChain != "Todas" -> "Sin promociones para $selectedChain"
                    else -> "Sin promociones activas"
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(
                        items = filteredEntries,
                        key = { _, entry -> entry.first },
                    ) { index, (brand, promos) ->
                        AnimatedBrandItem(
                            index = index,
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

@Composable
private fun AnimatedBrandItem(
    index: Int,
    brand: String,
    promos: List<PromotionEntity>,
    chainToLocales: Map<String, String>,
    onPromoClick: (String) -> Unit,
    onSharePromo: (PromotionEntity) -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(300, delayMillis = index * 60),
        ) + slideInVertically(
            animationSpec = tween(300, delayMillis = index * 60),
            initialOffsetY = { it / 3 },
        ),
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

@Composable
private fun BrandCard(
    brand: String,
    promos: List<PromotionEntity>,
    chainToLocales: Map<String, String>,
    onPromoClick: (String) -> Unit,
    onSharePromo: (PromotionEntity) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) promos else promos.take(3)
    val hasMore = promos.size > 3

    val groupedByChain = remember(visible) {
        visible.groupBy { it.chain.trim().uppercase() }
    }

    val sortedGroupKeys = remember(groupedByChain) {
        groupedByChain.keys.sorted()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(300)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingBag,
                        contentDescription = "Marca",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = brand.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFF97316), Color(0xFFEF4444))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uD83D\uDD25",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${promos.size} promo${if (promos.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            sortedGroupKeys.forEachIndexed { groupIndex, chainKey ->
                val chainPromos = groupedByChain[chainKey] ?: return@forEachIndexed

                if (groupIndex > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (chainKey.isNotBlank()) {
                    val localName = chainToLocales[chainKey] ?: chainKey

                    ChainHeader(
                        chain = chainKey,
                        localName = localName,
                        count = chainPromos.size,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                chainPromos.forEachIndexed { promoIndex, promo ->
                    if (promoIndex > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    ProductItem(
                        promo = promo,
                        onClick = { onPromoClick(promo.brand) },
                        onLongClick = { onSharePromo(promo) },
                    )
                }
            }

            if (hasMore && !expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Ver ${promos.size - 3} más",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainHeader(
    chain: String,
    localName: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\uD83C\uDFEA",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = localName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(chainColor(chain).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = chain.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = chainColor(chain),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductItem(
    promo: PromotionEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val urg = urgency(promo.endDate)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                when (urg) {
                    Urgency.TODAY -> Color(0xFFFFF0E0)
                    Urgency.TOMORROW -> Color(0xFFFFF8F0)
                    Urgency.NORMAL -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(
                start = if (urg != Urgency.NORMAL) 10.dp else 0.dp,
                end = if (urg != Urgency.NORMAL) 10.dp else 0.dp,
                top = if (urg != Urgency.NORMAL) 10.dp else 0.dp,
                bottom = if (urg != Urgency.NORMAL) 10.dp else 0.dp,
            ),
    ) {
        if (urg != Urgency.NORMAL) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(
                    text = if (urg == Urgency.TODAY) "\u26A0\uFE0F" else "\uD83D\uDD25",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (urg == Urgency.TODAY) "VENCE HOY" else "TERMINA MA\u00D1ANA",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD97706),
                )
            }
        }

        Text(
            text = promo.productName.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (promo.price.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = promo.price,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (urg != Urgency.NORMAL) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                )
                if (promo.price.startsWith("$")) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (urg != Urgency.NORMAL) Color(0xFFFEF3C7)
                                else Color(0xFFFEE2E2)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "DESCUENTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (urg != Urgency.NORMAL) Color(0xFFD97706) else Color(0xFFDC2626),
                        )
                    }
                }
            }
        }

        val dates = dateText(promo)
        if (dates.isNotBlank()) {
            Text(
                text = dates,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PromotionsOverviewScreenPreview() {
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
