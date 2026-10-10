package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.preferences.AppFont
import io.github.hootkit_dev.hootbook.data.preferences.ThemeStyle
import io.github.hootkit_dev.hootbook.data.preferences.AccentColor
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val themeUiState: StateFlow<ThemeUiState> = userPreferencesRepository.userPreferences
        .map { preferences ->
            ThemeUiState(
                isDarkTheme = preferences.isDarkThemeEnabled,
                isDynamicColorEnabled = preferences.isDynamicColorEnabled,
                isAmoledMode = preferences.isAmoledMode,
                appFont = preferences.appFont,
                themeStyle = preferences.themeStyle,
                accentColor = preferences.accentColor,
                blurEffects = preferences.blurEffects,
                isOnboardingFinished = preferences.hasShownScanTutorial,
                currentAppIcon = preferences.appIcon,
                isLoaded = true
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState(isLoaded = false)
        )

    fun updateDarkTheme(enabled: Boolean?) {
        viewModelScope.launch {
            userPreferencesRepository.updateDarkThemeEnabled(enabled)
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDynamicColorEnabled(enabled)
        }
    }

    fun updateAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAmoledMode(enabled)
        }
    }


    fun updateAppFont(font: AppFont) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppFont(font)
        }
    }

    fun updateThemeStyle(style: ThemeStyle) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeStyle(style)
        }
    }

    fun updateAccentColor(color: AccentColor) {
        viewModelScope.launch {
            userPreferencesRepository.updateAccentColor(color)
        }
    }

    fun updateBlurEffects(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateBlurEffects(enabled)
        }
    }

    fun updateAppIcon(icon: AppIcon) = viewModelScope.launch {
        userPreferencesRepository.updateAppIcon(icon)
    }
}

data class ThemeUiState(
    val isDarkTheme: Boolean? = null, // null = follow system
    val isDynamicColorEnabled: Boolean = false, // Default to custom theme colors
    val isAmoledMode: Boolean = false,
    val appFont: AppFont = AppFont.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    val accentColor: AccentColor = AccentColor.BLUE,
    val blurEffects: Boolean = false,
    val isOnboardingFinished: Boolean = false,
    val currentAppIcon: AppIcon = AppIcon.ORIGINAL,
    val isLoaded: Boolean = false
)