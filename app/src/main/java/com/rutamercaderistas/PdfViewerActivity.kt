package com.rutamercaderistas

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rutamercaderistas.ui.screens.PdfViewerScreen
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import java.io.File

private const val PREFS_NAME = "pdf_viewer_prefs"

class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pdfPath = intent.getStringExtra("pdf_path")
        val brandName = intent.getStringExtra("brand_name")
        val pageStart = intent.getIntExtra("page_start", 1) - 1
        val pageEnd = intent.getIntExtra("page_end", 1) - 1
        val pdfFile = if (pdfPath != null) File(pdfPath) else null

        setContent {
            MercaderistasTheme {
                PdfViewerScreen(
                    pdfFile = pdfFile,
                    brandName = brandName,
                    pageStart = pageStart.coerceAtLeast(0),
                    pageEnd = pageEnd.coerceAtLeast(pageStart),
                    onClose = { finish() },
                    onPageChanged = { docPage ->
                        brandName?.let { saveLastPage(it, docPage) }
                    },
                )
            }
        }
    }

    private fun saveLastPage(brandName: String, docPage: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("last_page_$brandName", docPage)
            .apply()
    }
}
