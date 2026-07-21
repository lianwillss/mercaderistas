package com.rutamercaderistas.models

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rutamercaderistas.utils.normalizeMarca
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

object PdfBrandScanner {

    private var recognizer: TextRecognizer? = null
    private val pageTextCache = mutableMapOf<Int, String>()
    private var cachedPdfPath: String? = null
    private var pdfPageCount: Int = 0

    val cachedPageCount: Int get() = pageTextCache.size

    fun clearCache() {
        pageTextCache.clear()
        cachedPdfPath = null
        pdfPageCount = 0
    }

    private fun getPdfPageCount(pdfFile: File): Int {
        if (pdfPageCount > 0 && cachedPdfPath == pdfFile.absolutePath) return pdfPageCount
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                pdfPageCount = renderer.pageCount
                cachedPdfPath = pdfFile.absolutePath
            }
        }
        return pdfPageCount
    }

    suspend fun findBrand(pdfFile: File, brandName: String): Int? {
        val brandNorm = brandName.normalizeMarca()
        val totalPages = if (cachedPdfPath == pdfFile.absolutePath && pdfPageCount > 0)
            pdfPageCount else getPdfPageCount(pdfFile)

        for (pageIdx in 0 until pageTextCache.size) {
            val text = pageTextCache[pageIdx] ?: continue
            if (text.normalizeMarca().contains(brandNorm)) {
                Timber.i("Encontrado en caché: \"%s\" en página %d", brandName, pageIdx + 1)
                return pageIdx + 1
            }
        }

        Timber.i("\"%s\" no está en caché. Re-escaneando...", brandName)
        return withContext(Dispatchers.Default) {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (pageIdx in 0 until totalPages) {
                        val text = recognizePage(renderer, pageIdx, widthPx = 300)
                        pageTextCache[pageIdx] = text
                        cachedPdfPath = pdfFile.absolutePath
                        if (text.normalizeMarca().contains(brandNorm)) {
                            Timber.i("\"%s\" encontrado en página %d (re-escaneo)", brandName, pageIdx + 1)
                            return@use pageIdx + 1
                        }
                    }
                    Timber.w("\"%s\" no encontrado en el PDF", brandName)
                    null
                }
            }
        }
    }

    suspend fun prescan(pdfFile: File) = withContext(Dispatchers.Default) {
        if (cachedPdfPath == pdfFile.absolutePath && pageTextCache.isNotEmpty()) return@withContext
        cachedPdfPath = pdfFile.absolutePath
        pageTextCache.clear()
        pdfPageCount = 0

        Timber.i("Pre-escaneo iniciado...")
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                pdfPageCount = renderer.pageCount
                for (pageIdx in 0 until pdfPageCount) {
                    pageTextCache[pageIdx] = recognizePage(renderer, pageIdx)
                }
            }
        }
        Timber.i("Pre-escaneo completado: %d páginas", cachedPageCount)
    }

    private fun getRecognizer(): TextRecognizer {
        val existing = recognizer
        if (existing != null) return existing
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also {
            recognizer = it
        }
    }

    fun release() {
        clearCache()
        recognizer?.close()
        recognizer = null
    }

    private fun recognizePage(renderer: PdfRenderer, pageIdx: Int, widthPx: Int = 300): String {
        val page = renderer.openPage(pageIdx)
        try {
            val scale = widthPx.toFloat() / page.width
            val w = widthPx
            val h = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val task = getRecognizer().process(inputImage)
            return try {
                com.google.android.gms.tasks.Tasks.await(task).text
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.close()
        }
    }

}
