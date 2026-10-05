package com.rutamercaderistas.util

import com.rutamercaderistas.data.local.EanProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EanShareTextTest {

    private fun product(
        desc: String = "Pistacho 500g",
        ean: String = "7801234567890",
        sku: String = "123",
        brand: String = "NAT NATURAL",
        conversion: String = "24",
    ) = EanProductEntity(
        descripcionProducto = desc,
        eanPrincipal = ean,
        codCencosud = sku,
        marca = brand,
        conversion = conversion,
    )

    @Test
    fun `agrupa por marca con EAN en su linea y sin SKU ni caja`() {
        val text = buildEanListShareText("T", listOf(product()))
        assertTrue(text.contains("NAT NATURAL (1)"))
        assertTrue(text.contains("• Pistacho 500g"))
        assertTrue(text.contains("7801234567890"))
        assertTrue(!text.contains("SKU"))
        assertTrue(!text.contains("Caja"))
    }

    @Test
    fun `sin EAN no hay linea de codigo`() {
        val text = buildEanListShareText("T", listOf(product(ean = "", sku = "", brand = "")))
        assertTrue(text.contains("Sin marca (1)"))
        assertTrue(!text.contains("🔢"))
    }

    @Test
    fun `ordena marcas por cantidad y productos por nombre`() {
        val products = listOf(
            product(desc = "Zeta", brand = "B"),
            product(desc = "Alfa", brand = "B"),
            product(desc = "Solo", brand = "A"),
        )
        val text = buildEanListShareText("T", products)
        val bIndex = text.indexOf("B (2)")
        val aIndex = text.indexOf("A (1)")
        assertTrue(bIndex >= 0 && aIndex > bIndex)
        assertTrue(text.indexOf("Alfa") < text.indexOf("Zeta"))
    }

    @Test
    fun `corta en maxItems con linea de resto`() {
        val products = List(5) { product(desc = "P$it", ean = "1$it") }
        val text = buildEanListShareText("T", products, maxItems = 2) { n -> "…y $n más" }
        assertTrue(text.contains("P0"))
        assertTrue(text.contains("P1"))
        assertTrue(!text.contains("P2"))
        assertTrue(text.contains("…y 3 más"))
    }

    @Test
    fun `titulo va primero y lista vacia no trae secciones`() {
        val text = buildEanListShareText("Mi título", emptyList())
        assertEquals("Mi título", text.lines().first())
        assertEquals(0, text.lines().filter { it.startsWith("•") }.size)
    }
}
