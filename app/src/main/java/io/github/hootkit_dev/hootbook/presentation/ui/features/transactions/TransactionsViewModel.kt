package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.common.getDateRangeForPeriod
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal val ledgerTables =
    setOf(
        Table.TRANSACTIONS,
        Table.CATEGORIES,
        Table.SUBCATEGORIES,
        Table.TAGS,
        Table.TRANSACTION_TAGS,
        Table.ACCOUNTS,
        Table.ACCOUNT_CURRENCIES,
        Table.SETTINGS,
        Table.EXCHANGE_RATES,
    )

internal fun java.time.LocalDateTime.coreTime() =
    dev.hootkit.core.LocalDateTime(
        year,
        monthValue.toUByte(),
        dayOfMonth.toUByte(),
        hour.toUByte(),
        minute.toUByte(),
        second.toUByte(),
        nano.toUInt(),
    )

internal fun LocalDate.coreDate() =
    dev.hootkit.core.LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())

internal fun TransactionTypeFilter.kind(): TransactionKind? =
    when (this) {
        TransactionTypeFilter.ALL -> null
        TransactionTypeFilter.LENT -> TransactionKind.LOAN_OUT
        TransactionTypeFilter.BORROWED -> TransactionKind.LOAN_IN
        else -> TransactionKind.valueOf(name)
    }

internal fun TransactionKind.typeFilter(): TransactionTypeFilter? =
    when (this) {
        TransactionKind.LOAN_OUT -> TransactionTypeFilter.LENT
        TransactionKind.LOAN_IN -> TransactionTypeFilter.BORROWED
        TransactionKind.UNKNOWN -> null
        else -> TransactionTypeFilter.valueOf(name)
    }

