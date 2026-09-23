package com.rutamercaderistas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File

object ShareImageGenerator {

    fun generateForEanProduct(
        context: Context,
        productName: String,
        ean: String,
    ): File {
        val width = 1080
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val digits = ean.filter(Char::isDigit)
        val barcode = BarcodeEncoder().encodeBitmap(
            digits,
            if (digits.length == 13 && digits[0] != '0') BarcodeFormat.EAN_13 else if (digits.length == 12) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128,
            960,
            300,
        )

        canvas.drawColor(Color.parseColor("#F7F9FC"))
        paint.color = Color.WHITE
        canvas.drawRoundRect(36f, 36f, width - 36f, height - 36f, 36f, 36f, paint)

        paint.color = Color.parseColor("#1976D2")
        canvas.drawRoundRect(36f, 36f, width - 36f, 56f, 10f, 10f, paint)

        paint.color = Color.parseColor("#17202A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 54f
        paint.textAlign = Paint.Align.CENTER
        val titleLines = wrapText(productName.ifBlank { digits }, paint, width - 140)
        titleLines.take(2).forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, 170f + index * 66f, paint)
        }

        val barcodeTop = if (titleLines.size > 1) 310f else 245f
        canvas.drawBitmap(barcode, null, Rect(60, barcodeTop.toInt(), width - 60, (barcodeTop + 300).toInt()), paint)

        paint.color = Color.parseColor("#17202A")
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 38f
        canvas.drawText(digits, width / 2f, barcodeTop + 360f, paint)

        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val file = File(dir, "ean_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        barcode.recycle()
        bitmap.recycle()
        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines.add(current)
                current = word
            }
        }
        if (current.isNotBlank()) lines.add(current)
        return lines.ifEmpty { listOf(text.take(28)) }
    }

    fun generateForLocal(
        context: Context,
        localName: String,
        address: String,
        comuna: String,
        marcas: List<String>,
        promosByBrand: Map<String, Int> = emptyMap(),
    ): File {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fondo blanco
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header azul
        paint.color = Color.parseColor("#1976D2")
        canvas.drawRect(0f, 0f, width.toFloat(), 180f, paint)

        // Título local
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        val title = if (localName.isBlank()) "S/N" else localName.take(28)
        canvas.drawText(title, 40f, 90f, paint)

        // Dirección
        paint.textSize = 30f
        paint.typeface = Typeface.DEFAULT
        val addr = buildString {
            if (address.isNotBlank()) append(address)
            if (comuna.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append(comuna)
            }
        }.take(50)
        if (addr.isNotBlank()) {
            canvas.drawText(addr, 40f, 140f, paint)
        }

        // Mapa placeholder (gris claro con grilla y pin)
        val mapTop = 180f
        val mapHeight = 500f
        paint.color = Color.parseColor("#E3F2FD")
        canvas.drawRect(0f, mapTop, width.toFloat(), mapTop + mapHeight, paint)

        // Grilla suave
        paint.color = Color.parseColor("#BBDEFB")
        paint.strokeWidth = 2f
        for (x in 0..width step 120) {
            canvas.drawLine(x.toFloat(), mapTop, x.toFloat(), mapTop + mapHeight, paint)
        }
        for (y in 0..mapHeight.toInt() step 120) {
            canvas.drawLine(0f, mapTop + y, width.toFloat(), mapTop + y, paint)
        }

        // Pin central
        val pinX = width / 2f
        val pinY = mapTop + mapHeight / 2f
        // Sombra pin
        paint.color = Color.parseColor("#1976D2")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pinX, pinY, 28f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(pinX, pinY, 12f, paint)
        // Número 1
        paint.color = Color.parseColor("#1976D2")
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("1", pinX, pinY + 8f, paint)

        // Tabla marcas
        var y = mapTop + mapHeight + 60f
        paint.color = Color.parseColor("#212121")
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Marcas (${marcas.size}):", 40f, y, paint)
        y += 12f

        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        for ((index, marca) in marcas.take(12).withIndex()) {
            y += 42f
            if (y > height - 80) break
            val promoCount = promosByBrand[marca] ?: 0
            val line = if (promoCount > 0) "• $marca  🔥 $promoCount promo${if (promoCount > 1) "s" else ""}" else "• $marca"
            // Truncar si muy largo
            val display = if (line.length > 42) line.take(42) + "…" else line
            paint.color = if (promoCount > 0) Color.parseColor("#D32F2F") else Color.parseColor("#424242")
            canvas.drawText(display, 40f, y, paint)
        }

        if (marcas.size > 12) {
            y += 42f
            paint.color = Color.parseColor("#757575")
            paint.textSize = 26f
            canvas.drawText("… y ${marcas.size - 12} más", 40f, y, paint)
        }

        // Footer
        paint.color = Color.parseColor("#757575")
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Ruta Mercaderistas • ${java.time.LocalDate.now()}", width / 2f, height - 30f, paint)

        // Guardar
        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()
        return file
    }
}
