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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.BrandIndigoText
import com.rutamercaderistas.ui.theme.DownloadGlassBorderBottom
import com.rutamercaderistas.ui.theme.DownloadGlassBorderTop
import com.rutamercaderistas.ui.theme.DownloadGlassWash
import com.rutamercaderistas.ui.theme.DownloadGlassWashBlue
import com.rutamercaderistas.ui.theme.DownloadNeonBlue
import com.rutamercaderistas.ui.theme.DownloadNeonGlow
import com.rutamercaderistas.ui.theme.DownloadNeonIconEdge
import com.rutamercaderistas.ui.theme.DownloadNeonTrackEdge
import com.rutamercaderistas.ui.theme.DownloadNeonViolet
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.GlassBorderHighlight
import com.rutamercaderistas.ui.theme.GlassShadowAmbient
import com.rutamercaderistas.ui.theme.GlassShadowSpot
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
 *
 * Marco: vidrio 2026 con borde iluminado en violeta y sombra índigo suave.
 * La descarga añade la barra fluorescente y ordena el contenido en la
 * jerarquía estado → porcentaje → barra → mensaje → cancelar, con aire
 * suficiente para que ningún texto se superponga.
 *
 * Accesibilidad: el bloque de título/subtítulo se anuncia como un solo
 * encabezado, los textos crecen sin truncarse y los targets mantienen 48 dp.
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
    val dimens = LocalAppDimens.current
    val reducedMotion = LocalReducedMotionEnabled.current

    UpdateSurface(
        expanded = expanded,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reducedMotion) tween(120) else MotionSprings.default(),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (expanded) dimens.spacingXl else dimens.spacingMd),
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
                // AnimatedContent coloca a sus hijos en una Box: sin este
                // Column, los bloques de una fase (cabecera, progreso,
                // mensaje, botón) se dibujaban unos encima de otros.
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (phase) {
                        UpdatePhase.AVAILABLE -> AvailableContent(
                            state = state,
                            onUpdate = onUpdate,
                            onLater = onLater,
                            expanded = expanded,
                            onClose = onClose,
                        )
                        UpdatePhase.DOWNLOADING -> DownloadingContent(
                            state = state,
                            onCancel = onCancel,
                            expanded = expanded,
                            onClose = onClose,
                        )
                        UpdatePhase.READY -> ReadyContent(
                            state = state,
                            onInstall = onInstall,
                            expanded = expanded,
                            onClose = onClose,
                        )
                        UpdatePhase.INSTALLING -> InstallingContent(
                            state = state,
                            expanded = expanded,
                            onClose = onClose,
                        )
                        UpdatePhase.FAILED -> FailedContent(
                            state = state,
                            onRetry = onRetry,
                            onLater = onLater,
                            expanded = expanded,
                            onClose = onClose,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vidrio del actualizador: reutiliza el material del scaffold (velo frío,
 * reflejo especular y sombra índigo) con un borde violeta que ilumina el
 * marco. Los tokens viven en el tema; acá solo se decide la dirección de la
 * luz (arriba violeta, abajo azul violeta).
 */
@Composable
private fun UpdateSurface(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = if (expanded) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val neonBorder = Brush.linearGradient(
        colors = listOf(
            DownloadGlassBorderTop,
            GlassBorderHighlight,
            DownloadGlassBorderBottom,
        ),
    )
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            elevation = if (expanded) Elevation.modal else Elevation.card,
            borderBrush = neonBorder,
        ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to DownloadGlassWash,
                        0.55f to Color.Transparent,
                        1f to DownloadGlassWashBlue,
                    ),
                ),
        )
        GlassSpecularSheen(shape = shape, modifier = Modifier.matchParentSize())
        content()
    }
}

