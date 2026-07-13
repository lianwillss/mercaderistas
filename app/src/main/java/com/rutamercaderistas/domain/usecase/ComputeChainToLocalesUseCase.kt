package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.components.effectiveChain
import javax.inject.Inject

class ComputeChainToLocalesUseCase @Inject constructor() {

    operator fun invoke(locales: List<LocalDelDia>): Map<String, String> {
        return locales
            .map { it to effectiveChain(it.cadena, it.formato).trim().uppercase() }
            .filter { (_, chain) -> chain.isNotBlank() }
            .distinctBy { (_, chain) -> chain }
            .associate { (local, chain) -> chain to local.local }
    }
}
