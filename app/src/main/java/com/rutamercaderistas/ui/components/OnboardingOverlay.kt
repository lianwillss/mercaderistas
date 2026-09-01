package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.R

private data class Step(
    val titleRes: Int,
    val descRes: Int,
    val icon: @Composable (Color) -> Unit,
    val isBottom: Boolean,
    val bottomIndex: Int = -1,
)

@Composable
fun OnboardingOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    syncTargetCenter: Offset? = null,
) {
    if (!visible) return

    val steps = remember {
        listOf(
            Step(R.string.onboarding_rutero_title, R.string.onboarding_rutero_desc,
                { tint -> Icon(Icons.Outlined.Storefront, null, tint = tint) }, true, 0),
            Step(R.string.onboarding_promociones_title, R.string.onboarding_promociones_desc,
                { tint -> Icon(Icons.Outlined.ShoppingBag, null, tint = tint) }, true, 1),
            Step(R.string.onboarding_locales_title, R.string.onboarding_locales_desc,
                { tint -> Icon(Icons.Outlined.Visibility, null, tint = tint) }, true, 2),
            Step(R.string.onboarding_codprov_title, R.string.onboarding_codprov_desc,
                { tint -> Icon(Icons.Outlined.Badge, null, tint = tint) }, true, 3),
            Step(R.string.onboarding_ean_title, R.string.onboarding_ean_desc,
                { tint -> Icon(painterResource(R.drawable.ic_barcode), null, tint = tint) }, true, 4),
            Step(R.string.onboarding_sync_title, R.string.onboarding_sync_desc,
                { tint -> Icon(Icons.Outlined.Sync, null, tint = tint) }, false),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex
    val titleForCd = stringResource(step.titleRes)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Guía de inicio, paso ${index + 1} de ${steps.size}: $titleForCd"
            }
    ) {
        val density = LocalDensity.current
        val maxWpx = constraints.maxWidth.toFloat()
        val maxHpx = constraints.maxHeight.toFloat()
        val slotWpx = maxWpx / 5f
        val bottomHpx = with(density) { 96.dp.toPx() }
        val holeRadiusPx = with(density) { 48.dp.toPx() }
        val targetCenter = if (step.isBottom) {
            Offset(slotWpx * (step.bottomIndex + 0.5f), maxHpx - bottomHpx / 2f)
        } else {
            syncTargetCenter ?: Offset(maxWpx - with(density) { 164.dp.toPx() }, with(density) { 62.dp.toPx() })
        }
        val infinite = rememberInfiniteTransition()
        val pulse by infinite.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse)
        )

        // Scrim with hole
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f)
                .clickable(
                    onClick = { onDismiss() },
                    role = Role.Button,
                )
                .semantics { contentDescription = "Cerrar guía" }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.62f))
                drawCircle(Color.Transparent, radius = holeRadiusPx, center = targetCenter, blendMode = BlendMode.Clear)
                drawCircle(Color.White, radius = holeRadiusPx, center = targetCenter, style = Stroke(width = 3.dp.toPx()))
                // pulsing outer ring
                drawCircle(
                    Color.White.copy(alpha = (0.35f * (1f - pulse))),
                    radius = holeRadiusPx + pulse * 14.dp.toPx(),
                    center = targetCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
                // arrow: small triangle pointing from card to hole
                val arrowSize = 18.dp.toPx()
                val arrowY = if (step.isBottom) targetCenter.y - holeRadiusPx - arrowSize - 8.dp.toPx()
                else targetCenter.y + holeRadiusPx + 8.dp.toPx()
                val arrowX = targetCenter.x
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (step.isBottom) {
                        moveTo(arrowX - arrowSize / 2, arrowY)
                        lineTo(arrowX + arrowSize / 2, arrowY)
                        lineTo(arrowX, arrowY + arrowSize)
                        close()
                    } else {
                        moveTo(arrowX - arrowSize / 2, arrowY + arrowSize)
                        lineTo(arrowX + arrowSize / 2, arrowY + arrowSize)
                        lineTo(arrowX, arrowY)
                        close()
                    }
                }
                drawPath(path, Color.White)
            }
        }

        // Tooltip card
        val cardModifier = if (step.isBottom) {
            Modifier.align(Alignment.TopCenter).padding(top = 72.dp).padding(horizontal = 24.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).padding(horizontal = 24.dp)
        }
        Column(modifier = cardModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { step.icon(MaterialTheme.colorScheme.primary) }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(step.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    Text(stringResource(step.descRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        steps.forEachIndexed { i, _ ->
                            Box(
                                Modifier.size(if (i == index) 18.dp else 8.dp, height = 8.dp)
                                    .background(
                                        if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(50)
                                    )
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_skip)) }
                        Button(onClick = { if (isLast) onDismiss() else index++ }) {
                            Text(if (isLast) stringResource(R.string.onboarding_got_it) else stringResource(R.string.onboarding_next), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
