package com.rutamercaderistas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.services.compactNorm

/**
 * Línea de marca encontrada por búsqueda: nombre resaltado + badge de
 * frecuencia, y días de visita debajo (ya no se truncan). Al tocar filtra
 * solo esa marca.
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
    val rowDescription = buildString {
        append(stringResource(R.string.marca_filtrar_cd, cliente.nombre))
        if (cliente.frecuenciaTexto.isNotBlank()) append(", ${cliente.frecuenciaTexto}")
        if (daysLabel.isNotBlank()) append(". Se visita: $daysLabel")
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = { onBrandSearch(cliente.nombre) },
                role = Role.Button,
                onClickLabel = stringResource(R.string.marca_filtrar_cd, cliente.nombre),
            )
            .semantics {
                contentDescription = rowDescription
            }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    append(highlightRanges(cliente.nombre, tokens, MaterialTheme.colorScheme.primary))
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cliente.frecuencia > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                FrequencyBadge(cliente = cliente)
            }
        }
        if (daysLabel.isNotBlank()) {
            Text(
                text = daysLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
