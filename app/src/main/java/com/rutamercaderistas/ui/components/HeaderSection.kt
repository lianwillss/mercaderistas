package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.lifecycle.compose.currentStateAsState
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity

import com.rutamercaderistas.data.preferences.prefsDataStore
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.HeaderDeepIndigo
import com.rutamercaderistas.ui.theme.HeaderIndigo
import com.rutamercaderistas.ui.theme.HeaderMidIndigo
import com.rutamercaderistas.ui.theme.HeaderViolet
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.OfflineRed
import com.rutamercaderistas.ui.theme.PromoGradientEnd
import com.rutamercaderistas.ui.theme.UrgencyOrange
import com.rutamercaderistas.ui.theme.Wave1Indigo
import com.rutamercaderistas.ui.theme.Wave2Violet
import com.rutamercaderistas.ui.theme.Wave3Lavender
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val TWO_PI = (2 * PI).toFloat()

// ── Métricas del header ──────────────────────────────────────────────
// El header colapsa por scroll: [HeaderTopRowHeight] siempre se ve (título +
// acciones de 48 dp) y las líneas de estado/fecha/banner quedan debajo,
// recortadas cuando el alto baja. El alto lo manda el gesto, no una animación:
// el estado sigue al dedo y el scroll se consume para que la lista no salte.
private val HeaderTopPadding = 6.dp
private val HeaderTopRowHeight = 56.dp
private val HeaderGap = 6.dp
private val HeaderBottomPadding = 10.dp
private val HeaderExpandedSlack = 2.dp

@Stable
class HeaderCollapseState internal constructor(
    private var expandedPx: Float,
    private var collapsedPx: Float,
) {
    var heightPx by mutableFloatStateOf(expandedPx)
        private set

    /** 0 = expandido; 1 = colapsado. */
    val fraction: Float
        get() = if (expandedPx <= collapsedPx) {
            1f
        } else {
            ((expandedPx - heightPx) / (expandedPx - collapsedPx)).coerceIn(0f, 1f)
        }

    fun reconfigurar(expanded: Float, collapsed: Float) {
        val nuevoColapsado = collapsed.coerceAtLeast(0f)
        val nuevoExpandido = expanded.coerceAtLeast(nuevoColapsado)
        val estabaExpandido = heightPx >= expandedPx - 0.5f
        val estabaColapsado = heightPx <= collapsedPx + 0.5f
        expandedPx = nuevoExpandido
        collapsedPx = nuevoColapsado
        heightPx = when {
            estabaExpandido -> expandedPx
            estabaColapsado -> collapsedPx
            else -> heightPx.coerceIn(collapsedPx, expandedPx)
        }
    }

    fun expandir() {
        heightPx = expandedPx
    }

    fun colapsar() {
        heightPx = collapsedPx
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        // Bajar en la lista (dedo hacia arriba: y < 0) encoge el header y ese
        // alto se descuenta del scroll: el contenido no salta.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return desplazar(available.y)
        }

        // Subir: el sobrante que la lista no pudo consumir (ya está arriba)
        // devuelve el alto al header.
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y <= 0f) return Offset.Zero
            return desplazar(available.y)
        }
    }

    private fun desplazar(delta: Float): Offset {
        val objetivo = (heightPx + delta).coerceIn(collapsedPx, expandedPx)
        val consumido = objetivo - heightPx
        if (consumido != 0f) heightPx = objetivo
        return Offset(0f, consumido)
    }
}

/**
 * Alto expandido = lo que miden las líneas de estado/fecha con la tipografía
 * actual (respeta zoom), más el banner de promos si existe. El colapsado es
 * la fila de título + acciones. Se recalcula si cambian tipografía o banner.
 */
@Composable
fun rememberHeaderCollapseState(hasPromos: Boolean): HeaderCollapseState {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val lineas = with(density) {
        typography.bodyMedium.lineHeight.toDp() + typography.labelLarge.lineHeight.toDp()
    }
    val promo = if (hasPromos) {
        val altoTexto = with(density) { typography.labelLarge.lineHeight.toDp() }
        6.dp + 12.dp + maxOf(22.dp, altoTexto)
    } else {
        0.dp
    }
    val base = HeaderTopPadding + HeaderTopRowHeight + HeaderGap
    val expanded = base + lineas + promo + HeaderBottomPadding + HeaderExpandedSlack
    val expandedPx = with(density) { expanded.toPx() }
    val collapsedPx = with(density) { base.toPx() }
    val state = remember { HeaderCollapseState(expandedPx, collapsedPx) }
    LaunchedEffect(expandedPx, collapsedPx) {
        state.reconfigurar(expandedPx, collapsedPx)
    }
    return state
}

