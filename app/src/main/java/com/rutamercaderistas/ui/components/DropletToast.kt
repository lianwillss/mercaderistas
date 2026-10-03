package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import kotlinx.coroutines.delay

/**
 * Confirmación breve estilo "gota": emerge comprimida y pequeña, se expande
 * sutilmente y se asienta; al salir, fade rápido. Solo transform + alpha
 * (GPU, 60/120 Hz). Sin touches: no intercepta gestos. Reemplaza al Toast
 * del sistema donde se quiere esta presentación (misma funcionalidad:
 * mensaje ~2s y se va solo).
 */
@Composable
fun DropletToast(
    message: String?,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 2000L,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(durationMs)
            onTimeout()
        }
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val reducedMotion = LocalReducedMotionEnabled.current
        AnimatedVisibility(
            visible = message != null,
            enter = if (reducedMotion) {
                fadeIn(animationSpec = tween(150))
            } else {
                // Gota: fade corto + nace al 85% y 1/6 de alto abajo,
                // el spring críticamente amortiguado la asienta (~400ms).
                fadeIn(animationSpec = tween(120)) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = MotionSprings.default(),
                    ) +
                    slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = MotionSprings.default(),
                    )
            },
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 96.dp)
                    .shadow(Elevation.card, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
