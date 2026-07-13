package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeChainToLocalesUseCaseTest {

    private val useCase = ComputeChainToLocalesUseCase()

    @Test
    fun `maps chain to locale name`() = runTest {
        val locales = listOf(
            local("COD1", "Local Uno", cadena = "LIDER"),
            local("COD2", "Local Dos", cadena = "JUMBO"),
        )
        val result = useCase(locales)

        assertEquals("Local Uno", result["LIDER"])
        assertEquals("Local Dos", result["JUMBO"])
    }

    @Test
    fun `filters blank chains`() = runTest {
        val locales = listOf(
            local("COD1", "Local Uno", cadena = "LIDER"),
            local("COD2", "Local Dos", cadena = ""),
            local("COD3", "Local Tres", cadena = "  "),
        )
        val result = useCase(locales)

        assertEquals(1, result.size)
        assertEquals("Local Uno", result["LIDER"])
    }

    @Test
    fun `deduplicates by uppercase chain`() = runTest {
        val locales = listOf(
            local("COD1", "Lider Uno", cadena = "LIDER"),
            local("COD2", "Lider Dos", cadena = "lider"),
        )
        val result = useCase(locales)

        assertEquals(1, result.size)
    }

    @Test
    fun `returns first locale when chains are duplicated`() = runTest {
        val locales = listOf(
            local("COD1", "Primero", cadena = "LIDER"),
            local("COD2", "Segundo", cadena = "LIDER"),
        )
        val result = useCase(locales)

        assertEquals("Primero", result["LIDER"])
    }

    @Test
    fun `returns empty map for empty input`() = runTest {
        assertTrue(useCase(emptyList()).isEmpty())
    }

    @Test
    fun `returns empty map when all chains are blank`() = runTest {
        val locales = listOf(
            local("COD1", "Local Uno", cadena = ""),
        )
        assertTrue(useCase(locales).isEmpty())
    }

    private fun local(codigo: String, local: String, cadena: String): LocalDelDia {
        return LocalDelDia(
            codigo = codigo,
            local = local,
            direccion = "",
            cadena = cadena,
            clientes = emptyList(),
        )
    }
}
