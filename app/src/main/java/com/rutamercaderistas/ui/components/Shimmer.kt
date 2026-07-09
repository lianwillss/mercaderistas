package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -200f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val highlight = Color.White.copy(alpha = 0.6f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset.Zero,
        end = Offset(translateX, translateX)
    )
}

@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, widthFraction: Float = 1f) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerBrush())
    )
}

@Composable
fun ShimmerSmallBlock(modifier: Modifier = Modifier, widthFraction: Float = 1f) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(shimmerBrush())
    )
}

@Composable
fun ShimmerCircle(size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

@Composable
fun ShimmerStoreCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerCircle(36)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBlock(widthFraction = 0.6f)
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerSmallBlock(widthFraction = 0.4f)
                    Spacer(modifier = Modifier.height(3.dp))
                    ShimmerSmallBlock(widthFraction = 0.5f)
                }
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerCircle(18)
            }
        }
    }
}

@Composable
fun ShimmerStatsCards(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShimmerCircle(12)
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBlock(modifier = Modifier.width(32.dp).height(18.dp))
                Spacer(modifier = Modifier.height(2.dp))
                ShimmerSmallBlock(modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
fun ShimmerDaySelector(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(5) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShimmerSmallBlock(modifier = Modifier.size(24.dp, 12.dp))
                Spacer(modifier = Modifier.height(2.dp))
                ShimmerSmallBlock(modifier = Modifier.size(16.dp, 10.dp))
            }
        }
    }
}

@Composable
fun ShimmerLoadingContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))
        ShimmerStatsCards()
        Spacer(modifier = Modifier.height(20.dp))
        ShimmerDaySelector()
        Spacer(modifier = Modifier.height(16.dp))
        repeat(6) {
            ShimmerStoreCard()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}