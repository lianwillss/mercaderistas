package com.rutamercaderistas.ui.components

import android.content.res.Configuration
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.ComponentShapes

@Composable
fun StatsCards(
    stats: RuteroRepository.Stats,
    modifier: Modifier = Modifier,
    onLocalesClick: () -> Unit = {},
    onMarcasClick: () -> Unit = {},
    marcasConPromo: Int = 0,
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
            label = stringResource(R.string.stats_locales_label),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onLocalesClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Rounded.ShoppingBag,
            value = stats.totalMarcas,
            label = stringResource(R.string.stats_marcas_label),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = onMarcasClick,
            modifier = Modifier.weight(1f),
            badge = if (marcasConPromo > 0) {
                {
                    Text(
                        text = stringResource(R.string.stats_con_promo, marcasConPromo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else null
        )
        StatCard(
            icon = Icons.Rounded.Visibility,
            value = stats.visitasTotales,
            label = stringResource(R.string.stats_visitas_label),
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
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    badge: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = ComponentShapes.cardSmall,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
            Text(
                text = "$value",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            StatsCards(
                stats = RuteroRepository.Stats(12, 45, 67),
                onLocalesClick = {},
                onMarcasClick = {},
                marcasConPromo = 3,
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsCardsPreviewDark() {
    if (BuildConfig.DEBUG) {
        StatsCardsPreview()
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsCardsPreviewNoPromos() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            StatsCards(
                stats = RuteroRepository.Stats(8, 20, 30),
                onLocalesClick = {},
                onMarcasClick = {},
                marcasConPromo = 0,
            )
        }
    }
}
