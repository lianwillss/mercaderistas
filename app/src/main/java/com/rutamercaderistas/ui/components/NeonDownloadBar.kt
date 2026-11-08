package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.ui.theme.DownloadNeonBlue
import com.rutamercaderistas.ui.theme.DownloadNeonGlow
import com.rutamercaderistas.ui.theme.DownloadNeonPurple
import com.rutamercaderistas.ui.theme.DownloadNeonShine
import com.rutamercaderistas.ui.theme.DownloadNeonTrack
import com.rutamercaderistas.ui.theme.DownloadNeonTrackEdge
import com.rutamercaderistas.ui.theme.DownloadNeonViolet
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled

private const val GLOW_LAYERS = 12
private const val SHINE_DURATION_MS = 2_800
private const val PULSE_DURATION_MS = 1_700

// Espacio para que el aura no quede recortada por el Canvas.
private val HaloPadding = 8.dp
private val TrackInset = 6.dp

/**
 * Barra de descarga fluorescente: carril translúcido, relleno violeta→azul,
 * aura luminosa alrededor de la parte descargada, destello que la recorre y
 * pulso suave mientras la descarga está activa.
 *
 * [progress] se lee en fase de dibujo (no de composición) para que el
 * destello y el aura animen a 60 fps sin recomponer la tarjeta. El valor es
 * siempre el porcentaje real de la descarga; la única animación del relleno
 * es el acercamiento al valor nuevo.
 *
 * Sin movimiento reducido el aura y el destello quedan quietos: el brillo se
 * mantiene, la animación no.
 */
@Composable
fun NeonDownloadBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val reducedMotion = LocalReducedMotionEnabled.current
    val motion = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "neonDownloadBar")
    }
    val shine = motion?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHINE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neonDownloadShine",
    )
    val pulse = motion?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonDownloadPulse",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + HaloPadding * 2)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress().coerceIn(0f, 1f),
                    range = 0f..1f,
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawNeonBar(
                progress = progress().coerceIn(0f, 1f),
                shine = shine?.value ?: 0.55f,
                pulse = pulse?.value ?: 0.5f,
                barHeightPx = barHeight.toPx(),
            )
        }
    }
}

private fun DrawScope.drawNeonBar(
    progress: Float,
    shine: Float,
    pulse: Float,
    barHeightPx: Float,
) {
    val inset = TrackInset.toPx()
    val trackWidth = size.width - inset * 2
    val trackHeight = barHeightPx
    if (trackWidth <= 0f || trackHeight <= 0f) return

    val radius = trackHeight / 2f
    val trackTop = (size.height - trackHeight) / 2f
    val trackLeft = inset
    val breathing = 0.55f + 0.45f * pulse

    // Aura tenue del carril completo: la barra está viva incluso en 0 %.
    drawRoundRect(
        color = DownloadNeonGlow.copy(alpha = 0.05f * breathing),
        topLeft = Offset(trackLeft - trackHeight * 0.45f, trackTop - trackHeight * 0.45f),
        size = Size(trackWidth + trackHeight * 0.9f, trackHeight * 1.9f),
        cornerRadius = CornerRadius(radius + trackHeight * 0.45f),
    )
    // Carril: transparencia sutil con un filo violeta que respira.
    drawRoundRect(
        color = DownloadNeonTrack,
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(radius),
    )
    drawRoundRect(
        color = DownloadNeonTrackEdge.copy(alpha = DownloadNeonTrackEdge.alpha * breathing),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = 1.dp.toPx()),
    )

    if (progress <= 0f) return

    // Extremo redondeado legible incluso en porcentajes muy bajos.
    val fillWidth = (trackWidth * progress).coerceIn(trackHeight, trackWidth)
    val fillTopLeft = Offset(trackLeft, trackTop)
    val fillSize = Size(fillWidth, trackHeight)

    // Aura fluorescente por capas concéntricas: mismo resultado en API 26+
    // que un blur, sin depender de RenderEffect.
    val glowInflation = trackHeight
    for (layer in GLOW_LAYERS downTo 1) {
        val fraction = layer / GLOW_LAYERS.toFloat()
        val inflate = glowInflation * fraction
        val alpha = 0.082f * (1f - fraction * 0.6f) * breathing
        drawRoundRect(
            color = DownloadNeonGlow.copy(alpha = alpha),
            topLeft = Offset(fillTopLeft.x - inflate, fillTopLeft.y - inflate),
            size = Size(fillWidth + inflate * 2, trackHeight + inflate * 2),
            cornerRadius = CornerRadius(radius + inflate),
        )
    }

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(DownloadNeonViolet, DownloadNeonPurple, DownloadNeonBlue),
            startX = trackLeft,
            endX = trackLeft + trackWidth,
        ),
        topLeft = fillTopLeft,
        size = fillSize,
        cornerRadius = CornerRadius(radius),
    )

    // Destello: una banda de luz recorre solo la fracción ya descargada.
    val shinePath = Path().apply {
        addRoundRect(
            RoundRect(
                left = fillTopLeft.x,
                top = fillTopLeft.y,
                right = fillTopLeft.x + fillWidth,
                bottom = fillTopLeft.y + trackHeight,
                radiusX = radius,
                radiusY = radius,
            ),
        )
    }
    clipPath(shinePath) {
        val band = trackWidth * 0.42f
        val x = trackLeft - band + (trackWidth + band) * shine
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    DownloadNeonShine.copy(alpha = 0.62f),
                    Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            topLeft = Offset(trackLeft, trackTop),
            size = Size(trackWidth, trackHeight),
        )
    }

    // Filo superior y punta luminosa: el relleno se lee como un tubo de luz.
    val glossWidth = (fillWidth - radius * 1.2f).coerceAtLeast(0f)
    if (glossWidth > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(DownloadNeonShine.copy(alpha = 0.45f), Color.Transparent),
                endY = trackTop + trackHeight * 0.7f,
            ),
            topLeft = Offset(fillTopLeft.x + radius * 0.6f, trackTop + trackHeight * 0.16f),
            size = Size(glossWidth, trackHeight * 0.3f),
            cornerRadius = CornerRadius(trackHeight * 0.15f),
        )
    }
    val tipCenter = Offset(fillTopLeft.x + fillWidth - radius, trackTop + trackHeight / 2f)
    // Halo radial (sin borde duro) + núcleo blanco: el extremo activo brilla.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                DownloadNeonShine.copy(alpha = 0.3f + 0.15f * pulse),
                DownloadNeonGlow.copy(alpha = 0.22f * breathing),
                Color.Transparent,
            ),
            center = tipCenter,
            radius = trackHeight * 1.45f,
        ),
        radius = trackHeight * 1.45f,
        center = tipCenter,
    )
    drawCircle(
        color = DownloadNeonShine.copy(alpha = 0.5f + 0.3f * pulse),
        radius = trackHeight * 0.17f,
        center = tipCenter,
    )
}
