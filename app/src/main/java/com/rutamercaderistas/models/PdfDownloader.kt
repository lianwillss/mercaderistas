package com.rutamercaderistas.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object PdfDownloader {
    const val PDF_FILE_NAME = "manual_marcas.pdf"
    const val PDF_DRIVE_ID = "1AeWTubwXIRyQYng6dK0xa53L1vMJAudj"
    val PDF_DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=$PDF_DRIVE_ID&confirm=t"

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    suspend fun downloadPdf(context: Context, onProgress: ((Int) -> Unit)? = null): Boolean {
        val pdfFile = File(context.filesDir, PDF_FILE_NAME)
        return try {
            pdfFile.delete()
            val request = Request.Builder().url(PDF_DOWNLOAD_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("Error HTTP %d descargando PDF", response.code)
                    pdfFile.delete()
                    return false
                }

                val body = response.body ?: run {
                    pdfFile.delete()
                    return false
                }
                val contentType = response.header("Content-Type", "")
                Timber.d("Content-Type: %s", contentType)

                body.byteStream().use { input ->
                    // Validar firma %PDF con los primeros 4 bytes
                    val magic = ByteArray(4)
                    var magicRead = 0
                    while (magicRead < 4) {
                        val n = input.read(magic, magicRead, 4 - magicRead)
                        if (n == -1) {
                            pdfFile.delete()
                            return false
                        }
                        magicRead += n
                    }
                    if (magic[0] != 0x25.toByte() || magic[1] != 0x50.toByte() ||
                        magic[2] != 0x44.toByte() || magic[3] != 0x46.toByte()) {
                        Timber.e("No es un PDF válido (sin firma %PDF): " + String(magic))
                        pdfFile.delete()
                        return false
                    }

                    FileOutputStream(pdfFile).use { output ->
                        output.write(magic, 0, 4)
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 4
                        var bytes: Int
                        var lastReportedPct = -1
                        val totalBytes = body.contentLength()
                        val minPctDelta = if (totalBytes > 0 && totalBytes < 500_000) 5 else 2

                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            bytesRead += bytes
                            if (totalBytes > 0) {
                                val pct = ((bytesRead * 100) / totalBytes).toInt()
                                if (pct >= lastReportedPct + minPctDelta) {
                                    lastReportedPct = pct
                                    // progress from IO thread via Handler to avoid withContext overhead
                                    mainHandler.post { onProgress?.invoke(pct) }
                                }
                            }
                        }
                    }
                }
            }
            if (pdfFile.length() == 0L) {
                pdfFile.delete()
                return false
            }
            Timber.d("PDF descargado: %d bytes", pdfFile.length())
            mainHandler.post { onProgress?.invoke(100) }
            true
        } catch (e: Exception) {
            Timber.e(e, "Error descargando PDF")
            pdfFile.delete()
            false
        }
    }

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
