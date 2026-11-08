package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EanCodigosParserTest {

    @Test
    fun `parse ean_codigos xlsx does not throw and yields products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val context = mockk<Context>(relaxed = true)

        val parser = EanExcelParser(context, dao)
        val path = "src/main/assets/ean_codigos.xlsx"
        val result = parser.loadFromFile(path)

        println("RESULT success=${result.isSuccess} count=${result.getOrNull()} error=${result.exceptionOrNull()?.message}")
        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse > 0 products, got ${result.getOrNull()}", (result.getOrNull() ?: 0) > 0)
    }

    @Test
    fun `brand assets are mapped correctly`() {
        assertTrue(brandFromFilename("ean_asmode.xlsx") == "ASMODE")
        assertTrue(brandFromFilename("ean_dix.xlsx") == "ASMODE")
        assertTrue(brandFromFilename("ean_cu.xlsx") == "CUK")
        assertTrue(brandFromFilename("ean_bwild.xlsx") == "BWILD")
        assertTrue(brandFromFilename("ean_super.xlsx") == "CASO Y CIA")
        assertTrue(brandFromFilename("ean_ccc.xlsx") == "CASO Y CIA")
        assertTrue(brandFromFilename("ean_cafellanquihue.xlsx") == "CAFÉ LLANQUIHUE")
        assertTrue(brandFromFilename("ean_keyfood.xlsx") == "KEYFOOD")
        assertTrue(brandFromFilename("ean_gomitas.xlsx") == "ABEJA DORADA")
        assertTrue(brandFromFilename("ean_pet.xlsx") == "PET KITCHEN")
        assertTrue(brandFromFilename("ean_fermen.xlsx") == "FERMENTISTA")
        assertTrue(brandFromFilename("ean_pett.xlsx") == "PET KITCHEN")
        assertTrue(brandFromFilename("ean_patpot.xlsx") == "PATPOT")
        assertTrue(brandFromFilename("ean_pet_kitchen.xlsx") == "PET KITCHEN")
        assertTrue(brandFromFilename("ean_indomitable.xlsx") == "PET KITCHEN")
        assertTrue(brandFromFilename("ean_caso_cia.xlsx") == "CASO Y CIA")
    }

    @Test
    fun `asmode xlsx parses products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile("src/main/assets/ean_asmode.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse ASMODE products", (result.getOrNull() ?: 0) > 0)
    }

    @Test
    fun `bwild xlsx parses products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile("src/main/assets/ean_bwild.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse BWILD products", (result.getOrNull() ?: 0) > 0)
    }

    @Test
    fun `bwild xlsx yields all 33 sku under BWILD`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile("src/main/assets/ean_bwild.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        // El archivo trae 33 filas: 18 "B FRESH" + 15 "B.TAN", ambas marcas BWILD
        assertTrue("debe importar 33 productos, got ${result.getOrNull()}", result.getOrNull() == 33)
        val inserted = slot.captured
        assertTrue("insertados deben ser 33, got ${inserted.size}", inserted.size == 33)
        val brands = inserted.map { it.marca }.toSet()
        assertTrue("todo debe agrupar en BWILD, got $brands", brands == setOf("BWILD"))
    }

    @Test
    fun `ccc xlsx parses single CASO Y CIA product`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_ccc.xlsx",
            brandFromFilename("ean_ccc.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 1 producto, got ${result.getOrNull()}", result.getOrNull() == 1)
        val inserted = slot.captured
        assertTrue("marca debe ser CASO Y CIA, got ${inserted.map { it.marca }}", inserted.all { it.marca == "CASO Y CIA" })
        assertTrue(
            "EAN debe ser 8431618015391, got ${inserted.map { it.eanPrincipal }}",
            inserted.any { it.eanPrincipal == "8431618015391" },
        )
    }

    @Test
    fun `cafellanquihue xlsx parses 3 CAFÉ LLANQUIHUE products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_cafellanquihue.xlsx",
            brandFromFilename("ean_cafellanquihue.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 3 productos, got ${result.getOrNull()}", result.getOrNull() == 3)
        val inserted = slot.captured
        assertTrue("marca debe ser CAFÉ LLANQUIHUE, got ${inserted.map { it.marca }}", inserted.all { it.marca == "CAFÉ LLANQUIHUE" })
        // Los EAN con 0 inicial se conservan tal cual.
        assertTrue(
            "EAN deben conservar el 0 inicial, got ${inserted.map { it.eanPrincipal }}",
            inserted.map { it.eanPrincipal }.containsAll(
                listOf("0739802463521", "0739802463514", "0739802463538")
            ),
        )
    }

    @Test
    fun `keyfood xlsx parses 5 KEYFOOD products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_keyfood.xlsx",
            brandFromFilename("ean_keyfood.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 5 productos, got ${result.getOrNull()}", result.getOrNull() == 5)
        val inserted = slot.captured
        assertTrue("marca debe ser KEYFOOD, got ${inserted.map { it.marca }}", inserted.all { it.marca == "KEYFOOD" })
        // EAN-13 tal cual (13 dígitos) y SKU Cencosud sin perder ceros.
        assertTrue(
            "EAN deben ser los 5 de Keyfood, got ${inserted.map { it.eanPrincipal }}",
            inserted.map { it.eanPrincipal }.containsAll(
                listOf("7804690890292", "7804690890339", "7804690890285", "7804690890315", "7804690890322")
            ),
        )
        assertTrue(
            "SKU deben conservarse, got ${inserted.map { it.codCencosud }}",
            inserted.map { it.codCencosud }.containsAll(
                listOf("2087431", "2089254", "2087430", "2087433", "2087434")
            ),
        )
    }

    @Test
    fun `gomitas xlsx parses 1 ABEJA DORADA product`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        // El archivo no trae columna Marca: la marca viene del nombre del archivo.
        val result = parser.loadFromFile(
            "src/main/assets/ean_gomitas.xlsx",
            brandFromFilename("ean_gomitas.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 1 producto, got ${result.getOrNull()}", result.getOrNull() == 1)
        val inserted = slot.captured
        assertTrue("marca debe ser ABEJA DORADA, got ${inserted.map { it.marca }}", inserted.all { it.marca == "ABEJA DORADA" })
        // El EAN con 0 inicial se conserva tal cual y el SKU no pierde ceros.
        assertTrue(
            "EAN debe ser 0658325654776, got ${inserted.map { it.eanPrincipal }}",
            inserted.any { it.eanPrincipal == "0658325654776" },
        )
        assertTrue(
            "SKU debe ser 2089654, got ${inserted.map { it.codCencosud }}",
            inserted.any { it.codCencosud == "2089654" },
        )
    }

    @Test
    fun `pet xlsx parses 2 PET KITCHEN products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_pet.xlsx",
            brandFromFilename("ean_pet.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 2 productos, got ${result.getOrNull()}", result.getOrNull() == 2)
        val inserted = slot.captured
        assertTrue("marca debe ser PET KITCHEN, got ${inserted.map { it.marca }}", inserted.all { it.marca == "PET KITCHEN" })
        assertTrue(
            "EAN deben ser los 2 de Pet Kitchen, got ${inserted.map { it.eanPrincipal }}",
            inserted.map { it.eanPrincipal }.containsAll(
                listOf("7804662094116", "7804662094125")
            ),
        )
        assertTrue(
            "SKU deben conservarse, got ${inserted.map { it.codCencosud }}",
            inserted.map { it.codCencosud }.containsAll(listOf("2094112", "2094113")),
        )
    }

    @Test
    fun `fermen xlsx parses 1 FERMENTISTA product`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_fermen.xlsx",
            brandFromFilename("ean_fermen.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 1 producto, got ${result.getOrNull()}", result.getOrNull() == 1)
        val inserted = slot.captured
        assertTrue("marca debe ser FERMENTISTA, got ${inserted.map { it.marca }}", inserted.all { it.marca == "FERMENTISTA" })
        // EAN de 12 dígitos (UPC-A) se normaliza a EAN-13 con 0 inicial.
        assertTrue(
            "EAN debe ser 0799192726439, got ${inserted.map { it.eanPrincipal }}",
            inserted.any { it.eanPrincipal == "0799192726439" },
        )
        assertTrue(
            "SKU debe ser 2093322, got ${inserted.map { it.codCencosud }}",
            inserted.any { it.codCencosud == "2093322" },
        )
    }

    @Test
    fun `pett xlsx parses 2 galletas PET KITCHEN via alias`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        // La columna Marca trae "INDOMITABLE" por error: el alias lo agrupa en PET KITCHEN.
        val result = parser.loadFromFile(
            "src/main/assets/ean_pett.xlsx",
            brandFromFilename("ean_pett.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        // La TWIST STICKS (2094117) se movió a ean_indomitable.xlsx con su EAN real.
        assertTrue("debe importar 2 productos, got ${result.getOrNull()}", result.getOrNull() == 2)
        val inserted = slot.captured
        assertTrue("marca debe ser PET KITCHEN, got ${inserted.map { it.marca }}", inserted.all { it.marca == "PET KITCHEN" })
        assertEquals(listOf("2094107", "2094114"), inserted.map { it.codCencosud }.sorted())
        // EAN con 0 inicial se conserva tal cual.
        assertTrue(
            "EAN de la TASTE SALMON, got ${inserted.map { it.eanPrincipal }}",
            inserted.any { it.eanPrincipal == "0628693826303" },
        )
        // El 7116218365596 quedó liberado para la TWIST STICKS; la CLASSIC BEEF
        // recuperó el EAN que le corresponde (0628693826013, con cero inicial).
        assertEquals(
            "0628693826013",
            inserted.single { it.codCencosud == "2094107" }.eanPrincipal,
        )
    }

    @Test
    fun `ean_pett_xlsx_parse_2_galletas_pet_kitchen_via_alias`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_pett.xlsx",
            brandFromFilename("ean_pett.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 2 productos, got ${result.getOrNull()}", result.getOrNull() == 2)
        val inserted = slot.captured
        assertTrue("marca debe ser PET KITCHEN, got ${inserted.map { it.marca }}", inserted.all { it.marca == "PET KITCHEN" })
        assertTrue(
            "EAN de las galletas, got ${inserted.map { it.eanPrincipal }}",
            inserted.map { it.eanPrincipal }.containsAll(listOf("0628693826013", "0628693826303")),
        )
        assertTrue(
            "SKU deben conservarse, got ${inserted.map { it.codCencosud }}",
            inserted.map { it.codCencosud }.containsAll(listOf("2094107", "2094114")),
        )
        // 2094117 salió de este archivo: su fila tenía un EAN que no corresponde.
        assertTrue(
            "2094117 no debe quedar en ean_pett.xlsx, got ${inserted.map { it.codCencosud }}",
            inserted.none { it.codCencosud == "2094117" },
        )
    }

    @Test
    fun `ean_indomitable_xlsx_imports_twist_sticks_with_real_ean`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_indomitable.xlsx",
            brandFromFilename("ean_indomitable.xlsx"),
        )

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrNull())
        val product = insertedSlot.captured.single()
        // Sin columna Marca en el Excel: la marca sale del nombre del asset.
        assertEquals("PET KITCHEN", product.marca)
        assertEquals("7116218365596", product.eanPrincipal)
        assertEquals("2094117", product.codCencosud)
        assertEquals("SNACK BITE TWIST STICKS 4UN INDOMITABLE", product.descripcionProducto)
    }

    @Test
    fun `ean_producto_bwild_gel_pomelo_xlsx_parse_1_bwild_product_via_alias`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val slot = io.mockk.slot<List<com.rutamercaderistas.data.local.EanProductEntity>>()
        coEvery { dao.insertAll(capture(slot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_producto_bwild_gel_pomelo.xlsx",
            brandFromFilename("ean_producto_bwild_gel_pomelo.xlsx"),
        )

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("debe importar 1 producto, got ${result.getOrNull()}", result.getOrNull() == 1)
        val inserted = slot.captured
        assertTrue("marca debe ser BWILD, got ${inserted.map { it.marca }}", inserted.all { it.marca == "BWILD" })
        assertTrue(
            "EAN debe ser 9347108004655, got ${inserted.map { it.eanPrincipal }}",
            inserted.any { it.eanPrincipal == "9347108004655" },
        )
        assertTrue(
            "SKU debe ser 2036605, got ${inserted.map { it.codCencosud }}",
            inserted.any { it.codCencosud == "2036605" },
        )
    }

    @Test
    fun `ean_patpot_xlsx_imports_product_with_upc_normalized`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_patpot.xlsx",
            brandFromFilename("ean_patpot.xlsx"),
        )

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrNull())
        val product = insertedSlot.captured.single()
        assertEquals("PATPOT", product.marca)
        // 12 dígitos (UPC-A) se normalizan a EAN-13 con cero inicial; la
        // columna "Código de Barra (EAN)" se conserva tal cual en codigoBarra.
        assertEquals("0799192728020", product.eanPrincipal)
        assertEquals("799192728020", product.codigoBarra)
        assertEquals("2097602", product.codCencosud)
        assertEquals("PAPAS FRITAS MERKEN PATPOT 150GR", product.descripcionProducto)
    }

    @Test
    fun `ean_pet_kitchen_xlsx_imports_snack_bite`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_pet_kitchen.xlsx",
            brandFromFilename("ean_pet_kitchen.xlsx"),
        )

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrNull())
        val product = insertedSlot.captured.single()
        // El Excel no trae columna Marca: se toma del nombre del asset.
        assertEquals("PET KITCHEN", product.marca)
        assertEquals("7116218365428", product.eanPrincipal)
        assertEquals("2094116", product.codCencosud)
        assertEquals("SNACK BITE JUMBO STICKS 2UN INDOMITABLE", product.descripcionProducto)
        // La columna "Código de Barras (imagen)" no debe tapar el EAN real.
        assertEquals("7116218365428", product.codigoBarra)
    }

    @Test
    fun `ningun EAN se repite en dos SKU distintos entre assets`() = runTest {
        // Guarda contra el error real: un EAN copiado a la fila de otro producto.
        // Sí se permite el mismo EAN con el mismo SKU (catálogos solapados:
        // ean_caso_cia/ean_casoy, ean_codigos/ean_loveco), pero no dos SKU distintos.
        val assetsDir = java.io.File("src/main/assets")
        val files = assetsDir.listFiles { f -> f.name.startsWith("ean") && f.name.endsWith(".xlsx") }
            ?.sortedBy { it.name }
            ?: error("no se encontraron assets EAN en ${assetsDir.absolutePath}")
        assertTrue("debe haber assets EAN", files.isNotEmpty())

        val porEan = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for (file in files) {
            val dao = mockk<EanProductDao>(relaxed = true)
            val inserted = slot<List<EanProductEntity>>()
            coEvery { dao.clearAll() } returns Unit
            coEvery { dao.insertAll(capture(inserted)) } returns Unit
            val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)
            val result = parser.loadFromFile(file.path, brandFromFilename(file.name))
            assertTrue("no se pudo parsear ${file.name}: ${result.exceptionOrNull()}", result.isSuccess)
            for (p in inserted.captured.filter { it.eanPrincipal.isNotBlank() }) {
                porEan.getOrPut(p.eanPrincipal) { mutableListOf() }.add(file.name to p.codCencosud)
            }
        }

        val choques = porEan
            .mapValues { (_, v) -> v.filter { it.second.isNotBlank() } }
            .filter { (_, v) -> v.map { it.second }.distinct().size > 1 }
        assertTrue("un EAN no puede estar en dos SKU distintos: $choques", choques.isEmpty())
    }

    @Test
    fun `caso_cia xlsx agrupa JUMEX y SUPERZINGS bajo CASO Y CIA`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile(
            "src/main/assets/ean_caso_cia.xlsx",
            brandFromFilename("ean_caso_cia.xlsx"),
        )

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(37, result.getOrNull())
        val inserted = insertedSlot.captured
        // Las submarcas del proveedor quedan dentro de CASO Y CIA (15
        // SUPERZINGS + 8 JUMEX + 1 propia), sin chips sueltos.
        val marcas = inserted.map { it.marca }.toSet()
        assertTrue("JUMEX/SUPERZINGS no deben quedar sueltos: $marcas", "JUMEX" !in marcas && "SUPERZINGS" !in marcas)
        assertEquals(24, inserted.count { it.marca == "CASO Y CIA" })
        // La submarca sigue en la descripción: buscar "jumex"/"superzings" funciona.
        assertEquals(15, inserted.count { it.descripcionProducto.contains("SUPERZINGS") })
        assertEquals(9, inserted.count { it.descripcionProducto.contains("JUMEX") })
        // Fila agregada: el néctar llega con sus datos completos.
        val product = inserted.single { it.eanPrincipal == "7501013118193" }
        assertEquals("CASO Y CIA", product.marca)
        assertEquals("263998", product.codCencosud)
        assertEquals("NECTAR JUMEX MANZANA LATA 335CC", product.descripcionProducto)
        assertEquals("UN", product.unBase)
        assertEquals("CS", product.unPedido)
        assertEquals("12", product.conversion)
        assertEquals("ACTIVO", product.estado)
    }

    @Test
    fun `image barcode column does not override the real ean`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Catálogo")
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Nombre")
            createCell(1).setCellValue("SKU")
            createCell(2).setCellValue("Código de Barra (EAN)")
            createCell(3).setCellValue("Código de Barras (imagen)")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("PAPAS FRITAS MERKEN PATPOT 150GR")
            createCell(1).setCellValue("2097602")
            createCell(2).setCellValue("799192728020")
            createCell(3).setBlank()
        }
        val bytes = ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        workbook.close()

        val result = parser.parseAndSave(bytes.inputStream(), "PATPOT")

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val product = insertedSlot.captured.single()
        assertEquals("0799192728020", product.eanPrincipal)
        assertEquals("PAPAS FRITAS MERKEN PATPOT 150GR", product.descripcionProducto)
    }

    @Test
    fun `marca dada de baja no entra al catalogo EAN`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Catálogo")
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Nombre")
            createCell(1).setCellValue("Marca")
            createCell(2).setCellValue("SKU")
            createCell(3).setCellValue("Código EAN")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("BATIDO BERRYSUR AVENA Y CRANBERRY 300ML")
            createCell(1).setCellValue("BERRY SUR")
            createCell(2).setCellValue("1990127")
            createCell(3).setCellValue("5912345678880")
        }
        sheet.createRow(2).apply {
            createCell(0).setCellValue("SNACK BITE JUMBO STICKS 2UN INDOMITABLE")
            createCell(1).setCellValue("PET KITCHEN")
            createCell(2).setCellValue("2094116")
            createCell(3).setCellValue("7116218365428")
        }
        val bytes = ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        workbook.close()

        val result = parser.parseAndSave(bytes.inputStream())

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val inserted = insertedSlot.captured
        // BERRY SUR está en EXCLUDED_BRANDS: su fila no llega a la base.
        assertEquals(1, result.getOrNull())
        assertTrue("BERRY SUR no debe entrar: ${inserted.map { it.marca }}", inserted.none { it.marca.contains("BERRY") })
        assertEquals("PET KITCHEN", inserted.single().marca)
    }

    @Test
    fun `ean_codigos xlsx ya no trae BERRY SUR`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        val insertedSlot = slot<List<EanProductEntity>>()
        coEvery { dao.insertAll(capture(insertedSlot)) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile("src/main/assets/ean_codigos.xlsx")

        assertTrue("parser should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val conMarca = insertedSlot.captured.filter { it.marca.contains("BERRY", ignoreCase = true) }
        assertEquals("BERRY SUR debe quedar fuera del catálogo: $conMarca", 0, conMarca.size)
    }

    @Test
    fun `concurrent imports do not interleave`() = runTest {
        val parser = EanExcelParser(
            mockk<Context>(relaxed = true),
            mockk<EanProductDao>(relaxed = true),
        )
        val events = mutableListOf<String>()
        suspend fun guarded(name: String) = parser.withImportLock {
            events.add("$name-start")
            delay(50)
            events.add("$name-end")
        }
        val a = async { guarded("a") }
        val b = async { guarded("b") }
        a.await()
        b.await()
        // Uno termina antes de que empiece el otro (orden cualquiera).
        val aSpan = events.indexOf("a-start")..events.indexOf("a-end")
        val bSpan = events.indexOf("b-start")..events.indexOf("b-end")
        assertTrue(
            "intercalado: $events",
            aSpan.last < bSpan.first || bSpan.last < aSpan.first,
        )
    }

    @Test
    fun `super xlsx parses products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao)

        val result = parser.loadFromFile("src/main/assets/ean_super.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse CASO Y CIA super products", (result.getOrNull() ?: 0) > 0)
    }
}
