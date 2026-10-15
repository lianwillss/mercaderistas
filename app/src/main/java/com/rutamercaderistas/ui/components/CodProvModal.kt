package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Badge
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.services.normalizeSearch
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentGreenText
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import com.rutamercaderistas.ui.theme.rs
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

data class CodProvItem(val nameRes: Int, val code: String)

val CodProvItems = listOf(
    CodProvItem(R.string.cod_prov_nat_natural, "13710"),
    CodProvItem(R.string.cod_prov_suk, "7088"),
    CodProvItem(R.string.cod_prov_fermentista, "1000339140"),
    CodProvItem(R.string.cod_prov_love_co, "76834823"),
    CodProvItem(R.string.cod_prov_cuk, "76381134 / 76381134-4"),
    CodProvItem(R.string.cod_prov_bymaria, "76371495"),
    CodProvItem(R.string.cod_prov_global_retail, "76448717"),
    CodProvItem(R.string.cod_prov_caso, "5591"),
    CodProvItem(R.string.cod_prov_ecocultiva, "11014"),
    CodProvItem(R.string.cod_prov_dusoleil, "11546"),
    CodProvItem(R.string.cod_prov_olimpia, "1000325189"),
    CodProvItem(R.string.cod_prov_beshos, "76166609 / 76166609-6"),
)

private val avatarColors = listOf(
    AccentBlue,
    // Texto/icono sobre claro: variante legible (fondo al 15% + tint pleno).
    AccentGreenText,
    AccentOrange,
    StoreColorFuchsia,
    StoreColorPurple,
    StoreColorRed,
)

private fun avatarColor(index: Int): Color = avatarColors[index % avatarColors.size]

/**
 * Filtro por nombre o código (AND de tokens, tolera acentos y mayúsculas).
 * `nameOf` resuelve el nombre visible (los tests pasan nombres falsos).
 * Función pura, testeable.
 */
fun filterCodProvItems(
    items: List<CodProvItem>,
    query: String,
    nameOf: (CodProvItem) -> String,
): List<CodProvItem> {
    val tokens = normalizeSearch(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return items
    return items.filter { item ->
        val hay = normalizeSearch("${nameOf(item)} ${item.code}")
        tokens.all { hay.contains(it) }
    }
}

/** Copia el código al portapapeles (el toast lo pone el llamador). */
fun copyCodProvToClipboard(context: android.content.Context, code: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("Código", code))
}

/**
 * Fila de marca compacta: avatar · nombre · píldora Cencosud centrada · chip
 * de código copiable (los códigos largos bajan a 2 líneas con ancho máximo).
 * Entrada con springs e stagger (cross-fade si hay movimiento reducido).
 */
@Composable
fun CodProvRow(
    item: CodProvItem,
    name: String,
    index: Int,
    copyCodeLabel: String,
    rowDescription: String,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotionEnabled.current
    val animAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
        label = "codProvAlpha",
    )
    val animOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 12.dp,
        animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
        label = "codProvOffset",
    )
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(minOf(index, 8) * 40L)
        visible = true
    }
    val density = LocalDensity.current
    val copyPress = rememberPressInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animAlpha
                translationY = animOffsetY.value * density.density
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            )
            .semantics(mergeDescendants = true) { contentDescription = rowDescription }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp * rs())
                .clip(CircleShape)
                .background(avatarColor(index).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Badge,
                contentDescription = null,
                tint = avatarColor(index),
                modifier = Modifier.size(20.dp * rs()),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CencosudPill()
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(max = 170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .pressScale(copyPress)
                .clickable(
                    interactionSource = copyPress,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = copyCodeLabel,
                    onClick = onCopyCode,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = item.code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Píldora "( Cencosud )" estilo M3 moderno: relleno verde flúor con brillo
 * superior, borde que la recorta sobre fondos claros y sombra sutil. Compacta
 * para convivir con nombre y chip en una sola línea. Texto negro sobre
 * [AccentGreen] (≈9.5:1, sobrado para AA).
 */
@Composable
fun CencosudPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(3.dp, CircleShape, spotColor = AccentGreenText.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AccentGreen.copy(alpha = 0.82f),
                        AccentGreen,
                    )
                ),
                CircleShape,
            )
            .border(1.dp, AccentGreenText.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.cod_prov_cencosud_pill),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            maxLines = 1,
        )
    }
}

@Composable
fun CodProvModal(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    IosModal(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(R.string.cod_prov_title),
        subtitle = stringResource(R.string.cod_prov_subtitle),
    ) {
        val dimens = LocalAppDimens.current
        val context = androidx.compose.ui.platform.LocalContext.current
        var copyToast by remember { mutableStateOf<String?>(null) }
        val copiedMessage = stringResource(R.string.cod_prov_copiado)
        // Toast-gota hermano en Box (nunca hijo de un Column con peso).
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                CodProvItems.forEachIndexed { index, item ->
                    val name = stringResource(item.nameRes)
                    CodProvRow(
                        item = item,
                        name = name,
                        index = index,
                        copyCodeLabel = stringResource(R.string.cod_prov_copiar_codigo, item.code),
                        rowDescription = stringResource(R.string.cod_prov_fila_cd, name, item.code),
                        onCopyCode = {
                            copyCodProvToClipboard(context, item.code)
                            copyToast = copiedMessage
                        },
                    )
                }
            }
            DropletToast(
                message = copyToast,
                onTimeout = { copyToast = null },
            )
        }
    }
}