@HiltViewModel
class TransactionsViewModel
@Inject
constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TransactionsUiState())
    val uiState = mutableState.asStateFlow()
    private var queryJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var initialized = false

    init {
        selectPeriod(TimePeriod.THIS_MONTH)
    }

    fun refresh() {
        val mine = ++generation
        queryJob?.cancel()
        moreJob?.cancel()
        val filter = uiState.value.filter
        mutableState.update { it.copy(isLoading = true, loadingMore = false) }
        queryJob =
            viewModelScope.launch {
                session
                    .observe(changes, ledgerTables + setOf(Table.DEBTS, Table.PEOPLE)) { core ->
                        coreQuery {
                            // Refresh every already-visible page after a change; never append to
                            // stale pages.
                            val limit = uiState.value.transactions.size.toUInt().coerceAtLeast(50u)
                            val page = core.ledger().use { it.list(filter, PageRequest(0u, limit)) }
                            val summary = core.ledger().use { it.summary(filter) }
                            val accounts =
                                core.accounts().use {
                                    it.overview().accounts.associateBy(Account::id)
                                }
                            val categories =
                                core.categories().use {
                                    it.list(null, true).associateBy(Category::id)
                                }
                            val tags =
                                core.tags().use {
                                    it.list(true).associateBy(Tag::id)
                                }
                            val prefs = preferences.ready()
                            val displayCurrency =
                                prefs.unifiedDisplayCurrency?.takeIf { prefs.unifiedDisplayEnabled }
                                    ?: summary.currency
                            val converted =
                                core.currency().use { currency ->
                                    page.items.associate { tx ->
                                        tx.id to
                                            if (tx.currency != displayCurrency)
                                                currency.convert(
                                                    tx.amount,
                                                    tx.currency,
                                                    displayCurrency,
                                                )
                                            else null
                                    }
                                }
                            val people =
                                core.plans().use {
                                    it.people(true, LocalDate.now().coreDate())
                                        .associateBy(Person::id)
                                }
                            val debts =
                                core.plans().use { plans ->
                                    people.keys
                                        .flatMap { plans.debts(it) }
                                        .associateBy(DebtEntry::id)
                                }
                            val currencies =
                                core.ledger().use {
                                    it.summary(
                                            emptyTransactionFilter()
                                                .copy(deleted = DeletedFilter.INCLUDE)
                                        )
                                        .perCurrency
                                        .map(CurrencyTotals::currency)
                                }
                            TransactionsUiState(
                                debts = debts,
                                people = people,
                                convertedAmounts = converted,
                                displayCurrency = displayCurrency,
                                currencies = currencies,
                                transactions = page.items,
                                total = page.total,
                                summary = summary,
                                accounts = accounts,
                                categories = categories,
                                tags = tags,
                                filter = filter,
                                isLoading = false,
                            )
                        }
                    }
                    .collect { result ->
                        val snapshot =
                            result.getOrElse { failure ->
                                if (mine == generation)
                                    mutableState.update {
                                        it.copy(
                                            isLoading = false,
                                            loadingMore = false,
                                            error = coreFailureText(failure),
                                        )
                                    }
                                return@collect
                            }
                        if (mine == generation) {
                            moreJob?.cancel()
                            mutableState.update {
                                it.copy(
                                    debts = snapshot.debts,
                                    people = snapshot.people,
                                    convertedAmounts = snapshot.convertedAmounts,
                                    displayCurrency = snapshot.displayCurrency,
                                    currencies = snapshot.currencies,
                                    transactions = snapshot.transactions,
                                    total = snapshot.total,
                                    summary = snapshot.summary,
                                    accounts = snapshot.accounts,
                                    categories = snapshot.categories,
                                    tags = snapshot.tags,
                                    isLoading = false,
                                    loadingMore = false,
                                )
                            }
                        }
                    }
            }
    }

    fun loadMore() {
        val before = uiState.value
        if (
            before.error != null ||
                before.isLoading ||
                before.loadingMore ||
                before.transactions.size.toUInt() >= before.total
        )
            return
        val mine = generation
        val revision = changes.revisions.value
        mutableState.update { it.copy(loadingMore = true) }
        moreJob =
            viewModelScope.launch {
                try {
                    val page =
                        session.withCore { core ->
                            core.ledger().use {
                                it.list(
                                    before.filter,
                                    PageRequest(before.transactions.size.toUInt(), 50u),
                                )
                            }
                        }
                    val converted =
                        session.withCore { core ->
                            core.currency().use { currency ->
                                page.items.associate { tx ->
                                    tx.id to
                                        if (tx.currency != before.displayCurrency)
                                            currency.convert(
                                                tx.amount,
                                                tx.currency,
                                                before.displayCurrency,
                                            )
                                        else null
                                }
                            }
                        }
                    ensureActive()
                    if (mine == generation && revision == changes.revisions.value) {
                        check(page.items.isNotEmpty()) { "Incomplete Core page" }
                        check(
                            page.total == before.total &&
                                (before.transactions + page.items)
                                    .distinctBy(Transaction::id)
                                    .size == before.transactions.size + page.items.size
                        ) {
                            "Ledger changed; refresh the list"
                        }
                        mutableState.update {
                            it.copy(
                                convertedAmounts = it.convertedAmounts + converted,
                                transactions = before.transactions + page.items,
                                total = page.total,
                                loadingMore = false,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (mine == generation)
                        mutableState.update {
                            it.copy(error = coreFailureText(failure), loadingMore = false)
                        }
                } finally {
                    if (mine == generation) mutableState.update { it.copy(loadingMore = false) }
                }
            }
    }

    fun applyFilter(filter: TransactionFilter) {
        mutableState.update {
            it.copy(
                filter = filter.copy(deleted = DeletedFilter.EXCLUDE),
                transactions = emptyList(),
                summary = null,
                selectedIds = emptySet(),
                error = null,
            )
        }
        refresh()
    }

    fun updateSearchQuery(text: String) =
        applyFilter(uiState.value.filter.copy(text = text.takeIf(String::isNotBlank)))

    fun selectCurrency(code: String) =
        applyFilter(uiState.value.filter.copy(currency = code.takeIf(String::isNotBlank)))

    fun selectSortOption(option: SortOption) =
        applyFilter(uiState.value.filter.copy(sort = option.core))


    fun setCategoryFilter(id: String) {
        val ids = uiState.value.filter.categoryIds
        applyFilter(uiState.value.filter.copy(categoryIds = if (id in ids) ids - id else ids + id))
    }

    fun clearCategoryFilter() = applyFilter(uiState.value.filter.copy(categoryIds = emptyList()))

    fun toggleTagFilter(id: String) {
        val ids = uiState.value.filter.tagIds
        applyFilter(uiState.value.filter.copy(tagIds = if (id in ids) ids - id else ids + id))
    }

    fun clearTagFilter() = applyFilter(uiState.value.filter.copy(tagIds = emptyList()))

    fun toggleAccountFilter(id: String) {
        val ids = uiState.value.filter.accountIds
        applyFilter(uiState.value.filter.copy(accountIds = if (id in ids) ids - id else ids + id))
    }

    fun toggleCurrencyFilter(code: String) =
        selectCurrency(if (uiState.value.filter.currency == code) "" else code)

    fun setTransactionTypeFilter(type: TransactionTypeFilter) {
        val kind = type.kind()
        val kinds = uiState.value.filter.kinds
        applyFilter(
            uiState.value.filter.copy(
                kinds =
                    if (kind == null) emptyList()
                    else if (kind in kinds) kinds - kind else kinds + kind
            )
        )
    }

    fun selectPeriod(period: TimePeriod) {
        mutableState.update { it.copy(period = period) }
        val range = if (period == TimePeriod.ALL) null else getDateRangeForPeriod(period)
        applyFilter(
            uiState.value.filter.copy(
                from = range?.first?.atStartOfDay()?.coreTime(),
                to = range?.second?.plusDays(1)?.atStartOfDay()?.coreTime(),
            )
        )
    }

    fun setCustomDateRange(from: LocalDate, to: LocalDate) {
        mutableState.update { it.copy(period = TimePeriod.CUSTOM, customDateRange = from to to) }
        applyFilter(
            uiState.value.filter.copy(
                from = from.atStartOfDay().coreTime(),
                to = to.plusDays(1).atStartOfDay().coreTime(),
            )
        )
    }

    fun resetFilters() {
        val currency = uiState.value.filter.currency
        mutableState.update { it.copy(customDateRange = null) }
        applyFilter(emptyTransactionFilter().copy(currency = currency))
        selectPeriod(TimePeriod.THIS_MONTH)
    }

    fun initialize(filter: TransactionFilter) {
        if (initialized) return
        initialized = true
        mutableState.update {
            it.copy(
                period =
                    if (filter.from == null && filter.to == null) TimePeriod.ALL
                    else TimePeriod.CUSTOM
            )
        }
        applyFilter(filter)
    }

    fun applyInitialFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        type: String?,
        account: String? = null,
        tag: String? = null,
    ) {
        if (initialized) return
        initialized = true
        period?.let { runCatching { TimePeriod.valueOf(it) }.getOrNull()?.let(::selectPeriod) }
        applyFilter(
            uiState.value.filter.copy(
                categoryIds = listOfNotNull(category),
                text = merchant,
                currency = currency,
                kinds =
                    listOfNotNull(
                        type?.let { runCatching { TransactionKind.valueOf(it) }.getOrNull() }
                    ),
                accountIds = listOfNotNull(account),
                tagIds = listOfNotNull(tag),
            )
        )
    }

    fun toggleSelectionMode() {
        if (!uiState.value.writing)
            mutableState.update {
                it.copy(selectionMode = !it.selectionMode, selectedIds = emptySet())
            }
    }

    fun toggleTransactionSelection(id: String) {
        if (!uiState.value.writing)
            mutableState.update {
                it.copy(
                    selectedIds =
                        if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
                )
            }
    }

    fun selectTransactionSet(ids: Set<String>) {
        if (!uiState.value.writing) mutableState.update { it.copy(selectedIds = ids) }
    }

    fun clearSelection() = selectTransactionSet(emptySet())

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    fun clearUndo() {
        mutableState.update { it.copy(undoToken = null) }
    }

    fun selectAllTransactions() {
        val filter = uiState.value.filter
        write {
            val revision = changes.revisions.value
            val ids =
                session.withCore { core ->
                    core.ledger().use { ledger ->
                        val result = mutableListOf<String>()
                        do {
                            check(changes.revisions.value == revision) {
                                "Ledger changed; select again"
                            }
                            val page = ledger.list(filter, PageRequest(result.size.toUInt(), 200u))
                            check(page.items.isNotEmpty() || result.size.toUInt() >= page.total) {
                                "Incomplete Core page"
                            }
                            result += page.items.map(Transaction::id)
                        } while (result.size.toUInt() < page.total)
                        check(result.distinct().size == result.size) {
                            "Ledger changed; select again"
                        }
                        result.toSet()
                    }
                }
            if (uiState.value.filter == filter && changes.revisions.value == revision)
                mutableState.update { it.copy(selectedIds = ids) }
        }
    }

    fun deleteSelectedTransactions() {
        val before = uiState.value
        if (before.selectedIds.isEmpty()) return
        write {
            val token =
                session.withCore { core ->
                    core.ledger().use {
                        it.delete(before.selectedIds.toList())
                    }
                }
            mutableState.update {
                it.copy(selectedIds = emptySet(), selectionMode = false, undoToken = token)
            }
        }
    }

    fun undoDelete(token: String) = write {
        session.withCore { it.ledger().use { ledger -> ledger.undo(token) } }
        clearUndo()
    }

    fun batchUpdateTransactions(
        selectedIds: Set<String>,
        newDate: LocalDate?,
        newTime: LocalTime?,
        newCategory: String?,
        newSubcategory: String?,
        newAmount: BigDecimal?,
        newNote: String?,
        addTagIds: List<String> = emptyList(),
        removeTagIds: List<String> = emptyList(),
    ) = write {
        session.withCore { core ->
            core.ledger().use {
                it.bulkUpdate(
                    selectedIds.toList(),
                    TransactionBulkPatch(
                        date = newDate?.coreDate(),
                        time =
                            newTime?.let {
                                dev.hootkit.core.LocalTime(
                                    it.hour.toUByte(),
                                    it.minute.toUByte(),
                                    it.second.toUByte(),
                                    it.nano.toUInt(),
                                )
                            },
                        categoryId = newCategory,
                        subcategoryId = newSubcategory,
                        amount = newAmount,
                        notes = newNote,
                        addTagIds = addTagIds,
                        removeTagIds = removeTagIds,
                    ),
                )
            }
        }
        mutableState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    private fun write(command: suspend () -> Unit) {
        if (uiState.value.writing) return
        mutableState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            try {
                command()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = coreFailureText(failure)) }
            } finally {
                mutableState.update { it.copy(writing = false) }
                refresh()
            }
        }
    }
}
