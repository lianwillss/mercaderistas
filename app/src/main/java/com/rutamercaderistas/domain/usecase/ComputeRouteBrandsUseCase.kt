package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.utils.cleanBrand
import javax.inject.Inject

class ComputeRouteBrandsUseCase @Inject constructor() {

    operator fun invoke(locales: List<LocalDelDia>): Set<String> {
        return locales.flatMap { local ->
            local.clientes.map { it.nombre.cleanBrand() }
        }.toSet()
    }
}
