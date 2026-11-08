package com.rutamercaderistas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Chip de selección secundaria: días, filtros de cadena, facetas de EAN y modo
 * de stock.
 *
 * El índigo suave queda reservado para la selección principal (la píldora de
 * la barra de vidrio). Acá la selección se marca con borde y texto índigo
 * sobre una superficie neutra, así el usuario distingue de un vistazo un
 * filtro de una acción principal. Las direcciones y los metadatos usan el
 * mismo criterio: superficie neutra + icono índigo.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon ?: if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            // onPrimaryContainer es la variante de texto/icono del índigo
            // (BrandIndigoText), con mejor contraste que el relleno.
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                // Borde visible sobre la superficie neutra (>= 3:1); el
                // outlineVariant del tema se perdía contra el fondo.
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            },
        ),
    )
}
