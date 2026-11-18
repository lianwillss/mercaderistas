package com.rutamercaderistas.data.local

/**
 * Cruce SKU (Cód. Cencosud) → código de barras del catálogo EAN.
 *
 * El detalle de stock lo consume como Flow: cuando el catálogo se reimporta
 * (nuevos assets o EAN_DATA_VERSION), Room reinvalida la consulta y el código
 * nuevo aparece sin reabrir la pantalla.
 */
sealed interface EanSkuLookup {

    /** Todavía no se consultó el catálogo. */
    data object Cargando : EanSkuLookup

    /** Resultado definitivo; null = el producto aún no tiene código catalogado. */
    data class Resultado(val ean: String?) : EanSkuLookup
}
