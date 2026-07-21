package com.rutamercaderistas.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rutamercaderistas.R
import org.junit.Rule
import org.junit.Test
import java.io.File

class PdfViewerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsError_whenPdfFileIsNull() {
        composeTestRule.setContent {
            PdfViewerScreen(
                pdfFile = null,
                brandName = null,
                pageStart = 0,
                pageEnd = 0,
                onClose = {},
            )
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.pdf_no_disponible)
        ).assertIsDisplayed()
    }

    @Test
    fun showsError_whenPdfFileDoesNotExist() {
        composeTestRule.setContent {
            PdfViewerScreen(
                pdfFile = File("/no/exist/file.pdf"),
                brandName = null,
                pageStart = 0,
                pageEnd = 0,
                onClose = {},
            )
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.pdf_archivo_no_encontrado)
        ).assertIsDisplayed()
    }

    @Test
    fun showsBrandName_whenProvided() {
        composeTestRule.setContent {
            PdfViewerScreen(
                pdfFile = null,
                brandName = "Marca Test",
                pageStart = 0,
                pageEnd = 0,
                onClose = {},
            )
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.pdf_no_disponible)
        ).assertIsDisplayed()
    }
}
