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
}
