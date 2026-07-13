package com.rutamercaderistas.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val EXCEL_FILE_NAME = "master_rutero.xlsx"
    }

    private val excelFile: File by lazy { File(context.filesDir, EXCEL_FILE_NAME) }

    fun excelExists(): Boolean = excelFile.exists()

    fun excelLastModified(): Long = excelFile.lastModified()
}
