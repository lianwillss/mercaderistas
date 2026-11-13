package com.rutamercaderistas.data.stock

/**
 * Aplica los filtros de estado/cobertura/orden sobre las filas que ya vienen
 * acotadas por local, ruta, marca y producto.
 *
 * Lo comparten el repositorio local y el remoto para que una búsqueda en vivo
 * y una del snapshot devuelvan exactamente los mismos conteos y el mismo orden.
 */
fun construirStockResult(
    base: List<StockItem>,
    filtros: StockFilters,
    source: StockSource = StockSource.SNAPSHOT,
    fetchedAt: String? = null,
    total: Int = 0,
    truncated: Boolean = false,
    aviso: String? = null,
): StockResult {
    val sinDuplicados = base.sinDuplicados()
    val unicos = sinDuplicados.distinctBy { it.sku }
    val conteoQuiebre = unicos.count { estadoDe(it) == EstadoTipo.Quiebre }
    val conteoBajo = unicos.count { estadoDe(it) == EstadoTipo.Bajo }
    val conteoDisponible = unicos.count { estadoDe(it) == EstadoTipo.Disponible }
    val conteoNegativos = unicos.count { it.stockUnits < 0.0 }
    val conteoCritico = unicos.count { coberturaDe(it) == CoberturaTipo.Critico }
    val conteoCoberturaBaja = unicos.count { coberturaDe(it) == CoberturaTipo.Bajo }
    val conteoSano = unicos.count { coberturaDe(it) == CoberturaTipo.Sano }
    val conteoSobrestock = unicos.count { coberturaDe(it) == CoberturaTipo.Sobrestock }

    val filtrados = sinDuplicados.filter { item ->
        (filtros.estado == null || estadoDe(item) == filtros.estado) &&
            (filtros.cobertura == null || coberturaDe(item) == filtros.cobertura) &&
            (!filtros.soloQuiebre || estadoDe(item) == EstadoTipo.Quiebre) &&
            (!filtros.soloNegativos || item.stockUnits < 0.0)
    }
    val ordenados = when (filtros.orden) {
        StockSort.GRAVEDAD -> filtrados.sortedWith(
            compareBy(
                { if (it.stockUnits < 0.0) 0 else 1 },
                { if (estadoDe(it) == EstadoTipo.Quiebre) 0 else 1 },
                { it.daysOfInventory ?: Int.MAX_VALUE },
                { it.stockUnits },
            ),
        )
        StockSort.PRODUCTO_AZ -> filtrados.sortedWith(
            compareBy({ it.product.lowercase() }, { it.storeCode }),
        )
        StockSort.STOCK_ASC -> filtrados.sortedBy { it.stockUnits }
        StockSort.STOCK_DESC -> filtrados.sortedByDescending { it.stockUnits }
        // Sin dato va al final: no es "0 días", es "no sé".
        StockSort.MENOR_COBERTURA -> filtrados.sortedBy {
            it.coberturaDias ?: Double.MAX_VALUE
        }
    }
    return StockResult(
        items = ordenados,
        conteoQuiebre = conteoQuiebre,
        conteoBajo = conteoBajo,
        conteoDisponible = conteoDisponible,
        conteoNegativos = conteoNegativos,
        conteoCritico = conteoCritico,
        conteoCoberturaBaja = conteoCoberturaBaja,
        conteoSano = conteoSano,
        conteoSobrestock = conteoSobrestock,
        source = source,
        fetchedAt = fetchedAt,
        total = if (total > 0) total else sinDuplicados.size,
        truncated = truncated,
        aviso = aviso,
    )
}
