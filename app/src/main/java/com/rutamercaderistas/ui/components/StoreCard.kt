package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.Outline
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor
import kotlin.math.abs

@Composable
fun StoreCard(
    local: LocalDelDia,
    marcaResaltada: String?,
    onBrandClick: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    index: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val animAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250, delayMillis = index * 50)
    )
    val animOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 12.dp,
        animationSpec = tween(250, delayMillis = index * 50)
    )

    LaunchedEffect(Unit) { visible = true }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = animAlpha)
            .offset(y = animOffsetY),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(storeSoftColor(local.local)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Store,
                        contentDescription = null,
                        tint = storeColor(local.local),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = local.local.ifBlank { "S/N" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp
                    )

                    if (local.codigo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = local.codigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }

                    if (local.direccion.isNotBlank() || local.comuna.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAddressClick(local.direccion) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                if (local.direccion.isNotBlank()) {
                                    Text(
                                        text = local.direccion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontSize = 15.sp,
                                        color = AccentBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (local.comuna.isNotBlank()) {
                                    Text(
                                        text = local.comuna,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontSize = 15.sp,
                                        color = AccentBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${local.totalClientes}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AccentBlue,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "marcas",
                            fontWeight = FontWeight.Medium,
                            fontSize = 7.sp,
                            color = AccentBlue,
                            lineHeight = 8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "›",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // ── Divider ──
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Outline)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ── Brands ──
            local.clientes.forEach { cliente ->
                BrandItem(
                    cliente = cliente,
                    isHighlighted = marcaResaltada != null &&
                        cliente.nombre.equals(marcaResaltada, ignoreCase = true),
                    onClick = { onBrandClick(cliente.nombre) }
                )
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

// ── Tarjeta de marca ─────────────────────────────────────────────

@Composable
private fun BrandItem(
    cliente: ClienteInfo,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    if (cliente.esPrioritaria) {
        PriorityBrandCard(cliente = cliente, isHighlighted = isHighlighted, onClick = onClick)
    } else {
        NormalBrandRow(cliente = cliente, isHighlighted = isHighlighted, onClick = onClick)
    }
}

@Composable
private fun PriorityBrandCard(
    cliente: ClienteInfo,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        AccentOrange,
                        RoundedCornerShape(topStart = 11.dp, bottomStart = 11.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 5.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(AccentOrangeSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Prioritaria",
                        tint = AccentOrange,
                        modifier = Modifier.size(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = cliente.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (cliente.frecuencia > 0) {
                    Spacer(modifier = Modifier.width(5.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentGreenSoft)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = cliente.frecuenciaTexto,
                                color = AccentGreen,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            )
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────

private val avatarColors = listOf(
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
    Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
    Color(0xFF039BE5), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFF7CB342), Color(0xFFFDD835), Color(0xFFFFB300),
    Color(0xFFFB8C00), Color(0xFFF4511E), Color(0xFF6D4C41),
    Color(0xFF546E7A),
)

private fun avatarColor(text: String): Color {
    return avatarColors[abs(text.hashCode()) % avatarColors.size]
}

private fun avatarSoftColor(text: String): Color {
    return avatarColor(text).copy(alpha = 0.15f)
}

private fun initials(text: String): String {
    val parts = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
    }
}

@Composable
private fun NormalBrandRow(
    cliente: ClienteInfo,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isHighlighted) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(avatarSoftColor(cliente.nombre)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials(cliente.nombre),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = avatarColor(cliente.nombre),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = cliente.nombre,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (cliente.frecuencia > 0) {
            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = cliente.frecuenciaTexto,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
