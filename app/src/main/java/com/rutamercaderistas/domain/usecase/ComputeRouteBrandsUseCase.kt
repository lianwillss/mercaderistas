package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.utils.cleanBrand
import com.rutamercaderistas.utils.normalizeBrand
import javax.inject.Inject

class ComputeRouteBrandsUseCase @Inject constructor() {

    operator fun invoke(locales: List<LocalDelDia>): Set<String> {
        return locales.flatMap { local ->
            local.clientes.map { normalizeBrand(it.nombre).cleanBrand() }
        }.toSet()
    }
}
