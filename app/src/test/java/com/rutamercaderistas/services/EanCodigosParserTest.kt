package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.EanProductDao
import io.mockk.coEvery
import io.mockk.mockk
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

        val parser = EanExcelParser(context, dao, mockk(relaxed = true))
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
    }

    @Test
    fun `asmode xlsx parses products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

        val result = parser.loadFromFile("src/main/assets/ean_asmode.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse ASMODE products", (result.getOrNull() ?: 0) > 0)
    }

    @Test
    fun `bwild xlsx parses products`() = runTest {
        val dao = mockk<EanProductDao>(relaxed = true)
        coEvery { dao.clearAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

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
    fun `concurrent imports do not interleave`() = runTest {
        val parser = EanExcelParser(
            mockk<Context>(relaxed = true),
            mockk<EanProductDao>(relaxed = true),
            mockk(relaxed = true),
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
        val parser = EanExcelParser(mockk<Context>(relaxed = true), dao, mockk(relaxed = true))

        val result = parser.loadFromFile("src/main/assets/ean_super.xlsx")

        assertTrue("parser should succeed", result.isSuccess)
        assertTrue("should parse CASO Y CIA super products", (result.getOrNull() ?: 0) > 0)
    }
}
