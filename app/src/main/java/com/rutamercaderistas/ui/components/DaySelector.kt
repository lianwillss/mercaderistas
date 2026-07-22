package com.rutamercaderistas.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.rs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.models.DiaSemana

@Composable
fun DaySelector(
    days: List<DiaSemana>,
    dayNumbers: List<Int>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = rs()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        days.forEachIndexed { index, dia ->
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
                    .height(44.dp * s)
                    .shadow(elevation, ComponentShapes.pill)
                    .clip(ComponentShapes.pill)
                    .background(bgColor)
                    .clickable {
                        onDaySelected(index)
                    }
                    .semantics { contentDescription = dia.nombreCompleto }
                    .padding(horizontal = 12.dp * s),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dia.abreviacion,
                        style = if (isSelected) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dayNumbers.getOrElse(index) { 0 }.toString(),
                        style = if (isSelected) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.labelMedium,
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
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            DaySelector(
                days = listOf(DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES),
                dayNumbers = listOf(15, 16, 17),
                selectedIndex = 0,
                onDaySelected = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DaySelectorPreviewDark() {
    if (BuildConfig.DEBUG) {
        DaySelectorPreview()
    }
}
