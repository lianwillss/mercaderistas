package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EanExcelParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val eanExcelParser: EanExcelParser,
) : ViewModel() {

    val fontScale: StateFlow<Float> = preferencesRepository.getFontScaleFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    val transportMode: StateFlow<String> = preferencesRepository.getTransportModeFlow()
        .map { it ?: "transit" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "transit")

    val versionName: String get() = BuildConfig.VERSION_NAME

    fun setFontScale(value: Float) {
        viewModelScope.launch { preferencesRepository.setFontScale(value) }
    }

    fun setTransportMode(mode: String) {
        viewModelScope.launch { preferencesRepository.setTransportMode(mode) }
    }

    fun clearEanCache() {
        viewModelScope.launch {
            // Fuerza la reimportación desde assets la próxima vez que se abra Cód. EAN.
            eanExcelParser.setEanDataVersion(0)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { preferencesRepository.clearSearchHistory() }
    }
}
