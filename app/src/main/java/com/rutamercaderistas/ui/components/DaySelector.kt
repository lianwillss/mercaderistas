package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.ui.theme.ComponentShapes
import com.rutamercaderistas.ui.theme.LocalAppDimens
import java.time.DayOfWeek
import java.time.LocalDate

private fun diaDeHoy(): DiaSemana? {
    return when (LocalDate.now().dayOfWeek) {
        DayOfWeek.MONDAY -> DiaSemana.LUNES
        DayOfWeek.TUESDAY -> DiaSemana.MARTES
        DayOfWeek.WEDNESDAY -> DiaSemana.MIERCOLES
        DayOfWeek.THURSDAY -> DiaSemana.JUEVES
        DayOfWeek.FRIDAY -> DiaSemana.VIERNES
        DayOfWeek.SATURDAY -> DiaSemana.SABADO
        DayOfWeek.SUNDAY -> DiaSemana.DOMINGO
        else -> null
    }
}

@Composable
fun DaySelector(
    days: List<DiaSemana>,
    dayNumbers: List<Int>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val hoy = diaDeHoy()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEachIndexed { index, dia ->
            val isSelected = index == selectedIndex
            val esHoy = dia == hoy

            Box(
                modifier = Modifier
                    .heightIn(min = dimens.touchMin)
                    .clip(ComponentShapes.pill)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        shape = ComponentShapes.pill,
                    )
                    .clickable { onDaySelected(index) }
                    .semantics { contentDescription = dia.nombreCompleto }
                    .padding(horizontal = dimens.spacingMd),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = dia.abreviacion,
                            style = if (isSelected) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Text(
                            text = dayNumbers.getOrElse(index) { 0 }.toString(),
                            style = if (isSelected) MaterialTheme.typography.labelLarge
                                else MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    if (esHoy) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                        )
                    }
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