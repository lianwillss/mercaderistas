package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeRouteBrandsUseCaseTest {

    private val useCase = ComputeRouteBrandsUseCase()

    @Test
    fun `extracts brands from locales`() = runTest {
        val locales = listOf(
            local("COD1", clientes = listOf(cliente("CUK"), cliente("OLIMPIA"))),
            local("COD2", clientes = listOf(cliente("JUMBO"))),
        )
        val result = useCase(locales)

        assertEquals(3, result.size)
        assertTrue(result.contains("CUK"))
        assertTrue(result.contains("OLIMPIA"))
        assertTrue(result.contains("JUMBO"))
    }

    @Test
    fun `deduplicates brands`() = runTest {
        val locales = listOf(
            local("COD1", clientes = listOf(cliente("CUK"))),
            local("COD2", clientes = listOf(cliente("CUK"))),
        )
        val result = useCase(locales)

        assertEquals(1, result.size)
    }

    @Test
    fun `cleans brand names`() = runTest {
        val locales = listOf(
            local("COD1", clientes = listOf(cliente("⭐ CUK"))),
            local("COD2", clientes = listOf(cliente("OlÍmpia"))),
        )
        val result = useCase(locales)

        assertTrue(result.contains("CUK"))
        assertTrue(result.contains("OLIMPIA"))
    }

    @Test
    fun `returns empty set for empty input`() = runTest {
        assertTrue(useCase(emptyList()).isEmpty())
    }

    @Test
    fun `returns empty set when locales have no clients`() = runTest {
        val locales = listOf(
            local("COD1", clientes = emptyList()),
        )
        assertTrue(useCase(locales).isEmpty())
    }

    private fun local(codigo: String, clientes: List<ClienteInfo>): LocalDelDia {
        return LocalDelDia(
            codigo = codigo,
            local = "Local",
            direccion = "",
            clientes = clientes,
        )
    }

    private fun cliente(nombre: String): ClienteInfo {
        return ClienteInfo(nombre = nombre, esPrioritaria = false, frecuencia = 1)
    }
}
