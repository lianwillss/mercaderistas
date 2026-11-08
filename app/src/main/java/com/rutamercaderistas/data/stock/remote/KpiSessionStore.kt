package com.rutamercaderistas.data.stock.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * Acceso cifrado a la sesión de KPI One. [SecureCookieStore] es la
 * implementación real (Android Keystore); los tests usan una en memoria.
 */
interface KpiSessionStore {
    val hasSession: StateFlow<Boolean>

    fun cookie(): String?
    fun guardarCookie(cookie: String): Boolean
    fun borrarSesion()

    fun hayCredenciales(): Boolean
    fun credenciales(): Pair<String, String>?
    fun guardarCredenciales(correo: String, clave: String)
    fun borrarTodo()

    /** false tras "Desconectar": no volver a entrar con las credenciales del build. */
    fun autoLoginPermitido(): Boolean = true
}
