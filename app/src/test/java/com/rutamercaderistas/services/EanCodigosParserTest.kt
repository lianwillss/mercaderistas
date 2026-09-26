package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.EanProductDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
