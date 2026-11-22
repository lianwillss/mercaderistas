package com.rutamercaderistas.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Caracterización del EAN: lo escribe el usuario, lo escanea la cámara o viene
 * del catálogo. El dígito verificador evita tratar un SKU numérico como EAN.
 */
class EanCodigoTest {

    @Test
    fun `UPC-A de 12 digitos se normaliza a EAN-13 con cero inicial`() {
        assertEquals("0859547004701", EanCodigo.normalizar("859547004701"))
        assertEquals("0859547004701", EanCodigo.normalizar("0859547004701"))
    }

    @Test
    fun `EAN-13 valido se acepta`() {
        assertEquals("5901234123457", EanCodigo.valido("5901234123457"))
    }

    @Test
    fun `EAN-8 valido se acepta`() {
        assertEquals("12345670", EanCodigo.valido("12345670"))
    }

    @Test
    fun `digito verificador invalido no es EAN`() {
        assertNull(EanCodigo.valido("5901234123458"))
        assertNull(EanCodigo.valido("12345671"))
    }

    @Test
    fun `un SKU numerico corto no es EAN`() {
        assertNull(EanCodigo.valido("1986305"))
        assertNull(EanCodigo.valido("2088850"))
    }

    @Test
    fun `texto con letras no es EAN`() {
        assertNull(EanCodigo.valido("aceite"))
        assertNull(EanCodigo.valido(""))
    }

    @Test
    fun `solo los digitos cuentan`() {
        assertEquals("5901234123457", EanCodigo.normalizar("590-1234-12345 7"))
        assertNull(EanCodigo.normalizar("590-1234-123"))
    }
}
