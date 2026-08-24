package com.rutamercaderistas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.PriceOrange
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft
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
            accent = AccentBlue,
            accentSoft = AccentBlueSoft,
            onClick = onLocalesClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Rounded.ShoppingBag,
            value = stats.totalMarcas,
            label = stringResource(R.string.stats_marcas_label),
            accent = AccentGreen,
            accentSoft = AccentGreenSoft,
            onClick = onMarcasClick,
            modifier = Modifier.weight(1f),
            promo = marcasConPromo
        )
        StatCard(
            icon = Icons.Rounded.Visibility,
            value = stats.visitasTotales,
            label = stringResource(R.string.stats_visitas_label),
            accent = AccentOrange,
            accentSoft = AccentOrangeSoft,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Rounded.Badge,
            value = CodProvItems.size,
            label = stringResource(R.string.stats_cod_prov_label),
            accent = AccentBlue,
            accentSoft = AccentBlueSoft,
            onClick = onCodProvClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            painterIcon = painterResource(R.drawable.ic_barcode),
            value = null,
            label = stringResource(R.string.stats_cod_ean_label),
            accent = StoreColorPurple,
            accentSoft = StoreColorPurpleSoft,
            actionLabel = stringResource(R.string.ean_tap_to_search),
            actionColor = StoreColorPurple,
            onClick = onCodEanClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector? = null,
    painterIcon: Painter? = null,
    value: Int? = null,
    label: String,
    accent: Color,
    accentSoft: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    actionLabel: String? = null,
    actionColor: Color = accent,
    promo: Int? = null,
) {
    val factor = rs()
    val chipSize = 22.dp * factor
    val iconSize = 14.dp * factor
    val padV = 6.dp * factor
    val padH = 6.dp * factor
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp * factor),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = accentSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(32.dp * factor)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, accent.copy(alpha = 0.30f))
                        )
                    )
            )
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
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (painterIcon != null) {
                        Icon(
                            painter = painterIcon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(iconSize)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
                if (value != null) {
                    Spacer(modifier = Modifier.height(4.dp * factor))
                    Text(
                        text = "$value",
                        style = numberTextStyle(value.toString().length),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp * factor))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (actionLabel != null) {
                    Spacer(modifier = Modifier.height(2.dp * factor))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = actionColor,
                        maxLines = 1,
                    )
                }
                if (promo != null && promo > 0) {
                    Spacer(modifier = Modifier.height(4.dp * factor))
                    Box(
                        modifier = Modifier
                            .background(AccentOrangeSoft, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp * factor, vertical = 2.dp * factor),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_promo),
                                    contentDescription = null,
                                    tint = PriceOrange,
                                    modifier = Modifier.size(10.dp * factor)
                                )
                                Spacer(modifier = Modifier.width(2.dp * factor))
                                Text(
                                    text = "$promo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PriceOrange,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = stringResource(R.string.promociones_word),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = PriceOrange,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun numberTextStyle(digits: Int) = MaterialTheme.typography.titleLarge.copy(
    fontSize = when {
        digits <= 2 -> 22.sp
        digits == 3 -> 20.sp
        digits == 4 -> 17.sp
        else -> 15.sp
    },
    fontWeight = FontWeight.Bold,
)

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
