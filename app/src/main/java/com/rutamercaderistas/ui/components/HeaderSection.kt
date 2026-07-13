package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.sin

private val TWO_PI = (2 * PI).toFloat()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderSection(
    isOnline: Boolean,
    lastSyncRelative: String,
    onRefresh: () -> Unit,
    onOpenManual: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenPromoDiagnostic: () -> Unit = {},
    promosExpiringToday: Int = 0,
    promosExpiringTomorrow: Int = 0,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFF0A2E5A),
                    0.35f to Color(0xFF0D4F8B),
                    0.65f to Color(0xFF1A7BB5),
                    0.85f to Color(0xFFB8DCF0),
                    1.0f to Color(0xFFF8F9FA),
                ),
            )

            val path1 = Path().apply {
                moveTo(0f, h * 0.72f)
                for (x in 0..w.toInt() step 4) {
                    val y = h * 0.72f + sin(x * 0.008f + phase) * 14f + sin(x * 0.015f + phase * 1.3f) * 8f
                    lineTo(x.toFloat(), y)
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path1, Color(0xFF1A7BB5).copy(alpha = 0.35f))

            val path2 = Path().apply {
                moveTo(0f, h * 0.82f)
                for (x in 0..w.toInt() step 4) {
                    val y = h * 0.82f + sin(x * 0.012f - phase * 0.7f) * 10f + sin(x * 0.02f + phase) * 5f
                    lineTo(x.toFloat(), y)
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path2, Color(0xFF2D9CDB).copy(alpha = 0.25f))

            val path3 = Path().apply {
                moveTo(0f, h * 0.62f)
                for (x in 0..w.toInt() step 4) {
                    val y = h * 0.62f + sin(x * 0.005f + phase * 0.5f) * 6f
                    lineTo(x.toFloat(), y)
                }
                lineTo(w, h * 0.9f)
                lineTo(0f, h * 0.9f)
                close()
            }
            drawPath(path3, Color(0xFF4DB8E8).copy(alpha = 0.15f))

            val dotPositions = (0..10).map { i ->
                val x = w * (i + 1) / 12f
                val y = h * 0.35f + sin(x * 0.025f + phase * 0.4f + i * 0.5f) * 16f
                Offset(x, y)
            }
            for (i in 0 until dotPositions.lastIndex) {
                drawLine(
                    Color.White.copy(alpha = 0.07f),
                    dotPositions[i],
                    dotPositions[i + 1],
                    strokeWidth = 1.5f,
                )
            }
            dotPositions.forEach { pos ->
                drawCircle(Color.White.copy(alpha = 0.18f), radius = 2.5f, center = pos)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RUTERO",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isOnline) "En l\u00EDnea \u00B7 Actualizado $lastSyncRelative"
                                       else "Sin conexi\u00F3n",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (promosExpiringToday > 0 || promosExpiringTomorrow > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (promosExpiringToday > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFFF0E0).copy(alpha = 0.9f))
                                            .heightIn(min = 26.dp, max = 36.dp)
                                            .widthIn(min = 60.dp, max = 200.dp)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "\uD83D\uDD25",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$promosExpiringToday promo${if (promosExpiringToday == 1) "" else "s"} hoy",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFFD97706),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                if (promosExpiringTomorrow > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFE0F2FE).copy(alpha = 0.9f))
                                            .heightIn(min = 26.dp, max = 36.dp)
                                            .widthIn(min = 60.dp, max = 200.dp)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "\uD83D\uDD25",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$promosExpiringTomorrow ma\u00F1ana",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFF0369A1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Actualizar",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { expanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Men\u00FA",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
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
