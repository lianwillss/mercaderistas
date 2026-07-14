package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.Outline
import com.rutamercaderistas.ui.components.normalizeChain
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor
import com.rutamercaderistas.utils.cleanBrand
import com.rutamercaderistas.utils.normalizeBrand
import kotlin.math.abs
import timber.log.Timber

@Composable
fun StoreCard(
    local: LocalDelDia,
    marcaResaltada: String?,
    onBrandClick: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    onShareLocal: (String) -> Unit = {},
    promotionsByBrand: Map<String, List<PromotionEntity>> = emptyMap(),
    index: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val animAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250, delayMillis = index * 50)
    )
    val animOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 12.dp,
        animationSpec = tween(250, delayMillis = index * 50)
    )

    val brandCleanCache = remember(local) {
        local.clientes.associate { it.nombre to normalizeBrand(it.nombre).cleanBrand() }
    }

    val hasPromos = remember(local, promotionsByBrand) {
        local.clientes.any { cliente ->
            val cleanName = brandCleanCache[cliente.nombre] ?: normalizeBrand(cliente.nombre).cleanBrand()
            val promos = promotionsByBrand[cleanName].orEmpty()
                .filter { matchesChain(it.chain, local.local, local.cadena, local.formato) }
            promos.isNotEmpty()
        }
    }

    LaunchedEffect(local) { visible = true }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = animAlpha)
            .offset(y = animOffsetY),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(250))
        ) {
            // ── Header row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(storeSoftColor(local.local)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Store,
                        contentDescription = "Local",
                        tint = storeColor(local.local),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = local.local.ifBlank { "S/N" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasPromos) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "\uD83D\uDD25",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    if (local.codigo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = local.codigo,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAddressClick(local.direccion) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Dirección",
                                tint = AccentBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                if (local.direccion.isNotBlank()) {
                                    Text(
                                        text = local.direccion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AccentBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (local.comuna.isNotBlank()) {
                                    Text(
                                        text = local.comuna,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AccentBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onShareLocal(buildStoreShareText(local, promotionsByBrand, brandCleanCache)) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Compartir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${local.totalClientes}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AccentBlue,
                        )
                        Text(
                            text = "marcas",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentBlue,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // ── Divider ──
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Outline)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ── Brands ──
            local.clientes.forEach { cliente ->
                val cleanBrandName = brandCleanCache[cliente.nombre] ?: normalizeBrand(cliente.nombre).cleanBrand()
                val promos = promotionsByBrand[cleanBrandName]
                    .orEmpty()
                    .filter { matchesChain(it.chain, local.local, local.cadena, local.formato) }
                if (promos.isNotEmpty()) {
                    Timber.d("STORE: \"%s\" | cadena=\"%s\" formato=\"%s\" effChain=\"%s\" | marca=\"%s\" clean=\"%s\" → %d promos",
                        local.local, local.cadena, local.formato,
                        normalizeChain(effectiveChain(local.cadena, local.formato)),
                        cliente.nombre, cleanBrandName, promos.size)
                } else {
                    val effChain = effectiveChain(local.cadena, local.formato)
                    val normEffChain = normalizeChain(effChain)
                    val brandPromos = promotionsByBrand[cleanBrandName].orEmpty()
                    val chainCount = brandPromos.size
                    val matchingChain = brandPromos.filter { normalizeChain(it.chain) == normEffChain }
                    val nonMatching = brandPromos.filter { normalizeChain(it.chain) != normEffChain }
                    if (chainCount > 0) {
                        Timber.d("STORE: \"%s\" | marca=\"%s\" clean=\"%s\" | %d promos en map, 0 tras filtro cadena | effChain=\"%s\" norm=\"%s\" | coincide=%d difiere=%d | chains=%s",
                            local.local, cliente.nombre, cleanBrandName,
                            chainCount, effChain, normEffChain,
                            matchingChain.size, nonMatching.size,
                            nonMatching.joinToString(",") { "\"${it.chain}\"" })
                    }
                }
                BrandItem(
                    cliente = cliente,
                    promotions = promos,
                    isHighlighted = marcaResaltada != null &&
                        cliente.nombre.equals(marcaResaltada, ignoreCase = true),
                    onClick = { onBrandClick(cliente.nombre) }
                )
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

// ── Tarjeta de marca ─────────────────────────────────────────────

@Composable
private fun BrandItem(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    if (cliente.esPrioritaria) {
        PriorityBrandCard(
            cliente = cliente,
            promotions = promotions,
            expanded = expanded,
            isHighlighted = isHighlighted,
            onClick = onClick,
            onToggle = { expanded = !expanded },
        )
    } else {
        NormalBrandRow(
            cliente = cliente,
            promotions = promotions,
            expanded = expanded,
            isHighlighted = isHighlighted,
            onClick = onClick,
            onToggle = { expanded = !expanded },
        )
    }
}

@Composable
private fun PriorityBrandCard(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    expanded: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250)),
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            AccentOrange,
                            RoundedCornerShape(topStart = 11.dp, bottomStart = 11.dp)
                        )
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 5.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(AccentOrangeSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Prioritaria",
                            tint = AccentOrange,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = cliente.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (promotions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(5.dp))
                        PromotionBadge(
                            count = promotions.size,
                            expanded = expanded,
                            onClick = onToggle,
                        )
                    }

                    if (cliente.frecuencia > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        FrequencyChip(text = cliente.frecuenciaTexto)
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && promotions.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(250)),
            ) {
                PromotionList(promotions = promotions, marginStart = 5.dp, showChain = true)
            }
        }
    }
}

