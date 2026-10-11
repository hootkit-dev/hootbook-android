package io.github.hootkit_dev.hootbook.presentation.core.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoreSettingsUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val localeFailure:CoreLocaleFailure?=null,
    val mainCurrency: String = "",
    val currencyDraft: String = "",
    val currencyDirty: Boolean = false,
    val defaultAccountId: String? = null,
    val accounts: List<Account> = emptyList(),
    val localeSelection: String = "",
    val commandRevision: Long = 0,
)

@HiltViewModel
class CoreSettingsViewModel private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val locale: CoreUiLocalePreference,
    private val testSettings: (suspend () -> SettingsInterface)?,
    private val testAccounts: (suspend () -> List<Account>)?,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, locale: CoreLocaleController) : this(session, changes, locale, null, null)
    internal constructor(changes: ChangeBus, locale: CoreUiLocalePreference, accounts: suspend () -> List<Account> = { emptyList() },
        settings: suspend () -> SettingsInterface) : this(null, changes, locale, settings, accounts)
    private val mutable = MutableStateFlow(CoreSettingsUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var revision = 0L
    private suspend fun <T> withSettings(block: suspend (SettingsInterface) -> T): T {
        testSettings?.let { provider ->
            val handle = provider()
            return if (handle is Disposable) handle.use { block(handle) } else block(handle)
        }
        return session!!.awaitCore().settings().use { block(it) }
    }
    private suspend fun accounts(): List<Account> = testAccounts?.invoke()
        ?: session!!.awaitCore().accounts().use { it.overview().accounts }
    init {
        viewModelScope.launch { changes.observe(setOf(Table.SETTINGS, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES,
            Table.CATEGORIES)).collect { refresh() } }
    }
    fun refresh() {
        val request = ++revision
        query?.cancel()
        mutable.update { it.copy(loading = true) }
        query = viewModelScope.launch {
            try {
                val values = withSettings { Pair(it.mainCurrency(), it.defaultAccount()) }
                val accounts = accounts()
                val selection = locale.selection()
                if (request == revision) mutable.update { it.copy(loading = false, loaded = true, mainCurrency = values.first,
                    currencyDraft = if (it.currencyDirty) it.currencyDraft else values.first,
                    defaultAccountId = values.second, accounts = accounts, localeSelection = selection) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { if (request == revision) mutable.update { it.copy(loading = false, error = coreFailureText(error)) } }
        }
    }
    fun currencyChanged(value: String) { if (!state.value.saving) mutable.update { it.copy(currencyDraft = value, currencyDirty = true) } }
    fun discardCurrencyDraft() { if (!state.value.saving) mutable.update { it.copy(currencyDraft = it.mainCurrency, currencyDirty = false) } }
    fun saveCurrency() {
        val draft = state.value.currencyDraft
        command { it.setMainCurrency(draft.trim()); mutable.update { state -> state.copy(currencyDirty = false) } }
    }
    fun defaultAccount(id: String?) = command { it.setDefaultAccount(id) }
    fun language(selection: String) = command(localeCommand=true) { settings ->
        val oldSelection = locale.selection()
        val oldTag = locale.effectiveTag(oldSelection)
        try { settings.setLocale(locale.effectiveTag(selection)) }
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Throwable){
            val kind=if(failure is CoreException.Invalid && failure.field=="locale")CoreLocaleFailure.CORE_COMMAND_FAILED else CoreLocaleFailure.DIVERGED
            throw CoreLocaleUiFailure(kind,failure)
        }
        // Once Core accepted the locale, finish Android persistence even during Activity recreation.
        withContext(NonCancellable) {
            try { locale.apply(selection) }
            catch (failure: Throwable) {
                try { settings.setLocale(oldTag) } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
                val uiRestored=runCatching{locale.selection()==oldSelection}.getOrDefault(false)
                throw CoreLocaleUiFailure(if(failure.suppressed.isNotEmpty()||!uiRestored)CoreLocaleFailure.DIVERGED else CoreLocaleFailure.UI_ROLLED_BACK,failure)
            }
        }
        mutable.update { it.copy(localeSelection = selection) }
    }
    fun clearError() { mutable.update { it.copy(error = null,localeFailure=it.localeFailure.takeIf{failure->failure==CoreLocaleFailure.DIVERGED}) } }
    fun back(): Boolean {
        if (!state.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }; return true
    }
    private fun command(localeCommand:Boolean=false,block: suspend (SettingsInterface) -> Unit) {
        if (state.value.saving || !state.value.loaded) return
        val previousLocaleFailure=state.value.localeFailure
        ++revision; query?.cancel()
        mutable.update { it.copy(saving = true, loading = false, error = null, localeFailure=if(localeCommand)null else it.localeFailure,navigationBlocked = false) }
        viewModelScope.launch {
            try {
                withSettings(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { mutable.update { it.copy(error = coreFailureText(error),localeFailure=when{
                error is CoreLocaleUiFailure && previousLocaleFailure==CoreLocaleFailure.DIVERGED && error.kind==CoreLocaleFailure.CORE_COMMAND_FAILED->CoreLocaleFailure.DIVERGED
                error is CoreLocaleUiFailure->error.kind
                localeCommand->CoreLocaleFailure.DIVERGED
                else->it.localeFailure
            }) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) }; refresh() }
        }
    }
}

/** Safe contextual outcome: never exposes provider/storage exception messages or claims a locale getter. */
enum class CoreLocaleFailure { CORE_COMMAND_FAILED, UI_ROLLED_BACK, DIVERGED }
internal class CoreLocaleUiFailure(val kind:CoreLocaleFailure,cause:Throwable):Exception(null,cause)
