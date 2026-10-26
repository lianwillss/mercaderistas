package com.rutamercaderistas.ui.screens

import com.rutamercaderistas.data.local.EanProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EanSearchRowsTest {

    private fun product(ean: String, marca: String, descripcion: String = "P$ean") =
        EanProductEntity(
            eanPrincipal = ean,
            descripcionProducto = descripcion,
            marca = marca,
        )

    @Test
    fun `empty list yields no rows`() {
        assertTrue(buildEanRows(emptyList(), showBrandHeaders = true).isEmpty())
    }

    @Test
    fun `catalog mode inserts contiguous brand headers without reordering`() {
        val items = listOf(
            product("1", "A"),
            product("2", "A"),
            product("3", "B"),
            product("4", ""),
        )
        val rows = buildEanRows(items, showBrandHeaders = true)

        assertEquals(7, rows.size)
        assertEquals(EanResultRow.BrandHeader("A"), rows[0])
        assertEquals(EanResultRow.Product(items[0]), rows[1])
        assertEquals(EanResultRow.Product(items[1]), rows[2])
        assertEquals(EanResultRow.BrandHeader("B"), rows[3])
        assertEquals(EanResultRow.Product(items[2]), rows[4])
        assertEquals(EanResultRow.BrandHeader(""), rows[5])
        assertEquals(EanResultRow.Product(items[3]), rows[6])
    }

    @Test
    fun `search mode keeps SQL order and adds no headers`() {
        val items = listOf(
            product("1", "B"),
            product("2", "A"),
            product("3", "B"),
        )
        val rows = buildEanRows(items, showBrandHeaders = false)

        assertEquals(items.map { EanResultRow.Product(it) }, rows)
    }

    @Test
    fun `repeated non contiguous brand gets its own header`() {
        // El builder nunca ordena: si SQL cambiara el orden, solo refleja lo recibido.
        val items = listOf(product("1", "A"), product("2", "B"), product("3", "A"))
        val rows = buildEanRows(items, showBrandHeaders = true)

        assertEquals(6, rows.size)
        assertEquals(EanResultRow.BrandHeader("A"), rows[0])
        assertEquals(EanResultRow.BrandHeader("B"), rows[2])
        assertEquals(EanResultRow.BrandHeader("A"), rows[4])
    }
}
