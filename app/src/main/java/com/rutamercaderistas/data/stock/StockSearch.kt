package com.rutamercaderistas.data.stock

/**
 * Aplica los filtros de estado/cobertura/orden sobre las filas que ya vienen
 * acotadas por local, ruta, marca y producto.
 *
 * Lo comparten el repositorio local y el remoto para que una búsqueda en vivo
 * y una del snapshot devuelvan exactamente los mismos conteos y el mismo orden.
 */
/**
 * Ordena las filas ya filtradas. Se separó de [construirStockResult] para
 * reordenar en memoria al cambiar sólo el orden, sin volver a consultar.
 */
fun ordenarStock(items: List<StockItem>, orden: StockSort): List<StockItem> = when (orden) {
    StockSort.PRIORIDAD -> items.sortedWith(
        compareBy(
            {
                when {
                    it.stockUnits < 0.0 -> 0
                    estadoDe(it) == EstadoTipo.Quiebre -> 1
                    else -> 2
                }
            },
            { it.product.lowercase() },
            { it.storeCode },
        ),
    )
    StockSort.GRAVEDAD -> items.sortedWith(
        compareBy(
            {
                when {
                    it.stockUnits >= 0.0 -> 2
                    // Primero lo que hay que verificar: negativo con OSA "sí".
                    validacionNegativo(it) == ValidacionNegativo.CONTRADICE -> 0
                    else -> 1
                }
            },
            { if (estadoDe(it) == EstadoTipo.Quiebre) 0 else 1 },
            { it.daysOfInventory ?: Int.MAX_VALUE },
            { it.stockUnits },
        ),
    )
    StockSort.PRODUCTO_AZ -> items.sortedWith(
        compareBy({ it.product.lowercase() }, { it.storeCode }),
    )
    StockSort.STOCK_ASC -> items.sortedBy { it.stockUnits }
    StockSort.STOCK_DESC -> items.sortedByDescending { it.stockUnits }
    // Sin dato va al final: no es "0 días", es "no sé".
    StockSort.MENOR_COBERTURA -> items.sortedBy {
        it.coberturaDias ?: Double.MAX_VALUE
    }
}

fun construirStockResult(
    base: List<StockItem>,
    filtros: StockFilters,
    source: StockSource = StockSource.SNAPSHOT,
    fetchedAt: String? = null,
    total: Int = 0,
    truncated: Boolean = false,
    aviso: String? = null,
    /** El cruce con OSA ya se intentó para todos los negativos. */
    osaConsultada: Boolean = false,
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
    // Los conteos OSA son por fila (no por SKU): es lo que el usuario ve en
    // la lista de negativos. "Sin OSA" sólo se cuenta si el cruce terminó.
    val conteoConfirmados = sinDuplicados.count {
        it.stockUnits < 0.0 && validacionNegativo(it) == ValidacionNegativo.CONFIRMADO
    }
    val conteoContradicen = sinDuplicados.count {
        it.stockUnits < 0.0 && validacionNegativo(it) == ValidacionNegativo.CONTRADICE
    }
    val conteoSinOsa = if (osaConsultada) {
        sinDuplicados.count {
            it.stockUnits < 0.0 && validacionNegativo(it) == ValidacionNegativo.SIN_DATO
        }
    } else {
        0
    }

    val filtrados = sinDuplicados.filter { item ->
        (filtros.estado == null || estadoDe(item) == filtros.estado) &&
            (filtros.cobertura == null || coberturaDe(item) == filtros.cobertura) &&
            (!filtros.soloQuiebre || estadoDe(item) == EstadoTipo.Quiebre) &&
            (!filtros.soloNegativos || item.stockUnits < 0.0) &&
            (filtros.validacion == null ||
                (item.stockUnits < 0.0 && validacionNegativo(item) == filtros.validacion))
    }
    val ordenados = ordenarStock(filtrados, filtros.orden)
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
        conteoConfirmados = conteoConfirmados,
        conteoContradicen = conteoContradicen,
        conteoSinOsa = conteoSinOsa,
        osaConsultada = osaConsultada,
        source = source,
        fetchedAt = fetchedAt,
        total = if (total > 0) total else sinDuplicados.size,
        truncated = truncated,
        aviso = aviso,
    )
}
