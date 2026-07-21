package com.rutamercaderistas.models

import android.content.Context
import android.content.Intent
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
        "ABEJA DORADA" to 8, "ALUSWEET" to 9, "ASMODEE" to 11,
        "BAGNO" to 17, "BERRYSUR" to 20, "BESHOS" to 19,
        "BIGU" to 21, "BREDEN MASTER" to 25, "BY MARIA" to 27,
        "CALIFORNIA" to 28, "CALLAQUI" to 29,
        "CASO Y CIA" to 33, "CORRALES DEL SUR" to 39, "CUK" to 45,
        "DEJAPOO" to 49, "DERAIZ" to 50, "DU SOLEIL" to 53,
        "ECOCULTIVA" to 55, "EL GAJO" to 56, "EVERSKIN" to 57,
        "GLOBAL RETAIL" to 58, "GRANA" to 60,
        "KOMBUCHACHA" to 62,
        "LA CABRESA" to 64, "LA FERMENTISTA" to 65,
        "LOVE CO" to 66, "LUTHIER" to 68,
        "MAILEMU MIEL" to 69, "MENESS" to 70,
        "MIEL TRAPENSE" to 73, "MIEL TRAPENSES" to 73,
        "NAT NATURAL" to 71, "NUTRIPOP" to 74,
        "PATPOT CHIPS" to 75, "PEPILU" to 76,
        "PROMERCO" to 77, "PROPAL" to 80,
        "QUINTAL" to 81,
        "SOHO" to 83, "SUK" to 86,
        "TASTY FREE" to 89, "TNOGAL" to 91,
        "VEG MONKEY" to 93,
        "WANKUN" to 95, "WILD LAMA" to 96,
        "YEET POWER DRINK" to 99
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
        detectedPages[norm]?.let { page -> return page..(page + PAGES_PER_BRAND - 1) }
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
            val norm = brandName.normalizeMarca()
            val lastDocPage = context.getSharedPreferences("pdf_viewer_prefs", Context.MODE_PRIVATE)
                .getInt("last_page_$brandName", -1)
            val startPage = if (lastDocPage >= 0 && range != null && lastDocPage in range) {
                lastDocPage
            } else {
                range?.first ?: page
            }
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", pdfFile.absolutePath)
                putExtra("page_start", startPage)
                putExtra("page_end", range?.last ?: page)
                putExtra("page_num", range?.first ?: page)
                putExtra("brand_name", brandName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error abriendo PDF")
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
