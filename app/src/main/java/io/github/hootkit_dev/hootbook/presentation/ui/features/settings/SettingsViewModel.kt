package io.github.hootkit_dev.hootbook.presentation.ui.features.settings

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
import io.github.hootkit_dev.hootbook.presentation.app.LocaleController
import io.github.hootkit_dev.hootbook.presentation.app.UiLocalePreference

@HiltViewModel
class SettingsViewModel internal constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    val preferences: CoreUiPreferencesStore,
    val assets: CorePlatformAssets,
    private val locale: UiLocalePreference,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, preferences: CoreUiPreferencesStore,
        assets: CorePlatformAssets, locale: LocaleController) : this(session, changes, preferences, assets, locale as UiLocalePreference)
    val userPreferences = preferences.state
    private val count = MutableStateFlow(0u)
    val totalTransactions = count.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    init {
        viewModelScope.launch { session.observe(changes, setOf(Table.TRANSACTIONS)) { core -> coreQuery {
            core.ledger().use { it.list(TransactionFilter(null, null, emptyList(), emptyList(), emptyList(), null, null,
                DeletedFilter.EXCLUDE, SortOrder.NEWEST_FIRST), PageRequest(0u, 1u)).total }
        } }.collect { result -> result.fold(onSuccess = { count.value = it }, onFailure = { failure.value = coreFailureText(it) }) } }
        command { preferences.ready() }
    }
    fun language(selection: String) = command {
        val previous = locale.selection()
        session.withCore { core -> core.settings().use { settings ->
            settings.setLocale(locale.effectiveTag(selection))
            withContext(NonCancellable) {
                try { locale.apply(selection) }
                catch (error: Exception) { settings.setLocale(locale.effectiveTag(previous)); throw error }
            }
        } }
    }
    private fun command(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true; failure.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure.value = coreFailureText(error) }
            finally { working.value = false }
        }
    }
}
