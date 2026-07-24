package com.rutamercaderistas.ui.theme

sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Error(val message: String) : ScreenState<Nothing>
    data class Success<T>(val data: T) : ScreenState<T>
    data object Empty : ScreenState<Nothing>
}
