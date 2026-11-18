package com.rutamercaderistas.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Máquina de estados única para todo el flujo de actualización.
 * Persistida en DataStore para sobrevivir a recreación de Activity o muerte del proceso.
 */
sealed interface UpdateState : Parcelable {
    /** Versión objetivo de la actualización. */
    val versionName: String
    /** versionCode objetivo de la actualización. */
    val versionCode: Int

    /** No hay actualización pendiente ni en curso. */
    object Idle : UpdateState {
        override val versionName: String = ""
        override val versionCode: Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        override fun describeContents(): Int = 0
    }

    /** Descargando el APK con progreso, velocidad y ETA suavizados. */
    data class Downloading(
        override val versionName: String,
        override val versionCode: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0L,
        val etaSeconds: Long? = null,
        val installedVersionName: String = "",
        val apkSize: Long = 0L,
    ) : UpdateState {
        val percent: Int
            get() = if (totalBytes <= 0L) 0
            else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
            parcel.writeLong(downloadedBytes)
            parcel.writeLong(totalBytes)
            parcel.writeLong(bytesPerSecond)
            parcel.writeLong(etaSeconds ?: -1L)
            parcel.writeString(installedVersionName)
            parcel.writeLong(apkSize)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Downloading> = object : Parcelable.Creator<Downloading> {
                override fun createFromParcel(parcel: Parcel): Downloading {
                    return Downloading(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                        downloadedBytes = parcel.readLong(),
                        totalBytes = parcel.readLong(),
                        bytesPerSecond = parcel.readLong(),
                        etaSeconds = parcel.readLong().takeIf { it >= 0 },
                        installedVersionName = parcel.readString() ?: "",
                        apkSize = parcel.readLong(),
                    )
                }
                override fun newArray(size: Int): Array<Downloading?> = arrayOfNulls(size)
            }
        }
    }

    /** Verificando el APK descargado (tamaño, SHA-256, versión, firma) antes de instalar. */
    data class Verifying(
        override val versionName: String,
        override val versionCode: Int,
        val installedVersionName: String = "",
        val apkSize: Long = 0L,
    ) : UpdateState {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
            parcel.writeString(installedVersionName)
            parcel.writeLong(apkSize)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Verifying> = object : Parcelable.Creator<Verifying> {
                override fun createFromParcel(parcel: Parcel): Verifying {
                    return Verifying(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                        installedVersionName = parcel.readString() ?: "",
                        apkSize = parcel.readLong(),
                    )
                }
                override fun newArray(size: Int): Array<Verifying?> = arrayOfNulls(size)
            }
        }
    }

    /** APK verificado, listo para instalar. El instalador se lanza automáticamente. */
    data class ReadyToInstall(
        override val versionName: String,
        override val versionCode: Int,
        val installedVersionName: String = "",
        val apkSize: Long = 0L,
        /** true si ya se intentó lanzar el instalador y Android no completó la instalación. */
        val installAttempted: Boolean = false,
    ) : UpdateState {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
            parcel.writeString(installedVersionName)
            parcel.writeLong(apkSize)
            parcel.writeInt(if (installAttempted) 1 else 0)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<ReadyToInstall> = object : Parcelable.Creator<ReadyToInstall> {
                override fun createFromParcel(parcel: Parcel): ReadyToInstall {
                    return ReadyToInstall(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                        installedVersionName = parcel.readString() ?: "",
                        apkSize = parcel.readLong(),
                        installAttempted = parcel.readInt() == 1,
                    )
                }
                override fun newArray(size: Int): Array<ReadyToInstall?> = arrayOfNulls(size)
            }
        }
    }

    /** Instalando: sesión de PackageInstaller commiteada, esperando confirmación del usuario. */
    data class Installing(
        override val versionName: String,
        override val versionCode: Int,
        val installedVersionName: String = "",
        /** La sesión quedó esperando la autorización del usuario en Android. */
        val waitingForUser: Boolean = false,
        /** ID de la sesión de PackageInstaller para poder abandonarla si hace falta. */
        val sessionId: Int = 0,
    ) : UpdateState {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
            parcel.writeString(installedVersionName)
            parcel.writeInt(if (waitingForUser) 1 else 0)
            parcel.writeInt(sessionId)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Installing> = object : Parcelable.Creator<Installing> {
                override fun createFromParcel(parcel: Parcel): Installing {
                    return Installing(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                        installedVersionName = parcel.readString() ?: "",
                        waitingForUser = parcel.readInt() == 1,
                        sessionId = parcel.readInt(),
                    )
                }
                override fun newArray(size: Int): Array<Installing?> = arrayOfNulls(size)
            }
        }
    }

    /** Actualización completada con éxito. Se muestra una vez al reabrir la app. */
    data class Installed(
        override val versionName: String,
        override val versionCode: Int,
    ) : UpdateState {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Installed> = object : Parcelable.Creator<Installed> {
                override fun createFromParcel(parcel: Parcel): Installed {
                    return Installed(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                    )
                }
                override fun newArray(size: Int): Array<Installed?> = arrayOfNulls(size)
            }
        }
    }

    /** Error recuperable o permanente. */
    data class Error(
        override val versionName: String,
        override val versionCode: Int,
        val message: String,
        val retryable: Boolean = true,
    ) : UpdateState {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(versionName)
            parcel.writeInt(versionCode)
            parcel.writeString(message)
            parcel.writeInt(if (retryable) 1 else 0)
        }
        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Error> = object : Parcelable.Creator<Error> {
                override fun createFromParcel(parcel: Parcel): Error {
                    return Error(
                        versionName = parcel.readString() ?: "",
                        versionCode = parcel.readInt(),
                        message = parcel.readString() ?: "",
                        retryable = parcel.readInt() == 1,
                    )
                }
                override fun newArray(size: Int): Array<Error?> = arrayOfNulls(size)
            }
        }
    }

    /** Helper para serializar a JSON para DataStore. */
    fun toJson(): String {
        return when (this) {
            is Idle -> "{\"type\":\"idle\"}"
            is Downloading -> "{\"type\":\"downloading\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode +
                ",\"downloadedBytes\":" + downloadedBytes + ",\"totalBytes\":" + totalBytes + ",\"bytesPerSecond\":" + bytesPerSecond +
                ",\"etaSeconds\":" + (etaSeconds ?: -1) + ",\"installedVersionName\":\"" + installedVersionName + "\",\"apkSize\":" + apkSize + "}"
            is Verifying -> "{\"type\":\"verifying\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode +
                ",\"installedVersionName\":\"" + installedVersionName + "\",\"apkSize\":" + apkSize + "}"
            is ReadyToInstall -> "{\"type\":\"ready\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode +
                ",\"installedVersionName\":\"" + installedVersionName + "\",\"apkSize\":" + apkSize +
                ",\"installAttempted\":" + installAttempted + "}"
            is Installing -> "{\"type\":\"installing\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode +
                ",\"installedVersionName\":\"" + installedVersionName + "\",\"waitingForUser\":" + waitingForUser +
                ",\"sessionId\":" + sessionId + "}"
            is Installed -> "{\"type\":\"installed\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode + "}"
            is Error -> "{\"type\":\"error\",\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode +
                ",\"message\":\"" + message.replace("\"", "\\\"") + "\",\"retryable\":" + retryable + "}"
        }
    }

    companion object {
        /** Deserializa desde JSON guardado en DataStore. */
        fun fromJson(json: String?): UpdateState? {
            if (json.isNullOrBlank()) return null
            try {
                val obj = org.json.JSONObject(json)
                val type = obj.optString("type", "")
                return when (type) {
                    "idle" -> Idle
                    "downloading" -> Downloading(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        bytesPerSecond = obj.optLong("bytesPerSecond", 0L),
                        etaSeconds = obj.optLong("etaSeconds", -1L).takeIf { it >= 0 },
                        installedVersionName = obj.optString("installedVersionName", ""),
                        apkSize = obj.optLong("apkSize", 0L),
                    )
                    "verifying" -> Verifying(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                        installedVersionName = obj.optString("installedVersionName", ""),
                        apkSize = obj.optLong("apkSize", 0L),
                    )
                    "ready" -> ReadyToInstall(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                        installedVersionName = obj.optString("installedVersionName", ""),
                        apkSize = obj.optLong("apkSize", 0L),
                        installAttempted = obj.optBoolean("installAttempted", false),
                    )
                    "installing" -> Installing(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                        installedVersionName = obj.optString("installedVersionName", ""),
                        waitingForUser = obj.optBoolean("waitingForUser", false),
                        sessionId = obj.optInt("sessionId", 0),
                    )
                    "installed" -> Installed(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                    )
                    "error" -> Error(
                        versionName = obj.optString("versionName", ""),
                        versionCode = obj.optInt("versionCode", 0),
                        message = obj.optString("message", ""),
                        retryable = obj.optBoolean("retryable", true),
                    )
                    else -> null
                }
            } catch (e: Exception) {
                return null
            }
        }
    }
}

