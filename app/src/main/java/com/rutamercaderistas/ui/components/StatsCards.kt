package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.services.RuteroRepository

@Composable
fun StatsCards(
    stats: RuteroRepository.Stats,
    onLocalesClick: () -> Unit = {},
    onMarcasClick: () -> Unit = {},
    marcasConPromo: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatCard(
            icon = Icons.Rounded.Store,
            value = stats.totalLocales,
            label = "Locales",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onLocalesClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Rounded.ShoppingBag,
            value = stats.totalMarcas,
            label = "Marcas",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = onMarcasClick,
            modifier = Modifier.weight(1f),
            badge = if (marcasConPromo > 0) {
                {
                    Text(
                        text = "${marcasConPromo} con promo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53935),
                    )
                }
            } else null
        )
        StatCard(
            icon = Icons.Rounded.Visibility,
            value = stats.visitasTotales,
            label = "Visitas",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: Int,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 })
                },
                label = "stat_value"
            ) { targetValue ->
                Text(
                    text = "$targetValue",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.height(2.dp))
                badge()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsCardsPreview() {
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        StatsCards(
            stats = RuteroRepository.Stats(12, 45, 67),
            onLocalesClick = {},
            onMarcasClick = {},
            marcasConPromo = 3,
        )
    }
}
