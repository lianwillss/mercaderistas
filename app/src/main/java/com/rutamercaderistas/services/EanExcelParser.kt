package com.rutamercaderistas.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import timber.log.Timber
import java.io.InputStream
import java.text.Normalizer
import javax.inject.Inject

const val EAN_DATA_VERSION = 1

// Prefijo/sufijo de los archivos Excel de catálogo EAN en assets.
// Para agregar más productos basta con soltar otro archivo "ean_*.xlsx"
// en app/src/main/assets/: la app los combina automáticamente al importar.
private const val EAN_ASSET_PREFIX = "ean_"
private const val EAN_ASSET_SUFFIX = ".xlsx"

fun normalizeSearch(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    return normalized.lowercase().trim()
}

class EanExcelParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eanProductDao: EanProductDao,
) {

    // Column indices based on the Excel structure
    private companion object {
        const val COL_COD_CENCOSUD = 0
        const val COL_COD_PROVEEDOR = 1
        const val COL_EAN_PRINCIPAL = 2
        const val COL_DESCRIPCION = 3
        const val COL_UNNAMED_4 = 4
        const val COL_MARCA = 5
        const val COL_UN_BASE = 6
        const val COL_UN_PEDIDO = 7
        const val COL_CONVERSION = 8
        const val COL_ESTADO = 9
        const val COL_CAT_N1_CENCOSUD = 10
        const val COL_CAT_N2_CENCOSUD = 11
        const val COL_CAT_N3_CENCOSUD = 12
        const val COL_CAT_N4_CENCOSUD = 13
        const val COL_CAT_N1_PROVEEDOR = 14
        const val COL_CAT_N2_PROVEEDOR = 15
        const val COL_CAT_N3_PROVEEDOR = 16
        const val COL_CAT_N4_PROVEEDOR = 17
        const val COL_CODIGO_BARRA = 18
    }

    suspend fun parseAndSave(inputStream: InputStream): Result<Int> {
        return try {
            val products = parse(inputStream)
            if (products.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(products)
            }
            Result.success(products.size)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing EAN Excel file")
            Result.failure(e)
        }
    }

    private fun parse(inputStream: InputStream): List<EanProductEntity> {
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        val products = mutableListOf<EanProductEntity>()

        var rowNum = 0
        for (row in sheet) {
            rowNum++
            if (rowNum == 1) continue // Skip header row

            val product = parseRow(row)
            if (product != null) {
                products.add(product)
            }
        }

        workbook.close()
        return products
    }

    private fun parseRow(row: Row): EanProductEntity? {
        val codCencosud = getStringCellValue(row.getCell(COL_COD_CENCOSUD))
        val codProveedor = getStringCellValue(row.getCell(COL_COD_PROVEEDOR))
        val eanPrincipal = getStringCellValue(row.getCell(COL_EAN_PRINCIPAL))
        val descripcion = getStringCellValue(row.getCell(COL_DESCRIPCION))
        val marca = getStringCellValue(row.getCell(COL_MARCA))
        val unBase = getStringCellValue(row.getCell(COL_UN_BASE))
        val unPedido = getStringCellValue(row.getCell(COL_UN_PEDIDO))
        val conversion = getStringCellValue(row.getCell(COL_CONVERSION))
        val estado = getStringCellValue(row.getCell(COL_ESTADO))
        val catN1Cencosud = getStringCellValue(row.getCell(COL_CAT_N1_CENCOSUD))
        val catN2Cencosud = getStringCellValue(row.getCell(COL_CAT_N2_CENCOSUD))
        val catN3Cencosud = getStringCellValue(row.getCell(COL_CAT_N3_CENCOSUD))
        val catN4Cencosud = getStringCellValue(row.getCell(COL_CAT_N4_CENCOSUD))
        val catN1Proveedor = getStringCellValue(row.getCell(COL_CAT_N1_PROVEEDOR))
        val catN2Proveedor = getStringCellValue(row.getCell(COL_CAT_N2_PROVEEDOR))
        val catN3Proveedor = getStringCellValue(row.getCell(COL_CAT_N3_PROVEEDOR))
        val catN4Proveedor = getStringCellValue(row.getCell(COL_CAT_N4_PROVEEDOR))
        val codigoBarra = getStringCellValue(row.getCell(COL_CODIGO_BARRA))

        // Skip rows with no meaningful data
        if (eanPrincipal.isBlank() && codCencosud.isBlank() && codigoBarra.isBlank() && descripcion.isBlank()) {
            return null
        }

        return EanProductEntity(
            codCencosud = codCencosud,
            codProveedor = codProveedor,
            eanPrincipal = eanPrincipal,
            descripcionProducto = descripcion,
            descripcionNorm = normalizeSearch(descripcion),
            marca = marca,
            marcaNorm = normalizeSearch(marca),
            unBase = unBase,
            unPedido = unPedido,
            conversion = conversion,
            estado = estado,
            catN1Cencosud = catN1Cencosud,
            catN2Cencosud = catN2Cencosud,
            catN3Cencosud = catN3Cencosud,
            catN4Cencosud = catN4Cencosud,
            catN1Proveedor = catN1Proveedor,
            catN2Proveedor = catN2Proveedor,
            catN3Proveedor = catN3Proveedor,
            catN4Proveedor = catN4Proveedor,
            codigoBarra = codigoBarra,
        )
    }

    private fun getStringCellValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                // Handle numeric cells (codes that might be stored as numbers)
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.stringCellValue.trim()
            else -> ""
        }.also { it.trim() }
    }

    // Cargar desde assets (todos los archivos "ean_*.xlsx" se combinan)
    suspend fun loadFromAssets(): Result<Int> {
        return try {
            val assetFiles = (context.assets.list("") ?: emptyArray())
                .filter { it.startsWith(EAN_ASSET_PREFIX) && it.endsWith(EAN_ASSET_SUFFIX) }
                .sorted()
            if (assetFiles.isEmpty()) {
                return Result.failure(IllegalStateException("No se encontraron archivos EAN en assets"))
            }
            val all = mutableListOf<EanProductEntity>()
            for (file in assetFiles) {
                context.assets.open(file).use { stream ->
                    all.addAll(parse(stream))
                }
            }
            if (all.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(all)
            }
            Result.success(all.size)
        } catch (e: Exception) {
            Timber.e(e, "Error loading EAN file from assets")
            Result.failure(e)
        }
    }

    // Cargar desde archivo descargado
    suspend fun loadFromFile(filePath: String): Result<Int> {
        return try {
            val inputStream = java.io.FileInputStream(filePath)
            parseAndSave(inputStream)
        } catch (e: Exception) {
            Timber.e(e, "Error loading EAN file from path: $filePath")
            Result.failure(e)
        }
    }

    private fun prefs() = context.getSharedPreferences("ean_catalog_prefs", Context.MODE_PRIVATE)

    fun getEanDataVersion(): Int = prefs().getInt("ean_data_version", 0)

    fun setEanDataVersion(version: Int) {
        prefs().edit().putInt("ean_data_version", version).apply()
    }
}