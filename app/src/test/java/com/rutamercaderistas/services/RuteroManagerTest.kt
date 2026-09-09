package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.models.EntradaRuta
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuteroManagerTest {

    private lateinit var filesDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        val tempDir = Files.createTempDirectory("rutero-manager-test").toFile()
        filesDir = tempDir
        context = mockk {
            every { filesDir } returns tempDir
        }
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `invalid excel does not replace the previous file`() = kotlinx.coroutines.test.runTest {
        val target = File(filesDir, RuteroManager.EXCEL_FILE_NAME)
        target.writeText("old-valid-cache")
        val manager = RuteroManager(context, mockk<RouteEntryDao>(relaxed = true))

        assertFalse(manager.saveMasterExcel("not-an-excel".toByteArray()))
        assertEquals("old-valid-cache", target.readText())
    }

    @Test
    fun `master import requires routes and meaningful route fields`() {
        val valid = EntradaRuta(
            reponedor = "",
            rutero = "RUTA-1",
            codigo = "001",
            local = "Local A",
            direccion = "",
            cliente = "Marca A",
        )
        val missingRequiredFields = valid.copy(codigo = "", local = "", cliente = "")

        assertTrue(isValidMasterImport(listOf("RUTA-1"), listOf(valid)))
        assertFalse(isValidMasterImport(emptyList(), listOf(valid)))
        assertFalse(isValidMasterImport(listOf("RUTA-1"), emptyList()))
        assertFalse(isValidMasterImport(listOf("RUTA-1"), listOf(missingRequiredFields)))
    }
}
