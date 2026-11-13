package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockAccessUiState(
    val email: String = "",
    val password: String = "",
    val conectado: Boolean = false,
    val trabajando: Boolean = false,
    val mensaje: String? = null,
    val error: Boolean = false,
    val esDemo: Boolean = false,
)

/** Acceso a KPI One administrado desde Ajustes, no desde la web. */
@HiltViewModel
class StockAccessViewModel @Inject constructor(
    private val repo: StockRepository,
    private val client: KpiOneClient,
    private val session: KpiSessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        StockAccessUiState(
            email = session.credenciales()?.first.orEmpty(),
            conectado = session.hasSession.value,
            esDemo = repo.isDemo,
        ),
    )
    val state: StateFlow<StockAccessUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.hasSession.collect { haySesion ->
                _state.update { it.copy(conectado = haySesion) }
            }
        }
    }

    fun onEmail(valor: String) {
        _state.update { it.copy(email = valor, mensaje = null) }
    }

    fun onPassword(valor: String) {
        _state.update { it.copy(password = valor, mensaje = null) }
    }

    fun conectar() {
        val actual = _state.value
        if (actual.email.isBlank() || actual.password.isBlank()) {
            _state.update { it.copy(mensaje = "Escribe correo y clave", error = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(trabajando = true, mensaje = null, error = false) }
            val ok = runCatching { client.login(actual.email, actual.password) }
                .getOrDefault(false)
            if (ok) session.guardarCredenciales(actual.email.trim(), actual.password)
            _state.update {
                it.copy(
                    trabajando = false,
                    conectado = ok,
                    password = if (ok) "" else it.password,
                    mensaje = if (ok) "Conectado a KPI One" else "No se pudo conectar. Revisa correo y clave.",
                    error = !ok,
                )
            }
        }
    }

    fun desconectar() {
        viewModelScope.launch {
            runCatching {
                // El router también borra la caché local de la última búsqueda.
                repo.desconectar()
            }
            _state.update {
                it.copy(conectado = false, password = "", mensaje = "Acceso eliminado", error = false)
            }
        }
    }
}
