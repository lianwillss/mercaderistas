package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.DateFormatters
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.StoreColorPurple

@Composable
fun PromotionBadge(
    count: Int,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        val promoIconCd = stringResource(R.string.promo_icon_cd)
        val expandidoCd = stringResource(R.string.expandido_cd)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "\uD83D\uDD25", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.semantics { contentDescription = promoIconCd })
            Text(
                text = stringResource(R.string.promos_count, count, if (count != 1) "s" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (expanded) {
                Text(
                    text = "▲",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = expandidoCd },
                )
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
        PriceType.MONEY -> AccentBlue to null
        PriceType.PERCENT -> AccentGreen to null
        PriceType.MULTI_BUY -> StoreColorPurple to null
        PriceType.TEXT -> AccentOrange to null
    }
    Text(
        text = price.trim(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
fun PromotionList(
    promotions: List<PromotionEntity>,
    marginStart: Dp = 0.dp,
    showChain: Boolean = false,
) {
    Column(
        modifier = Modifier
            .padding(start = marginStart)
            .fillMaxWidth()
    ) {
        promotions.forEachIndexed { index, promo ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp),
            ) {
                Text(
                    text = promo.productName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
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
                            text = promo.chain.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = chainColor(promo.chain),
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
