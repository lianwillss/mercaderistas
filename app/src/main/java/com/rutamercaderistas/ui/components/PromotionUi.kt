package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.theme.AccentBlue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PromotionBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FF6B6B))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "\uD83D\uDD25", style = MaterialTheme.typography.labelSmall)
            Text(
                text = "${count} promo${if (count != 1) "nes" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE53935),
            )
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

private fun formatDate(iso: String): String {
    return try {
        LocalDate.parse(iso).format(dateFormatter)
    } catch (_: Exception) {
        iso
    }
}

@Composable
fun PromotionList(
    promotions: List<PromotionEntity>,
    marginStart: Dp = 0.dp,
) {
    Column(
        modifier = Modifier
            .padding(start = marginStart)
            .fillMaxWidth()
    ) {
        promotions.forEachIndexed { index, promo ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, top = 3.dp, bottom = 1.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = promo.productName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        if (promo.price.isNotBlank()) {
                            Text(
                                text = promo.price,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentBlue,
                            )
                        }
                        if (promo.endDate.isNotBlank()) {
                            Text(
                                text = "Hasta ${formatDate(promo.endDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