@Composable
private fun rememberFechaHoy(): String? {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEEE d", Locale.forLanguageTag("es-CL"))
    }
    return remember { LocalDate.now().format(formatter).replaceFirstChar { it.uppercase() } }
}

@Composable
fun HeaderSection(
    isOnline: Boolean,
    lastSyncRelative: String,
    onRefresh: () -> Unit,
    onOpenManual: () -> Unit,
    estado: HeaderCollapseState,
    modifier: Modifier = Modifier,
    onShare: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    promosExpiringSoon: List<PromotionEntity> = emptyList(),
    onExpiringSoonClick: () -> Unit = {},
    onGlobalSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRefreshPositioned: (Offset) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val dimens = LocalAppDimens.current
    val themeBackground = MaterialTheme.colorScheme.background
    val syncCd = stringResource(R.string.header_sincronizar_cd)
    val searchCd = stringResource(R.string.header_buscar_cd)
    val menuCd = stringResource(R.string.header_menu_cd)
    val fechaHoy = rememberFechaHoy()

    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = with(density) { estado.heightPx.toDp() }
    val fraction = estado.fraction
    // Las dos versiones del título se cruzan mientras el header baja; el
    // estado/ fecha y el banner de promos se van antes de quedar recortados.
    val bigAlpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
    val compactAlpha = ((fraction - 0.35f) * 2.2f).coerceIn(0f, 1f)
    val extraAlpha = ((0.72f - fraction) / 0.72f).coerceIn(0f, 1f)
    val waveAlpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
    val showExtras = fraction < 0.72f
    val showBigTitle = fraction <= 0.5f

    // La onda solo corre con la pantalla activa. Al abrir Promos o Stock el
    // MainRoute queda en STARTED: la transición infinita se dispone y no
    // sigue gastando frames fuera de pantalla. También se apaga al colapsar
    // el header y con movimiento reducido.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val headerOnScreen = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val reducedMotion = LocalReducedMotionEnabled.current
    val animateWaves = !reducedMotion && headerOnScreen && fraction < 0.98f
    val phase = if (animateWaves) {
        val transition = rememberInfiniteTransition(label = "headerWave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "headerWavePhase",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight + statusTop)
            .clipToBounds(),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to HeaderDeepIndigo,
                    0.42f to HeaderMidIndigo,
                    0.72f to HeaderIndigo,
                    0.93f to HeaderViolet,
                    1.0f to themeBackground,
                ),
            )

            if (waveAlpha > 0.01f) {
                val path1 = Path().apply {
                    moveTo(0f, h * 0.72f)
                    for (x in 0..w.toInt() step 4) {
                        val y = h * 0.72f + sin(x * 0.008f + phase) * 8f + sin(x * 0.015f + phase * 1.3f) * 4f
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path1, Wave1Indigo.copy(alpha = 0.25f * waveAlpha))

                val path2 = Path().apply {
                    moveTo(0f, h * 0.82f)
                    for (x in 0..w.toInt() step 4) {
                        val y = h * 0.82f + sin(x * 0.012f - phase * 0.7f) * 6f + sin(x * 0.02f + phase) * 3f
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path2, Wave2Violet.copy(alpha = 0.18f * waveAlpha))

                val path3 = Path().apply {
                    moveTo(0f, h * 0.62f)
                    for (x in 0..w.toInt() step 4) {
                        val y = h * 0.62f + sin(x * 0.005f + phase * 0.5f) * 4f
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(w, h * 0.9f)
                    lineTo(0f, h * 0.9f)
                    close()
                }
                drawPath(path3, Wave3Lavender.copy(alpha = 0.12f * waveAlpha))

                val dotPositions = (0..8).map { i ->
                    val x = w * (i + 1) / 10f
                    val y = h * 0.35f + sin(x * 0.025f + phase * 0.4f + i * 0.5f) * 10f
                    Offset(x, y)
                }
                for (i in 0 until dotPositions.lastIndex) {
                    drawLine(
                        Color.White.copy(alpha = 0.05f * waveAlpha),
                        dotPositions[i],
                        dotPositions[i + 1],
                        strokeWidth = 1.2f,
                    )
                }
                dotPositions.forEach { pos ->
                    drawCircle(Color.White.copy(alpha = 0.12f * waveAlpha), radius = 2f, center = pos)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = dimens.spacingSection,
                    end = dimens.spacingSection,
                    top = HeaderTopPadding,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderTopRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Título expandido (34 sp): se apaga mientras baja.
                    Row(
                        modifier = Modifier
                            .alpha(bigAlpha)
                            .then(
                                if (showBigTitle) Modifier
                                else Modifier.clearAndSetSemantics { },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.header_rutero),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() },
                        )
                        Spacer(Modifier.width(8.dp))
                        OnlineDot(isOnline)
                    }
                    // Título compacto (17 sp): aparece cuando la fila de 56 dp
                    // ya no alcanza para el de 34 sp.
                    Row(
                        modifier = Modifier
                            .alpha(compactAlpha)
                            .then(
                                if (showBigTitle) Modifier.clearAndSetSemantics { }
                                else Modifier,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.header_rutero),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() },
                        )
                        Spacer(Modifier.width(8.dp))
                        OnlineDot(isOnline)
                    }
                }

                Spacer(Modifier.width(8.dp))
                HeaderAction(
                    icon = Icons.Outlined.Refresh,
                    description = syncCd,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        onRefreshPositioned(coordinates.boundsInWindow().center)
                    },
                )
                Spacer(Modifier.width(8.dp))
                HeaderAction(
                    icon = Icons.Filled.Search,
                    description = searchCd,
                    onClick = onGlobalSearch,
                )
                Spacer(Modifier.width(8.dp))
                HeaderAction(
                    icon = Icons.Outlined.Description,
                    description = stringResource(R.string.header_manual_usuario),
                    onClick = {
                        expanded = false
                        onOpenManual()
                    },
                )
                Spacer(Modifier.width(8.dp))
                HeaderAction(
                    icon = Icons.Outlined.MoreVert,
                    description = menuCd,
                    onClick = { expanded = true },
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.header_compartir_ruta)) },
                        onClick = {
                            expanded = false
                            onShare()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.header_compartir_ruta),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.header_buscar_actualizacion)) },
                        onClick = {
                            expanded = false
                            onCheckUpdate()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.SystemUpdate,
                                contentDescription = stringResource(R.string.header_buscar_actualizacion),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.header_ajustes)) },
                        onClick = {
                            expanded = false
                            onOpenSettings()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.header_ajustes),
                            )
                        },
                    )

                }
            }

            Spacer(Modifier.height(HeaderGap))

            if (showExtras) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(extraAlpha),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOnline) {
                                stringResource(R.string.header_en_linea, lastSyncRelative)
                            } else {
                                stringResource(R.string.header_sin_conexion)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (fechaHoy != null) {
                        Text(
                            text = fechaHoy,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                    if (promosExpiringSoon.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        PromosPorVencerBanner(
                            count = promosExpiringSoon.size,
                            onClick = onExpiringSoonClick,
                            active = headerOnScreen,
                        )
                    }
                }
            }

            Spacer(Modifier.height(HeaderBottomPadding))
        }
    }
}

