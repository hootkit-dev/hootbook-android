package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import io.github.hootkit_dev.hootbook.core.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.data.preferences.*
import io.github.hootkit_dev.hootbook.utils.IconSwitchingUtils

@HiltViewModel
class ThemeViewModel @Inject constructor(
    val preferences: CoreUiPreferencesStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val themeUiState = preferences.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<Int?>(null)
    val error = mutableError.asStateFlow()
    init { command { preferences.ready() } }
    fun updateDarkTheme(value: Boolean?) = update { it.copy(darkTheme = value) }
    fun updateDynamicColor(value: Boolean) = update { it.copy(dynamicColor = value) }
    fun updateAmoledMode(value: Boolean) = update { it.copy(amoled = value) }
    fun updateAppFont(value: AppFont) = update { it.copy(font = value) }
    fun updateThemeStyle(value: ThemeStyle) = update { it.copy(themeStyle = value, dynamicColor = value == ThemeStyle.DYNAMIC) }
    fun updateAccentColor(value: AccentColor) = update { it.copy(accent = value) }
    fun updateBlurEffects(value: Boolean) = update { it.copy(blur = value) }
    fun updateAppIcon(value: AppIcon) = command {
        val previous = preferences.ready().appIcon
        withContext(NonCancellable) {
            try {
                withContext(Dispatchers.Main.immediate) { IconSwitchingUtils.switchAppIcon(context, value) }
                preferences.update { it.copy(appIcon = value) }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main.immediate) { IconSwitchingUtils.switchAppIcon(context, previous) }
                throw failure
            }
        }
    }
    private fun update(transform: (CoreUiPreferences) -> CoreUiPreferences) = command { preferences.update(transform) }
    private fun command(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableError.value = coreFailureText(failure) }
            finally { mutableBusy.value = false }
        }
    }
}
