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
}
