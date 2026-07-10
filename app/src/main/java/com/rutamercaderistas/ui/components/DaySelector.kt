package com.rutamercaderistas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.rutamercaderistas.models.DiaSemana

@Composable
fun DaySelector(
    days: List<DiaSemana>,
    dayNumbers: List<Int>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        itemsIndexed(days) { index, dia ->
            val isSelected = index == selectedIndex

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
                label = "day_bg"
            )
            val elevation by animateDpAsState(
                targetValue = if (isSelected) 4.dp else 0.dp,
                label = "day_elev"
            )

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .shadow(elevation, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(bgColor)
                    .clickable {
                        onDaySelected(index)
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dia.abreviacion,
                        style = MaterialTheme.typography.labelMedium,
                        lineHeight = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dayNumbers.getOrElse(index) { 0 }.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DaySelectorPreview() {
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        DaySelector(
            days = listOf(DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES),
            dayNumbers = listOf(15, 16, 17),
            selectedIndex = 0,
            onDaySelected = {},
        )
    }
}
