package com.rutamercaderistas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.services.compactNorm

/**
 * Línea de marca encontrada por búsqueda ("BIGU · 3 días/sem · LUN, MIE,
 * VIE"): resalta lo coincidente y al tocar filtra solo esa marca.
 */
@Composable
fun MatchedBrandLine(
    cliente: ClienteInfo,
    daysLabel: String,
    query: String,
    onBrandSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = remember(query) {
        compactNorm(query).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }
    val suffix = buildString {
        if (cliente.frecuenciaTexto.isNotBlank()) append(" · ${cliente.frecuenciaTexto}")
        if (daysLabel.isNotBlank()) append(" · $daysLabel")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = { onBrandSearch(cliente.nombre) },
                role = Role.Button,
            )
            .semantics {
                contentDescription = "Filtrar por marca ${cliente.nombre}$suffix"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append(highlightRanges(cliente.nombre, tokens, MaterialTheme.colorScheme.primary))
                append(suffix)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
