package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.common.*
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreTime
import java.time.LocalDate as JavaDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val analyticsTables =
    setOf(
        Table.TRANSACTIONS,
        Table.CATEGORIES,
        Table.ACCOUNTS,
        Table.EXCHANGE_RATES,
        Table.SETTINGS,
        Table.TAGS,
        Table.TRANSACTION_TAGS,
    )

@HiltViewModel
class AnalyticsViewModel
private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val saved: SavedStateHandle,
    private val testLedger: (suspend () -> LedgerInterface)?,
    private val testCategories: (suspend () -> List<Category>)?,
) : ViewModel() {
    @Inject
    constructor(
        session: CoreSession,
        changes: ChangeBus,
        saved: SavedStateHandle,
    ) : this(session, changes, saved, null, null)

    internal constructor(
        changes: ChangeBus,
        saved: SavedStateHandle = SavedStateHandle(),
        categories: suspend () -> List<Category> = { emptyList() },
        ledger: suspend () -> LedgerInterface,
    ) : this(null, changes, saved, ledger, categories)

    private val mutable =
        MutableStateFlow(
            AnalyticsUiState(
                period =
                    saved.get<String>("period")?.let(TimePeriod::valueOf) ?: TimePeriod.THIS_MONTH,
                selectedCurrency = saved["currency"],
                types =
                    saved
                        .get<ArrayList<String>>("types")
                        ?.map(TransactionTypeFilter::valueOf)
                        ?.toSet() ?: setOf(TransactionTypeFilter.EXPENSE),
                customRange =
                    saved.get<LongArray>("customDateRange")?.let {
                        JavaDate.ofEpochDay(it[0]) to JavaDate.ofEpochDay(it[1])
                    },
            )
        )
    val uiState = mutable.asStateFlow()
    private var query: Job? = null
    private var generation = 0L

    init {
        refresh()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun refresh() {
        val revision = ++generation
        query?.cancel()
        val selection = mutable.value
        query =
            viewModelScope.launch {
                try {
                    val filter = selection.toFilter()
                    mutable.update { it.copy(filter = filter, isLoading = true, error = null) }
                    val snapshots =
                        if (session != null)
                            session.observe(changes, analyticsTables) { core ->
                                coreQuery {
                                    core.ledger().use { ledger ->
                                        load(
                                            selection,
                                            filter,
                                            ledger,
                                            core.categories().use { it.list(null, true) },
                                            core.accounts().use { it.overview().accounts },
                                        ) { amount, from, to ->
                                            core.currency().use { it.convert(amount, from, to) }
                                        }
                                    }
                                }
                            }
                        else
                            changes.observe(analyticsTables).mapLatest {
                                coreQuery {
                                    val ledger = testLedger!!.invoke()
                                    if (ledger is Disposable)
                                        ledger.use {
                                            load(
                                                selection,
                                                filter,
                                                ledger,
                                                testCategories!!.invoke(),
                                                emptyList(),
                                            )
                                        }
                                    else
                                        load(
                                            selection,
                                            filter,
                                            ledger,
                                            testCategories!!.invoke(),
                                            emptyList(),
                                        )
                                }
                            }
                    snapshots.collect { result ->
                        if (revision == generation)
                            result.fold(
                                onSuccess = { mutable.value = it },
                                onFailure = { error ->
                                    mutable.update {
                                        it.copy(isLoading = false, error = error as Exception)
                                    }
                                },
                            )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (revision == generation)
                        mutable.update { it.copy(isLoading = false, error = failure) }
                }
            }
    }

    private suspend fun load(
        selection: AnalyticsUiState,
        filter: TransactionFilter,
        ledger: LedgerInterface,
        categories: List<Category>,
        accounts: List<Account>,
        convert: suspend (java.math.BigDecimal, String, String) -> java.math.BigDecimal? =
            { _, _, _ ->
                null
            },
    ): AnalyticsUiState {
        val summary = ledger.summary(filter)
        val categoryAmounts =
            if (
                selection.selectedCurrency == null || selection.selectedCurrency == summary.currency
            )
                emptyMap()
            else
                (if (selection.incomeOnly) summary.incomeByCategory else summary.expenseByCategory)
                    .associate { total ->
                        total.categoryId to
                            convert(total.total, summary.currency, selection.selectedCurrency)
                                ?.let { Money(it, selection.selectedCurrency) }
                    }
        val currencies =
            ledger.summary(filter.copy(currency = null)).perCurrency.map { it.currency }.sorted()
        val total = ledger.list(filter, PageRequest(0u, 1u)).total
        val range =
            if (selection.period == TimePeriod.ALL) {
                fun Transaction.day() =
                    JavaDate.of(dateTime.year, dateTime.month.toInt(), dateTime.day.toInt())
                val oldest =
                    ledger
                        .list(filter.copy(sort = SortOrder.OLDEST_FIRST), PageRequest(0u, 1u))
                        .items
                        .firstOrNull()
                        ?.day()
                val newest = ledger.list(filter, PageRequest(0u, 1u)).items.firstOrNull()?.day()
                (oldest ?: JavaDate.now()) to maxOf(newest ?: JavaDate.now(), JavaDate.now())
            } else selection.range()!!
        val yearly =
            selection.period == TimePeriod.ALL &&
                ChronoUnit.YEARS.between(range.first, range.second) >= 2
        val monthly = !yearly && selection.period in setOf(TimePeriod.ALL, TimePeriod.CURRENT_FY)
        var date =
            when {
                yearly -> range.first.withDayOfYear(1)
                monthly -> range.first.withDayOfMonth(1)
                else -> range.first
            }
        val trend = linkedMapOf<JavaDate, LedgerSummary>()
        val trendFilters = linkedMapOf<JavaDate, TransactionFilter>()
        while (date <= range.second) {
            currentCoroutineContext().ensureActive()
            val next =
                when {
                    yearly -> date.plusYears(1)
                    monthly -> date.plusMonths(1)
                    else -> date.plusDays(1)
                }
            // Chart windows are query inputs; every amount is returned by Core.
            val window =
                filter.copy(
                    from = maxOf(date, range.first).atStartOfDay().coreTime(),
                    to = minOf(next, range.second.plusDays(1)).atStartOfDay().coreTime(),
                )
            trendFilters[date] = window
            trend[date] = ledger.summary(window)
            date = next
        }
        return selection.copy(
            filter = filter,
            summary = summary,
            availableCurrencies = currencies,
            transactionCount = total,
            categoryAmounts = categoryAmounts,
            categories = categories.associateBy { it.id },
            accounts = accounts.associateBy { it.id },
            trend = trend,
            trendFilters = trendFilters,
            isLoading = false,
            error = null,
        )
    }

    private fun AnalyticsUiState.range() =
        if (period == TimePeriod.CUSTOM) customRange else getDateRangeForPeriod(period)

    private fun AnalyticsUiState.toFilter(): TransactionFilter {
        val range = if (period == TimePeriod.ALL) null else requireNotNull(range())
        require(range == null || range.first <= range.second)
        return emptyAnalyticsFilter()
            .copy(
                from = range?.first?.atStartOfDay()?.coreTime(),
                to = range?.second?.plusDays(1)?.atStartOfDay()?.coreTime(),
                kinds =
                    if (TransactionTypeFilter.ALL in types) emptyList()
                    else types.map { TransactionKind.valueOf(it.name) },
                currency = selectedCurrency,
            )
    }

    fun selectPeriod(period: TimePeriod) {
        if (period == TimePeriod.CUSTOM && mutable.value.customRange == null) return
        saved["period"] = period.name
        mutable.update { it.copy(period = period) }
        refresh()
    }

    fun setCustomDateRange(start: JavaDate, end: JavaDate) {
        saved["customDateRange"] = longArrayOf(start.toEpochDay(), end.toEpochDay())
        mutable.update { it.copy(customRange = start to end) }
        selectPeriod(TimePeriod.CUSTOM)
    }

    fun selectCurrency(currency: String?) {
        saved["currency"] = currency
        mutable.update { it.copy(selectedCurrency = currency) }
        refresh()
    }

    fun toggleTransactionTypeFilter(type: TransactionTypeFilter) {
        if (
            type !in
                listOf(
                    TransactionTypeFilter.ALL,
                    TransactionTypeFilter.INCOME,
                    TransactionTypeFilter.EXPENSE,
                )
        )
            return
        val selected =
            if (type == TransactionTypeFilter.ALL) setOf(type)
            else {
                (mutable.value.types - TransactionTypeFilter.ALL)
                    .let { if (type in it) it - type else it + type }
                    .ifEmpty { setOf(TransactionTypeFilter.ALL) }
            }
        saved["types"] = ArrayList(selected.map { it.name })
        mutable.update { it.copy(types = selected) }
        refresh()
    }
}
