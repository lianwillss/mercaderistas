package com.rutamercaderistas.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.AppWindowWidth
import com.rutamercaderistas.ui.theme.BrandIndigoText
import com.rutamercaderistas.ui.theme.BrandViolet
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.GlassBorderHighlight
import com.rutamercaderistas.ui.theme.GlassBorderShade
import com.rutamercaderistas.ui.theme.GlassPillBorder
import com.rutamercaderistas.ui.theme.GlassPillBottom
import com.rutamercaderistas.ui.theme.GlassPillTop
import com.rutamercaderistas.ui.theme.GlassShadowAmbient
import com.rutamercaderistas.ui.theme.GlassShadowSpot
import com.rutamercaderistas.ui.theme.GlassSheen
import com.rutamercaderistas.ui.theme.GlassShapes
import com.rutamercaderistas.ui.theme.GlassTintBottom
import com.rutamercaderistas.ui.theme.GlassTintTop
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import com.rutamercaderistas.ui.theme.appWindowWidth
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import kotlin.math.roundToInt

// ── Métricas del vidrio ──────────────────────────────────────────────
// La barra es una cápsula flotante: no toca el borde inferior ni el riel
// del sistema, así que el contenido se ve por debajo y el vidrio tiene algo
// que mostrar. [BottomBarScrollInset] es justo lo que la barra ocupa sobre
// el contenido (márgenes incluidos) y se suma al padding de scroll.
// Se mantiene aunque el scroll aparte la barra: quitarlo a mitad del gesto
// cambiaría el alto útil de la lista y el contenido saltaría. El precio es
// un pequeño espacio al final de la lista cuando la barra está escondida.
val BottomBarScrollInset: Dp = 82.dp

private val BarHeight = 64.dp
private val BarMarginH = 12.dp
private val BarMarginTop = 8.dp
private val BarMarginBottom = 10.dp
private val BarInnerPadding = 6.dp
private val RailWidth = 72.dp
private val RailMargin = 8.dp
private val RailSlotWidth: Dp = RailWidth + RailMargin * 2

/**
 * Scaffold de navegación con vidrio líquido.
 *
 * Compacto: barra inferior flotante y translúcida superpuesta al contenido
 * (el contenido pasa por debajo: eso es lo que hace visible el vidrio).
 * Medium/Expanded: riel de vidrio a la izquierda, contenido al lado.
 *
 * El contenido NO se condiciona a la visibilidad del vidrio: la navegación
 * se oculta en la ruta EAN, pero el NavHost sigue siendo el mismo.
 *
 * En compacto la barra inferior además se aparta al hacer scroll hacia
 * adelante y vuelve al retroceder (o al cambiar [navigationResetKey]): así
 * deja ver las tarjetas que quedaban debajo sin tocar el padding de la lista.
 */
