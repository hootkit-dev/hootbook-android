package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.CoreDisplayedMoney
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** All queried values are generated Core records, never re-calculated ledger values. */
data class CoreAccountsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val overview: AccountsOverview? = null,
    val institutions: List<Institution> = emptyList(),
    val selectedId: String? = null,
    val account: Account? = null,
    val currency: String? = null,
    val anchors: List<BalanceAnchor> = emptyList(),
    val cardStatus: CardStatus? = null,
    val transactionCount: UInt = 0u,
    val pocketTransactionCount: UInt = 0u,
    val draft: CoreAccountDraft? = null,
    val editorRevision: Long = 0,
    val commandRevision: Long = 0,
    val historyOpen: Boolean = false,
    val historyLoading: Boolean = false,
    val historyFrom: String = java.time.LocalDate.now().minusDays(30).toString(),
    val historyTo: String = java.time.LocalDate.now().toString(),
    val balanceAtInput: String = java.time.LocalDateTime.now().withNano(0).toString(),
    val history: List<BalanceDay> = emptyList(),
    val historicalBalance: java.math.BigDecimal? = null,
    val customSymbols: Map<String, String> = emptyMap(),
    val displayValues: Map<String, CoreDisplayedMoney> = emptyMap(),
)

@HiltViewModel
class CoreAccountsViewModel private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val testAccounts: (suspend () -> AccountsInterface)?,
    private val testInstitutions: (() -> List<Institution>)?,
    private val savedState: SavedStateHandle? = null,
    private val preferences: CoreUiPreferencesStore? = null,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, savedState: SavedStateHandle? = null, preferences: CoreUiPreferencesStore? = null) : this(session, changes, null, null, savedState, preferences)
    internal constructor(changes: ChangeBus, accounts: suspend () -> AccountsInterface,
        institutions: () -> List<Institution> = { emptyList() }) : this(null, changes, accounts, institutions)

    private suspend fun <T> withAccounts(block: suspend (AccountsInterface) -> T): T {
        if (testAccounts != null) return block(testAccounts.invoke())
        return session!!.awaitCore().accounts().use { block(it) }
    }
    private val mutable = MutableStateFlow(CoreAccountsUiState(draft = restoreAccountDraft(savedState?.get("account_draft"))))
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var historyQuery: Job? = null
    private var historyRevision = 0L
    private var initialized = false

    init {
        viewModelScope.launch {
            changes.observe(setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
                Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE)).collect { refresh() }
        }
    }

    fun refresh() {
        val revision = ++queryRevision
        query?.cancel()
        val selected = mutable.value.selectedId
        val currency = mutable.value.currency
        mutable.update { it.copy(loading = true) }
        query = viewModelScope.launch {
            try {
                val institutions = testInstitutions?.invoke() ?: session!!.awaitCore().catalog().use { it.all() }
                val result = withAccounts { api ->
                    val overview = api.overview()
                    val account = overview.accounts.find { it.id == selected }
                    val pocket = currency?.takeIf { value -> account?.pockets?.any { it.currency == value } == true }
                        ?: account?.mainCurrency
                    val anchors = if (account != null && pocket != null) api.anchors(account.id, pocket) else emptyList()
                    val count = if (account != null) api.transactionCount(account.id, null) else 0u
                    val pocketCount = if (account != null && pocket != null) api.transactionCount(account.id, pocket) else 0u
                    val today = java.time.LocalDate.now()
                    val card = if (account?.kind == AccountKind.CREDIT_CARD) api.cardStatus(account.id,
                        LocalDate(today.year, today.monthValue.toUByte(), today.dayOfMonth.toUByte())) else null
                    QueryResult(overview, account, pocket, anchors, card, count, pocketCount)
                }
                val preferencesValue = preferences?.ready()
                val symbols = if (preferences != null) session!!.awaitCore().currency().use { it.customCurrencies().associate { row -> row.code to row.symbol } } else emptyMap()
                val unified = preferencesValue?.unifiedDisplayCurrency?.takeIf { preferencesValue.unifiedDisplayEnabled }
                val display = if (unified != null) session!!.awaitCore().currency().use { api ->
                    result.overview.accounts.associate { row -> row.id to CoreMoneyPresentation.convert(api, row.total.amount, row.total.currency, unified) }
                } else emptyMap()
                if (revision == queryRevision) mutable.update { it.copy(loading = false, overview = result.overview, institutions = institutions,
                    selectedId = result.account?.id, account = result.account, currency = result.currency,
                    anchors = result.anchors, cardStatus = result.card, transactionCount = result.count,
                    pocketTransactionCount = result.pocketCount, customSymbols = symbols, displayValues = display) }
                if (revision == queryRevision && mutable.value.historyOpen) queryHistory()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (revision == queryRevision) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }

    fun select(id: String?) {
        if (rejectNavigationWhileSaving()) return
        ++historyRevision; historyQuery?.cancel()
        mutable.update { it.copy(selectedId = id, account = null, currency = null, anchors = emptyList(), cardStatus = null, history = emptyList(), historicalBalance = null) }
        refresh()
    }
    fun selectCurrency(currency: String) { if (rejectNavigationWhileSaving()) return; ++historyRevision; historyQuery?.cancel(); mutable.update { it.copy(currency = currency, history = emptyList(), historicalBalance = null) }; refresh() }
    fun edit(account: Account? = null) {
        if (mutable.value.saving) return
        mutable.update { it.copy(draft = account?.let(CoreAccountDraft::from) ?: CoreAccountDraft(), editorRevision = it.editorRevision + 1) }
        persistDraft()
    }
    fun draftChanged(draft: CoreAccountDraft) { if (!mutable.value.saving) { mutable.update { it.copy(draft = draft) }; persistDraft() } }
    fun dismissEditor() { if (!mutable.value.saving) { mutable.update { it.copy(draft = null) }; persistDraft() } }
    /** true consumes back locally; false lets the host pop this screen. */
    fun back(): Boolean {
        if (rejectNavigationWhileSaving()) return true
        if (mutable.value.draft != null) { dismissEditor(); return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    fun transactionNavigationTarget(): String? {
        if (rejectNavigationWhileSaving()) return null
        return mutable.value.account?.id
    }
    private fun rejectNavigationWhileSaving(): Boolean {
        if (!mutable.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }
        return true
    }
    fun clearError() { mutable.update { it.copy(error = null) } }

    fun saveAccount() {
        val draft = mutable.value.draft ?: return
        command { api ->
            if (draft.id == null) {
                val id = api.create(draft.newAccount())
                mutable.update { it.copy(selectedId = id, currency = null) }
            } else api.update(draft.id, draft.patch())
            mutable.update { it.copy(draft = null) }
            persistDraft()
        }
    }
    fun setDefault(account: Account) = command {
        session!!.awaitCore().settings().use { it.setDefaultAccount(if (account.isDefault) null else account.id) }
    }
    fun archive(account: Account) = command { it.update(account.id, AccountPatch(archived = !account.archived)) }
    fun delete(account: Account, deleteTransactions: Boolean) = command {
        it.delete(account.id, deleteTransactions)
        mutable.update { state -> state.copy(selectedId = null, account = null) }
    }
    fun addPocket(accountId: String, currency: String, balance: String, at: String) = command {
        it.addCurrency(accountId, currency.trim().uppercase(), optionalAmount(balance), optionalDateTime(at))
    }
    fun removePocket(accountId: String, currency: String) = command { it.removeCurrency(accountId, currency) }
    fun setPocketLimit(accountId: String, currency: String, limit: String) = command {
        it.setCurrencyCreditLimit(accountId, currency, optionalAmount(limit))
    }
    fun calibrate(accountId: String, currency: String, balance: String, at: String) = command {
        it.calibrate(accountId, currency, requiredAmount(balance), optionalDateTime(at))
    }
    fun updateAnchor(anchorId: String, balance: String, at: String, note: String) = command {
        it.updateAnchor(anchorId, AnchorPatch(balance = requiredAmount(balance), at = optionalDateTime(at),
            note = note.ifBlank { null }, clearNote = note.isBlank()))
    }
    fun deleteAnchor(anchorId: String) = command { it.deleteAnchor(anchorId) }
    fun move(account: Account, direction: Int) {
        val rows = mutable.value.overview?.accounts.orEmpty().filter { it.isDefault == account.isDefault }
        val index = rows.indexOfFirst { it.id == account.id }
        val target = rows.getOrNull(index + direction) ?: return
        command { api ->
            // sortOrder is an explicit Core field; no balance or ledger rule lives here.
            val reordered = rows.toMutableList()
            reordered[index] = target
            reordered[index + direction] = account
            reordered.forEachIndexed { order, row -> api.update(row.id, AccountPatch(sortOrder = order)) }
        }
    }

    private fun persistDraft() { savedState?.set("account_draft", mutable.value.draft?.let(::saveAccountDraft)) }
    fun initialize(accountId: String?, history: Boolean) {
        if (initialized) return
        initialized = true
        if (accountId != null) mutable.update { it.copy(selectedId = accountId, historyOpen = history) }
        refresh()
    }
    fun historyInputs(from: String, to: String, at: String) {
        if (mutable.value.saving) return
        mutable.update { it.copy(historyFrom = from, historyTo = to, balanceAtInput = at) }
    }
    fun openHistory() { mutable.update { it.copy(historyOpen = true) }; queryHistory() }
    fun queryHistory() {
        val selected = mutable.value.selectedId ?: return
        val currency = mutable.value.currency ?: return
        val snapshot = mutable.value
        val revision = ++historyRevision
        historyQuery?.cancel()
        mutable.update { it.copy(historyLoading = true) }
        historyQuery = viewModelScope.launch {
            try {
                val from = java.time.LocalDate.parse(snapshot.historyFrom).let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val to = java.time.LocalDate.parse(snapshot.historyTo).let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val at = requireNotNull(optionalDateTime(snapshot.balanceAtInput))
                val result = withAccounts { api -> api.balanceSeries(selected, currency, from, to) to api.balanceAt(selected, currency, at) }
                if (revision == historyRevision && mutable.value.selectedId == selected && mutable.value.currency == currency)
                    mutable.update { it.copy(history = result.first, historicalBalance = result.second, historyLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == historyRevision) mutable.update { it.copy(historyLoading = false, error = coreFailureText(failure)) } }
        }
    }

    private fun command(block: suspend (AccountsInterface) -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try {
                withAccounts(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) } }
        }
    }
    private data class QueryResult(val overview: AccountsOverview, val account: Account?, val currency: String?,
        val anchors: List<BalanceAnchor>, val card: CardStatus?, val count: UInt, val pocketCount: UInt)
}
