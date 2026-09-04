package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.DateFormatters
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.PriceBlue
import com.rutamercaderistas.ui.theme.PriceGreen
import com.rutamercaderistas.ui.theme.PriceOrange
import com.rutamercaderistas.ui.theme.PricePurple

@Composable
fun PromotionBadge(
    count: Int,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    Box(
        modifier = Modifier
            .heightIn(min = dimens.touchMin)
            .toggleable(
                value = expanded,
                role = Role.Button,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = pluralStringResource(R.plurals.promos_count_plural, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (expanded) {
                    Text(
                        text = "▲",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private enum class PriceType { MONEY, PERCENT, MULTI_BUY, TEXT }

private fun classifyPrice(price: String): PriceType {
    val t = price.trim().uppercase()
    return when {
        t.startsWith("$") -> PriceType.MONEY
        t.contains("%") -> PriceType.PERCENT
        Regex("""\d+X\d+""").containsMatchIn(t) -> PriceType.MULTI_BUY
        else -> PriceType.TEXT
    }
}

@Composable
fun PromoPriceLabel(price: String) {
    val (color, prefix) = when (classifyPrice(price)) {
        PriceType.MONEY -> PriceBlue to null
        PriceType.PERCENT -> PriceGreen to null
        PriceType.MULTI_BUY -> PricePurple to null
        PriceType.TEXT -> PriceOrange to null
    }
    Text(
        text = price.trim(),
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

@Composable
fun PromotionList(
    promotions: List<PromotionEntity>,
    marginStart: Dp = 0.dp,
    showChain: Boolean = false,
) {
    if (promotions.isEmpty()) return

    val dimens = LocalAppDimens.current
    val promosTitle = stringResource(R.string.promociones_title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = marginStart)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(top = dimens.spacingMd),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Spacer(modifier = Modifier.height(dimens.spacingMd))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = promosTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacingSm))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = dimens.spacingLg, vertical = dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
        ) {
            promotions.forEach { promo ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = promo.productName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showChain && promo.chain.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(chainColor(promo.chain).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = promo.chain,
                                style = MaterialTheme.typography.labelMedium,
                                color = chainTextColor(promo.chain),
                            )
                        }
                    }
                    if (promo.price.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        PromoPriceLabel(price = promo.price)
                    }
                    if (promo.endDate.isNotBlank() || promo.startDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        val dateText = buildString {
                            if (promo.startDate.isNotBlank()) append(formatDate(promo.startDate))
                            if (promo.endDate.isNotBlank()) {
                                if (isNotEmpty()) append(" → ")
                                append(formatDate(promo.endDate))
                            }
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