@Composable
private fun OnlineDot(isOnline: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isOnline) AccentGreen else OfflineRed),
    )
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val touch = LocalAppDimens.current.touchMin
    val interaction = rememberPressInteractionSource()
    Box(
        modifier = modifier
            .size(touch)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Banner naranja de promos por vencer. Vive dentro de las líneas que se
 * ocultan al colapsar, así el pulso infinito se dispone con el header
 * compacto.
 */
@Composable
private fun PromosPorVencerBanner(
    count: Int,
    onClick: () -> Unit,
    active: Boolean = true,
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    // Igual que las olas: sin pantalla activa no se anima el pulso.
    val porVencerTransition = if (reducedMotion || !active) {
        null
    } else {
        rememberInfiniteTransition(label = "porVencer")
    }
    val bgPulse: Float = porVencerTransition?.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bgPulse",
    )?.value ?: 0.76f
    val iconScale: Float = porVencerTransition?.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "iconScale",
    )?.value ?: 1f
    var countPopTarget by remember { mutableStateOf(1f) }
    val countScale by animateFloatAsState(
        targetValue = countPopTarget,
        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "countPop",
    )
    LaunchedEffect(count) {
        countPopTarget = 1.22f
        kotlinx.coroutines.delay(180)
        countPopTarget = 1f
    }
    Box(
        modifier = Modifier
            // El pulso vive en la sombra, no en el alfa del contenido: animar
            // la opacidad bajaba el contraste del texto y del botón.
            .shadow(6.dp + 6.dp * bgPulse, shape = MaterialTheme.shapes.extraSmall)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Brush.horizontalGradient(listOf(UrgencyOrange, PromoGradientEnd)))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = PromoGradientEnd,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.header_por_vencer, count),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = countScale
                        scaleY = countScale
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderSectionPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            HeaderSection(
                isOnline = true,
                lastSyncRelative = "hace 2 min",
                onRefresh = {},
                onOpenManual = {},
                estado = rememberHeaderCollapseState(hasPromos = false),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderSectionPreviewOffline() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            HeaderSection(
                isOnline = false,
                lastSyncRelative = "hace 2 días",
                onRefresh = {},
                onOpenManual = {},
                estado = rememberHeaderCollapseState(hasPromos = false),
            )
        }
    }
}