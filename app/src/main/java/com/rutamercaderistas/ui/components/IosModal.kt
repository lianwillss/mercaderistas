package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rutamercaderistas.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings

@Composable
fun IosModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.7f),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Estado de transición persistente: el Dialog se mantiene montado
    // durante la salida para animarla (entrar y salir por el mismo camino).
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    if (transitionState.currentState || transitionState.targetState || !transitionState.isIdle) {
        val dimens = LocalAppDimens.current
        val reducedMotion = LocalReducedMotionEnabled.current
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // El scrim se atenúa junto con la superficie (misma curva).
            val scrimAlpha by animateFloatAsState(
                targetValue = if (transitionState.targetState) 1f else 0f,
                animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
                label = "iosModalScrim",
            )
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * scrimAlpha))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = if (reducedMotion) {
                        fadeIn(animationSpec = tween(150))
                    } else {
                        fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.92f, animationSpec = MotionSprings.default())
                    },
                    exit = if (reducedMotion) {
                        fadeOut(animationSpec = tween(150))
                    } else {
                        fadeOut(animationSpec = tween(180)) +
                            scaleOut(targetScale = 0.95f, animationSpec = MotionSprings.default())
                    },
                ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = Elevation.modal),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    ) {
                        if (title != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                    if (subtitle != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                val cerrarCd = stringResource(R.string.cerrar_cd)
                                Box(
                                    modifier = Modifier
                                        .size(dimens.touchMin)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = cerrarCd,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        content()

                        if (confirmText != null || dismissText != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (dismissText != null) {
                                    val onClickDismiss = onDismissAction ?: onDismiss
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(ComponentShapes.button)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable(onClick = onClickDismiss)
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dismissText,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (confirmText != null) {
                                    Box(
                                        modifier = Modifier
                                            .weight(if (dismissText != null) 1f else 0f)
                                            .clip(ComponentShapes.button)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable(onClick = onConfirm ?: onDismiss)
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = confirmText,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
