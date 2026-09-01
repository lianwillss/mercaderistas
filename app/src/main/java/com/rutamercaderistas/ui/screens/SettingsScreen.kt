package com.rutamercaderistas.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.viewmodel.SettingsViewModel

private val TRANSPORT_OPTIONS = listOf(
    "transit" to R.string.settings_transit,
    "drive" to R.string.settings_drive,
    "bike" to R.string.settings_bike,
    "walk" to R.string.settings_walk,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()
    var stagedTransport by remember { mutableStateOf<String?>(null) }
    val displayMode = stagedTransport ?: transportMode
    val hasPendingChange = stagedTransport != null && stagedTransport != transportMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.volver_cd))
            }
            Spacer(modifier = Modifier.width(dimens.spacingSm))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            SettingsCard(title = stringResource(R.string.settings_font_size)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.setFontScale((fontScale - 0.1f).coerceIn(0.8f, 1.8f)) }) {
                        Text("A−", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setFontScale((fontScale + 0.1f).coerceIn(0.8f, 1.8f)) }) {
                        Text("A+", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    text = String.format("%.1f×", fontScale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = stringResource(R.string.settings_transport_mode)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TRANSPORT_OPTIONS.forEach { (key, labelRes) ->
                        val selected = displayMode == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    onClick = { stagedTransport = key },
                                    role = Role.RadioButton,
                                )
                                .semantics {
                                    this.selected = selected
                                    if (selected) stateDescription = "Seleccionado"
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (hasPendingChange) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                stagedTransport?.let { viewModel.setTransportMode(it) }
                                Toast.makeText(context, R.string.transporte_aplicado, Toast.LENGTH_SHORT).show()
                                stagedTransport = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.aplicar))
                        }
                    }
                }
            }

            SettingsCard(title = stringResource(R.string.settings_clear_ean_cache)) {
                Text(
                    text = stringResource(R.string.settings_clear_ean_cache_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(onClick = {
                    viewModel.clearEanCache()
                    Toast.makeText(context, R.string.settings_ean_cleared, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_clear_ean_cache))
                }
            }

            SettingsCard(title = stringResource(R.string.settings_clear_history)) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    viewModel.clearSearchHistory()
                    Toast.makeText(context, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_clear_history))
                }
            }

            SettingsCard(title = stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_version, viewModel.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
