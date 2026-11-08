package com.rutamercaderistas.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource

/**
 * Icono de ayuda "(i)" que abre un [IosModal] con una explicación de una línea.
 * Diseñado para términos de jerga (Planilla, Cod. proveedor, EAN) sin forzar al
 * usuario a salir a buscar el manual de 107 páginas.
 *
 * - Target táctil de 48 dp (cumple touchMin).
 * - pressScale + ripple por defecto.
 * - contentDescription + Role.Button para TalkBack.
 * - Todos los colores del tema (sin colores crudos).
 */
@Composable
fun InfoTooltip(
    helper: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showState = remember { mutableStateOf<Boolean>(false) }
    val dimens = LocalAppDimens.current
    val press = rememberPressInteractionSource()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ayudaCd = stringResource(R.string.ayuda_inline_cd)
        IconButton(
            onClick = { showState.value = true },
            modifier = Modifier
                .size(dimens.touchMin)
                .pressScale(press)
                .semantics { contentDescription = ayudaCd },
        ) {
            Text(
                text = "(i)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showState.value) {
            IosModal(
                visible = true,
                onDismiss = { showState.value = false },
                title = stringResource(R.string.ayuda_inline_titulo),
                dismissText = stringResource(R.string.ayuda_entendido),
            ) {
                helper()
            }
        }
    }
}
