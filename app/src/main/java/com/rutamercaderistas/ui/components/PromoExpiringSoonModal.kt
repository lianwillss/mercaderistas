package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rutamercaderistas.data.local.PromotionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun PromoExpiringSoonModal(
    promos: List<PromotionEntity>,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250)),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = false) { }
                        .padding(24.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Por vencer",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${promos.size} promo${if (promos.size != 1) "s" else ""} pr\u00F3xima${if (promos.size != 1) "s" else ""} a vencer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "\u2715",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (promos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No hay promociones pr\u00F3ximas a vencer.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                items(promos, key = { it.id }) { promo ->
                                    ExpiringPromoRow(promo = promo)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cerrar",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringPromoRow(promo: PromotionEntity) {
    val daysRemaining = try {
        val end = LocalDate.parse(promo.endDate)
        ChronoUnit.DAYS.between(LocalDate.now(), end)
    } catch (_: Exception) { Long.MAX_VALUE }

    val endFormatted = try {
        LocalDate.parse(promo.endDate).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    } catch (_: Exception) { promo.endDate }

    val daysText = when {
        daysRemaining < 0 -> "Venci\u00F3"
        daysRemaining == 0L -> "Vence hoy"
        daysRemaining == 1L -> "Vence ma\u00F1ana"
        else -> "Vence en $daysRemaining d\u00EDas"
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
                        text = endFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = daysText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (daysRemaining <= 1) FontWeight.Bold else FontWeight.Medium,
            color = when {
                daysRemaining < 0 -> MaterialTheme.colorScheme.error
                daysRemaining <= 1 -> Color(0xFFD97706)
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}
