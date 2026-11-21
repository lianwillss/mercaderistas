package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.Elevation
import com.rutamercaderistas.ui.theme.LimpiarGlow
import com.rutamercaderistas.ui.theme.LimpiarGradEnd
import com.rutamercaderistas.ui.theme.LimpiarGradMid
import com.rutamercaderistas.ui.theme.LimpiarGradStart
import com.rutamercaderistas.ui.theme.LimpiarPlaca
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource

/**
 * Variantes del botón de la app. La forma y la altura no se negocian por
 * pantalla: todas salen de acá.
 */
enum class AppButtonVariant {
    /** Acción principal: relleno índigo. */
    Filled,
    /** Acción secundaria sobre una superficie: relleno neutro. */
    Tonal,
    /** Acción secundaria con borde. */
    Outlined,
    /** Acción terciaria: sin relleno ni borde. */
    Text,
    /** Limpiar (Stock): degradado con etiqueta sobre placa oscura. */
    Gradient,
}

/**
 * Botón único de la app (reemplaza M3 Button/TextButton/OutlinedButton y los
 * botones a mano de IosModal).
 *
 * - Altura mínima de 48 dp (`touchMin`) en todas las variantes.
 * - Forma [ComponentShapes.button]: las píldoras quedan para filtros,
 *   selección y navegación.
 * - Etiqueta siempre en labelLarge; el color lo maneja la variante, así el
 *   estado deshabilitado se atenúa solo.
 * - Icono decorativo de 18 dp + 8 dp de separación (no aplica en [Gradient],
 *   cuyo diseño es solo texto).
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color? = null,
    border: BorderStroke? = null,
) {
    val dimens = LocalAppDimens.current
    val colors: ButtonColors = when (variant) {
        AppButtonVariant.Filled -> if (contentColor != null) {
            ButtonDefaults.buttonColors(contentColor = contentColor)
        } else {
            ButtonDefaults.buttonColors()
        }
        AppButtonVariant.Tonal -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppButtonVariant.Outlined -> if (contentColor != null) {
            ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
        AppButtonVariant.Text -> if (contentColor != null) {
            ButtonDefaults.textButtonColors(contentColor = contentColor)
        } else {
            ButtonDefaults.textButtonColors()
        }
        AppButtonVariant.Gradient -> ButtonDefaults.buttonColors()
    }
    val contentPadding = if (variant == AppButtonVariant.Text) {
        PaddingValues(horizontal = dimens.spacingMd)
    } else {
        PaddingValues(horizontal = dimens.spacingLg)
    }
    val buttonModifier = modifier.heightIn(min = dimens.touchMin)
    val shape = ComponentShapes.button

    when (variant) {
        AppButtonVariant.Gradient -> GradientButton(
            text = text,
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
        )

        AppButtonVariant.Filled, AppButtonVariant.Tonal -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            contentPadding = contentPadding,
        ) {
            AppButtonLabel(text = text, icon = icon)
        }

        AppButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border ?: BorderStroke(
                width = 1.dp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            contentPadding = contentPadding,
        ) {
            AppButtonLabel(text = text, icon = icon)
        }

        AppButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
        ) {
            AppButtonLabel(text = text, icon = icon)
        }
    }
}

@Composable
private fun AppButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(LocalAppDimens.current.iconSm),
        )
        Spacer(Modifier.width(LocalAppDimens.current.spacingSm))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * From Uiverse.io by cssbuttons-io - https://uiverse.io/cssbuttons-io/wonderful-baboon-62
 * MIT License, Copyright (c) cssbuttons-io (cssbuttons.io)
 *
 * Botón de degradado con etiqueta sobre placa oscura: al presionar la placa
 * se desvanece (en la web es `:hover`, en Android el equivalente es el
 * pressed) y el botón se encoge al 90 %. Sin ripple: el propio cambio de
 * fondo y la escala son el feedback del diseño original.
 */
@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val interactionSource = rememberPressInteractionSource()
    val presionado by interactionSource.collectIsPressedAsState()
    val placa by animateColorAsState(
        targetValue = if (presionado) Color.Transparent else LimpiarPlaca,
        animationSpec = tween(durationMillis = 300),
        label = "gradientButtonPlate",
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(interactionSource, pressedScale = 0.9f)
            .shadow(
                elevation = Elevation.level4,
                shape = ComponentShapes.gradientButton,
                ambientColor = LimpiarGlow.copy(alpha = 0.2f),
                spotColor = LimpiarGlow.copy(alpha = 0.2f),
            )
            .clip(ComponentShapes.gradientButton)
            .background(
                Brush.linearGradient(
                    colors = listOf(LimpiarGradStart, LimpiarGradMid, LimpiarGradEnd),
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .clip(ComponentShapes.gradientButtonInset)
                .background(placa)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
