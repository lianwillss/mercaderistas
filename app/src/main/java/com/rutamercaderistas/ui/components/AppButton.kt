package com.rutamercaderistas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens

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
 * - Icono decorativo de 18 dp + 8 dp de separación.
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
    }
    val contentPadding = if (variant == AppButtonVariant.Text) {
        PaddingValues(horizontal = dimens.spacingMd)
    } else {
        PaddingValues(horizontal = dimens.spacingLg)
    }
    val buttonModifier = modifier.heightIn(min = dimens.touchMin)
    val shape = ComponentShapes.button

    when (variant) {
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
