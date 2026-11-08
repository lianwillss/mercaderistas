package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import com.rutamercaderistas.viewmodel.UpdateCardUiState

/** Modal de la actualización: reutiliza la tarjeta, sin duplicar estilos. */
@Composable
fun UpdateModal(
    visible: Boolean,
    state: UpdateCardUiState?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible && state != null
    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val reducedMotion = LocalReducedMotionEnabled.current
    val currentState = state ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (transitionState.targetState) 1f else 0f,
            animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
            label = "updateModalScrim",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = colors.scrim.alpha * scrimAlpha))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = if (reducedMotion) {
                    fadeIn(animationSpec = tween(150))
                } else {
                    fadeIn(animationSpec = tween(200)) +
                        scaleIn(initialScale = 0.94f, animationSpec = MotionSprings.default())
                },
                exit = if (reducedMotion) {
                    fadeOut(animationSpec = tween(150))
                } else {
                    fadeOut(animationSpec = tween(160)) +
                        scaleOut(targetScale = 0.96f, animationSpec = MotionSprings.default())
                },
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = if (dimens.isCompact) dimens.spacingLg else 24.dp)
                        .widthIn(max = 440.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    UpdateCard(
                        state = currentState,
                        expanded = true,
                        onClose = onDismiss,
                        onUpdate = onUpdate,
                        onLater = onLater,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        onInstall = onInstall,
                    )
                }
            }
        }
    }
}
