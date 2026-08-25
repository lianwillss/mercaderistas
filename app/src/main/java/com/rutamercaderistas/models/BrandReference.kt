package com.rutamercaderistas.models

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.rutamercaderistas.R
import com.rutamercaderistas.data.preferences.BrandPagesRepository
import timber.log.Timber
import com.rutamercaderistas.PdfViewerActivity
import com.rutamercaderistas.utils.normalizeMarca
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandReference @Inject constructor(
    private val brandPagesRepository: BrandPagesRepository,
    @ApplicationContext private val appContext: Context,
) {
    companion object {
        val PDF_FILE_NAME get() = PdfDownloader.PDF_FILE_NAME
        const val PAGES_PER_BRAND = 6
    }

    private val brandPages = mapOf(
        "ABEJA DORADA" to 8, "ALUSWEET" to 9, "TAGATOSA" to 10, "ASMODEE" to 11,
        "BAGNO" to 16, "BERRYSUR" to 18, "BESHOS" to 19, "BIGU" to 20,
        "BREDEN MASTER" to 24, "BY MARIA" to 26, "CALIFORNIA" to 27, "CALLAQUI" to 28,
        "CASO Y CIA" to 32, "JUMEX" to 32, "SCRUB" to 32, "SCRUB DADDY" to 32,
        "SUPERZINGS" to 32, "APPLIED NUTRITION" to 32,
        "CINNABON" to 45, "COMERCIAL SZ" to 46, "ETNIKER" to 46,
        "CORRALES DEL SUR" to 47, "CUK" to 51,
        "DEJAPOO" to 56, "DERAIZ" to 57, "DU SOLEIL" to 60,
        "ECOCULTIVA" to 62, "EL GAJO" to 63, "EVERSKIN" to 64,
        "FROZT" to 65, "GLARE" to 66,
        "GLOBAL RETAIL" to 67, "GRANA" to 69,
        "JAPI JANE" to 71, "KOBBO" to 72,
        "LA CABRESA" to 75, "LA FERMENTISTA" to 76,
        "LOVE CO" to 77,
        "MAILEMU" to 79, "MAILEMU MIEL" to 79,
        "MANADA" to 80,
        "MENESS" to 81,
        "MIEL TRAPENSE" to 82, "MIEL TRAPENSES" to 82, "TRAPENSE" to 82, "MORETTA WINES" to 83, "MORETTA" to 83,
        "NAT NATURAL" to 84,
        "OLIMPIA" to 86, "FRANUI" to 86,
        "PAT POT" to 87, "PATPOT CHIPS" to 87, "PEPILU" to 88,
        "PROPAL" to 89,
        "QUINTAL" to 90,
        "SOHO" to 92, "SUK" to 95,
        "TALLOW" to 98, "THE POWER OF FOOD" to 99, "TNOGAL" to 100,
        "UP WINE" to 102,
        "VEG MONKEY" to 103,
        "WANKUN" to 104, "WILD LAMA" to 105
    )

    private val normalizedPages: Map<String, Int> by lazy {
        brandPages.mapKeys { it.key.normalizeMarca() }
    }

    private val brandRanges: Map<String, IntRange> by lazy {
        val sorted = brandPages.entries.sortedBy { it.value }
        val result = mutableMapOf<String, IntRange>()
        for (i in sorted.indices) {
            val (name, start) = sorted[i]
            val end = if (i < sorted.lastIndex) {
                sorted[i + 1].value - 1
            } else {
                start + PAGES_PER_BRAND - 1
            }
            result[name] = start..end.coerceAtLeast(start)
        }
        result
    }

    private val knownBrandStarts: List<Int> by lazy {
        brandPages.values.distinct().sorted()
    }

    private val detectedPages = ConcurrentHashMap<String, Int>()

    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        fallbackScope.launch(Dispatchers.IO) {
            val all = brandPagesRepository.getAll()
            if (all.isNotEmpty()) {
                detectedPages.putAll(all)
            }
            copyPdfFromRaw()
        }
    }

    private fun copyPdfFromRaw() {
        try {
            val pdfFile = File(appContext.filesDir, PDF_FILE_NAME)
            if (pdfFile.exists()) return

            appContext.resources.openRawResource(R.raw.manual_marcas).use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("PDF copiado desde raw: %d bytes", pdfFile.length())

            fallbackScope.launch {
                PdfBrandScanner.prescan(pdfFile)
                Timber.d("Pre-escaneo completo: %d páginas", PdfBrandScanner.cachedPageCount)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error copiando PDF desde raw")
        }
    }

    private suspend fun saveDetectedPage(normalizedName: String, page: Int) {
        detectedPages[normalizedName] = page
        brandPagesRepository.set(normalizedName, page)
    }

    fun getPageForBrand(brandName: String): Int? {
        val norm = brandName.normalizeMarca()
        normalizedPages[norm]?.let { return it }
        return detectedPages[norm]
    }

    fun getPageRange(brandName: String): IntRange? {
        val norm = brandName.normalizeMarca()
        brandRanges.entries.firstOrNull { it.key.normalizeMarca() == norm }?.value?.let { return it }
        detectedPages[norm]?.let { page ->
            val end = knownBrandStarts.firstOrNull { it > page }
                ?.minus(1)
                ?: (page + PAGES_PER_BRAND - 1)
            return page..end.coerceAtLeast(page)
        }
        return null
    }

    fun openPdfForBrand(context: Context, brandName: String) {
        val pdfFile = File(context.filesDir, PDF_FILE_NAME)
        if (!pdfFile.exists()) {
            copyPdfFromRaw()
            if (!pdfFile.exists()) return
        }

        val page = getPageForBrand(brandName)
        if (page != null) {
            abrirPdf(context, pdfFile, brandName, page)
        } else {
            escanearYabrir(context, pdfFile, brandName)
        }
    }

    private fun abrirPdf(context: Context, pdfFile: File, brandName: String, page: Int) {
        try {
            val range = getPageRange(brandName)
            val realPageCount = pdfPageCount(pdfFile)
            val endPage = if (realPageCount > 0) minOf(range?.last ?: page, realPageCount) else range?.last ?: page
            val startPage = if (range != null) page.coerceIn(range.first, endPage) else page.coerceAtLeast(1)
            val lastDocPage = context.getSharedPreferences("pdf_viewer_prefs", Context.MODE_PRIVATE)
                .getInt("last_page_$brandName", -1)
            val resolvedStart = if (lastDocPage >= 0 && lastDocPage in startPage..endPage) {
                lastDocPage
            } else {
                startPage
            }
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", pdfFile.absolutePath)
                putExtra("page_start", resolvedStart)
                putExtra("page_end", endPage)
                putExtra("page_num", startPage)
                putExtra("brand_name", brandName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error abriendo PDF")
        }
    }

    private fun pdfPageCount(pdfFile: File): Int {
        return try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error leyendo páginas del PDF")
            0
        }
    }

    private fun escanearYabrir(context: Context, pdfFile: File, brandName: String) {
        fallbackScope.launch(Dispatchers.IO) {
            try {
                val foundPage = PdfBrandScanner.findBrand(pdfFile, brandName)
                if (foundPage != null) {
                    Timber.i("Marca \"%s\" encontrada en página %d", brandName, foundPage)
                    saveDetectedPage(brandName.normalizeMarca(), foundPage)
                    withContext(Dispatchers.Main) { abrirPdf(context, pdfFile, brandName, foundPage) }
                } else {
                    Timber.w("Marca \"%s\" no encontrada en el PDF", brandName)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error escaneando PDF")
            }
        }
    }
}
