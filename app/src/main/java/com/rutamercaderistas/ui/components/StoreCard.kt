package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.rutamercaderistas.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.domain.model.effectiveChain
import com.rutamercaderistas.domain.model.matchesChain
import com.rutamercaderistas.domain.model.normalizeChain
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor
import com.rutamercaderistas.utils.cleanBrand
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
    val dimens = LocalAppDimens.current
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
        local.clientes.associate { it.nombre to it.nombre.cleanBrand() }
    }

    val hasPromos = remember(local, promotionsByBrand) {
        local.clientes.any { cliente ->
            val cleanName = brandCleanCache[cliente.nombre] ?: cliente.nombre.cleanBrand()
            val promos = promotionsByBrand[cleanName].orEmpty()
                .filter { matchesChain(it.chain, local.local, local.cadena, local.formato) }
            promos.isNotEmpty()
        }
    }

    LaunchedEffect(Unit) { visible = true }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = animAlpha)
            .offset(y = animOffsetY),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingLg)
                .animateContentSize(animationSpec = tween(250))
        ) {
            // ── Header row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.iconXl)
                        .clip(MaterialTheme.shapes.medium)
                        .background(storeSoftColor(local.local)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Store,
                        contentDescription = stringResource(R.string.store_icon_cd),
                        tint = storeColor(local.local),
                        modifier = Modifier.size(dimens.iconSm)
                    )
                }

                Spacer(modifier = Modifier.width(dimens.spacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = local.local.ifBlank { stringResource(R.string.sin_numero) },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasPromos) {
                            val promosCd = stringResource(R.string.promociones_cd)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "\uD83D\uDD25",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { contentDescription = promosCd },
                            )
                        }
                    }

                    if (local.codigo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = local.codigo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    onClick = { onAddressClick(local.direccion) },
                                    role = androidx.compose.ui.semantics.Role.Button,
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = stringResource(R.string.direccion_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                if (local.direccion.isNotBlank()) {
                                    Text(
                                        text = local.direccion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (local.comuna.isNotBlank()) {
                                    Text(
                                        text = local.comuna,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(dimens.spacingSm))

                Box(
                    modifier = Modifier
                        .size(dimens.iconXxl)
                        .clip(CircleShape)
                        .clickable(
                            onClick = { onShareLocal(buildStoreShareText(local, promotionsByBrand, brandCleanCache)) },
                            role = androidx.compose.ui.semantics.Role.Button,
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.compartir),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconSm),
                    )
                }

                Spacer(modifier = Modifier.width(dimens.spacingSm))

                Box(
                    modifier = Modifier
                        .size(dimens.iconXl)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${local.totalClientes}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.storecard_marcas),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp * rs()))

                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // ── Divider ──
            Spacer(modifier = Modifier.height(14.dp * rs()))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.height(14.dp * rs()))

            // ── Brands ──
            local.clientes.forEach { cliente ->
                val cleanBrandName = brandCleanCache[cliente.nombre] ?: cliente.nombre.cleanBrand()
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
                Spacer(modifier = Modifier.height(5.dp * rs()))
            }
        }
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
                val clean = brandCleanCache[c.nombre] ?: c.nombre.cleanBrand()
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

// ── Preview ────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun StoreCardPreview() {
    if (BuildConfig.DEBUG) {
        StoreCardPreviewContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StoreCardPreviewDark() {
    if (BuildConfig.DEBUG) {
        StoreCardPreviewContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun StoreCardPreviewEmpty() {
    if (BuildConfig.DEBUG) {
        StoreCardPreviewContent(
            marcasConPromo = false,
            withPromotions = false,
        )
    }
}

@Composable
private fun StoreCardPreviewContent(
    marcasConPromo: Boolean = true,
    withPromotions: Boolean = true,
) {
    val testPromos = if (withPromotions) {
        mapOf(
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
    } else emptyMap()
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
                    ClienteInfo("Marca A", marcasConPromo, 3),
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
