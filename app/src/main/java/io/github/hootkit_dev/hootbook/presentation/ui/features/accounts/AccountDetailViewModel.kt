package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreDate
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreTime
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.emptyTransactionFilter
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountDetailUiState())
    val uiState = mutable.asStateFlow()
    private var accountId: String? = savedState["accountId"]
    private var query: Job? = null
    private var more: Job? = null
    private var generation = 0L
    private var filter = emptyTransactionFilter()
    init { accountId?.let { initialize(it) } }
    fun initialize(id: String) {
        if (accountId == id && query != null) return
        accountId = id; savedState["accountId"] = id
        mutable.update { it.copy(account = null, currency = null, transactions = emptyList(), summary = null) }
        refresh()
    }
    fun selectDateRange(range: DateRange) {
        mutable.update { it.copy(selectedDateRange = range, transactions = emptyList(), summary = null, error = null) }
        refresh()
    }
    fun selectCurrency(currency: String) {
        if (uiState.value.saving) return
        mutable.update { it.copy(currency = currency, anchors = emptyList(), balanceSeries = emptyList(), error = null) }
        refresh()
    }
    private fun bounds(range: DateRange): Pair<JavaDate, JavaDate> {
        val today = JavaDate.now()
        val from = when (range) {
            DateRange.LAST_7_DAYS -> today.minusDays(7)
            DateRange.LAST_30_DAYS -> today.minusDays(30)
            DateRange.LAST_3_MONTHS -> today.minusMonths(3)
            DateRange.LAST_6_MONTHS -> today.minusMonths(6)
            DateRange.LAST_YEAR -> today.minusYears(1)
            DateRange.ALL_TIME -> JavaDate.of(2000, 1, 1)
        }
        return from to today
    }
    fun refresh() {
        val id = accountId ?: return
        val mine = ++generation
        query?.cancel(); more?.cancel()
        val state = uiState.value
        val (from, to) = bounds(state.selectedDateRange)
        val requestedCurrency = state.currency
        filter = emptyTransactionFilter().copy(accountIds = listOf(id),
            from = if (state.selectedDateRange == DateRange.ALL_TIME) null else from.atStartOfDay().coreTime(),
            to = to.plusDays(1).atStartOfDay().coreTime())
        val requestedFilter = filter
        mutable.update { it.copy(isLoading = true, loadingMore = false) }
        query = viewModelScope.launch {
            session.observe(changes, accountTables + setOf(Table.CATEGORIES, Table.SUBCATEGORIES, Table.PEOPLE, Table.DEBTS)) { core ->
                coreQuery {
                    val account = core.accounts().use { it.get(id) }
                    val currency = requestedCurrency?.takeIf { code -> account.pockets.any { it.currency == code } } ?: account.mainCurrency
                    val accountResults = core.accounts().use { api ->
                        Triple(api.anchors(id, currency), api.balanceSeries(id, currency, from.coreDate(), to.coreDate()),
                            if (account.kind == AccountKind.CREDIT_CARD) api.cardStatus(id, to.coreDate()) else null)
                    }
                    val accountCount = core.accounts().use { it.transactionCount(id, null) }
                    val page = core.ledger().use { it.list(requestedFilter, PageRequest(0u, uiState.value.transactions.size.toUInt().coerceAtLeast(50u))) }
                    val summary = core.ledger().use { it.summary(requestedFilter) }
                    val categories = core.categories().use { it.list(null, true).associateBy(Category::id) }
                    val accounts = core.accounts().use { it.overview().accounts.associateBy(Account::id) }
                    val converted = core.currency().use { api -> page.items.associate { tx ->
                        tx.id to if (tx.currency == summary.currency) null else api.convert(tx.amount, tx.currency, summary.currency)
                    } }
                    val people = core.plans().use { it.people(true, to.coreDate()).associateBy(Person::id) }
                    val debts = core.plans().use { api -> people.keys.flatMap { api.debts(it) }.associateBy(DebtEntry::id) }
                    AccountDetailUiState(account = account, currency = currency, anchors = accountResults.first,
                        balanceSeries = accountResults.second, cardStatus = accountResults.third,
                        transactions = page.items, total = page.total, accountTransactionCount = accountCount, summary = summary, categories = categories,
                        accounts = accounts, people = people, debts = debts, convertedAmounts = converted,
                        selectedDateRange = state.selectedDateRange, isLoading = false)
                }
            }.collect { result ->
                if (mine != generation) return@collect
                result.fold(onSuccess = { snapshot ->
                    more?.cancel()
                    mutable.update { snapshot.copy(saving = it.saving, commandRevision = it.commandRevision) }
                }, onFailure = { failure -> mutable.update { it.copy(isLoading = false, loadingMore = false, error = failure.message) } })
            }
        }
    }
    fun loadMore() {
        val state = uiState.value
        if (state.isLoading || state.loadingMore || state.error != null || state.transactions.size.toUInt() >= state.total) return
        val mine = generation; val revision = changes.revisions.value; val request = filter
        mutable.update { it.copy(loadingMore = true) }
        more = viewModelScope.launch {
            try {
                val result = session.withCore { core ->
                    val page = core.ledger().use { it.list(request, PageRequest(state.transactions.size.toUInt(), 50u)) }
                    val converted = core.currency().use { api -> page.items.associate { tx -> tx.id to
                        if (tx.currency == state.summary?.currency) null else api.convert(tx.amount, tx.currency, state.summary!!.currency) } }
                    page to converted
                }
                ensureActive()
                if (mine == generation && revision == changes.revisions.value) {
                    check(result.first.items.isNotEmpty() && result.first.total == state.total &&
                        (state.transactions + result.first.items).distinctBy(Transaction::id).size == state.transactions.size + result.first.items.size)
                    mutable.update { it.copy(transactions = state.transactions + result.first.items,
                        convertedAmounts = state.convertedAmounts + result.second, loadingMore = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (mine == generation) mutable.update { it.copy(loadingMore = false, error = failure.message) } }
            finally { if (mine == generation) mutable.update { it.copy(loadingMore = false) } }
        }
    }
    fun calibrate(balance: BigDecimal, currency: String, onSaved: () -> Unit = {}) {
        val id = accountId ?: return
        command(onSaved) { it.calibrate(id, currency, balance, null) }
    }
    fun updateAnchor(id: String, balance: BigDecimal) = command { it.updateAnchor(id, AnchorPatch(balance = balance)) }
    fun deleteAnchor(id: String) = command { it.deleteAnchor(id) }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (AccountsInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                session.withCore { core -> core.accounts().use { block(it) } }
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
