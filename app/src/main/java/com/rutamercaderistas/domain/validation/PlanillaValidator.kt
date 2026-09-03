package com.rutamercaderistas.domain.validation

import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.models.EntradaRuta

data class ValidationError(
    val row: Int,
    val field: String,
    val message: String,
    val value: String = "",
)

object PlanillaValidator {

    fun validateRutero(entries: List<EntradaRuta>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val seenCodigos = mutableMapOf<String, Int>()

        entries.forEachIndexed { index, e ->
            val row = index + 2 // +1 header, +1 1-based
            if (e.local.isBlank()) {
                errors.add(ValidationError(row, "Local", "Local sin nombre", e.local))
            }
            if (e.direccion.isBlank()) {
                errors.add(ValidationError(row, "Dirección", "Sin dirección", e.codigo))
            }
            if (e.comuna.isBlank()) {
                errors.add(ValidationError(row, "Comuna", "Sin comuna", e.codigo))
            }
            if (e.codigo.isNotBlank()) {
                val prev = seenCodigos[e.codigo]
                if (prev != null) {
                    errors.add(ValidationError(row, "Cód. Cencosud", "Duplicado (fila $prev)", e.codigo))
                } else {
                    seenCodigos[e.codigo] = row
                }
            }
        }
        return errors
    }

    fun validateEan(products: List<EanProductEntity>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val seenEan = mutableMapOf<String, Int>()
        val seenSku = mutableMapOf<String, Int>()

        products.forEachIndexed { index, p ->
            val row = index + 2
            if (p.eanPrincipal.isNotBlank()) {
                if (!p.eanPrincipal.all { it.isDigit() } || p.eanPrincipal.length !in 8..14) {
                    errors.add(ValidationError(row, "EAN", "EAN inválido", p.eanPrincipal))
                }
                val prev = seenEan[p.eanPrincipal]
                if (prev != null) {
                    errors.add(ValidationError(row, "EAN", "Duplicado (fila $prev)", p.eanPrincipal))
                } else {
                    seenEan[p.eanPrincipal] = row
                }
            }
            if (p.codCencosud.isNotBlank()) {
                val prev = seenSku[p.codCencosud]
                if (prev != null) {
                    errors.add(ValidationError(row, "SKU", "Duplicado (fila $prev)", p.codCencosud))
                } else {
                    seenSku[p.codCencosud] = row
                }
            }
            if (p.descripcionProducto.isBlank() && p.eanPrincipal.isNotBlank()) {
                errors.add(ValidationError(row, "Descripción", "Sin descripción", p.eanPrincipal))
            }
        }
        return errors
    }
}
