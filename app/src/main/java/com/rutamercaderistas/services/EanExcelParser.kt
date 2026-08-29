package com.rutamercaderistas.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import timber.log.Timber
import java.io.InputStream
import java.text.Normalizer
import javax.inject.Inject

const val EAN_DATA_VERSION = 11

// Prefijo/sufijo de los archivos Excel de catálogo EAN en assets.
// Para agregar más productos basta con soltar otro archivo "ean*.xlsx"
// (p. ej. "ean loveco.xlsx", "ean_otromarca.xlsx") en app/src/main/assets/:
// la app los combina automáticamente al importar.
private const val EAN_ASSET_PREFIX = "ean"
private const val EAN_ASSET_SUFFIX = ".xlsx"

// Marca por defecto según el nombre del archivo de origen (p. ej. "ean_loveco.xlsx"
// → "Love Co") cuando el Excel no trae columna de marca. Permite que los productos
// recién agregados figuren bajo su marca real en la búsqueda EAN.
private val EAN_FILE_BRANDS = mapOf(
    "loveco" to "Love Co",
    "caso_cia" to "CASO Y CIA",
    "nat" to "NAT NATURAL",
)

// Alias de marca: la empresa ve algunas marcas con un nombre distinto al del
// catálogo (p. ej. "Lola" → "Kobbo", siendo Kobbo su representación superior).
// Se aplica al importar para que agrupen y se muestren con el nombre correcto.
private val BRAND_ALIASES = mapOf(
    "lola" to "Kobbo",
)

// Nota aclaratoria para marcas canónicas (p. ej. "Kobbo" es la representación
// superior de "Lola Cosmetic"). Se muestra bajo el nombre en la interfaz EAN.
private val BRAND_CANONICAL_NOTES = mapOf(
    "kobbo" to "Lola Cosmetic",
)

fun brandNote(canonical: String): String? = BRAND_CANONICAL_NOTES[normalizeSearch(canonical)]

private fun brandFromFilename(fileName: String): String {
    val base = fileName.removePrefix(EAN_ASSET_PREFIX)
        .removePrefix("_")
        .removeSuffix(EAN_ASSET_SUFFIX)
        .trim()
        .lowercase()
    return EAN_FILE_BRANDS[base]
        ?: base.replace("_", " ").replaceFirstChar { it.uppercase() }.trim()
}

fun normalizeSearch(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    return normalized.lowercase().trim()
}

// Normalización compacta: igual que normalizeSearch pero sin espacios ni
// signos, para que "bymaria" coincida con "by maria" y viceversa.
fun compactNorm(text: String): String =
    normalizeSearch(text).replace(Regex("[^a-z0-9]"), "")

class EanExcelParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eanProductDao: EanProductDao,
) {

    // Mapea columnas por NOMBRE de encabezado (no por posición fija), porque
    // distintos archivos "ean*.xlsx" pueden tener distinto orden de columnas.
    private data class ColumnMap(
        val codCencosud: Int? = null,
        val codProveedor: Int? = null,
        val eanPrincipal: Int? = null,
        val descripcion: Int? = null,
        val marca: Int? = null,
        val unBase: Int? = null,
        val unPedido: Int? = null,
        val conversion: Int? = null,
        val estado: Int? = null,
        val codigoBarra: Int? = null,
        val catCencosud: List<Int?> = listOf(null, null, null, null),
        val catProveedor: List<Int?> = listOf(null, null, null, null),
    )

    suspend fun parseAndSave(inputStream: InputStream): Result<Int> {
        return try {
            val products = parse(inputStream)
            val deduped = dedupe(products)
            if (deduped.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(deduped)
            }
            Result.success(deduped.size)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing EAN Excel file")
            Result.failure(e)
        }
    }

    // Evita productos duplicados al combinar varios archivos "ean*.xlsx" (o
    // dentro de uno solo). La clave es el EAN; si el EAN está vacío se usa el
    // SKU Cencosud. Los productos sin ninguno de los dos no se deduplican.
    private fun dedupe(products: List<EanProductEntity>): List<EanProductEntity> {
        val seen = mutableSetOf<String>()
        return products.filter { p ->
            val key = p.eanPrincipal.ifBlank { p.codCencosud }
            if (key.isBlank()) return@filter true
            seen.add(key)
        }
    }

    private fun parse(inputStream: InputStream, defaultBrand: String? = null): List<EanProductEntity> {
        val workbook = XSSFWorkbook(inputStream)
        val sheet = selectSheet(workbook)
        val rows = sheet.iterator()
        if (!rows.hasNext()) {
            workbook.close()
            return emptyList()
        }
        val columnMap = buildColumnMap(rows.next())
        val products = mutableListOf<EanProductEntity>()
        while (rows.hasNext()) {
            val product = parseRow(rows.next(), columnMap, defaultBrand)
            if (product != null) products.add(product)
        }
        workbook.close()
        return products
    }

    // Algunos catálogos traen varias hojas (p. ej. un resumen "DT" y los datos
    // reales en "DATA"). Elegimos la primera hoja cuyo encabezado declara una
    // columna de código (EAN o Código de Barra); sin consumir su fila (para no
    // descartarla al iterar después). No basta con "marca", pues una hoja-resumen
    // puede tener una fila "Marca: X" que no es encabezado de catálogo.
    private fun selectSheet(workbook: XSSFWorkbook): Sheet {
        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val firstRow = sheet.getRow(sheet.firstRowNum) ?: continue
            val map = buildColumnMap(firstRow)
            if (map.eanPrincipal != null || map.codigoBarra != null) return sheet
        }
        return workbook.getSheetAt(0)
    }

    private fun buildColumnMap(headerRow: Row): ColumnMap {
        var codCencosud: Int? = null
        var codProveedor: Int? = null
        var eanPrincipal: Int? = null
        var descripcion: Int? = null
        var marca: Int? = null
        var unBase: Int? = null
        var unPedido: Int? = null
        var conversion: Int? = null
        var estado: Int? = null
        var codigoBarra: Int? = null
        val catCencosud = mutableListOf<Int?>(null, null, null, null)
        val catProveedor = mutableListOf<Int?>(null, null, null, null)

        for (cell in headerRow) {
            val col = cell.columnIndex
            val h = normalizeSearch(getStringCellValue(cell))
            when {
                "cat" in h -> {
                    val level = Regex("""\d""").find(h)?.value?.toIntOrNull()
                    if (level != null && level in 1..4) {
                        if ("proveedor" in h) catProveedor[level - 1] = col
                        else catCencosud[level - 1] = col
                    }
                }
                "barra" in h -> codigoBarra = col
                "cencosud" in h -> codCencosud = col
                "proveedor" in h -> codProveedor = col
                "marca" in h -> marca = col
                "estado" in h -> estado = col
                "conversion" in h || "convers" in h -> conversion = col
                "pedido" in h -> unPedido = col
                "base" in h -> unBase = col
                "descrip" in h -> descripcion = col
                "ean" in h -> eanPrincipal = col
            }
        }
        return ColumnMap(
            codCencosud = codCencosud,
            codProveedor = codProveedor,
            eanPrincipal = eanPrincipal,
            descripcion = descripcion,
            marca = marca,
            unBase = unBase,
            unPedido = unPedido,
            conversion = conversion,
            estado = estado,
            codigoBarra = codigoBarra,
            catCencosud = catCencosud,
            catProveedor = catProveedor,
        )
    }

    private fun parseRow(row: Row, map: ColumnMap, defaultBrand: String? = null): EanProductEntity? {
        val codCencosud = map.codCencosud?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val codProveedor = map.codProveedor?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val rawEan = map.eanPrincipal?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val descripcion = map.descripcion?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val marcaRaw = map.marca?.let { getStringCellValue(row.getCell(it)) }?.takeIf { it.isNotBlank() }
            ?: defaultBrand?.takeIf { it.isNotBlank() }
            ?: ""
        val marca = BRAND_ALIASES[normalizeSearch(marcaRaw)] ?: marcaRaw
        // Colapsar espacios duplicados (p. ej. "DE  RAIZ" → "DE RAIZ") para que
        // la marca se muestre y agrupe correctamente.
        val marcaClean = marca.replace(Regex("\\s+"), " ").trim()
        val unBase = map.unBase?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val unPedido = map.unPedido?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val conversion = map.conversion?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val estado = map.estado?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN1Cencosud = map.catCencosud[0]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN2Cencosud = map.catCencosud[1]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN3Cencosud = map.catCencosud[2]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN4Cencosud = map.catCencosud[3]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN1Proveedor = map.catProveedor[0]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN2Proveedor = map.catProveedor[1]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN3Proveedor = map.catProveedor[2]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val catN4Proveedor = map.catProveedor[3]?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val codigoBarra = map.codigoBarra?.let { getStringCellValue(row.getCell(it)) } ?: ""

        // El EAN real suele estar en "Código de Barra" (a veces con ceros
        // iniciales y asteriscos). Si está presente y es numérico, se prefiere.
        val cleanedBarra = codigoBarra.replace(Regex("""\D"""), "")
        val eanRaw = if (cleanedBarra.isNotBlank()) cleanedBarra else rawEan
        // Un EAN de 12 dígitos suele ser un UPC-A; se normaliza a EAN-13 anteponiendo
        // 0 para que el código de barras se genere correctamente y la búsqueda por el
        // código escaneado (12 dígitos) lo encuentre como subcadena del EAN-13.
        val eanPrincipal = if (eanRaw.length == 12 && eanRaw.all { it.isDigit() }) "0$eanRaw" else eanRaw

        if (eanPrincipal.isBlank() && codCencosud.isBlank() && codigoBarra.isBlank() && descripcion.isBlank()) {
            return null
        }

        return EanProductEntity(
            codCencosud = codCencosud,
            codProveedor = codProveedor,
            eanPrincipal = eanPrincipal,
            descripcionProducto = descripcion,
            descripcionNorm = normalizeSearch(descripcion),
            descripcionNormNospace = compactNorm(descripcion),
            marca = marcaClean,
            marcaNorm = normalizeSearch(marcaClean),
            marcaNormNospace = compactNorm(marcaClean),
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
                .filter {
                    it.startsWith(EAN_ASSET_PREFIX, ignoreCase = true) &&
                        it.endsWith(EAN_ASSET_SUFFIX, ignoreCase = true)
                }
                .sorted()
            if (assetFiles.isEmpty()) {
                return Result.failure(IllegalStateException("No se encontraron archivos EAN en assets"))
            }
            val all = mutableListOf<EanProductEntity>()
            for (file in assetFiles) {
                try {
                    context.assets.open(file).use { stream ->
                        all.addAll(parse(stream, brandFromFilename(file)))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parseando catálogo EAN: $file")
                }
            }
            val deduped = dedupe(all)
            if (deduped.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(deduped)
            }
            Result.success(deduped.size)
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