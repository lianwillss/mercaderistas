package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R

/**
 * Código de local resaltado: pill con dígitos tabulares (labelMedium trae
 * tnum). Solo visual, sin tamaño mínimo táctil porque no es interactivo.
 */
@Composable
fun CodigoChip(
    codigo: String,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.busqueda_codigo_label, codigo)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = cd },
    ) {
        Text(
            text = codigo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}
