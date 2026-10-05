package com.rutamercaderistas.util

import com.rutamercaderistas.data.local.EanProductEntity

const val EAN_SHARE_MAX_ITEMS = 200

/**
 * Texto para compartir una lista EAN filtrada (WhatsApp, Gmail...).
 * Organizado por marca con el EAN en su propia línea para copiarlo fácil;
 * sin SKU ni caja para no saturar. Nunca sale del tamaño razonable
 * gracias a [maxItems]. Puro y testeable.
 *
 * Formato: título, una sección por marca (emoji etiqueta + nombre +
 * cantidad), y por producto una línea con el nombre más una línea
 * indentada con el EAN (emoji números, sin SKU ni caja).
 */
fun buildEanListShareText(
    title: String,
    products: List<EanProductEntity>,
    maxItems: Int = EAN_SHARE_MAX_ITEMS,
    moreLine: ((Int) -> String)? = null,
): String = buildString {
    appendLine(title)
    val shown = products.take(maxItems)
    val grouped = shown.groupBy { it.marca.ifBlank { "Sin marca" } }
    val orderedBrands = grouped.keys.sortedWith(
        compareByDescending<String> { grouped.getValue(it).size }.thenBy { it }
    )
    for (brand in orderedBrands) {
        val items = grouped.getValue(brand).sortedBy { it.descripcionProducto }
        appendLine()
        appendLine("\uD83C\uDFF7\uFE0F $brand (${items.size})")
        for (p in items) {
            val name = p.descripcionProducto.ifBlank { p.eanPrincipal }
            appendLine("• $name")
            val ean = p.eanPrincipal.ifBlank { p.codCencosud }
            if (ean.isNotBlank()) {
                appendLine("  \uD83D\uDD22 $ean")
            }
        }
    }
    if (products.size > maxItems && moreLine != null) {
        appendLine()
        appendLine(moreLine(products.size - maxItems))
    }
}
