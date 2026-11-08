package com.rutamercaderistas.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Resultado asíncrono de la sesión de PackageInstaller. */
sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
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