@Composable
private fun NormalBrandRow(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    expanded: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(250)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (isHighlighted) Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) else Modifier
                )
                .clickable(onClick = onClick)
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(avatarSoftColor(cliente.nombre)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(cliente.nombre),
                    style = MaterialTheme.typography.labelSmall,
                    color = avatarColor(cliente.nombre),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = cliente.nombre,
                style = if (isHighlighted) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (promotions.isNotEmpty()) {
                Spacer(modifier = Modifier.width(5.dp))
                PromotionBadge(
                    count = promotions.size,
                    expanded = expanded,
                    onClick = onToggle,
                )
            }

            if (cliente.frecuencia > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = cliente.frecuenciaTexto,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && promotions.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)),
        ) {
            PromotionList(promotions = promotions, marginStart = 2.dp, showChain = true)
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────

private val avatarColors = listOf(
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
    Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
    Color(0xFF039BE5), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFF7CB342), Color(0xFFFDD835), Color(0xFFFFB300),
    Color(0xFFFB8C00), Color(0xFFF4511E), Color(0xFF6D4C41),
    Color(0xFF546E7A),
)

private fun avatarColor(text: String): Color {
    return avatarColors[abs(text.hashCode()) % avatarColors.size]
}

private fun avatarSoftColor(text: String): Color {
    return avatarColor(text).copy(alpha = 0.15f)
}

private fun initials(text: String): String {
    val parts = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
    }
}

// ── Compartir ──────────────────────────────────────────────────

private fun buildStoreShareText(
    local: LocalDelDia,
    promotionsByBrand: Map<String, List<PromotionEntity>>,
    brandCleanCache: Map<String, String>,
): String {
    return buildString {
        appendLine("\uD83C\uDFEA ${local.local.ifBlank { "S/N" }}")
        if (local.direccion.isNotBlank()) appendLine("\uD83D\uDCCD ${local.direccion}")
        if (local.comuna.isNotBlank()) appendLine("\uD83C\uDFD9\uFE0F ${local.comuna}")
        appendLine()
        if (local.clientes.isNotEmpty()) {
            appendLine("Marcas (${local.totalClientes}):")
            local.clientes.forEach { c ->
                val clean = brandCleanCache[c.nombre] ?: normalizeBrand(c.nombre).cleanBrand()
                val promos = promotionsByBrand[clean].orEmpty()
                    .filter { matchesChain(it.chain, local.local, local.cadena, local.formato) }
                append("  \u2022 ${c.nombre}")
                if (promos.isNotEmpty()) append(" \uD83D\uDD25 ${promos.size} promo${if (promos.size != 1) "s" else ""}")
                appendLine()
                promos.forEach { p ->
                    append("    - ${p.productName}")
                    if (p.price.isNotBlank()) append(" \uD83D\uDCB0 ${p.price}")
                    appendLine()
                }
            }
        }
    }
}

// ── Frecuencia ────────────────────────────────────────────

@Composable
private fun FrequencyChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AccentGreenSoft)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
            )
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = "Frecuencia",
                tint = AccentGreen,
                modifier = Modifier.size(8.dp)
            )
        }
    }
}

// ── Preview ────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun StoreCardPreview() {
    val testPromos = mapOf(
        "marca a" to listOf(
            PromotionEntity(
                brand = "Marca A",
                chain = "Jumbo",
                productName = "Producto de prueba 1 kg",
                price = "$1.990",
                startDate = "2026-01-01",
                endDate = "2026-12-31",
            ),
        ),
    )
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        StoreCard(
            local = LocalDelDia(
                codigo = "001",
                local = "Local de prueba",
                direccion = "Av. Siempre Viva 123",
                cadena = "Jumbo",
                formato = "",
                region = "",
                comuna = "Santiago",
                clientes = listOf(
                    ClienteInfo("Marca A", true, 3),
                    ClienteInfo("Marca B", false, 0),
                ),
            ),
            promotionsByBrand = testPromos,
            marcaResaltada = null,
            onBrandClick = {},
            onAddressClick = {},
        )
    }
}
