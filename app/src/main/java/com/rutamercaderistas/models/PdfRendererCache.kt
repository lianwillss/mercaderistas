package com.rutamercaderistas.models

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
import kotlin.comparisons.minOf

object PdfRendererCache {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val lock = Any()

    fun init(pdfPath: String): Boolean {
        synchronized(lock) {
            close()
            val file = File(pdfPath)
            if (!file.exists()) {
                Timber.w("INIT_FAILED: %s no existe", pdfPath)
                return false
            }
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = pfd?.let { PdfRenderer(it) }
                val ok = renderer != null
                Timber.d("INIT_OK: %d págs en %s", renderer?.pageCount, pdfPath)
                return ok
            } catch (e: Exception) {
                Timber.e(e, "INIT_FAILED: PDF corrupto o inválido")
                close()
                return false
            }
        }
    }

    fun get(): PdfRenderer? = synchronized(lock) { renderer }

    fun getPageCount(): Int = synchronized(lock) { renderer?.pageCount ?: 0 }

    fun renderBitmap(pageNum: Int, maxPixels: Int = 1200): Bitmap? = synchronized(lock) {
        val r = renderer ?: return@synchronized null
        if (pageNum - 1 >= r.pageCount) return@synchronized null
        val page = r.openPage(pageNum - 1)
        try {
            val scale = minOf(maxPixels.toFloat() / page.width, maxPixels.toFloat() / page.height, 1f)
            val w = (page.width * scale).toInt()
            val h = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    fun close() {
        synchronized(lock) {
            renderer?.close()
            renderer = null
            pfd?.close()
            pfd = null
        }
    }
}
