package com.rutamercaderistas.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import timber.log.Timber
import androidx.core.content.FileProvider
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.models.toNaturalCase
import com.rutamercaderistas.services.RuteroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WIDTH_DP = 420
        private const val PAD_DP = 20
        private const val GREEN = -16738336  // 0xFF1B5E20
        private const val GREEN_SOFT = -0x170AE7  // 0xFFE8F5E9
        private const val ORANGE = -0x19AFFF  // 0xFFE65100
        private const val DIVIDER = -0x1F1F1F  // 0xFFE0E0E0
        private const val CARD_BG = -0x70708  // 0xFFF8F8F8
        private const val TEXT_PRIMARY = -0xE3E4E1  // 0xFF1C1B1F
        private const val TEXT_SECONDARY = -0xB6BAC1  // 0xFF666666
        private const val TEXT_LIGHT = -0x666667  // 0xFF999999
        private const val WHITE = -0x1
    }
    private val density = context.resources.displayMetrics.density
    private val dp: (Int) -> Int = { (it * density).toInt() }
    private val sp: (Int) -> Float = { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it.toFloat(), context.resources.displayMetrics) }

    private fun paint(
        color: Int,
        sizeSp: Int = 14,
        bold: Boolean = false,
        italic: Boolean = false,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = sp(sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (italic) typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    suspend fun exportAsImage(
        routeName: String,
        entries: List<EntradaRuta>,
        stats: RuteroRepository.Stats,
    ): File = withContext(Dispatchers.IO) {
        val w = dp(WIDTH_DP)
        val pad = dp(PAD_DP)
        val contentW = w - pad * 2
        val byDay = groupByDay(entries)

        val totalH = measureHeight(contentW, routeName, stats, byDay)
        val maxH = 6000
        val finalH = minOf(totalH, maxH)
        if (totalH > maxH) {
            Timber.w("Route too tall for export (%d > %dpx), truncating", totalH, maxH)
        }
        val bitmap = try {
            Bitmap.createBitmap(w, finalH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw RuntimeException("Memoria insuficiente para exportar ruta", e)
        }
        try {
            val c = Canvas(bitmap)
            c.drawColor(WHITE)

            var y = pad
            y = drawHeader(c, routeName, y, pad)
            y += dp(8)
            y = drawStats(c, stats, y, contentW)
            y += dp(12)
            for ((day, locales) in byDay) {
                y = drawDaySection(c, day, locales, y, contentW)
            }
            y += dp(8)
            c.drawRoundRect(
                pad.toFloat(), y.toFloat(),
                (w - pad).toFloat(), (y + 1).toFloat(),
                0f, 0f, paint(DIVIDER),
            )
            y += dp(12)
            c.drawText("Generado por Mercaderistas app", pad.toFloat(), y.toFloat(), paint(TEXT_LIGHT, 11))
            y += dp(20)

            cleanupOldExports()

            val exportDir = File(context.cacheDir, "route_exports").also { it.mkdirs() }
            val file = File(exportDir, "ruta_${sanitize(routeName)}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Timber.d("Export done: %s (%dx%d)", file.absolutePath, w, totalH)
            file
        } finally {
            bitmap.recycle()
        }
    }

    fun shareImage(file: File, routeName: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ruta: $routeName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir ruta")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')

    private fun cleanupOldExports() {
        File(context.cacheDir, "route_exports")
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.name.startsWith("ruta_") && it.name.endsWith(".png") }
            ?.forEach { it.delete() }
    }

    // ── Measurement ──────────────────────────────────

    private fun measureHeight(
        w: Int,
        routeName: String,
        stats: RuteroRepository.Stats,
        byDay: Map<DiaSemana, List<LocalDelDia>>,
    ): Int {
        val pad = dp(PAD_DP)
        val contentW = w - pad * 2
        var y = pad

        // Header
        y += dp(24) + dp(16)

        // Stats
        val statH = dp(50)
        y += dp(8) + statH + dp(12)

        // Days
        for ((_, locales) in byDay) {
            y += dp(24)  // day header
            for (local in locales) {
                y += dp(10) + dp(12) // card header
                if (local.direccion.isNotBlank()) y += dp(16)
                if (local.cadena.isNotBlank() || local.comuna.isNotBlank()) y += dp(14)
                if (local.clientes.isNotEmpty()) {
                    y += dp(12) + local.clientes.size * dp(18)
                }
                y += dp(10)
            }
            y += dp(8)
        }

        // Footer
        y += dp(8) + dp(4) + dp(20)
        return y
    }

    // ── Header ───────────────────────────────────────

    private fun drawHeader(c: Canvas, name: String, y: Int, pad: Int): Int {
        val p = paint(GREEN, 20, bold = true)
        var cy = y
        // Green square bullet
        c.drawRoundRect(
            pad.toFloat(), cy.toFloat(),
            (pad + dp(10)).toFloat(), (cy + dp(10)).toFloat(),
            dp(2).toFloat(), dp(2).toFloat(), paint(GREEN),
        )
        c.drawText("Ruta Mercaderistas", (pad + dp(16)).toFloat(), (cy + dp(9)).toFloat(), p)
        cy += dp(22)

        val p2 = paint(TEXT_SECONDARY, 14)
        c.drawText(name.uppercase(Locale.ROOT), pad.toFloat(), (cy + dp(6)).toFloat(), p2)
        cy += dp(16)
        return cy
    }

    // ── Stats ────────────────────────────────────────

    private fun drawStats(c: Canvas, s: RuteroRepository.Stats, y: Int, contentW: Int): Int {
        val gap = dp(10)
        val cardW = (contentW - gap * 2) / 3
        val cardH = dp(50)
        val radius = dp(12).toFloat()
        val r = RectF()

        val items = listOf(
            s.totalLocales.toString() to "Locales",
            s.totalMarcas.toString() to "Marcas",
            s.visitasTotales.toString() to "Visitas",
        )

        items.forEachIndexed { idx, (value, label) ->
            val lx = dp(PAD_DP) + idx * (cardW + gap)
            r.set(lx.toFloat(), y.toFloat(), (lx + cardW).toFloat(), (y + cardH).toFloat())
            c.drawRoundRect(r, radius, radius, paint(GREEN_SOFT))

            val vp = paint(GREEN, 26, bold = true)
            val lp = paint(TEXT_LIGHT, 13)
            val vx = r.centerX() - vp.measureText(value) / 2f
            val vy = r.centerY() - 4f
            val lx2 = r.centerX() - lp.measureText(label) / 2f
            c.drawText(value, vx, vy, vp)
            c.drawText(label, lx2, vy + dp(20).toFloat(), lp)
        }
        return y + cardH
    }

    // ── Day Section ──────────────────────────────────

    private fun drawDaySection(
        c: Canvas,
        day: DiaSemana,
        locales: List<LocalDelDia>,
        y: Int,
        contentW: Int,
    ): Int {
        var cy = y + dp(8)
        val pad = dp(PAD_DP)
        val x0 = pad.toFloat()

        // Day header
        val dayP = paint(GREEN, 14, bold = true)
        c.drawText(day.nombreCompleto.uppercase(Locale.ROOT), x0, cy + dp(6).toFloat(), dayP)
        val dayEnd = pad + dayP.measureText(day.nombreCompleto.uppercase(Locale.ROOT)).toInt() + dp(8)
        // Divider line
        c.drawRoundRect(
            dayEnd.toFloat(), (cy + dp(6) - 0.5f).toFloat(),
            (pad + contentW).toFloat(), (cy + dp(6) + 0.5f).toFloat(),
            0f, 0f, paint(DIVIDER),
        )
        // Count
        val countText = "${locales.size} local${if (locales.size != 1) "es" else ""}"
        c.drawText(countText, (pad + contentW - paint(TEXT_LIGHT, 11).measureText(countText)).toFloat(), (cy + dp(6)).toFloat(), paint(TEXT_LIGHT, 11))
        cy += dp(24)

        for (local in locales) {
            cy = drawStoreCard(c, local, cy, contentW)
            cy += dp(6)
        }
        return cy
    }

    // ── Store Card ───────────────────────────────────

    private fun drawStoreCard(c: Canvas, local: LocalDelDia, y: Int, contentW: Int): Int {
        var cy = y
        val pad = dp(PAD_DP)
        val radius = dp(10).toFloat()
        val cardPad = dp(12)
        val cardW = contentW - dp(0)
        val r = RectF()

        // Calculate card height
        var h = dp(12) * 2  // padding top+bottom
        h += dp(18)  // title row
        if (local.direccion.isNotBlank()) h += dp(16)
        if (local.cadena.isNotBlank() || local.comuna.isNotBlank()) h += dp(14)
        if (local.clientes.isNotEmpty()) {
            h += dp(12) + local.clientes.size * dp(18)
        }

        // Card background
        r.set(pad.toFloat(), cy.toFloat(), (pad + cardW).toFloat(), (cy + h).toFloat())
        c.drawRoundRect(r, radius, radius, paint(CARD_BG))

        cy += cardPad

        // Code + name
        val codeP = paint(TEXT_LIGHT, 11)
        val nameP = paint(TEXT_PRIMARY, 14, bold = true)
        val codeW = codeP.measureText(local.codigo)
        c.drawText(local.codigo, (pad + dp(4)).toFloat(), (cy + dp(6)).toFloat(), codeP)
        c.drawText(
            local.local.ifBlank { "S/N" },
            (pad + dp(4) + codeW + dp(8)).toFloat(),
            (cy + dp(5)).toFloat(),
            nameP,
        )
        cy += dp(18)

        // Address
        if (local.direccion.isNotBlank()) {
            c.drawText(local.direccion.toNaturalCase(), pad + dp(4).toFloat(), (cy + dp(4)).toFloat(), paint(TEXT_SECONDARY, 12))
            cy += dp(16)
        }

        // Chain / comuna
        if (local.cadena.isNotBlank() || local.comuna.isNotBlank()) {
            val meta = buildString {
                if (local.cadena.isNotBlank()) append(local.cadena)
                if (local.comuna.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(local.comuna)
                }
            }
            c.drawText(meta, pad + dp(4).toFloat(), (cy + dp(4)).toFloat(), paint(TEXT_LIGHT, 11))
            cy += dp(14)
        }

        // Brands
        if (local.clientes.isNotEmpty()) {
            cy += dp(4)
            c.drawRoundRect(
                (pad + dp(4)).toFloat(), cy.toFloat(),
                (pad + cardW - dp(4)).toFloat(), (cy + 1).toFloat(),
                0f, 0f, paint(DIVIDER),
            )
            cy += dp(8)

            for (cliente in local.clientes) {
                val starP = paint(ORANGE, 12)
                val nameP2 = if (cliente.esPrioritaria) paint(ORANGE, 12, bold = true) else paint(TEXT_SECONDARY, 12)
                val freqP = paint(TEXT_LIGHT, 10)
                var bx = pad + dp(4).toFloat()

                if (cliente.esPrioritaria) {
                    c.drawText("★", bx, (cy + dp(5)).toFloat(), starP)
                    bx += starP.measureText("★") + dp(4)
                }
                c.drawText(cliente.nombre, bx, (cy + dp(5)).toFloat(), nameP2)
                if (cliente.frecuenciaTexto.isNotBlank()) {
                    val ft = cliente.frecuenciaTexto
                    c.drawText(ft, (pad + cardW - dp(4) - freqP.measureText(ft)).toFloat(), (cy + dp(5)).toFloat(), freqP)
                }
                cy += dp(18)
            }
        }

        return cy - cardPad + dp(10)
    }

    // ── Data helpers (same as before) ────────────────

    private fun groupByDay(entries: List<EntradaRuta>): Map<DiaSemana, List<LocalDelDia>> {
        val grouped = mutableMapOf<DiaSemana, MutableList<LocalDelDia>>()
        for (day in DiaSemana.todos()) {
            val locales = entries
                .filter { isAssignedForDay(it, day) }
                .groupBy { it.codigo.uppercase() + it.local.uppercase() }
                .map { (_, groupEntries) ->
                    val first = groupEntries.first()
                    LocalDelDia(
                        codigo = first.codigo,
                        local = first.local.toNaturalCase(),
                        direccion = first.direccion.toNaturalCase(),
                        cadena = first.cadena,
                        formato = first.formato,
                        region = first.region,
                        comuna = first.comuna,
                        clientes = groupEntries.map { entry ->
                            ClienteInfo(
                                nombre = entry.cliente,
                                esPrioritaria = entry.esPrioritaria,
                                frecuencia = entry.frecuencia,
                            )
                        }.sortedByDescending { it.esPrioritaria },
                    )
                }
                .sortedBy { it.codigo.padStart(6, '0') }
            if (locales.isNotEmpty()) grouped[day] = locales.toMutableList()
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
}
