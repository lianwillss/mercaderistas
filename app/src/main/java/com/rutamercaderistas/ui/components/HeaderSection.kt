package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HeaderSection(
    isOnline: Boolean,
    lastSyncRelative: String,
    onRefresh: () -> Unit,
    onOpenManual: () -> Unit,
    onShare: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenPromoDiagnostic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ruta Mercaderistas",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF34C759) else Color(0xFFFF3B30))
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isOnline) "En l\u00EDnea \u00B7 Actualizado $lastSyncRelative"
                           else "Sin conexi\u00F3n",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Actualizar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Men\u00FA",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Forzar sincronizaci\u00F3n") },
                    onClick = {
                        expanded = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Sincronizar")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Manual de usuario") },
                    onClick = {
                        expanded = false
                        onOpenManual()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Description, contentDescription = "Manual de usuario")
                    }
                )
    DropdownMenuItem(
        text = { Text("Compartir ruta") },
        onClick = {
            expanded = false
            onShare()
        },
        leadingIcon = {
            Icon(Icons.Outlined.Share, contentDescription = "Compartir ruta")
        }
    )
    DropdownMenuItem(
        text = { Text("Buscar actualización") },
        onClick = {
            expanded = false
            onCheckUpdate()
        },
        leadingIcon = {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = "Buscar actualización")
        }
    )
    DropdownMenuItem(
        text = { Text("Diagnóstico de Promociones") },
        onClick = {
            expanded = false
            onOpenPromoDiagnostic()
        },
        leadingIcon = {
            Icon(Icons.Outlined.BugReport, contentDescription = "Diagnóstico de Promociones")
        }
    )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderSectionPreview() {
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        HeaderSection(
            isOnline = true,
            lastSyncRelative = "hace 2 min",
            onRefresh = {},
            onOpenManual = {},
        )
    }
}