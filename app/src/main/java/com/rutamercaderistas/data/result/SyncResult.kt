package com.rutamercaderistas.data.result

sealed interface SyncResult<out T> {
    data class Success<T>(val data: T) : SyncResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult<Nothing>
    data object NoChange : SyncResult<Nothing>
    data object Offline : SyncResult<Nothing>
}

sealed interface UpdateResult {
    data class Available(val versionName: String, val versionCode: Int, val apkUrl: String) : UpdateResult
    data object UpToDate : UpdateResult
    data class Error(val message: String) : UpdateResult
}

fun <T> SyncResult<T>.getOrNull(): T? = when (this) {
    is SyncResult.Success -> data
    else -> null
}

fun SyncResult<*>.messageOrNull(): String? = when (this) {
    is SyncResult.Error -> message
    is SyncResult.Offline -> "Sin conexión a Internet"
    is SyncResult.NoChange -> null
    is SyncResult.Success -> null
}
