package com.rutamercaderistas.services

import com.rutamercaderistas.models.EntradaRuta
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.openxml4j.opc.PackagingURIHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.model.SharedStrings
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

class ExcelParser {

    private val mapper = ColumnMapper()

    interface ProgressListener {
        fun onProgress(message: String, percentage: Int)
    }

    suspend fun parseOnlyIndex(file: File): Result<List<String>> {
        return try {
            val pkg = OPCPackage.open(file, PackageAccess.READ)
            try {
                val reader = XSSFReader(pkg)
                val sharedStrings: SharedStrings = reader.sharedStringsTable
                val rId = findSheetRId(pkg, "RUTA RUTERO")
                    ?: return Result.failure(Exception("Hoja 'RUTA RUTERO' no encontrada"))

                val sheetStream = reader.getSheet(rId)
                val ruteros = mutableSetOf<String>()
                var idxRutero = -1
                var headerParsed = false

                val handler = sheetHandler(sharedStrings, onRow = { rowNum, values ->
                    if (!headerParsed && rowNum == 1) {
                        mapper.map(values)
                        idxRutero = mapper.getIndex("RUTERO")
                        headerParsed = true
                    } else if (headerParsed) {
                        val rutero = values.getOrElse(idxRutero) { "" }
                        if (rutero.isNotBlank()) ruteros.add(rutero)
                    }
                })

                parseSheetStream(sheetStream, handler)
                sheetStream.close()
                Result.success(ruteros.toList())
            } finally {
                pkg.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseSpecificRoute(
        file: File, targetRutero: String,
        listener: ProgressListener? = null
    ): Result<List<EntradaRuta>> {
        return try {
            val pkg = OPCPackage.open(file, PackageAccess.READ)
            try {
                val reader = XSSFReader(pkg)
                val sharedStrings: SharedStrings = reader.sharedStringsTable
                val rId = findSheetRId(pkg, "RUTA RUTERO")
                    ?: return Result.failure(Exception("Hoja 'RUTA RUTERO' no encontrada"))

                val sheetStream = reader.getSheet(rId)
                val entries = mutableListOf<EntradaRuta>()
                var idxRutero = -1; var idxLocal = -1; var idxCodigo = -1
                var idxCliente = -1; var idxDireccion = -1; var idxCadena = -1
                var idxLun = -1; var idxMar = -1; var idxMie = -1
                var idxJue = -1; var idxVie = -1; var idxSab = -1; var idxDom = -1
                var headerParsed = false
                var rowCount = 0

                val handler = sheetHandler(sharedStrings, onRow = { rowNum, values ->
                    if (!headerParsed && rowNum == 1) {
                        mapper.map(values)
                        idxRutero = mapper.getIndex("RUTERO"); idxLocal = mapper.getIndex("LOCAL")
                        idxCodigo = mapper.getIndex("COD KPI ONE"); idxCliente = mapper.getIndex("CLIENTE")
                        idxDireccion = mapper.getIndex("DIRECCIÓN", "DIRECCION")
                        idxCadena = mapper.getIndex("CADENA")
                        idxLun = mapper.getIndex("LUNES", "LUN"); idxMar = mapper.getIndex("MARTES", "MAR")
                        idxMie = mapper.getIndex("MIERCOLES", "MIÉRCOLES", "MIE")
                        idxJue = mapper.getIndex("JUEVES", "JUE"); idxVie = mapper.getIndex("VIERNES", "VIE")
                        idxSab = mapper.getIndex("SABADO", "SÁBADO", "SAB"); idxDom = mapper.getIndex("DOMINGO", "DOM")
                        headerParsed = true
                    } else if (headerParsed) {
                        val rutero = values.getOrElse(idxRutero) { "" }
                        if (rutero.equals(targetRutero, ignoreCase = true)) {
                            fun v(idx: Int): String = values.getOrElse(idx) { "" }
                            fun isDay(idx: Int): Boolean {
                                if (idx == -1) return false
                                val vv = v(idx); if (vv.isBlank()) return false
                                return vv == "1" || vv == "1.0" || vv.lowercase() in setOf("x", "si", "(todas)")
                            }
                            entries.add(EntradaRuta(
                                reponedor = "", rutero = rutero, codigo = v(idxCodigo),
                                local = v(idxLocal), direccion = v(idxDireccion), cliente = v(idxCliente),
                                cadena = v(idxCadena),
                                lunes = isDay(idxLun), martes = isDay(idxMar), miercoles = isDay(idxMie),
                                jueves = isDay(idxJue), viernes = isDay(idxVie), sabado = isDay(idxSab),
                                domingo = isDay(idxDom)
                            ))
                        }
                        rowCount++
                        if (rowCount % 500 == 0) listener?.onProgress("Filtrando registros...", 50)
                    }
                })

                parseSheetStream(sheetStream, handler)
                sheetStream.close()
                Result.success(entries)
            } finally {
                pkg.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseAll(
        file: File, listener: ProgressListener? = null
    ): Result<Pair<List<String>, Map<String, List<EntradaRuta>>>> {
        return try {
            val pkg = OPCPackage.open(file, PackageAccess.READ)
            try {
                val reader = XSSFReader(pkg)
                val sharedStrings: SharedStrings = reader.sharedStringsTable
                val rId = findSheetRId(pkg, "RUTA RUTERO")
                    ?: return Result.failure(Exception("Hoja 'RUTA RUTERO' no encontrada"))

                val sheetStream = reader.getSheet(rId)
                val ruteros = mutableSetOf<String>()
                val allEntries = mutableListOf<EntradaRuta>()
                var idxRutero = -1; var idxLocal = -1; var idxCodigo = -1
                var idxCliente = -1; var idxDireccion = -1; var idxCadena = -1
                var idxLun = -1; var idxMar = -1; var idxMie = -1
                var idxJue = -1; var idxVie = -1; var idxSab = -1; var idxDom = -1
                var headerParsed = false
                var rowCount = 0

                val handler = sheetHandler(sharedStrings, onRow = { rowNum, values ->
                    if (!headerParsed && rowNum == 1) {
                        mapper.map(values)
                        idxRutero = mapper.getIndex("RUTERO"); idxLocal = mapper.getIndex("LOCAL")
                        idxCodigo = mapper.getIndex("COD KPI ONE"); idxCliente = mapper.getIndex("CLIENTE")
                        idxDireccion = mapper.getIndex("DIRECCIÓN", "DIRECCION")
                        idxCadena = mapper.getIndex("CADENA")
                        idxLun = mapper.getIndex("LUNES", "LUN"); idxMar = mapper.getIndex("MARTES", "MAR")
                        idxMie = mapper.getIndex("MIERCOLES", "MIÉRCOLES", "MIE")
                        idxJue = mapper.getIndex("JUEVES", "JUE"); idxVie = mapper.getIndex("VIERNES", "VIE")
                        idxSab = mapper.getIndex("SABADO", "SÁBADO", "SAB"); idxDom = mapper.getIndex("DOMINGO", "DOM")
                        headerParsed = true
                    } else if (headerParsed) {
                        val rutero = values.getOrElse(idxRutero) { "" }
                        if (rutero.isNotBlank()) {
                            ruteros.add(rutero)
                            fun v(idx: Int): String = values.getOrElse(idx) { "" }
                            fun isDay(idx: Int): Boolean {
                                if (idx == -1) return false
                                val vv = v(idx); if (vv.isBlank()) return false
                                return vv == "1" || vv == "1.0" || vv.lowercase() in setOf("x", "si", "(todas)")
                            }
                            allEntries.add(EntradaRuta(
                                reponedor = "", rutero = rutero, codigo = v(idxCodigo),
                                local = v(idxLocal), direccion = v(idxDireccion), cliente = v(idxCliente),
                                cadena = v(idxCadena),
                                lunes = isDay(idxLun), martes = isDay(idxMar), miercoles = isDay(idxMie),
                                jueves = isDay(idxJue), viernes = isDay(idxVie), sabado = isDay(idxSab),
                                domingo = isDay(idxDom)
                            ))
                        }
                        rowCount++
                        if (rowCount % 500 == 0) listener?.onProgress("Procesando registros...", 50)
                    }
                })

                parseSheetStream(sheetStream, handler)
                sheetStream.close()

                val byRoute = allEntries.groupBy { it.rutero }
                Result.success(Pair(ruteros.toList().sorted(), byRoute))
            } finally {
                pkg.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── helpers ──

    private fun sheetHandler(
        sharedStrings: SharedStrings,
        onRow: (rowNum: Int, values: List<String>) -> Unit
    ): DefaultHandler = object : DefaultHandler() {
        private var inRow = false
        private var inValue = false
        private var colIndex = 0
        private var cellType = ""
        private val currentValues = mutableListOf<String>()
        private var currentValue = StringBuilder()
        private var currentRowR = 0

        override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
            when (qName) {
                "row" -> {
                    inRow = true
                    colIndex = 0
                    currentValues.clear()
                    currentRowR = atts.getValue("r")?.toIntOrNull() ?: 0
                }
                "c" -> {
                    cellType = atts.getValue("t") ?: ""
                    if (inRow) {
                        val ref = atts.getValue("r") ?: ""
                        val colLetter = ref.takeWhile { it.isLetter() }
                        colIndex = columnLetterToIndex(colLetter)
                    }
                }
                "v" -> if (inRow) { inValue = true; currentValue = StringBuilder() }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inValue) currentValue.append(ch, start, length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (qName == "v" && inRow && inValue) {
                inValue = false
                val value = currentValue.toString()
                while (currentValues.size <= colIndex) currentValues.add("")
                currentValues[colIndex] = if (cellType == "s") {
                    try { sharedStrings.getItemAt(value.toInt()).string } catch (_: Exception) { value }
                } else value
            }
            if (qName == "row") {
                inRow = false
                onRow(currentRowR, currentValues.toList())
            }
        }
    }

    private fun findSheetRId(pkg: OPCPackage, sheetName: String): String? {
        return try {
            val part = pkg.getPart(PackagingURIHelper.createPartName("/xl/workbook.xml"))
            val stream = part.inputStream
            var rId: String? = null
            val factory = SAXParserFactory.newInstance()
            val xmlReader = factory.newSAXParser().xmlReader
            xmlReader.contentHandler = object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {
                    if (qName == "sheet") {
                        val name = atts.getValue("name")
                        val id = atts.getValue("r:id")
                        if (name != null && id != null && name.equals(sheetName, ignoreCase = true)) {
                            rId = id
                        }
                    }
                }
            }
            xmlReader.parse(InputSource(stream))
            stream.close()
            rId
        } catch (_: Exception) { null }
    }

    private fun parseSheetStream(stream: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.newSAXParser().xmlReader.apply {
            contentHandler = handler
            parse(InputSource(stream))
        }
    }

    private fun columnLetterToIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase()) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return result - 1
    }
}
