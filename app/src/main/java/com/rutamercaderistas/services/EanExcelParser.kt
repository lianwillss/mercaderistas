package com.rutamercaderistas.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rutamercaderistas.data.local.EanFtsManager
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

const val EAN_DATA_VERSION = 31

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
    "up_wine" to "UP WINE",
    "cuk" to "CUK",
    "tnogal" to "TNOGAL",
    "olimpia_franui" to "OLIMPIA-FRANUI",
    "casoy" to "CASO Y CIA",
    "suk" to "SUK",
    "vegmonkey" to "VEGMONKEY",
    "japi_jane" to "JAPI JANE",
    "asmode" to "ASMODE",
    "dix" to "ASMODE",
    "cu" to "CUK",
    "bwild" to "BWILD",
    "super" to "CASO Y CIA",
    "ccc" to "CASO Y CIA",
)

// Alias de marca: la empresa ve algunas marcas con un nombre distinto al del
// catálogo (p. ej. "Lola" → "Kobbo", siendo Kobbo su representación superior).
// Se aplica al importar para que agrupen y se muestren con el nombre correcto.
private val BRAND_ALIASES = mapOf(
    "lola" to "Kobbo",
    "b fresh" to "BWILD",
    "b.tan" to "BWILD",
    "caso&cia" to "CASO Y CIA",
    "caso & cia" to "CASO Y CIA",
)

// Marcas excluidas del catálogo EAN (ya no se comercializan).
private val EXCLUDED_BRANDS = setOf(
    "cinnabon",
)

// Nota aclaratoria para marcas canónicas (p. ej. "Kobbo" es la representación
// superior de "Lola Cosmetic"). Se muestra bajo el nombre en la interfaz EAN.
private val BRAND_CANONICAL_NOTES = mapOf(
    "kobbo" to "Lola Cosmetic",
)

fun brandNote(canonical: String): String? = BRAND_CANONICAL_NOTES[normalizeSearch(canonical)]