@Composable
fun LiquidGlassNavigationScaffold(
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    navigationVisible: Boolean,
    marcasConPromo: Int,
    hasPlanillaChanges: Boolean,
    modifier: Modifier = Modifier,
    navigationResetKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val items = glassNavItems(marcasConPromo, hasPlanillaChanges)
    val reducedMotion = LocalReducedMotionEnabled.current
    val autoHide = rememberBottomBarAutoHideState()
    // Capa del contenido para el vidrio: la barra no reproduce el fondo
    // inventado, muestrea lo que de verdad pasa por debajo y lo difumina.
    val backdropLayer = rememberGraphicsLayer()
    var backdropCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useRail = appWindowWidth(maxWidth) != AppWindowWidth.Compact
        val railVisible = navigationVisible && useRail
        // "Disponible" = hay barra en esta ruta y ancho; "visible" además
        // depende de si el scroll la apartó. El inset sigue a disponible, no
        // a visible: si cambiara con cada gesto, la lista saltaría a mitad
        // del scroll (ver AGENTS: no cambiar el viewport durante el gesto).
        val barAvailable = navigationVisible && !useRail
        val barShown = barAvailable && !autoHide.hidden
        // Estado mutable (en vez de visible =) para saber cuándo termina la
        // salida: mientras el vidrio se va hay que seguir muestreando el fondo
        // o el blur desaparece antes que la barra.
        val barVisibility = remember { MutableTransitionState(false) }
        barVisibility.targetState = barShown

        // Al cambiar de pantalla, volver del riel o entrar a compacto, la
        // barra vuelve: ninguna ruta puede quedar “heredando” el scroll de otra.
        LaunchedEffect(navigationResetKey, navigationVisible, useRail) {
            if (navigationVisible && !useRail) autoHide.show()
        }

        val dimens = LocalAppDimens.current
        // El inset se anima con la misma spec que el alto del vidrio para que
        // la lista no salte al entrar o salir de una ruta sin barra (EAN).
        val barInset by animateDpAsState(
            targetValue = if (barAvailable) BottomBarScrollInset else 0.dp,
            animationSpec = MotionSprings.default(),
            label = "glassBarInset",
        )
        val railInset by animateDpAsState(
            targetValue = if (railVisible) RailSlotWidth else 0.dp,
            animationSpec = MotionSprings.default(),
            label = "glassRailInset",
        )
        val contentDimens = remember(dimens, barInset) {
            dimens.copy(scrollBottomPadding = dimens.scrollBottomPadding + barInset)
        }
        // En riel, en EAN o con la barra ya escondida y quieta no hay vidrio
        // que muestrear: no se graba la capa (evita un offscreen que nadie usa).
        val captureBackdrop = barAvailable &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (barVisibility.currentState || !barVisibility.isIdle)

        CompositionLocalProvider(LocalAppDimens provides contentDimens) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Start),
                    )
                    .padding(start = railInset)
                    // Se engancha al contenido y no a cada lista: el scaffold
                    // observa el scroll ya consumido, sin robar gestos ni
                    // interferir con el header colapsable ni con pull-to-refresh.
                    .nestedScroll(autoHide.nestedScrollConnection)
                    .onGloballyPositioned { backdropCoordinates = it }
                    .drawWithContent {
                        if (captureBackdrop) {
                            backdropLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(backdropLayer)
                        } else {
                            drawContent()
                        }
                    },
            ) {
                content()
            }
        }

        AnimatedVisibility(
            visible = railVisible,
            enter = railEnter(reducedMotion),
            exit = railExit(reducedMotion),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            LiquidGlassRail(
                items = items,
                selectedKey = selectedKey,
                onNavigate = onNavigate,
            )
        }
        AnimatedVisibility(
            visibleState = barVisibility,
            enter = bottomBarEnter(reducedMotion),
            exit = bottomBarExit(reducedMotion),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LiquidGlassBottomBar(
                items = items,
                selectedKey = selectedKey,
                onNavigate = onNavigate,
                backdropLayer = backdropLayer,
                backdropCoordinates = backdropCoordinates,
            )
        }
    }
}

// ── Barra inferior ───────────────────────────────────────────────────

