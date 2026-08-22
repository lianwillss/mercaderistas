package com.rutamercaderistas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs

@Composable
fun StatsCards(
    stats: RuteroRepository.Stats,
    modifier: Modifier = Modifier,
    onLocalesClick: () -> Unit = {},
    onMarcasClick: () -> Unit = {},
    onCodProvClick: () -> Unit = {},
    onCodEanClick: () -> Unit = {},
    marcasConPromo: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LocalAppDimens.current.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(4.dp * rs())
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
                        text = stringResource(R.string.marcas_con_promociones, marcasConPromo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
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
        StatCard(
            icon = Icons.Rounded.Badge,
            value = CodProvItems.size,
            label = stringResource(R.string.stats_cod_prov_label),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onCodProvClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Rounded.FormatAlignJustify,
            value = stats.totalCodEan,
            label = stringResource(R.string.stats_cod_ean_label),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCodEanClick,
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
    val factor = rs()
    val chipSize = 18.dp * factor
    val iconSize = 12.dp * factor
    val padV = 3.dp * factor
    val padH = 5.dp * factor
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padH, vertical = padV),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(chipSize)
                    .clip(CircleShape)
                    .background(containerColor.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
                )
            }
            Spacer(modifier = Modifier.height(1.dp * factor))
            Text(
                text = "$value",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(1.dp * factor))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (badge != null) {
                Spacer(modifier = Modifier.height(1.dp * factor))
                badge()
            }
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
