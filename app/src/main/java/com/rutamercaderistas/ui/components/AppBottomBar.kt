package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft

enum class BottomBarKey { MAIN, MARCAS, LOCALES, CODPROV, EAN }

@Composable
fun AppBottomBar(
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
    promosExpiringToday: Int,
    hasPlanillaChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = appNavItems(stats, marcasConPromo, promosExpiringToday, hasPlanillaChanges)
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val density = LocalDensity.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            val slotWidthDp = (constraints.maxWidth / items.size) / density.density * 1f * 1f
            val slotDp = ((constraints.maxWidth.toFloat() / items.size) / density.density).dp
            var barHeightPx by remember { mutableIntStateOf(0) }

            val slideOffsetDp by animateDpAsState(
                targetValue = slotDp * selectedIndex,
                label = "bottomBarSlide",
            )
            val pillColor by animateColorAsState(
                targetValue = items[selectedIndex].accentSoft,
                label = "bottomBarPillColor",
            )
            val pillHeightDp = ((barHeightPx.toFloat() / density.density).dp - 12.dp).coerceAtLeast(28.dp)

            if (barHeightPx > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = slideOffsetDp + 8.dp, y = 6.dp)
                        .size(width = slotDp - 16.dp, height = pillHeightDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillColor),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                items.forEach { item ->
                    val isSelected = item.key == selectedKey
                    BottomBarItem(
                        selected = isSelected,
                        onClick = { onNavigate(item.key) },
                        icon = item.icon,
                        label = item.label,
                        accent = item.accent,
                        badge = item.badge,
                        dot = item.dot,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigationRail(
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
    promosExpiringToday: Int,
    hasPlanillaChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = appNavItems(stats, marcasConPromo, promosExpiringToday, hasPlanillaChanges)

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
                val bg by animateColorAsState(
                    targetValue = if (isSelected) item.accentSoft else Color.Transparent,
                    label = "railPill${item.key}",
                )
                BottomBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(item.key) },
                    icon = item.icon,
                    label = item.label,
                    accent = item.accent,
                    badge = item.badge,
                    dot = item.dot,
                    background = bg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun appNavItems(
    stats: RuteroRepository.Stats,
    marcasConPromo: Int,
    promosExpiringToday: Int,
    hasPlanillaChanges: Boolean,
): List<BottomBarItemData> = listOf(
    BottomBarItemData(
        key = BottomBarKey.MAIN,
        label = stringResource(R.string.rutero_title),
        icon = { tint, selected ->
            if (selected) Icon(Icons.Filled.Storefront, contentDescription = null, tint = tint)
            else Icon(Icons.Outlined.Storefront, contentDescription = null, tint = tint)
        },
        accent = MaterialTheme.colorScheme.primary,
        accentSoft = MaterialTheme.colorScheme.primaryContainer,
        dot = hasPlanillaChanges,
    ),
    BottomBarItemData(
        key = BottomBarKey.MARCAS,
        label = stringResource(R.string.stats_marcas_label),
        icon = { tint, selected ->
            if (selected) Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = tint)
            else Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = tint)
        },
        accent = AccentGreen,
        accentSoft = AccentGreenSoft,
        badge = if (marcasConPromo > 0) marcasConPromo.toString() else null,
    ),
    BottomBarItemData(
        key = BottomBarKey.LOCALES,
        label = stringResource(R.string.stats_locales_label),
        icon = { tint, selected ->
            if (selected) Icon(Icons.Filled.Visibility, contentDescription = null, tint = tint)
            else Icon(Icons.Outlined.Visibility, contentDescription = null, tint = tint)
        },
        accent = AccentOrange,
        accentSoft = AccentOrangeSoft,
    ),
    BottomBarItemData(
        key = BottomBarKey.CODPROV,
        label = stringResource(R.string.stats_cod_prov_label),
        icon = { tint, selected ->
            // Outlined para ambos; el color y scale marcan selección
            Icon(Icons.Outlined.Badge, contentDescription = null, tint = tint)
        },
        accent = AccentBlue,
        accentSoft = AccentBlueSoft,
    ),
    BottomBarItemData(
        key = BottomBarKey.EAN,
        label = stringResource(R.string.stats_cod_ean_label),
        icon = { tint, _ -> Icon(painterResource(R.drawable.ic_barcode), contentDescription = null, tint = tint) },
        accent = StoreColorPurple,
        accentSoft = StoreColorPurpleSoft,
    ),
)

private data class BottomBarItemData(
    val key: BottomBarKey,
    val label: String,
    val icon: @Composable (Color, Boolean) -> Unit,
    val accent: Color,
    val accentSoft: Color,
    val badge: String? = null,
    val dot: Boolean = false,
)

@Composable
private fun BottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color, Boolean) -> Unit,
    label: String,
    accent: Color,
    badge: String? = null,
    dot: Boolean = false,
    background: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "iconScale")
    val contentDesc = buildString {
        append(label)
        if (badge != null) append(": $badge")
        if (dot) append(", novedad")
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = contentDesc }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
                contentAlignment = Alignment.Center,
            ) {
                icon(iconColor, selected)
            }
            if (dot) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp, end = 2.dp)
                        .size(9.dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)),
                )
            }
            if (badge != null) {
                androidx.compose.material3.Badge(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 0.dp, end = 2.dp),
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}