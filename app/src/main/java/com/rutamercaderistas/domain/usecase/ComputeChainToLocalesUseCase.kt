package com.rutamercaderistas.domain.usecase

import com.rutamercaderistas.models.LocalDelDia
import javax.inject.Inject

class ComputeChainToLocalesUseCase @Inject constructor() {

    operator fun invoke(locales: List<LocalDelDia>): Map<String, String> {
        return locales
            .filter { it.cadena.isNotBlank() }
            .distinctBy { it.cadena.trim().uppercase() }
            .associate { it.cadena.trim().uppercase() to it.local }
    }
}
