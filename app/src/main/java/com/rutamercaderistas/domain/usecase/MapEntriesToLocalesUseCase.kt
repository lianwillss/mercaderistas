package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.models.brandVisitDays
import com.rutamercaderistas.models.toNaturalCase
import javax.inject.Inject

class MapEntriesToLocalesUseCase @Inject constructor() {

    operator fun invoke(entries: List<EntradaRuta>): List<LocalDelDia> {
        if (entries.isEmpty()) return emptyList()
        return entries.groupBy { it.codigo.uppercase() + it.local.uppercase() }
            .map { (_, ents) ->
                val first = ents.first()
                LocalDelDia(
                    codigo = first.codigo,
                    local = first.local.toNaturalCase(),
                    direccion = first.direccion.toNaturalCase(),
                    rutero = ents.map { it.rutero }.filter { it.isNotBlank() }.distinct().sorted().joinToString(" · "),
                    cadena = first.cadena,
                    formato = first.formato,
                    region = first.region,
                    comuna = first.comuna,
                    clientes = ents.map { e -> ClienteInfo(e.cliente, e.esPrioritaria, e.frecuencia) }.sortedByDescending { it.esPrioritaria },
                    marcasDias = brandVisitDays(ents),
                )
            }
    }
}