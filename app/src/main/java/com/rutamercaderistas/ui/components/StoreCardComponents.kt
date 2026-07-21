package com.rutamercaderistas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import kotlin.math.abs

@Composable
fun BrandItem(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    if (cliente.esPrioritaria) {
        PriorityBrandCard(
            cliente = cliente,
            promotions = promotions,
            expanded = expanded,
            isHighlighted = isHighlighted,
            onClick = onClick,
            onToggle = { expanded = !expanded },
        )
    } else {
        NormalBrandRow(
            cliente = cliente,
            promotions = promotions,
            expanded = expanded,
            isHighlighted = isHighlighted,
            onClick = onClick,
            onToggle = { expanded = !expanded },
        )
    }
}

@Composable
private fun PriorityBrandCard(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    expanded: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250)),
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
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
                            contentDescription = stringResource(R.string.prioritaria_cd),
                            tint = AccentOrange,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = cliente.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (promotions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(5.dp))
                        PromotionBadge(
                            count = promotions.size,
                            expanded = expanded,
                            onClick = onToggle,
                        )
                    }

                    if (cliente.frecuencia > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        FrequencyChip(text = cliente.frecuenciaTexto)
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && promotions.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(250)),
            ) {
                PromotionList(promotions = promotions, marginStart = 5.dp, showChain = true)
            }
        }
    }
}

@Composable
private fun NormalBrandRow(
    cliente: ClienteInfo,
    promotions: List<PromotionEntity>,
    expanded: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(250)),
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
                    style = MaterialTheme.typography.labelSmall,
                    color = avatarColor(cliente.nombre),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = cliente.nombre,
                style = if (isHighlighted) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (promotions.isNotEmpty()) {
                Spacer(modifier = Modifier.width(5.dp))
                PromotionBadge(
                    count = promotions.size,
                    expanded = expanded,
                    onClick = onToggle,
                )
            }

            if (cliente.frecuencia > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = cliente.frecuenciaTexto,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && promotions.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)),
        ) {
            PromotionList(promotions = promotions, marginStart = 2.dp, showChain = true)
        }
    }
}

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
fun FrequencyChip(text: String) {
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
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
            )
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = stringResource(R.string.frecuencia_cd),
                tint = AccentGreen,
                modifier = Modifier.size(8.dp)
            )
        }
    }
}