@Composable
private fun AvailableContent(
    state: UpdateCardUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val dimens = LocalAppDimens.current
    val available = state as? UpdateCardUiState.Available
    val installedName = available?.installedVersionName.orEmpty()
    val targetName = available?.versionName ?: state.versionName
    val apkSize = available?.apkSize ?: 0L
    val transition = versionTransition(installedName, targetName)
    val size = if (apkSize > 0L) formatMb(apkSize) else null
    val subtitle = when {
        transition != null && size != null -> stringResource(
            R.string.update_version_transition_size,
            installedName,
            targetName,
            size,
        )
        transition != null -> transition
        expanded -> stringResource(R.string.update_card_subtitle_expanded, targetName)
        else -> stringResource(R.string.update_card_subtitle, targetName)
    }
    UpdateHeader(
        icon = Icons.Rounded.SystemUpdate,
        tint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_card_title),
        subtitle = subtitle,
        version = targetName,
        expanded = expanded,
        onClose = onClose,
    )
    Spacer(Modifier.height(if (expanded) dimens.spacingLg else dimens.spacingSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(
            text = stringResource(R.string.mas_tarde),
            onClick = onLater,
            variant = AppButtonVariant.Text,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val reducedMotion = LocalReducedMotionEnabled.current
    val downloading = state as UpdateCardUiState.Downloading
    val animatedPercent by animateIntAsState(
        targetValue = downloading.percent,
        animationSpec = if (reducedMotion) snap() else tween(280),
        label = "updatePercent",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = downloading.percent / 100f,
        animationSpec = if (reducedMotion) snap() else tween(280),
        label = "updateProgress",
    )
    val percentText = stringResource(R.string.update_download_percent, animatedPercent)
    // TalkBack no debe recibir un anuncio por cada 1 %: la región viva solo
    // cambia al cruzar cada decena. La barra conserva progressBarRangeInfo
    // para consultar el progreso exacto con el foco manual.
    val announcedPercent = (animatedPercent / 10) * 10
    val announcedLabel = stringResource(R.string.update_download_percent, announcedPercent)

    DownloadHeader(
        versionName = downloading.versionName,
        expanded = expanded,
        onClose = onClose,
    )

    // Contexto del salto de versión (12.52 → 12.53), antes de la barra.
    val transition = versionTransition(downloading.installedVersionName, downloading.versionName)
    if (transition != null) {
        Spacer(Modifier.height(if (expanded) dimens.spacingSm else 2.dp))
        Text(
            text = transition,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // 1. Estados y porcentaje: el dato grande y el tamaño comparten línea
    //    base, así el ojo no salta entre tamaños de tipografía.
    Spacer(Modifier.height(if (expanded) dimens.spacingLg else dimens.spacingMd))
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = percentText,
            style = if (expanded) {
                MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
            } else {
                MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
            },
            color = BrandIndigoText,
            modifier = Modifier
                .alignByBaseline()
                .clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcedLabel
                },
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = downloadSizeText(downloading.downloadedBytes, downloading.totalBytes),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.alignByBaseline(),
        )
    }

    // 2. Barra fluorescente: el Canvas ya reserva halo arriba y abajo, por
    //    eso los espaciadores son cortos.
    Spacer(Modifier.height(2.dp))
    NeonDownloadBar(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth(),
        barHeight = if (expanded) 14.dp else 10.dp,
    )

    // 3. Velocidad y tiempo restante (o "Calculando…" al arrancar).
    Spacer(Modifier.height(4.dp))
    Text(
        text = downloadStatsText(downloading),
        style = if (expanded) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )

    // 4. Cancelar: fila propia, alineado a la derecha, sin competir con el
    //    resto. El target conserva 48 dp.
    Spacer(Modifier.height(if (expanded) 6.dp else 2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(
            text = stringResource(R.string.update_cancelar),
            onClick = onCancel,
            variant = AppButtonVariant.Text,
            contentColor = colors.onSurfaceVariant,
            border = BorderStroke(1.dp, DownloadNeonTrackEdge),
        )
    }
}

@Composable
private fun ReadyContent(
    state: UpdateCardUiState,
    onInstall: () -> Unit,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val dimens = LocalAppDimens.current
    UpdateHeader(
        icon = Icons.Rounded.CheckCircle,
        tint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_ready_title),
        subtitle = stringResource(R.string.update_ready_subtitle, state.versionName),
        version = null,
        expanded = expanded,
        onClose = onClose,
    )
    Spacer(Modifier.height(if (expanded) dimens.spacingLg else dimens.spacingMd))
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
private fun InstallingContent(
    state: UpdateCardUiState,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val reducedMotion = LocalReducedMotionEnabled.current
    val installing = state as? UpdateCardUiState.Installing
    val versionName = installing?.versionName ?: state.versionName
    UpdateHeader(
        icon = Icons.Rounded.SystemUpdate,
        tint = colors.primary,
        title = stringResource(R.string.update_installing_title, versionName),
        subtitle = stringResource(R.string.update_installing_subtitle),
        version = null,
        expanded = expanded,
        onClose = onClose,
    )
    Spacer(Modifier.height(if (expanded) dimens.spacingLg else dimens.spacingMd))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sin animación en loop cuando el usuario pidió movimiento reducido.
        if (!reducedMotion) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (expanded) 22.dp else 18.dp),
                strokeWidth = 2.dp,
                color = colors.primary,
            )
            Spacer(Modifier.width(dimens.spacingSm))
        }
        Text(
            text = stringResource(R.string.update_installing_waiting),
            style = if (expanded) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedContent(
    state: UpdateCardUiState,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val failed = state as UpdateCardUiState.Failed
    UpdateHeader(
        icon = Icons.Rounded.ErrorOutline,
        tint = colors.error,
        title = stringResource(R.string.update_error_titulo),
        subtitle = failed.message.ifBlank {
            stringResource(R.string.update_descarga_error)
        },
        version = null,
        expanded = expanded,
        onClose = onClose,
    )
    Spacer(Modifier.height(if (expanded) dimens.spacingLg else dimens.spacingSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(
            text = stringResource(
                if (failed.retryable) R.string.mas_tarde else R.string.update_ocultar,
            ),
            onClick = onLater,
            variant = AppButtonVariant.Text,
            contentColor = colors.onSurfaceVariant,
        )
        if (failed.retryable) {
            Spacer(Modifier.width(4.dp))
            PrimaryAction(
                text = stringResource(R.string.update_reintentar),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun UpdateHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    version: String?,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    val titleStyle = if (expanded) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleSmall
    }
    val subtitleStyle = if (expanded) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        // El icono y el cierre se alinean con el título; el bloque de texto
        // crece hacia abajo sin desplazarlos a la mitad del párrafo.
        verticalAlignment = Alignment.Top,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = colors.onSurface,
            )
            if (version != null) {
                Spacer(Modifier.height(4.dp))
                VersionPill(version)
            }
            Spacer(Modifier.height(if (version != null) 6.dp else 2.dp))
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = colors.onSurfaceVariant,
            )
        }
        if (onClose != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(dimens.touchMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cerrar_cd),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadHeader(
    versionName: String,
    expanded: Boolean,
    onClose: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val dimens = LocalAppDimens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadIconBubble(expanded = expanded)
        Spacer(Modifier.width(dimens.spacingMd))
        Text(
            text = stringResource(R.string.update_downloading_title, versionName),
            style = if (expanded) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(dimens.touchMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cerrar_cd),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Icono de la descarga en una cápsula violeta con halo fluorescente. */
@Composable
private fun DownloadIconBubble(expanded: Boolean) {
    val bubbleSize = if (expanded) 44.dp else 38.dp
    Box(
        modifier = Modifier
            .size(bubbleSize)
            .shadow(
                elevation = if (expanded) 10.dp else 6.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = DownloadNeonGlow,
                spotColor = DownloadNeonGlow,
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(DownloadNeonViolet, DownloadNeonBlue),
                ),
            )
            .border(1.dp, DownloadNeonIconEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (expanded) 24.dp else 21.dp),
        )
    }
}

@Composable
private fun VersionPill(version: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(UpdateCardShape)
            .background(colors.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.update_version_chip, version),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimaryContainer,
        )
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    AppButton(text = text, onClick = onClick)
}

/** "12.52 → 12.53"; null si no hay salto que contar. */
@Composable
private fun versionTransition(installedVersionName: String, versionName: String): String? {
    if (installedVersionName.isBlank() || installedVersionName == versionName) return null
    return stringResource(
        R.string.update_version_transition,
        installedVersionName,
        versionName,
    )
}

/** "1,3 MB/s · quedan 24 s" o el estado de cálculo inicial. */
@Composable
private fun downloadStatsText(downloading: UpdateCardUiState.Downloading): String {
    if (downloading.bytesPerSecond <= 0L) {
        return stringResource(R.string.update_eta_calculating)
    }
    val speed = stringResource(R.string.update_speed_mb_s, formatMb(downloading.bytesPerSecond))
    val eta = downloading.etaSeconds
    return if (eta != null && eta >= 0L) {
        stringResource(R.string.update_download_stats, speed, etaText(eta))
    } else {
        stringResource(R.string.update_download_speed_only, speed)
    }
}

@Composable
private fun etaText(seconds: Long): String = when {
    seconds < 60L -> stringResource(R.string.update_eta_seconds, seconds)
    seconds < 3600L -> stringResource(
        R.string.update_eta_minutes,
        seconds / 60L,
        seconds % 60L,
    )
    else -> stringResource(
        R.string.update_eta_hours,
        seconds / 3600L,
        (seconds % 3600L) / 60L,
    )
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
        is UpdateCardUiState.Installing -> UpdatePhase.INSTALLING
        is UpdateCardUiState.Failed -> UpdatePhase.FAILED
    }
