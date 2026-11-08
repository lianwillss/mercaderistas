package com.rutamercaderistas.data.stock.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda cifrados (Android Keystore) el acceso a KPI One: correo, clave y la
 * cookie de sesión. Nada de esto se escribe en el código ni viaja en la APK.
 */
@Singleton
class SecureCookieStore @Inject constructor(
    @ApplicationContext context: Context,
) : KpiSessionStore {
    private val prefs = context.getSharedPreferences("stock_secure", Context.MODE_PRIVATE)

    private val _hasSession = MutableStateFlow(!prefs.getString(KEY_COOKIE, null).isNullOrBlank())
    override val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    val emailGuardado: String get() = descifrar(prefs.getString(KEY_EMAIL, null)).orEmpty()

    override fun hayCredenciales(): Boolean =
        !prefs.getString(KEY_EMAIL, null).isNullOrBlank() &&
            !prefs.getString(KEY_CLAVE, null).isNullOrBlank()

    override fun credenciales(): Pair<String, String>? {
        val correo = descifrar(prefs.getString(KEY_EMAIL, null))?.takeIf { it.isNotBlank() }
        val clave = descifrar(prefs.getString(KEY_CLAVE, null))?.takeIf { it.isNotBlank() }
        return if (correo != null && clave != null) correo to clave else null
    }

    override fun guardarCredenciales(correo: String, clave: String) {
        prefs.edit()
            .putString(KEY_EMAIL, cifrar(correo))
            .putString(KEY_CLAVE, cifrar(clave))
            .remove(KEY_BLOQUEADO)
            .apply()
    }

    override fun guardarCookie(cookie: String): Boolean {
        val cifrada = cifrar(cookie) ?: return false
        prefs.edit()
            .putString(KEY_COOKIE, cifrada)
            .remove(KEY_BLOQUEADO)
            .apply()
        _hasSession.value = true
        return true
    }

    override fun cookie(): String? = descifrar(prefs.getString(KEY_COOKIE, null))

    override fun borrarSesion() {
        prefs.edit().remove(KEY_COOKIE).apply()
        _hasSession.value = false
    }

    override fun borrarTodo() {
        prefs.edit()
            .clear()
            .putBoolean(KEY_BLOQUEADO, true)
            .apply()
        _hasSession.value = false
    }

    override fun autoLoginPermitido(): Boolean =
        !prefs.getBoolean(KEY_BLOQUEADO, false)

    // ── Cifrado ────────────────────────────────────────────────────

    private fun cifrar(texto: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, clave())
        Base64.encodeToString(
            cipher.iv + cipher.doFinal(texto.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
    }.getOrNull()

    private fun descifrar(guardado: String?): String? {
        if (guardado.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(guardado, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMACION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                clave(),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
            )
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generador.generateKey()
    }

    private companion object {
        const val ALIAS = "kpi_one_cookie"
        const val KEY_COOKIE = "cookie_cifrada"
        const val KEY_EMAIL = "kpi_email"
        const val KEY_CLAVE = "kpi_clave"
        const val KEY_BLOQUEADO = "auto_login_bloqueado"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
