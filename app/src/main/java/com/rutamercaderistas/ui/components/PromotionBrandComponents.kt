package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.DateFormatters
import com.rutamercaderistas.ui.theme.DiscountRed
import com.rutamercaderistas.ui.theme.DiscountSoft
import com.rutamercaderistas.ui.theme.PromoGradientEnd
import com.rutamercaderistas.ui.theme.PromoGradientStart
import com.rutamercaderistas.ui.theme.UrgencyBadgeSoft
import com.rutamercaderistas.ui.theme.UrgencyOrange
import com.rutamercaderistas.ui.theme.UrgencyOrangeSoft
import com.rutamercaderistas.ui.theme.UrgencyTomorrowSoft
import timber.log.Timber
import java.time.LocalDate

enum class Urgency { NORMAL, TOMORROW, TODAY }

fun urgency(endDate: String): Urgency {
    return try {
        if (endDate.isBlank()) return Urgency.NORMAL
        val end = LocalDate.parse(endDate)
        val today = LocalDate.now()
        when {
            end == today -> Urgency.TODAY
            end == today.plusDays(1) -> Urgency.TOMORROW
            else -> Urgency.NORMAL
        }
    } catch (_: Exception) {
        Timber.w("Error parseando endDate '%s' en computeUrgency", endDate)
        Urgency.NORMAL
    }
}

fun formatDate(iso: String): String = DateFormatters.formatFull(iso)

fun dateText(promo: PromotionEntity): String {
    val start = if (promo.startDate.isNotBlank()) formatDate(promo.startDate) else null
    val end = if (promo.endDate.isNotBlank()) formatDate(promo.endDate) else null
    return when {
        start != null && end != null -> "$start → $end"
        start != null -> "Desde $start"
        end != null -> "Hasta $end"
        else -> ""
    }
}

@Composable
fun BrandCard(
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
                        contentDescription = stringResource(R.string.marca_cd),
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
                            Brush.horizontalGradient(listOf(PromoGradientStart, PromoGradientEnd))
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    val promocionesCd = stringResource(R.string.promociones_cd)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uD83D\uDD25",
                            modifier = Modifier.semantics { contentDescription = promocionesCd },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.promos_count, promos.size, if (promos.size != 1) "s" else ""),
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
                    ChainHeader(chain = chainKey, localName = localName, count = chainPromos.size)
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
                        text = stringResource(R.string.ver_mas, promos.size - 3),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChainHeader(chain: String, localName: String, count: Int) {
    val cadenaCd = stringResource(R.string.cadena_cd)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\uD83C\uDFEA",
            modifier = Modifier.semantics { contentDescription = cadenaCd },
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
                    Urgency.TODAY -> UrgencyOrangeSoft
                    Urgency.TOMORROW -> UrgencyTomorrowSoft
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
                val emojiDesc = if (urg == Urgency.TODAY) stringResource(R.string.vence_hoy) else stringResource(R.string.termina_manana)
                Text(
                    text = if (urg == Urgency.TODAY) "\u26A0\uFE0F" else "\uD83D\uDD25",
                    modifier = Modifier.semantics { contentDescription = emojiDesc },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = emojiDesc,
                    style = MaterialTheme.typography.labelLarge,
                    color = UrgencyOrange,
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
                    color = if (urg != Urgency.NORMAL) UrgencyOrange else MaterialTheme.colorScheme.primary,
                )
                if (promo.price.startsWith("$")) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (urg != Urgency.NORMAL) UrgencyBadgeSoft else DiscountSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.descuento),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (urg != Urgency.NORMAL) UrgencyOrange else DiscountRed,
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
