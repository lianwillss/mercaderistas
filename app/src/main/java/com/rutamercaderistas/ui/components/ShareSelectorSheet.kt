package com.rutamercaderistas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R

enum class ShareMode { SOLO_MARCAS, CON_PROMOS, CON_MAPA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSelectorSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onShare: (ShareMode, Boolean) -> Unit,
) {
    if (!visible) return

    var selectedMode by remember { mutableStateOf(ShareMode.CON_PROMOS) }
    var includeMaps by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.compartir_titulo),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            ShareOption(
                label = stringResource(R.string.compartir_solo_marcas),
                selected = selectedMode == ShareMode.SOLO_MARCAS,
                onClick = { selectedMode = ShareMode.SOLO_MARCAS },
            )
            ShareOption(
                label = stringResource(R.string.compartir_con_promos),
                selected = selectedMode == ShareMode.CON_PROMOS,
                onClick = { selectedMode = ShareMode.CON_PROMOS },
            )
            ShareOption(
                label = stringResource(R.string.compartir_con_mapa),
                selected = selectedMode == ShareMode.CON_MAPA,
                onClick = { selectedMode = ShareMode.CON_MAPA },
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { includeMaps = !includeMaps }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.compartir_incluir_maps),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = includeMaps, onCheckedChange = { includeMaps = it })
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.compartir_cancelar))
                }
                Button(onClick = {
                    onShare(selectedMode, includeMaps)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.compartir))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
