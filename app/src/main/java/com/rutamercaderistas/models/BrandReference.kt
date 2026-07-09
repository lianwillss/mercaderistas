package com.rutamercaderistas.models

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.rutamercaderistas.PdfViewerActivity
import com.rutamercaderistas.utils.normalizeMarca
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object BrandReference {

    val PDF_FILE_NAME get() = PdfDownloader.PDF_FILE_NAME

    private val TAG = "BrandReference"

    const val PAGES_PER_BRAND = 6

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

    private var prefs: SharedPreferences? = null
    private val detectedPages = mutableMapOf<String, Int>()
    private val lastTapTime = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("brand_pages", Context.MODE_PRIVATE)
        detectedPages.clear()
        prefs?.all?.forEach { (key, value) ->
            if (value is Int) detectedPages[key] = value
        }
        Log.d(TAG, "init: ${detectedPages.size} marcas detectadas cargadas")
    }

    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun scope(context: Context): CoroutineScope {
        return (context as? AppCompatActivity)?.lifecycleScope ?: fallbackScope
    }

    private fun Context.snackbar(msg: String) {
        if (this is AppCompatActivity) {
            findViewById<android.view.View>(android.R.id.content)?.let { root ->
                Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun isDebounced(brandName: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastTapTime[brandName.normalizeMarca()] ?: 0L
        if (now - last < 2000) return true
        lastTapTime[brandName.normalizeMarca()] = now
        return false
    }

    private fun saveDetectedPage(normalizedName: String, page: Int) {
        detectedPages[normalizedName] = page
        prefs?.edit()?.putInt(normalizedName, page)?.apply()
    }

    fun getThumbnailFile(context: Context, brandName: String): File? {
        val clean = brandName.normalizeMarca()
        val page = getPageForBrand(brandName)
        val file = if (page != null) {
            File(File(context.filesDir, "thumbnails"), "${clean}_p${page}.png")
        } else null
        return if (file?.exists() == true) file else null
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

    fun descargarPdf(context: Context, callback: ((Boolean) -> Unit)? = null, onProgress: ((Int) -> Unit)? = null) {
        scope(context).launch(Dispatchers.IO) {
            try {
                val success = PdfDownloader.downloadPdf(context, onProgress)
                if (!success) {
                    withContext(Dispatchers.Main) { callback?.invoke(false) }
                    return@launch
                }

                val pdfFile = File(context.filesDir, PdfDownloader.PDF_FILE_NAME)
                try {
                    PdfBrandScanner.prescan(pdfFile)
                    Log.d(TAG, "Pre-escaneo completo: ${PdfBrandScanner.cachedPageCount} páginas")
                } catch (e: Exception) {
                    Log.w(TAG, "Error en pre-escaneo del PDF", e)
                }

                withContext(Dispatchers.Main) { callback?.invoke(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando PDF", e)
                withContext(Dispatchers.Main) { callback?.invoke(false) }
            }
        }
    }

    fun openPdfForBrand(context: Context, brandName: String) {
        if (isDebounced(brandName)) return

        val pdfFile = File(context.filesDir, PDF_FILE_NAME)
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            context.snackbar("Descargando manual... presiona de nuevo")
            descargarPdf(context, callback = { success ->
                if (success) {
                    context.snackbar("✅ Manual descargado, tócala de nuevo")
                } else {
                    context.snackbar("Error al descargar el manual")
                }
            })
            return
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
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", pdfFile.absolutePath)
                putExtra("page_start", range?.first ?: page)
                putExtra("page_end", range?.last ?: page)
                putExtra("page_num", range?.first ?: page)
                putExtra("brand_name", brandName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo PDF", e)
            context.snackbar("Error al abrir el manual")
        }
    }

    private fun escanearYabrir(context: Context, pdfFile: File, brandName: String) {
        scope(context).launch(Dispatchers.IO) {
            try {
                val foundPage = PdfBrandScanner.findBrand(pdfFile, brandName)
                if (foundPage != null) {
                    Log.i(TAG, "Marca \"$brandName\" encontrada en página $foundPage")
                    saveDetectedPage(brandName.normalizeMarca(), foundPage)
                    withContext(Dispatchers.Main) { abrirPdf(context, pdfFile, brandName, foundPage) }
                } else {
                    Log.w(TAG, "Marca \"$brandName\" no encontrada en el PDF")
                    withContext(Dispatchers.Main) { context.snackbar("Marca no encontrada") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error escaneando PDF", e)
                withContext(Dispatchers.Main) { context.snackbar("Error al buscar \"$brandName\"") }
            }
        }
    }
}
