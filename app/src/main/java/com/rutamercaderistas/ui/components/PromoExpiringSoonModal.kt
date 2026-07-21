package com.rutamercaderistas.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.theme.UrgencyOrange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import timber.log.Timber

@Composable
fun PromoExpiringSoonModal(
    promos: List<PromotionEntity>,
    onDismiss: () -> Unit,
) {
    IosModal(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.por_vencer_title),
        subtitle = "${promos.size} promo${if (promos.size != 1) "s" else ""} próxima${if (promos.size != 1) "s" else ""} a vencer",
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        ) {
            itemsIndexed(promos, key = { _, p -> "${p.id}_${p.endDate}_${p.brand}" }) { index, promo ->
                ExpiringPromoRow(promo = promo)
                if (index < promos.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiringPromoRow(promo: PromotionEntity) {
    val endDate = try {
        val end = LocalDate.parse(promo.endDate)
        val days = ChronoUnit.DAYS.between(LocalDate.now(), end)
        val formatted = end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        DateInfo(parsed = true, daysRemaining = days, formatted = formatted)
    } catch (_: Exception) {
        Timber.w("Error parseando endDate '%s' en ExpiringPromoRow", promo.endDate)
        DateInfo(parsed = false, formatted = promo.endDate)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = promo.brand,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = promo.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = promo.chain,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = endDate.formatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (endDate.parsed) {
            val daysColor = when {
                endDate.daysRemaining < 0 -> MaterialTheme.colorScheme.error
                endDate.daysRemaining <= 1 -> UrgencyOrange
                else -> MaterialTheme.colorScheme.primary
            }
            val daysText = when {
                endDate.daysRemaining < 0 -> stringResource(R.string.vencio)
                endDate.daysRemaining == 0L -> stringResource(R.string.vence_hoy_label)
                endDate.daysRemaining == 1L -> stringResource(R.string.vence_manana_label)
                else -> stringResource(R.string.vence_en_dias, endDate.daysRemaining)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = daysText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (endDate.daysRemaining <= 1) FontWeight.Bold else FontWeight.Medium,
                color = daysColor,
            )
        }
    }
}

private data class DateInfo(
    val parsed: Boolean,
    val daysRemaining: Long = 0,
    val formatted: String = "",
) {
    val daysText: String get() = when {
        daysRemaining < 0 -> "Venció"
        daysRemaining == 0L -> "Vence hoy"
        daysRemaining == 1L -> "Vence mañana"
        else -> "Vence en $daysRemaining días"
    }
}
