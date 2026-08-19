package com.rutamercaderistas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun dayLabel(
    abreviacion: String,
    num: String,
    scale: Float,
): AnnotatedString {
    val abbrevSize = MaterialTheme.typography.labelMedium.fontSize
    val numSize = MaterialTheme.typography.labelLarge.fontSize
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = abbrevSize * scale)) {
            append(abreviacion)
        }
        append(" ")
        withStyle(SpanStyle(fontSize = numSize * scale)) {
            append(num)
        }
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
    val gap = 4.dp
    val innerPadding = 4.dp
    val borderWidth = 1.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingMd),
    ) {
        val count = days.size.coerceAtLeast(1)
        val segmentWidth = (maxWidth - gap * (count - 1)) / count
        val availableTextWidth = segmentWidth - innerPadding * 2 - borderWidth * 2
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val baseStyle = MaterialTheme.typography.labelMedium

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.touchMin),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, dia ->
                val isSelected = index == selectedIndex
                val esHoy = dia == hoy
                val num = dayNumbers.getOrElse(index) { 0 }.toString()

                val label = dayLabel(dia.abreviacion, num, 1f)
                val fontScale = density.fontScale
                val naturalWidth = remember(dia.abreviacion, num, segmentWidth, fontScale) {
                    with(density) { textMeasurer.measure(label, baseStyle).size.width.toDp() }
                }
                val scale = if (naturalWidth <= availableTextWidth) 1f
                    else (availableTextWidth / naturalWidth).coerceIn(0.55f, 1f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.touchMin)
                        .clip(ComponentShapes.pill)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            width = borderWidth,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            shape = ComponentShapes.pill,
                        )
                        .clickable { onDaySelected(index) }
                        .semantics { contentDescription = dia.nombreCompleto }
                        .padding(horizontal = innerPadding, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayLabel(dia.abreviacion, num, scale),
                            style = baseStyle,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
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