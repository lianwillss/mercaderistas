package com.rutamercaderistas.models

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

object PdfRendererCache {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    val lock = Any()

    private const val TAG = "PdfRendererCache"

    fun init(pdfPath: String) {
        synchronized(lock) {
            close()
            val file = File(pdfPath)
            if (!file.exists()) {
                Log.w(TAG, "INIT_FAILED: $pdfPath no existe")
                return
            }
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = pfd?.let { PdfRenderer(it) }
            Log.d(TAG, "INIT_OK: ${renderer?.pageCount} págs en $pdfPath")
        }
    }

    fun get(): PdfRenderer? = synchronized(lock) { renderer }

    fun getPageCount(): Int = synchronized(lock) { renderer?.pageCount ?: 0 }

    fun close() {
        synchronized(lock) {
            renderer?.close()
            renderer = null
            pfd?.close()
            pfd = null
        }
    }
}
