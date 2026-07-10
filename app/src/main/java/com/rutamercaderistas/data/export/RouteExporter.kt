package com.rutamercaderistas.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.View
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import androidx.core.content.FileProvider
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.models.toNaturalCase
import com.rutamercaderistas.services.RuteroRepository
import java.io.File
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Green = Color(0xFF1B5E20)
private val GreenSoft = Color(0xFFE8F5E9)
private val Orange = Color(0xFFE65100)
private val OrangeSoft = Color(0xFFFFF3E0)
private val Divider = Color(0xFFE0E0E0)
private val CardBg = Color(0xFFF8F8F8)

@Singleton
class RouteExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RouteExporter"
        private const val EXPORT_WIDTH_DP = 420
    }

    fun exportAsImage(
        routeName: String,
        entries: List<EntradaRuta>,
        stats: RuteroRepository.Stats = RuteroRepository.getStats(),
        onComplete: (File) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        val widthPx = (EXPORT_WIDTH_DP * density).toInt()

        val composeView = ComposeView(context).apply {
            id = View.generateViewId()
            setContent {
                MaterialTheme {
                    RouteExportLayout(routeName, entries, stats)
                }
            }
            measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, widthPx, measuredHeight)
        }

        try {
            val bitmap = Bitmap.createBitmap(widthPx, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            composeView.draw(canvas)

            val file = File(context.cacheDir, "ruta_${sanitize(routeName)}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            Log.d(TAG, "Export done: ${file.absolutePath} (${widthPx}x${composeView.measuredHeight})")
            onComplete(file)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
        }
    }

    fun shareImage(file: File, routeName: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ruta: $routeName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ruta"))
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')
}

@Composable
private fun RouteExportLayout(
    routeName: String,
    entries: List<EntradaRuta>,
    stats: RuteroRepository.Stats,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // ── Header ─────────────────────────────────────
        HeaderSection(routeName)

        Spacer(Modifier.height(16.dp))

        // ── Stats ──────────────────────────────────────
        StatsRow(stats)

        Spacer(Modifier.height(20.dp))

        // ── Entries by day ─────────────────────────────
        val byDay = remember(entries) { groupByDay(entries) }
        byDay.forEach { (day, locales) ->
            DaySection(day, locales)
            Spacer(Modifier.height(16.dp))
        }

        // ── Footer ─────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        DividerLine()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Generado por Mercaderistas app",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9E9E9E),
        )
    }
}

@Composable
private fun HeaderSection(routeName: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Green),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Ruta Mercaderistas",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = routeName.uppercase(Locale.ROOT),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666),
        )
    }
}

@Composable
private fun StatsRow(stats: RuteroRepository.Stats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard("Locales", stats.totalLocales, GreenSoft, Green)
        StatCard("Marcas", stats.totalMarcas, GreenSoft, Green)
        StatCard("Visitas", stats.visitasTotales, GreenSoft, Green)
    }
}

@Composable
private fun RowScope.StatCard(
    label: String,
    value: Int,
    bg: Color,
    fg: Color,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
            )
        }
    }
}

@Composable
private fun DaySection(day: DiaSemana, locales: List<LocalDelDia>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.nombreCompleto.uppercase(Locale.ROOT),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Divider),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${locales.size} local${if (locales.size != 1) "es" else ""}",
                fontSize = 11.sp,
                color = Color(0xFF999999),
            )
        }
        Spacer(Modifier.height(8.dp))

        locales.forEachIndexed { idx, local ->
            StoreExportCard(local)
            if (idx < locales.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}

@Composable
private fun StoreExportCard(local: LocalDelDia) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = local.codigo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF888888),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = local.local.ifBlank { "S/N" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1B1F),
            )
        }

        if (local.direccion.isNotBlank()) {
            Text(
                text = local.direccion.toNaturalCase(),
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (local.cadena.isNotBlank() || local.comuna.isNotBlank()) {
            Text(
                text = buildString {
                    if (local.cadena.isNotBlank()) append(local.cadena)
                    if (local.comuna.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(local.comuna)
                    }
                },
                fontSize = 11.sp,
                color = Color(0xFF888888),
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        if (local.clientes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            DividerLine()
            Spacer(Modifier.height(6.dp))
            local.clientes.forEach { cliente ->
                BrandExportRow(cliente)
            }
        }
    }
}

@Composable
private fun BrandExportRow(cliente: ClienteInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cliente.esPrioritaria) {
            Text(
                text = "★",
                fontSize = 12.sp,
                color = Orange,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = cliente.nombre,
            fontSize = 12.sp,
            fontWeight = if (cliente.esPrioritaria) FontWeight.SemiBold else FontWeight.Normal,
            color = if (cliente.esPrioritaria) Orange else Color(0xFF49454F),
            modifier = Modifier.weight(1f),
        )
        if (cliente.frecuenciaTexto.isNotBlank()) {
            Text(
                text = cliente.frecuenciaTexto,
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
            )
        }
    }
}

private fun groupByDay(entries: List<EntradaRuta>): Map<DiaSemana, List<LocalDelDia>> {
    val grouped = mutableMapOf<DiaSemana, MutableList<LocalDelDia>>()
    val allDays = DiaSemana.todos()

    for (day in allDays) {
        val locales = entries
            .filter { isAssignedForDay(it, day) }
            .groupBy { it.codigo + it.local }
            .map { (_, entries) ->
                val first = entries.first()
                LocalDelDia(
                    codigo = first.codigo,
                    local = first.local.toNaturalCase(),
                    direccion = first.direccion.toNaturalCase(),
                    cadena = first.cadena,
                    formato = first.formato,
                    region = first.region,
                    comuna = first.comuna,
                    clientes = entries.map { entry ->
                        ClienteInfo(
                            nombre = entry.cliente,
                            esPrioritaria = entry.esPrioritaria,
                            frecuencia = entry.frecuencia,
                        )
                    }.sortedByDescending { it.esPrioritaria },
                )
            }
            .sortedBy { it.codigo.padStart(6, '0') }

        if (locales.isNotEmpty()) {
            grouped[day] = locales.toMutableList()
        }
    }
    return grouped
}

private fun isAssignedForDay(entry: EntradaRuta, dia: DiaSemana): Boolean = when (dia) {
    DiaSemana.LUNES -> entry.lunes
    DiaSemana.MARTES -> entry.martes
    DiaSemana.MIERCOLES -> entry.miercoles
    DiaSemana.JUEVES -> entry.jueves
    DiaSemana.VIERNES -> entry.viernes
    DiaSemana.SABADO -> entry.sabado
    DiaSemana.DOMINGO -> entry.domingo
}
