package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal val accountTables = setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
    Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE)

data class ManageAccountsUiState(
    val overview: AccountsOverview? = null,
    val connections: List<BrokerageConnection> = emptyList(),
    val institutions: List<Institution> = emptyList(),
    val customCurrencies: List<CustomCurrency> = emptyList(),
    val defaultCurrency: String = "CNY",
    val isLoading: Boolean = true,
    val isSavingAccount: Boolean = false,
    val accountSaveError: Int? = null,
    val errorMessage: Int? = null,
    val commandRevision: Long = 0,
)

@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManageAccountsUiState())
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            session.observe(changes, accountTables) { core ->
                coreQuery {
                    val overview = core.accounts().use { it.overview() }
                    val institutions = core.catalog().use { it.all() }
                    val currency = core.settings().use { it.mainCurrency() }
                    val custom = core.currency().use { it.customCurrencies() }
                    val connections = core.brokerage().use { it.connections() }
                    ManageAccountsUiState(overview = overview, connections = connections, institutions = institutions,
                        customCurrencies = custom, defaultCurrency = currency, isLoading = false)
                }
            }.collect { result ->
                result.fold(
                    onSuccess = { snapshot -> mutable.update { it.copy(overview = snapshot.overview, connections = snapshot.connections,
                        institutions = snapshot.institutions, customCurrencies = snapshot.customCurrencies,
                        defaultCurrency = snapshot.defaultCurrency, isLoading = false) } },
                    onFailure = { failure -> mutable.update { it.copy(isLoading = false, errorMessage = coreFailureText(failure)) } },
                )
            }
        }
    }
    fun addAccount(input: NewAccount, onSaved: () -> Unit = {}) = command(onSaved) { core ->
        core.accounts().use { it.create(input) }
    }
    fun editAccount(account: Account, input: NewAccount, onSaved: () -> Unit = {}) = command(onSaved) { core ->
        core.accounts().use { api ->
            // Added pockets retain their explicit opening anchors. Editing account metadata never calibrates balances.
            input.extraCurrencies.forEach { api.addCurrency(account.id, it.currency, it.openingBalance, null) }
            api.update(account.id, AccountPatch(name = input.name, last4 = input.last4,
                clearLast4 = input.last4 == null, kind = input.kind, mainCurrency = input.mainCurrency,
                institutionKey = input.institutionKey, clearInstitution = input.institutionKey == null,
                icon = input.icon, color = input.color, creditLimit = input.creditLimit,
                clearCreditLimit = input.creditLimit == null, statementDay = input.statementDay,
                dueDay = input.dueDay, clearBillingDays = true))
        }
    }
    fun deleteAccount(accountId: String, deleteTransactions: Boolean = false, onDeleted: () -> Unit = {}) = command(onDeleted) { core ->
        core.accounts().use { it.delete(accountId, deleteTransactions) }
    }
    fun archive(account: Account) = command { core -> core.accounts().use { it.update(account.id, AccountPatch(archived = !account.archived)) } }
    fun setDefault(id: String?) = command { core -> core.settings().use { it.setDefaultAccount(id) } }
    fun reorder(ids: List<String>) = command { core -> core.accounts().use { api ->
        ids.forEachIndexed { order, id -> api.update(id, AccountPatch(sortOrder = order)) }
    } }
    fun addPocket(id: String, currency: String, openingBalance: java.math.BigDecimal?) = command { core ->
        core.accounts().use { it.addCurrency(id, currency, openingBalance, null) }
    }
    fun removePocket(id: String, currency: String) = command { core -> core.accounts().use { it.removeCurrency(id, currency) } }
    fun setPocketLimit(id: String, currency: String, amount: java.math.BigDecimal?) = command { core ->
        core.accounts().use { it.setCurrencyCreditLimit(id, currency, amount) }
    }
    fun clearAccountSaveError() { mutable.update { it.copy(accountSaveError = null) } }
    fun clearError() { mutable.update { it.copy(errorMessage = null) } }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (HootKitCoreInterface) -> Unit) {
        if (uiState.value.isSavingAccount) return
        mutable.update { it.copy(isSavingAccount = true, accountSaveError = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                session.withCore(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(accountSaveError = coreFailureText(failure), errorMessage = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(isSavingAccount = false) } }
        }
    }
}
