package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import com.rutamercaderistas.viewmodel.UpdateCardUiState
import com.rutamercaderistas.viewmodel.UpdatePhase
import java.text.NumberFormat
import java.util.Locale

private val UpdateCardShape = RoundedCornerShape(999.dp)

/**
 * Tarjeta única de actualización. La misma superficie sirve de banner
 * compacto y de contenido del modal, así el progreso no cambia de lenguaje
 * visual al abrirse.
 */
@Composable
fun UpdateCard(
    state: UpdateCardUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val reducedMotion = LocalReducedMotionEnabled.current
    val shape = if (expanded) MaterialTheme.shapes.large else MaterialTheme.shapes.medium

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reducedMotion) tween(120) else MotionSprings.default(),
            ),
        shape = shape,
        color = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = if (expanded) Elevation.dialog else Elevation.level0,
        border = BorderStroke(
            width = 1.dp,
            color = colors.outlineVariant.copy(alpha = if (expanded) 0.7f else 0.5f),
        ),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.07f), Color.Transparent),
                ),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (expanded) dimens.spacingXl else dimens.spacingMd)
                    .padding(end = if (onClose != null) 56.dp else 0.dp),
            ) {
                AnimatedContent(
                    targetState = state.phase,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                        } else {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "updateCardPhase",
                ) { phase ->
                    when (phase) {
                        UpdatePhase.AVAILABLE -> AvailableContent(
                            state = state,
                            onUpdate = onUpdate,
                            onLater = onLater,
                            expanded = expanded,
                        )
                        UpdatePhase.DOWNLOADING -> DownloadingContent(
                            state = state,
                            onCancel = onCancel,
                        )
                        UpdatePhase.READY -> ReadyContent(
                            state = state,
                            onInstall = onInstall,
                        )
                        UpdatePhase.FAILED -> FailedContent(
                            state = state,
                            onRetry = onRetry,
                            onLater = onLater,
                        )
                    }
                }
            }
            if (onClose != null) {
                val closeCd = stringResource(R.string.cerrar_cd)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spacingSm)
                        .size(dimens.touchMin)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = closeCd,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(dimens.iconSm),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailableContent(
    state: UpdateCardUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    expanded: Boolean,
) {
    UpdateHeader(
        icon = Icons.Rounded.SystemUpdate,
        tint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_card_title),
        subtitle = if (expanded) {
            stringResource(R.string.update_card_subtitle_expanded, state.versionName)
        } else {
            stringResource(R.string.update_card_subtitle, state.versionName)
        },
        version = state.versionName,
    )
    Spacer(Modifier.height(if (expanded) 18.dp else 10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLater) {
            Text(
                text = stringResource(R.string.mas_tarde),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        PrimaryAction(
            text = stringResource(R.string.actualizar_btn),
            onClick = onUpdate,
        )
    }
}

@Composable
private fun DownloadingContent(
    state: UpdateCardUiState,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotionEnabled.current
    val downloading = state as UpdateCardUiState.Downloading
    val animatedPercent by animateIntAsState(
        targetValue = downloading.percent,
        animationSpec = if (reducedMotion) snap() else tween(260),
        label = "updatePercent",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = downloading.percent / 100f,
        animationSpec = if (reducedMotion) snap() else tween(260),
        label = "updateProgress",
    )

    UpdateHeader(
        icon = Icons.Rounded.SystemUpdate,
        tint = colors.primary,
        title = stringResource(R.string.update_downloading_title, downloading.versionName),
        subtitle = stringResource(R.string.update_downloading_subtitle),
        version = null,
    )
    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(UpdateCardShape),
        color = colors.primary,
        trackColor = colors.surfaceContainerHighest,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.update_download_percent, animatedPercent),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
        Text(
            text = downloadSizeText(downloading.downloadedBytes, downloading.totalBytes),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.update_cancelar),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: UpdateCardUiState,
    onInstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    UpdateHeader(
        icon = Icons.Rounded.CheckCircle,
        tint = colors.primary,
        title = stringResource(R.string.update_ready_title),
        subtitle = stringResource(R.string.update_ready_subtitle, state.versionName),
        version = null,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        PrimaryAction(
            text = stringResource(R.string.update_instalar),
            onClick = onInstall,
        )
    }
}

@Composable
private fun FailedContent(
    state: UpdateCardUiState,
    onRetry: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val failed = state as UpdateCardUiState.Failed
    UpdateHeader(
        icon = Icons.Rounded.ErrorOutline,
        tint = colors.error,
        title = stringResource(R.string.update_error_titulo),
        subtitle = failed.message.ifBlank {
            stringResource(R.string.update_descarga_error)
        },
        version = null,
    )
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLater) {
            Text(
                text = stringResource(R.string.mas_tarde),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        PrimaryAction(
            text = stringResource(R.string.update_reintentar),
            onClick = onRetry,
        )
    }
}

@Composable
private fun UpdateHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    version: String?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (dimens.isCompact) 34.dp else 38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        Spacer(Modifier.width(dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (version != null) {
                    Spacer(Modifier.width(dimens.spacingSm))
                    VersionPill(version)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VersionPill(version: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(UpdateCardShape)
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.update_version_chip, version),
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    val dimens = LocalAppDimens.current
    Button(
        onClick = onClick,
        shape = ComponentShapes.button,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.heightIn(min = dimens.touchMin),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun downloadSizeText(downloadedBytes: Long, totalBytes: Long): String {
    val downloaded = formatMb(downloadedBytes)
    return if (totalBytes > 0) {
        stringResource(R.string.update_download_mb, downloaded, formatMb(totalBytes))
    } else {
        stringResource(R.string.update_download_mb_unknown, downloaded)
    }
}

private fun formatMb(bytes: Long): String {
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return number.format(bytes / 1_000_000.0)
}

private val UpdateCardUiState.phase: UpdatePhase
    get() = when (this) {
        is UpdateCardUiState.Available -> UpdatePhase.AVAILABLE
        is UpdateCardUiState.Downloading -> UpdatePhase.DOWNLOADING
        is UpdateCardUiState.Ready -> UpdatePhase.READY
        is UpdateCardUiState.Failed -> UpdatePhase.FAILED
    }