@Composable
private fun LiquidGlassBottomBar(
    items: List<GlassNavItem>,
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
    backdropLayer: GraphicsLayer,
    backdropCoordinates: LayoutCoordinates?,
) {
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var glassCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val blurRadius = with(LocalDensity.current) { 22.dp.toPx() }
    val blurLayer = rememberGraphicsLayer()
    // El efecto se reutiliza: crear un BlurEffect por frame asignaba basura
    // en cada dibujo del vidrio.
    val blurEffect = remember(blurRadius, blurSupported) {
        if (blurSupported) {
            BlurEffect(
                radiusX = blurRadius * BLUR_DOWNSCALE,
                radiusY = blurRadius * BLUR_DOWNSCALE,
                edgeTreatment = TileMode.Clamp,
            )
        } else {
            null
        }
    }
    val backdrop: (DrawScope.() -> Unit)? = if (blurSupported) {
        val drawer = DrawScopeBackdrop(
            layer = backdropLayer,
            blurLayer = blurLayer,
            contentCoordinates = backdropCoordinates,
            glassCoordinates = { glassCoordinates },
            blurEffect = blurEffect,
        )
        val drawBlur: DrawScope.() -> Unit = { drawer(this) }
        drawBlur
    } else {
        null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            )
            // navigationBars (no safeDrawing) para que el teclado no empuje
            // la barra: queda detrás del IME, como cualquier barra inferior.
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            )
            .padding(
                start = BarMarginH,
                end = BarMarginH,
                top = BarMarginTop,
                bottom = BarMarginBottom,
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .onGloballyPositioned { glassCoordinates = it }
                .liquidGlass(GlassShapes.bar, Elevation.glassBar, backdrop),
        ) {
            GlassSpecularSheen(
                shape = GlassShapes.bar,
                modifier = Modifier.matchParentSize(),
            )
            val slot = (maxWidth - BarInnerPadding * 2) / items.size
            val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
            // El indicador no trae momentum de gesto: spring críticamente
            // amortiguado, sin rebote (MotionSprings.bouncy() es solo para
            // arrastres con velocidad).
            val pillOffset by animateDpAsState(
                targetValue = slot * selectedIndex,
                animationSpec = MotionSprings.default(),
                label = "glassPillOffset",
            )
            Box(
                modifier = Modifier
                    .offset(x = BarInnerPadding + pillOffset + 3.dp, y = 8.dp)
                    .size(
                        width = (slot - 6.dp).coerceAtLeast(40.dp),
                        height = BarHeight - 16.dp,
                    )
                    .shadow(
                        elevation = Elevation.glassPill,
                        shape = GlassShapes.pill,
                        clip = false,
                        ambientColor = GlassShadowAmbient,
                        spotColor = GlassShadowSpot,
                    )
                    .clip(GlassShapes.pill)
                    .background(Brush.verticalGradient(listOf(GlassPillTop, GlassPillBottom)))
                    .border(1.dp, GlassPillBorder, GlassShapes.pill),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = BarInnerPadding)
                    // TalkBack agrupa los destinos como pestañas ("1 de 6").
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    GlassNavItemButton(
                        item = item,
                        selected = item.key == selectedKey,
                        onClick = { onNavigate(item.key) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ── Riel (Medium / Expanded) ─────────────────────────────────────────

@Composable
private fun LiquidGlassRail(
    items: List<GlassNavItem>,
    selectedKey: BottomBarKey,
    onNavigate: (BottomBarKey) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(RailSlotWidth)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                ),
            )
            .padding(RailMargin),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(RailWidth)
                .liquidGlass(GlassShapes.rail, Elevation.glassRail),
        ) {
            GlassSpecularSheen(shape = GlassShapes.rail, modifier = Modifier.matchParentSize())
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Con seis destinos, en un teléfono acostado el riel no entra:
                // se vuelve scrolleable en vez de recortar targets.
                val needsScroll = maxHeight < (6 * 58).dp + 24.dp
                Column(
                    modifier = (if (needsScroll) {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxSize()
                    })
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        // TalkBack agrupa los destinos del riel como pestañas.
                        .selectableGroup(),
                    verticalArrangement = if (needsScroll) {
                        Arrangement.spacedBy(4.dp)
                    } else {
                        Arrangement.SpaceEvenly
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items.forEach { item ->
                        val isSelected = item.key == selectedKey
                        GlassNavItemButton(
                            item = item,
                            selected = isSelected,
                            onClick = { onNavigate(item.key) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = LocalAppDimens.current.touchMin)
                                .clip(GlassShapes.pill)
                                .background(
                                    if (isSelected) GlassPillTop else Color.Transparent,
                                ),
                        )
                    }
                }
            }
        }
    }
}

// ── Ítem ─────────────────────────────────────────────────────────────

