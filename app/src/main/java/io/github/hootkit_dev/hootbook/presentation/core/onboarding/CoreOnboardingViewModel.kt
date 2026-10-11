package io.github.hootkit_dev.hootbook.presentation.core.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.CustomCurrency
import dev.hootkit.core.Institution
import dev.hootkit.core.NewAccount
import dev.hootkit.core.use
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class CoreOnboardingStep(val number: Int) { WELCOME(1), SYNC(2), ACCOUNT(2), PROFILE(3), NOTIFICATIONS(4) }

/** Form and navigation state only. All account creation and currency validation stay in Core. */
internal data class CoreOnboardingState(
    val step: CoreOnboardingStep = CoreOnboardingStep.WELCOME,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: Boolean = false,
    val finished: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val institutions: List<Institution> = emptyList(),
    val currencies: List<CustomCurrency> = emptyList(),
    val currency: String = "",
    val accountName: String = "",
    val openingBalance: String = "",
    val last4: String = "",
    val institution: Institution? = null,
)

@HiltViewModel
class CoreOnboardingViewModel @Inject constructor(
    private val session: CoreSession,
    private val preferences: CoreUiPreferencesStore,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(CoreOnboardingState(
        step = saved.get<String>("step")?.let { name -> CoreOnboardingStep.entries.find { it.name == name } } ?: CoreOnboardingStep.WELCOME,
        currency = saved["currency"] ?: "", accountName = saved["accountName"] ?: "",
        openingBalance = saved["openingBalance"] ?: "", last4 = saved["last4"] ?: "",
    ))
    internal val state = mutable.asStateFlow()

    init { refresh() }
    fun refresh() {
        if (state.value.busy) return
        viewModelScope.launch {
            try {
                preferences.ready()
                val core = session.awaitCore()
                val accounts = core.accounts().use { it.overview().accounts }
                val currency = core.settings().use { it.mainCurrency() }
                val institutions = core.catalog().use { it.all() }
                val custom = core.currency().use { it.customCurrencies() }
                mutable.update { it.copy(loading = false, accounts = accounts, institutions = institutions, currencies = custom,
                    currency = it.currency.ifBlank { currency }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = true) } }
        }
    }
    internal fun step(step: CoreOnboardingStep) {
        if (state.value.busy) return
        saved["step"] = step.name
        mutable.update { it.copy(step = step, error = false) }
    }
    internal fun previous() = step(when (state.value.step) {
        CoreOnboardingStep.WELCOME, CoreOnboardingStep.SYNC, CoreOnboardingStep.ACCOUNT -> CoreOnboardingStep.WELCOME
        CoreOnboardingStep.PROFILE -> CoreOnboardingStep.ACCOUNT
        CoreOnboardingStep.NOTIFICATIONS -> CoreOnboardingStep.PROFILE
    })
    fun currency(value: String) { if (!state.value.busy) { saved["currency"] = value; mutable.update { it.copy(currency = value) } } }
    fun name(value: String) { if (!state.value.busy) { saved["accountName"] = value; mutable.update { it.copy(accountName = value, institution = null) } } }
    fun balance(value: String) { if (!state.value.busy) { saved["openingBalance"] = value; mutable.update { it.copy(openingBalance = value) } } }
    fun last4(value: String) { if (!state.value.busy) { saved["last4"] = value; mutable.update { it.copy(last4 = value) } } }
    fun institution(value: Institution, name: String) {
        if (state.value.busy) return
        saved["accountName"] = name
        mutable.update { it.copy(accountName = name, institution = value) }
        currency(value.currency)
    }
    fun saveAccount() = command {
        val input = state.value
        val core = session.awaitCore()
        core.settings().use { it.setMainCurrency(input.currency) }
        // Re-read after a restore or sync; onboarding must never create a duplicate starter account.
        val accounts = core.accounts().use { api ->
            val existing = api.overview().accounts
            if (existing.isEmpty()) {
                val amount = input.openingBalance.trim().takeIf(String::isNotEmpty)?.let { requireNotNull(it.toBigDecimalOrNull()) }
                api.create(NewAccount(name = input.accountName.trim(), last4 = input.last4.trim().ifBlank { null }, kind = AccountKind.BANK,
                    mainCurrency = input.currency, openingBalance = amount, openingAt = null, creditLimit = null, statementDay = null, dueDay = null,
                    institutionKey = input.institution?.key, icon = input.institution?.iconName, color = input.institution?.color, extraCurrencies = emptyList()))
            }
            api.overview().accounts
        }
        saved["step"] = CoreOnboardingStep.PROFILE.name
        mutable.update { it.copy(accounts = accounts, step = CoreOnboardingStep.PROFILE) }
    }
    fun finish() = command { preferences.update { it.copy(onboardingFinished = true) }; mutable.update { it.copy(finished = true) } }
    private fun command(action: suspend () -> Unit) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = true) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
