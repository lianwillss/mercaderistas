package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.components.CodProvItems
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft

enum class BottomBarKey { MAIN, MARCAS, VISITAS, CODPROV, EAN }

@Composable
fun AppBottomBar(
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    onCodProvClick: () -> Unit,
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
    modifier: Modifier = Modifier,
) {
    val items = appNavItems(stats, marcasConPromo)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item.key == selectedKey
                val isAction = item.key == BottomBarKey.CODPROV
                BottomBarItem(
                    selected = isSelected,
                    onClick = {
                        if (isAction) onCodProvClick() else onNavigate(item.key)
                    },
                    icon = item.icon,
                    label = item.label,
                    accent = item.accent,
                    accentSoft = item.accentSoft,
                    value = item.value,
                    badge = item.badge,
                )
            }
        }
    }
}

@Composable
private fun appNavItems(
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
): List<BottomBarItemData> = listOf(
    BottomBarItemData(
        key = BottomBarKey.MAIN,
        label = stringResource(R.string.rutero_title),
        icon = { tint -> Icon(Icons.Outlined.Storefront, contentDescription = null, tint = tint) },
        accent = MaterialTheme.colorScheme.primary,
        accentSoft = MaterialTheme.colorScheme.primaryContainer,
    ),
    BottomBarItemData(
        key = BottomBarKey.MARCAS,
        label = stringResource(R.string.stats_marcas_label),
        icon = { tint -> Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = tint) },
        accent = AccentGreen,
        accentSoft = AccentGreenSoft,
        value = stats.totalMarcas.toString(),
        badge = if (marcasConPromo > 0) marcasConPromo.toString() else null,
    ),
    BottomBarItemData(
        key = BottomBarKey.VISITAS,
        label = stringResource(R.string.stats_visitas_label),
        icon = { tint -> Icon(Icons.Filled.Visibility, contentDescription = null, tint = tint) },
        accent = AccentOrange,
        accentSoft = AccentOrangeSoft,
        value = stats.visitasTotales.toString(),
    ),
    BottomBarItemData(
        key = BottomBarKey.CODPROV,
        label = stringResource(R.string.stats_cod_prov_label),
        icon = { tint -> Icon(Icons.Filled.Badge, contentDescription = null, tint = tint) },
        accent = AccentBlue,
        accentSoft = AccentBlueSoft,
        value = CodProvItems.size.toString(),
    ),
    BottomBarItemData(
        key = BottomBarKey.EAN,
        label = stringResource(R.string.stats_cod_ean_label),
        icon = { tint -> Icon(painterResource(R.drawable.ic_barcode), contentDescription = null, tint = tint) },
        accent = StoreColorPurple,
        accentSoft = StoreColorPurpleSoft,
    ),
)

@Composable
fun AppNavigationRail(
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    onCodProvClick: () -> Unit,
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
    modifier: Modifier = Modifier,
) {
    val items = appNavItems(stats, marcasConPromo)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item ->
                val isSelected = item.key == selectedKey
                val isAction = item.key == BottomBarKey.CODPROV
                BottomBarItem(
                    selected = isSelected,
                    onClick = {
                        if (isAction) onCodProvClick() else onNavigate(item.key)
                    },
                    icon = item.icon,
                    label = item.label,
                    accent = item.accent,
                    accentSoft = item.accentSoft,
                    value = item.value,
                    badge = item.badge,
                )
            }
        }
    }
}

private data class BottomBarItemData(
    val key: BottomBarKey,
    val label: String,
    val icon: @Composable (Color) -> Unit,
    val accent: Color,
    val accentSoft: Color,
    val value: String? = null,
    val badge: String? = null,
)

@Composable
private fun BottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    label: String,
    accent: Color,
    accentSoft: Color,
    value: String? = null,
    badge: String? = null,
) {
    val iconColor = if (selected) accent else accent.copy(alpha = 0.55f)
    val bg = if (selected) accentSoft else Color.Transparent
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick, role = Role.Button)
                    .semantics {
                        contentDescription = if (value != null || badge != null) {
                            "$label${if (value != null) ": $value" else ""}${if (badge != null) ", $badge promociones" else ""}"
                        } else {
                            label
                        }
                    }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon(iconColor)
            if (badge != null) {
                Badge(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd),
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = iconColor,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        if (selected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 3.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}
