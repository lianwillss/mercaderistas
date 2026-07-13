package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.DateFormatters
import com.rutamercaderistas.utils.cleanBrand
import com.rutamercaderistas.utils.normalizeBrand
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromoDaySection(
    locales: List<LocalDelDia>,
    promotionsByBrand: Map<String, List<PromotionEntity>>,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
) {
    val dayPromos = remember(locales, promotionsByBrand) {
        val matched = mutableListOf<PromotionEntity>()
        val seen = mutableSetOf<String>()
        for (local in locales) {
            for (cliente in local.clientes) {
                val cleanName = normalizeBrand(cliente.nombre).cleanBrand()
                val brandPromos = promotionsByBrand[cleanName].orEmpty()
                for (promo in brandPromos) {
                    val ok = local.cadena.isBlank() || normalizeChain(promo.chain) == normalizeChain(local.cadena)
                    if (ok && seen.add(promo.productName + promo.brand + promo.chain)) {
                        matched.add(promo)
                    }
                }
            }
        }
        matched.sortedWith(compareByDescending<PromotionEntity> { promo ->
            urgency(promo.endDate).ordinal
        }.thenBy { it.brand }.thenBy { it.chain })
    }

    if (dayPromos.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val today = LocalDate.now()
    val expiringToday = dayPromos.count { promo ->
        try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today }
        catch (_: Exception) { false }
    }
    val expiringTomorrow = dayPromos.count { promo ->
        try { promo.endDate.isNotBlank() && LocalDate.parse(promo.endDate) == today.plusDays(1) }
        catch (_: Exception) { false }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .animateContentSize(animationSpec = tween(250)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRefreshing) MaterialTheme.colorScheme.errorContainer.copy(alpha = pulseAlpha)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uD83D\uDD25",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${dayPromos.size} promo${if (dayPromos.size != 1) "nes" else ""} activas",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (expiringToday > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFFF0E0))
                                        .heightIn(min = 22.dp, max = 32.dp)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "\uD83D\uDD25 $expiringToday promo${if (expiringToday == 1) "" else "s"} hoy",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFD97706),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (expiringTomorrow > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .heightIn(min = 22.dp, max = 32.dp)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "\uD83D\uDD25 $expiringTomorrow ma\u00F1ana",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF0369A1),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(10.dp))

                val groupedByChain = dayPromos.groupBy { normalizeChain(it.chain).ifBlank { "Sin cadena" } }
                groupedByChain.forEach { (chain, promos) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uD83C\uDFEA",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = chain.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = chainColor(chain),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    promos.forEach { promo ->
                        Row(
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = promo.productName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row {
                                    if (promo.price.isNotBlank()) {
                                        Text(
                                            text = promo.price,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (promo.endDate.isNotBlank()) {
                                        Text(
                                            text = relativeDate(promo.endDate),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = relativeDateColor(promo.endDate),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun relativeDate(iso: String): String {
    return try {
        val date = LocalDate.parse(iso)
        val today = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        when {
            days < 0 -> "Vencida"
            days == 0L -> "\uD83D\uDD25 Hoy"
            days == 1L -> "\uD83D\uDD25 Termina ma\u00F1ana"
            days <= 7 -> "$days d\u00EDas"
            else -> DateFormatters.formatShort(date)
        }
    } catch (_: Exception) { iso }
}

@Composable
private fun relativeDateColor(iso: String): Color {
    val colorScheme = MaterialTheme.colorScheme
    return try {
        val date = LocalDate.parse(iso)
        val today = LocalDate.now()
        when {
            date == today -> Color(0xFFD97706)
            date == today.plusDays(1) -> Color(0xFF0369A1)
            else -> colorScheme.onSurfaceVariant
        }
    } catch (_: Exception) { colorScheme.onSurfaceVariant }
}

private enum class UrgencyLevel { NORMAL, TOMORROW, TODAY }

private fun urgency(endDate: String): UrgencyLevel {
    return try {
        if (endDate.isBlank()) return UrgencyLevel.NORMAL
        val date = LocalDate.parse(endDate)
        val today = LocalDate.now()
        when {
            date == today -> UrgencyLevel.TODAY
            date == today.plusDays(1) -> UrgencyLevel.TOMORROW
            else -> UrgencyLevel.NORMAL
        }
    } catch (_: Exception) { UrgencyLevel.NORMAL }
}
