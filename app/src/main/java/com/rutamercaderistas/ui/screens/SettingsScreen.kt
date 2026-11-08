package com.rutamercaderistas.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.components.AppButton
import com.rutamercaderistas.ui.components.AppButtonVariant
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.viewmodel.SettingsViewModel
import com.rutamercaderistas.viewmodel.StockAccessViewModel

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
    accessViewModel: StockAccessViewModel = hiltViewModel(),
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()
    var stagedTransport by remember { mutableStateOf<String?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
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
                .padding(dimens.spacingMd)
                .padding(bottom = dimens.scrollBottomPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            val acceso by accessViewModel.state.collectAsStateWithLifecycle()
            // El acceso manual es el respaldo si falla el login automático.
            SettingsCard(title = "Acceso a KPI One (Stock)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = acceso.email,
                        onValueChange = accessViewModel::onEmail,
                        label = { Text("Correo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = acceso.password,
                        onValueChange = accessViewModel::onPassword,
                        label = { Text("Clave") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppButton(
                            text = if (acceso.conectado) "Reconectar" else "Conectar",
                            onClick = accessViewModel::conectar,
                            enabled = !acceso.trabajando,
                        )
                        if (acceso.conectado) {
                            Spacer(Modifier.width(8.dp))
                            AppButton(
                                text = "Desconectar",
                                onClick = accessViewModel::desconectar,
                                variant = AppButtonVariant.Text,
                            )
                        }
                        if (acceso.trabajando) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    acceso.mensaje?.let { texto ->
                        Text(
                            text = texto,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (acceso.error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            SettingsCard(title = stringResource(R.string.settings_font_size)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        text = "A−",
                        onClick = { viewModel.setFontScale((fontScale - 0.1f).coerceIn(0.8f, 1.8f)) },
                        variant = AppButtonVariant.Text,
                    )
                    Slider(
                        value = fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = "A+",
                        onClick = { viewModel.setFontScale((fontScale + 0.1f).coerceIn(0.8f, 1.8f)) },
                        variant = AppButtonVariant.Text,
                    )
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
                            RadioButton(
                                selected = selected,
                                onClick = null,
                            )
                        }
                    }
                    if (hasPendingChange) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppButton(
                            text = stringResource(R.string.aplicar),
                            onClick = {
                                stagedTransport?.let { viewModel.setTransportMode(it) }
                                Toast.makeText(context, R.string.transporte_aplicado, Toast.LENGTH_SHORT).show()
                                stagedTransport = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                AppButton(
                    text = stringResource(R.string.settings_clear_ean_cache),
                    onClick = {
                        viewModel.clearEanCache()
                        Toast.makeText(context, R.string.settings_ean_cleared, Toast.LENGTH_SHORT).show()
                    },
                    variant = AppButtonVariant.Outlined,
                    icon = Icons.Outlined.Delete,
                )
            }

            SettingsCard(title = stringResource(R.string.settings_clear_history)) {
                AppButton(
                    text = stringResource(R.string.settings_clear_history),
                    onClick = {
                        viewModel.clearSearchHistory()
                        Toast.makeText(context, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
                    },
                    variant = AppButtonVariant.Outlined,
                    icon = Icons.Outlined.Delete,
                )
            }

            SettingsCard(title = stringResource(R.string.sync_historial_titulo)) {
                val syncHistory by viewModel.syncHistory.collectAsStateWithLifecycle()
                val lastCheck by viewModel.lastSyncCheck.collectAsStateWithLifecycle()
                Text(
                    text = if (lastCheck > 0L) stringResource(
                        R.string.sync_ultima_revision,
                        formatSyncHistoryDate(lastCheck),
                    ) else stringResource(R.string.sync_sin_revision),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (syncHistory.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sync_historial_vacio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    syncHistory.forEach { entry ->
                        Text(
                            text = formatSyncHistoryDate(entry.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = entry.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AppButton(
                        text = stringResource(R.string.sync_historial_borrar),
                        onClick = { confirmClearHistory = true },
                        variant = AppButtonVariant.Outlined,
                        icon = Icons.Outlined.Delete,
                    )
                }
            }

            IosModal(
                visible = confirmClearHistory,
                onDismiss = { confirmClearHistory = false },
                title = stringResource(R.string.sync_historial_titulo),
                confirmText = stringResource(R.string.sync_historial_borrar),
                onConfirm = {
                    viewModel.clearSyncHistory()
                    confirmClearHistory = false
                },
                dismissText = stringResource(R.string.update_cancelar),
            ) {
                Text(
                    text = stringResource(R.string.sync_historial_borrar_pregunta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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

private fun formatSyncHistoryDate(timestamp: Long): String {
    return try {
        val dt = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        dt.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.forLanguageTag("es")))
    } catch (_: Exception) {
        ""
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