@Composable
private fun GlassNavItemButton(
    item: GlassNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberPressInteractionSource()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = MotionSprings.default(),
        label = "glassIconScale",
    )
    val contentDesc = buildString {
        append(item.label)
        if (item.badge != null) append(": ").append(item.badge)
        if (item.dot) append(", novedad")
    }
    Column(
        modifier = modifier
            .pressScale(interaction)
            .clip(GlassShapes.pill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = {
                    // Tick corto solo si cambia la pestaña. performHapticFeedback
                    // respeta el ajuste de feedback táctil del sistema.
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    onClick()
                },
            )
            .semantics {
                contentDescription = contentDesc
                this.selected = selected
                if (selected) stateDescription = "Seleccionada"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // El área del icono es más ancha que el icono para que el badge
        // (16 dp) salga del trazo sin taparlo y siga dentro del vidrio.
        Box(
            modifier = Modifier.size(width = 32.dp, height = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                GlassItemIcon(item = item, selected = selected)
            }
            if (item.dot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = 1.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
            if (item.badge != null) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-3).dp),
                ) {
                    Text(item.badge, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) BrandIndigoText else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GlassItemIcon(item: GlassNavItem, selected: Boolean) {
    val tint = if (selected) BrandIndigoText else MaterialTheme.colorScheme.onSurfaceVariant
    val vector = if (selected) item.selectedIcon ?: item.icon else item.icon
    when {
        vector != null -> Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        item.iconRes != 0 -> Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── Fondo difuminado (API 31+) ───────────────────────────────────────

/** El contenido se graba a 1/3 de resolución: el blur cuesta 1/9. */
private const val BLUR_DOWNSCALE = 0.34f

/**
 * Dibuja el contenido capturado en [layer] por debajo de la barra, reducido
 * y difuminado. Sin difuminado, un velo translúcido deja ver el texto nítido
 * de la lista y se lee como una superposición rota, no como vidrio.
 *
 * En API < 31 el RenderEffect no existe y [backdrop] es null: queda el
 * vidrio translúcido con el velo al 84–92 %.
 */
private class DrawScopeBackdrop(
    private val layer: GraphicsLayer,
    private val blurLayer: GraphicsLayer,
    private val contentCoordinates: LayoutCoordinates?,
    private val glassCoordinates: () -> LayoutCoordinates?,
    private val blurEffect: BlurEffect?,
) {
    private val downscale = BLUR_DOWNSCALE

    operator fun invoke(scope: DrawScope) = with(scope) {
        val content = contentCoordinates ?: return@with
        val glass = glassCoordinates() ?: return@with
        val offset = try {
            content.localPositionOf(glass, Offset.Zero)
        } catch (_: Exception) {
            return@with
        }
        val width = (content.size.width * downscale).roundToInt().coerceAtLeast(1)
        val height = (content.size.height * downscale).roundToInt().coerceAtLeast(1)
        blurLayer.record(size = IntSize(width, height)) {
            scale(downscale, downscale, Offset.Zero) {
                translate(-offset.x, -offset.y) {
                    drawLayer(layer)
                }
            }
        }
        blurLayer.pivotOffset = Offset.Zero
        blurLayer.scaleX = 1f / downscale
        blurLayer.scaleY = 1f / downscale
        blurLayer.renderEffect = blurEffect
        drawLayer(blurLayer)
    }
}

// ── Superficie de vidrio ─────────────────────────────────────────────

/**
 * El vidrio no es solo "blanco con alfa": lleva un borde especular arriba
 * (luz), un borde frío apagado abajo (volumen), sombra índigo (reflejo del
 * contenido) y un velo frío que deja pasar lo que hay debajo.
 */
internal fun Modifier.liquidGlass(
    shape: Shape,
    elevation: Dp,
    backdrop: (DrawScope.() -> Unit)? = null,
    borderBrush: Brush = Brush.verticalGradient(
        listOf(
            GlassBorderHighlight,
            GlassBorderHighlight.copy(alpha = 0.35f),
            GlassBorderShade,
        ),
    ),
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = GlassShadowAmbient,
        spotColor = GlassShadowSpot,
    )
    .clip(shape)
    // La capa difuminada va debajo del relleno translúcido: el velo la
    // apaga sin borrar la mancha de color del contenido.
    .then(if (backdrop != null) Modifier.drawBehind(backdrop) else Modifier)
    .background(Brush.verticalGradient(listOf(GlassTintTop, GlassTintBottom)))
    .border(
        width = 1.dp,
        brush = borderBrush,
        shape = shape,
    )

/**
 * Reflejo del vidrio: un brillo blanco que cae desde el borde superior y un
 * halo violeta muy tenue en la esquina de la luz. Es lo que separa "blanco
 * translúcido" de "vidrio": el material se lee por cómo refleja, no por su
 * color de relleno.
 */
@Composable
internal fun GlassSpecularSheen(shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(BrandViolet.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.width * 0.55f,
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to GlassSheen,
                        0.55f to Color.Transparent,
                    ),
                )
                // Borde interno inferior: le da espesor al vidrio sin dibujar
                // una segunda línea visible.
                drawRect(
                    Brush.verticalGradient(
                        0.72f to Color.Transparent,
                        1f to GlassBorderShade.copy(alpha = 0.10f),
                    ),
                )
            },
    )
}

// ── Destinos ─────────────────────────────────────────────────────────

@Immutable
private data class GlassNavItem(
    val key: BottomBarKey,
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null,
    @DrawableRes val iconRes: Int = 0,
    val badge: String? = null,
    val dot: Boolean = false,
)

@Composable
private fun glassNavItems(
    marcasConPromo: Int,
    hasPlanillaChanges: Boolean,
): List<GlassNavItem> = listOf(
    GlassNavItem(
        key = BottomBarKey.MAIN,
        label = stringResource(R.string.rutero_title),
        icon = Icons.Outlined.Storefront,
        selectedIcon = Icons.Filled.Storefront,
        dot = hasPlanillaChanges,
    ),
    GlassNavItem(
        key = BottomBarKey.MARCAS,
        label = stringResource(R.string.nav_promos_label),
        icon = Icons.Outlined.ShoppingBag,
        selectedIcon = Icons.Filled.ShoppingBag,
        badge = if (marcasConPromo > 0) marcasConPromo.toString() else null,
    ),
    GlassNavItem(
        key = BottomBarKey.LOCALES,
        label = stringResource(R.string.stats_locales_label),
        icon = Icons.Outlined.Visibility,
        selectedIcon = Icons.Filled.Visibility,
    ),
    GlassNavItem(
        key = BottomBarKey.STOCK,
        label = stringResource(R.string.stock_title),
        icon = Icons.Outlined.Inventory2,
        selectedIcon = Icons.Filled.Inventory2,
    ),
    GlassNavItem(
        key = BottomBarKey.CODPROV,
        label = stringResource(R.string.stats_cod_prov_label),
        icon = Icons.Outlined.Badge,
    ),
    GlassNavItem(
        key = BottomBarKey.EAN,
        label = stringResource(R.string.stats_cod_ean_label),
        iconRes = R.drawable.ic_barcode,
    ),
)

// ── Transiciones ─────────────────────────────────────────────────────

private fun bottomBarEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) {
        fadeIn(animationSpec = tween(durationMillis = 120))
    } else {
        // Entra desde abajo del todo: es la misma salida, al revés.
        fadeIn(animationSpec = tween(durationMillis = 160)) +
            slideInVertically(
                animationSpec = MotionSprings.default<IntOffset>(),
                initialOffsetY = { it },
            )
    }

private fun bottomBarExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) {
        fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        // Sale completa: si solo se corriera un tercio seguiría tapando la
        // última tarjeta, que es justo lo que el scroll quiere despejar.
        fadeOut(animationSpec = tween(durationMillis = 160)) +
            slideOutVertically(
                animationSpec = MotionSprings.default<IntOffset>(),
                targetOffsetY = { it },
            )
    }

private fun railEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) {
        fadeIn(animationSpec = tween(durationMillis = 120))
    } else {
        fadeIn(animationSpec = tween(durationMillis = 200)) +
            slideInHorizontally(
                animationSpec = MotionSprings.default<IntOffset>(),
                initialOffsetX = { -it / 2 },
            )
    }

private fun railExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) {
        fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        fadeOut(animationSpec = tween(durationMillis = 200)) +
            slideOutHorizontally(
                animationSpec = MotionSprings.default<IntOffset>(),
                targetOffsetX = { -it / 2 },
            )
    }