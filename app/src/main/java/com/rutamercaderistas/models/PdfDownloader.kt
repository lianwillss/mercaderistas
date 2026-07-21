package com.rutamercaderistas.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfDownloader {
    const val PDF_FILE_NAME = "manual_marcas.pdf"

    fun isPdfValid(file: File): Boolean {
        try {
            if (!file.exists() || file.length() < 1024) return false
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    return renderer.pageCount > 0
                }
            }
        } catch (e: Exception) {
            file.delete()
            return false
        }
    }

    suspend fun generateThumbnails(context: Context, brandRanges: Map<String, IntRange>, pdfTotalPages: Int, force: Boolean = false) {
        try {
            val pdfFile = File(context.filesDir, PDF_FILE_NAME)
            if (!pdfFile.exists()) return

            val dir = File(context.filesDir, "thumbnails")
            if (force) dir.deleteRecursively()
            dir.mkdirs()

            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for ((brandName, range) in brandRanges) {
                        val endPage = minOf(range.last, pdfTotalPages)
                        for (pageNum in range.first..endPage) {
                            val thumbFile = File(dir, "${brandName}_p$pageNum.png")
                            if (thumbFile.exists()) continue

                            val page = renderer.openPage(pageNum - 1)
                            try {
                                val scale = 120f / page.width
                                val thumbW = 120
                                val thumbH = (page.height * scale).toInt()
                                val bitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                                }
                                bitmap.recycle()
                            } finally {
                                page.close()
                            }
                        }
                    }
                }
            }
            Timber.d("Thumbnails generados para %d marcas", brandRanges.size)
        } catch (e: Exception) {
            Timber.e(e, "Error generando thumbnails")
        }
    }
}
