package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EanDedupeTest {

    private val parser = EanExcelParser(
        context = mockk<Context>(relaxed = true),
        eanProductDao = mockk<EanProductDao>(relaxed = true),
    )

    @Test
    fun `richer duplicate wins and blank fields are merged`() {
        val sparse = EanProductEntity(
            eanPrincipal = "7800000000001",
            marca = "CUK",
            conversion = "8",
        )
        val complete = EanProductEntity(
            eanPrincipal = "7800000000001",
            codCencosud = "2092204",
            descripcionProducto = "POSTRE SUSPIRO",
            marca = "CUK",
        )

        val result = parser.dedupeEanProducts(listOf(sparse, complete))
        val product = result.products.single()

        assertEquals(1, result.duplicatesMerged)
        assertEquals("2092204", product.codCencosud)
        assertEquals("POSTRE SUSPIRO", product.descripcionProducto)
        assertEquals("8", product.conversion)
    }

    @Test
    fun `products without ean or sku are kept`() {
        val result = parser.dedupeEanProducts(
            listOf(EanProductEntity(descripcionProducto = "Sin código")),
        )

        assertEquals(1, result.products.size)
        assertTrue(result.products.single().eanPrincipal.isBlank())
    }
}
