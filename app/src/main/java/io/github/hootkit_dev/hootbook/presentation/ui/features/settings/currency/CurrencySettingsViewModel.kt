package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencySettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrencySettingsUiState())
    val uiState: StateFlow<CurrencySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.unifiedCurrencyEnabled,
                userPreferencesRepository.unifiedCurrencyCode,
                userPreferencesRepository.defaultCurrencyEnabled,
                userPreferencesRepository.defaultCurrencyCode
            ) { unifiedEnabled, unifiedCode, defaultEnabled, defaultCode ->
                CurrencySettingsUiState(
                    unifiedCurrencyEnabled = unifiedEnabled,
                    unifiedCurrencyCode = unifiedCode,
                    defaultCurrencyEnabled = defaultEnabled,
                    defaultCurrencyCode = defaultCode
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleUnifiedCurrency() {
        viewModelScope.launch {
            val newState = !_uiState.value.unifiedCurrencyEnabled
            userPreferencesRepository.setUnifiedCurrencyEnabled(newState)
        }
    }

    fun setUnifiedCurrency(code: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUnifiedCurrencyCode(code)
        }
    }

    fun toggleDefaultCurrency() {
        viewModelScope.launch {
            val newState = !_uiState.value.defaultCurrencyEnabled
            userPreferencesRepository.setDefaultCurrencyEnabled(newState)
        }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultCurrencyCode(code)
        }
    }
}