/** Claves de DataStore para persistir el estado de actualización. */
internal object UpdateStateKeys {
    const val STATE_JSON = "update_state_json"
    const val INSTALL_ATTEMPTED_CODE = "update_install_attempted_code"
    const val INSTALL_SESSION_ID = "install_session_id"
    const val REJECTED_UPDATE_TAG = "rejected_update_tag"
    const val UPDATE_SUPPRESSED_UNTIL = "update_suppressed_until"
    const val LAST_UPDATE_CHECK = "last_update_check"
    const val PENDING_UPGRADE_NOTICE = "pending_upgrade_notice"
    const val LAST_MAIN_TAB = "last_main_tab"
    const val LAST_VERSION_CODE = "last_version_code"
}

@Suppress("UNUSED_PARAMETER")
internal fun Preferences.toUpdateState(): UpdateState? = UpdateState.fromJson(this[stringPreferencesKey(UpdateStateKeys.STATE_JSON)])

internal fun Preferences.toInstallAttemptedCode(): Int = this[intPreferencesKey(UpdateStateKeys.INSTALL_ATTEMPTED_CODE)] ?: 0

internal fun Preferences.toInstallSessionId(): Int = this[intPreferencesKey(UpdateStateKeys.INSTALL_SESSION_ID)] ?: 0

internal fun Preferences.toRejectedUpdateTag(): String = this[stringPreferencesKey(UpdateStateKeys.REJECTED_UPDATE_TAG)].orEmpty()

internal fun Preferences.toUpdateSuppressedUntil(): Long = this[longPreferencesKey(UpdateStateKeys.UPDATE_SUPPRESSED_UNTIL)] ?: 0L

internal fun Preferences.toLastUpdateCheck(): Long = this[longPreferencesKey(UpdateStateKeys.LAST_UPDATE_CHECK)] ?: 0L

internal fun Preferences.toPendingUpgradeNotice(): String? = this[stringPreferencesKey(UpdateStateKeys.PENDING_UPGRADE_NOTICE)]

internal fun Preferences.toLastMainTab(): String? = this[stringPreferencesKey(UpdateStateKeys.LAST_MAIN_TAB)]

internal fun Preferences.toLastVersionCode(): Int = this[intPreferencesKey(UpdateStateKeys.LAST_VERSION_CODE)] ?: 0