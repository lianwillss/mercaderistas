package com.rutamercaderistas.ui.screens

import com.rutamercaderistas.data.local.EanProductEntity

/**
 * Fila de la lista EAN. La separación existe para poder intercalar el
 * encabezado de marca sin reordenar: el orden de las filas es **siempre** el
 * que devuelve SQL (ranking en búsquedas, marca/descripción en el catálogo).
 */
internal sealed interface EanResultRow {
    data class BrandHeader(val brand: String) : EanResultRow
    data class Product(val product: EanProductEntity) : EanResultRow
}

/**
 * Camina [items] en el orden recibido e inserta un encabezado cada vez que
 * cambia la marca (agrupación contigua, sin reordenar).
 *
 * [showBrandHeaders] se activa solo al navegar el catálogo: con texto de
 * búsqueda el orden es por relevancia y un encabezado por producto sería ruido.
 */
internal fun buildEanRows(
    items: List<EanProductEntity>,
    showBrandHeaders: Boolean,
): List<EanResultRow> {
    if (items.isEmpty()) return emptyList()
    val rows = ArrayList<EanResultRow>(items.size + if (showBrandHeaders) 8 else 0)
    var lastBrand: String? = null
    for (product in items) {
        if (showBrandHeaders && product.marca != lastBrand) {
            rows.add(EanResultRow.BrandHeader(product.marca))
            lastBrand = product.marca
        }
        rows.add(EanResultRow.Product(product))
    }
    return rows
}