internal fun brandFromFilename(fileName: String): String {
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
    private val eanFts: EanFtsManager,
) {

    private var lastDiagnostics: EanDiagnostics = EanDiagnostics()

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

    suspend fun parseAndSave(inputStream: InputStream, defaultBrand: String? = null): Result<Int> {
        return try {
            val products = parse(inputStream, defaultBrand)
            val dedupeResult = dedupeEanProducts(products)
            logDedupe(dedupeResult)
            val deduped = dedupeResult.products
            if (deduped.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(deduped)
                eanFts.ensureAndRebuild()
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
    internal fun dedupeEanProducts(products: List<EanProductEntity>): EanDedupeResult {
        val map = mutableMapOf<String, EanProductEntity>()
        val blanks = mutableListOf<EanProductEntity>()
        var duplicatesMerged = 0
        for (p in products) {
            val key = p.eanPrincipal.trim().ifBlank { p.codCencosud.trim() }
            if (key.isBlank()) {
                blanks.add(p)
                continue
            }
            val existing = map[key]
            if (existing == null) {
                map[key] = p
            } else {
                map[key] = mergeProducts(existing, p)
                duplicatesMerged++
            }
        }
        return EanDedupeResult(products = map.values + blanks, duplicatesMerged = duplicatesMerged)
    }

    private fun mergeProducts(first: EanProductEntity, second: EanProductEntity): EanProductEntity {
        val primary = if (eanCompleteness(second) > eanCompleteness(first)) second else first
        val fallback = if (primary === first) second else first
        fun choose(primaryValue: String, fallbackValue: String): String =
            primaryValue.ifBlank { fallbackValue }

        return primary.copy(
            codCencosud = choose(primary.codCencosud, fallback.codCencosud),
            codProveedor = choose(primary.codProveedor, fallback.codProveedor),
            eanPrincipal = choose(primary.eanPrincipal, fallback.eanPrincipal),
            descripcionProducto = choose(primary.descripcionProducto, fallback.descripcionProducto),
            descripcionNorm = choose(primary.descripcionNorm, fallback.descripcionNorm),
            marca = choose(primary.marca, fallback.marca),
            marcaNorm = choose(primary.marcaNorm, fallback.marcaNorm),
            descripcionNormNospace = choose(primary.descripcionNormNospace, fallback.descripcionNormNospace),
            marcaNormNospace = choose(primary.marcaNormNospace, fallback.marcaNormNospace),
            unBase = choose(primary.unBase, fallback.unBase),
            unPedido = choose(primary.unPedido, fallback.unPedido),
            conversion = choose(primary.conversion, fallback.conversion),
            estado = choose(primary.estado, fallback.estado),
            catN1Cencosud = choose(primary.catN1Cencosud, fallback.catN1Cencosud),
            catN2Cencosud = choose(primary.catN2Cencosud, fallback.catN2Cencosud),
            catN3Cencosud = choose(primary.catN3Cencosud, fallback.catN3Cencosud),
            catN4Cencosud = choose(primary.catN4Cencosud, fallback.catN4Cencosud),
            catN1Proveedor = choose(primary.catN1Proveedor, fallback.catN1Proveedor),
            catN2Proveedor = choose(primary.catN2Proveedor, fallback.catN2Proveedor),
            catN3Proveedor = choose(primary.catN3Proveedor, fallback.catN3Proveedor),
            catN4Proveedor = choose(primary.catN4Proveedor, fallback.catN4Proveedor),
            codigoBarra = choose(primary.codigoBarra, fallback.codigoBarra),
        )
    }

    private fun eanCompleteness(product: EanProductEntity): Int = listOf(
        product.codCencosud,
        product.codProveedor,
        product.eanPrincipal,
        product.descripcionProducto,
        product.marca,
        product.unBase,
        product.unPedido,
        product.conversion,
        product.estado,
        product.catN1Cencosud,
        product.catN2Cencosud,
        product.catN3Cencosud,
        product.catN4Cencosud,
        product.catN1Proveedor,
        product.catN2Proveedor,
        product.catN3Proveedor,
        product.catN4Proveedor,
        product.codigoBarra,
    ).count { it.isNotBlank() }

    private fun logDedupe(result: EanDedupeResult) {
        val d = lastDiagnostics
        if (result.duplicatesMerged > 0 || d.invalidEanCleared > 0 || d.emptyFiles > 0) {
            Timber.i(
                "EAN: %d productos finales; %d duplicados fusionados; %d EAN inválidos limpiados; %d archivos vacíos",
                result.products.size, result.duplicatesMerged, d.invalidEanCleared, d.emptyFiles
            )
        } else {
            Timber.i("EAN: %d productos finales", result.products.size)
        }
    }

    fun getLastDiagnostics(): EanDiagnostics = lastDiagnostics

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
                "sku" in h -> codCencosud = col
                "cencosud" in h -> codCencosud = col
                "proveedor" in h -> codProveedor = col
                "marca" in h -> marca = col
                "estado" in h -> estado = col
                "conversion" in h || "convers" in h -> conversion = col
                "caja" in h -> conversion = col
                "pedido" in h -> unPedido = col
                "base" in h -> unBase = col
                "descrip" in h -> descripcion = col
                "articulo" in h -> descripcion = col
                "producto" in h -> descripcion = col
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
        if (normalizeSearch(marcaClean) in EXCLUDED_BRANDS) return null
        val unBase = map.unBase?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val unPedido = map.unPedido?.let { getStringCellValue(row.getCell(it)) } ?: ""
        val conversionRaw = map.conversion?.let { getStringCellValue(row.getCell(it)) } ?: ""
        // Hardcoded CAJA para marcas cuyo Excel no trae columna Conversión/Caja
        val conversion = when {
            conversionRaw.isNotBlank() -> conversionRaw
            codCencosud == "1871451" -> "12"
            codCencosud == "1846223" -> "16"
            normalizeSearch(marcaClean) == "nat natural" -> "24"
            normalizeSearch(marcaClean) == "japi jane" -> "6"
            else -> conversionRaw
        }
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
        var eanPrincipal = if (eanRaw.length == 12 && eanRaw.all { it.isDigit() }) "0$eanRaw" else eanRaw
        // Validación silenciosa: EAN debe ser 8-14 dígitos numéricos; si es inválido se limpia sin mostrar error
        if (eanPrincipal.isNotBlank() && (!eanPrincipal.all { it.isDigit() } || eanPrincipal.length !in 8..14)) {
            Timber.d("EAN inválido descartado: %s (%s)", eanPrincipal, descripcion)
            lastDiagnostics = lastDiagnostics.copy(invalidEanCleared = lastDiagnostics.invalidEanCleared + 1)
            eanPrincipal = ""
        }

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
        val raw = when (cell.cellType) {
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
        }
        // Quita el apóstrofe inicial que Excel usa como marcador de texto
        // (p. ej. "'7798147784442" → "7798147784442").
        return raw.trim().removePrefix("'")
    }

    // Cargar desde assets (todos los archivos "ean_*.xlsx" se combinan)
    suspend fun loadFromAssets(): Result<Int> {
        return try {
            lastDiagnostics = EanDiagnostics()
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
                    val parsed = context.assets.open(file).use { stream -> parse(stream, brandFromFilename(file)) }
                    if (parsed.isEmpty()) {
                        lastDiagnostics = lastDiagnostics.copy(emptyFiles = lastDiagnostics.emptyFiles + 1)
                        Timber.w("EAN: archivo vacío o sin productos: %s", file)
                    }
                    all.addAll(parsed)
                } catch (e: Exception) {
                    Timber.e(e, "Error parseando catálogo EAN: $file")
                }
            }
            val dedupeResult = dedupeEanProducts(all)
            logDedupe(dedupeResult)
            val deduped = dedupeResult.products
            if (deduped.isNotEmpty()) {
                eanProductDao.clearAll()
                eanProductDao.insertAll(deduped)
                eanFts.ensureAndRebuild()
            }
            Result.success(deduped.size)
        } catch (e: Exception) {
            Timber.e(e, "Error loading EAN file from assets")
            Result.failure(e)
        }
    }

    // Cargar desde archivo descargado (defaultBrand para archivos sin columna Marca)
    suspend fun loadFromFile(filePath: String, defaultBrand: String? = null): Result<Int> {
        return try {
            val inputStream = java.io.FileInputStream(filePath)
            parseAndSave(inputStream, defaultBrand)
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

    fun getEanAssetsHash(): String = prefs().getString("ean_assets_hash", "") ?: ""

    fun setEanAssetsHash(hash: String) {
        prefs().edit().putString("ean_assets_hash", hash).apply()
    }

    fun computeAssetsHash(): String {
        return try {
            val files = (context.assets.list("") ?: emptyArray())
                .filter { it.startsWith(EAN_ASSET_PREFIX, ignoreCase = true) && it.endsWith(EAN_ASSET_SUFFIX, ignoreCase = true) }
                .sorted()
            if (files.isEmpty()) return ""
            val md = java.security.MessageDigest.getInstance("SHA-256")
            for (f in files) {
                md.update(f.toByteArray())
                try {
                    context.assets.openFd(f).use { fd -> md.update(fd.length.toString().toByteArray()) }
                } catch (_: Exception) {
                    // Fallback: hash first KB of content
                    try {
                        context.assets.open(f).use { ins ->
                            val buf = ByteArray(1024)
                            val read = ins.read(buf)
                            if (read > 0) md.update(buf, 0, read)
                        }
                    } catch (_: Exception) {}
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }
}

data class EanDedupeResult(
    val products: List<EanProductEntity>,
    val duplicatesMerged: Int,
)

data class EanDiagnostics(
    val invalidEanCleared: Int = 0,
    val emptyFiles: Int = 0,
)
