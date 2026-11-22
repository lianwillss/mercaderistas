package com.rutamercaderistas.data.local

/**
 * Normalización y validación de códigos EAN/UPC del catálogo.
 *
 * Compartido por el escáner de Cód. EAN y por la búsqueda de Stock: un código
 * escrito o escaneado debe llegar igual a ambas pantallas. El dígito
 * verificador se valida para no resolver como EAN un SKU numérico o un texto
 * con dígitos.
 */
object EanCodigo {

    /** Longitudes que se aceptan: EAN-8, UPC-A (12), EAN-13 y GTIN-14. */
    private val LONGITUDES_VALIDAS = setOf(8, 13, 14)

    /**
     * EAN normalizado (sólo dígitos) o null si el texto no corresponde.
     *
     * UPC-A de 12 dígitos se convierte a EAN-13 anteponiendo cero, igual que
     * al importar el catálogo: así el código escaneado encuentra el EAN-13.
     */
    fun normalizar(crud: String): String? {
        val digits = crud.filter { it.isDigit() }
        return when (digits.length) {
            12 -> "0$digits"
            8, 13, 14 -> digits
            else -> null
        }
    }

    /**
     * Dígito verificador (modo 10) de EAN-8, EAN-13 y GTIN-14.
     *
     * Desde el dígito más a la derecha del cuerpo los pesos alternan 3, 1, 3…
     * que es la regla común de los tres formatos.
     */
    fun digitoVerificadorOk(ean: String): Boolean {
        if (ean.length !in LONGITUDES_VALIDAS) return false
        if (ean.any { !it.isDigit() }) return false
        var suma = 0
        var peso = 3
        for (i in ean.length - 2 downTo 0) {
            suma += (ean[i] - '0') * peso
            peso = if (peso == 3) 1 else 3
        }
        val esperado = (10 - suma % 10) % 10
        return esperado == (ean.last() - '0')
    }

    /**
     * EAN normalizado y con dígito verificador correcto; null si el texto no
     * es un EAN válido (entonces se trata como SKU o nombre).
     */
    fun valido(crud: String): String? {
        val ean = normalizar(crud) ?: return null
        return if (digitoVerificadorOk(ean)) ean else null
    }
}
