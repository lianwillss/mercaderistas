package com.rutamercaderistas.ui.screens

import com.rutamercaderistas.ui.components.CodProvItems
import org.junit.Assert.assertTrue
import org.junit.Test

class CodProvScreenTest {

    @Test
    fun codProvItems_notEmpty() {
        assertTrue(CodProvItems.isNotEmpty())
    }

    @Test
    fun codProvItems_codesNotBlank() {
        CodProvItems.forEach { item ->
            assertTrue(item.code.isNotBlank())
            assertTrue(item.nameRes != 0)
        }
    }

    @Test
    fun codProvItems_sizeMatchesExpected() {
        // 13 proveedores definidos (incluye KHATAR)
        assertTrue(CodProvItems.size == 13)
    }
}
