package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency

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

@HiltViewModel
class CurrencySettingsViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(CurrencySettingsUiState())
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch { preferences.state.collect { p -> mutable.update { it.copy(
            unifiedCurrencyEnabled = p.unifiedDisplayEnabled, unifiedCurrencyCode = p.unifiedDisplayCurrency,
            defaultCurrencyEnabled = p.defaultEntryEnabled, defaultCurrencyCode = p.defaultEntryCurrency) } } }
        viewModelScope.launch { session.observe(changes, setOf(Table.SETTINGS)) { core ->
            coreQuery { core.settings().use { it.mainCurrency() } }
        }.collect { result -> result.fold(
            onSuccess = { code -> mutable.update { it.copy(mainCurrency = code, loading = false) } },
            onFailure = { error -> mutable.update { it.copy(loading = false, error = coreFailureText(error)) } }) } }
        command { preferences.ready() }
    }
    fun setMainCurrency(code: String) = command { session.withCore { core -> core.settings().use { it.setMainCurrency(code) } } }
    fun toggleUnifiedCurrency() = command { preferences.update { it.copy(unifiedDisplayEnabled = !it.unifiedDisplayEnabled) } }
    fun setUnifiedCurrency(code: String) = command { preferences.update { it.copy(unifiedDisplayCurrency = code) } }
    fun toggleDefaultCurrency() = command { preferences.update { it.copy(defaultEntryEnabled = !it.defaultEntryEnabled) } }
    fun setDefaultCurrency(code: String) = command { preferences.update { it.copy(defaultEntryCurrency = code) } }
    private fun command(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
