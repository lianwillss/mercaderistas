package com.rutamercaderistas.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Resultado asíncrono de la sesión de PackageInstaller. Lleva el id de la
 * sesión para que el ViewModel descarte resultados atrasados de una sesión
 * abandonada y no marque como fallido el intento actual.
 */
sealed interface InstallOutcome {
    val sessionId: Int

    data class Success(override val sessionId: Int) : InstallOutcome

    data class Failed(
        override val sessionId: Int,
        val message: String,
    ) : InstallOutcome
}

/**
 * Puente entre el BroadcastReceiver de la sesión y el ViewModel. Sin
 * notificaciones: el estado vive en la app y se consume una sola vez.
 */
object UpdateInstallBus {

    private val _events = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)
    val events: SharedFlow<InstallOutcome> = _events.asSharedFlow()

    fun post(outcome: InstallOutcome) {
        _events.tryEmit(outcome)
    }
}
