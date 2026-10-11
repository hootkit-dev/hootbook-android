package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionQuery
import javax.inject.Inject
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.core.analytics.*
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CoreHomeUiState(
    val overview: AccountsOverview? = null,
    val summary: LedgerSummary? = null,
    val previousSummary: LedgerSummary? = null,
    val budgets: List<Budget> = emptyList(),
    val subscriptions: SubscriptionSummary? = null,
    val debts: DebtSummary? = null,
    val displayNetWorth:CoreDisplayedMoney?=null,
    val customSymbols:Map<String,String> = emptyMap(),
    val recent: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentDisplay: Map<String, CoreDisplayedMoney> = emptyMap(),
    val upcoming: List<Subscription> = emptyList(),
    val heatmap: List<CoreDayCount> = emptyList(),
    val widgetsLoading: Boolean = false,
    val widgetsLoaded: Boolean = false,
    val heatmapLoading: Boolean = false,
    val refreshing: Boolean = false,
    val widgetError: Int? = null,
    val errorResource: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

internal val HOME_TABLES = setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
    Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE,
    Table.BUDGETS, Table.BUDGET_ACCOUNTS, Table.BUDGET_LIMITS, Table.CATEGORIES, Table.SUBCATEGORIES,
    Table.SUBSCRIPTIONS, Table.PEOPLE, Table.DEBTS)

@HiltViewModel
class CoreHomeViewModel private constructor(private val session: CoreSession, private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore?, private val widgetQueries: Boolean) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, preferences: CoreUiPreferencesStore) : this(session, changes, preferences, true)
    // Existing NoPointer tests deliberately exercise primary queries only, never missing native overrides.
    internal constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, false)
    private val mutableState = MutableStateFlow(CoreHomeUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var version = 0L
    private data class RequestKey(
        val revisions: Map<Table, Long>,
        val display: Pair<Boolean, String?>,
        val hidden: Set<HomeWidget>,
        val date: java.time.LocalDate,
    )
    private var requestedKey: RequestKey? = null
    init {
        viewModelScope.launch {
            preferences?.ready()
            if (preferences == null) changes.observe(HOME_TABLES).collect { refresh(force = false) }
            else combine(changes.observe(HOME_TABLES), preferences.state.map {
                Triple(it.unifiedDisplayEnabled, it.unifiedDisplayCurrency, it.hiddenHomeWidgets)
            }.distinctUntilChanged()) { _, _ -> Unit }.collect { refresh(force = false) }
        }
    }

    fun refresh() = refresh(force = true)

    private fun refresh(force: Boolean) {
        val preference = preferences?.state?.value
        val revisions = changes.revisions.value
        val key = RequestKey(HOME_TABLES.associateWith { revisions.getValue(it) },
            (preference?.unifiedDisplayEnabled == true) to preference?.unifiedDisplayCurrency,
            preference?.hiddenHomeWidgets.orEmpty(), java.time.LocalDate.now())
        // Resume and the initial Flow emission may describe the same in-flight work. A real
        // table/preference revision still cancels and supersedes the old native request.
        if (key == requestedKey && (!force || request?.isActive == true)) return
        requestedKey = key
        val mine = ++version
        request?.cancel()
        val previous = mutableState.value
        mutableState.update { it.copy(loading = it.overview == null, refreshing = it.overview != null,
            widgetsLoading = widgetQueries, heatmapLoading = false, error = null, errorResource = null, widgetError = null) }
        request = viewModelScope.launch {
            try {
                val core = session.awaitCore()
                val overview = core.accounts().use { it.overview() }
                val monthStart = key.date.withDayOfMonth(1)
                val summary = core.ledger().use { it.summary(CoreTransactionQuery(
                    fromDate = monthStart.toString(), toDateExclusive = monthStart.plusMonths(1).toString()).toFilter()) }
                val previousSummary = core.ledger().use { it.summary(CoreTransactionQuery(
                    fromDate = monthStart.minusMonths(1).toString(), toDateExclusive = monthStart.toString()).toFilter()) }
                val today = key.date.let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val prefs = preferences?.ready()
                val display = prefs?.unifiedDisplayCurrency?.takeIf { prefs.unifiedDisplayEnabled }
                val budgets = core.budgets().use { it.list(today, true, display) }
                val (subscriptions, debts) = core.plans().use { it.subscriptionSummary(today) to it.debtSummary(today) }
                val primary = previous.copy(overview = overview, summary = summary, previousSummary = previousSummary,
                    budgets = budgets, subscriptions = subscriptions, debts = debts, loading = false,
                    refreshing = false, widgetsLoading = widgetQueries, heatmapLoading = false,
                    error = null, errorResource = null, widgetError = null)
                // First launch can show basic cards promptly. A refresh keeps its coherent old
                // snapshot until amounts, conversions, symbols, and activity are ready together.
                if (mine == version && (previous.overview == null || !widgetQueries)) mutableState.value = primary
                if (widgetQueries && mine == version) {
                    try {
                        val (customSymbols, convertedNetWorth) = core.currency().use { api ->
                            val symbols = api.customCurrencies().associate { it.code to it.symbol }
                            val converted = display?.takeIf { it != overview.netWorth.currency }?.let {
                                CoreMoneyPresentation.convert(api, overview.netWorth.amount, overview.netWorth.currency, it)
                            }
                            symbols to converted
                        }
                        val hidden = prefs?.hiddenHomeWidgets.orEmpty()
                        val recent = if (HomeWidget.RECENT_TRANSACTIONS !in hidden) core.ledger().use { it.list(emptyCoreFilter(), PageRequest(0u, 6u)).items } else emptyList()
                        val categories = if (recent.isNotEmpty()) core.categories().use { it.list(null, true) } else emptyList()
                        val recentDisplay = core.currency().use { currencyApi ->
                            recent.filter { it.currency != overview.netWorth.currency }.associate { transaction ->
                                transaction.id to CoreMoneyPresentation.convert(currencyApi, transaction.amount, transaction.currency, overview.netWorth.currency)
                            }
                        }
                        val upcoming = if (HomeWidget.UPCOMING_SUBSCRIPTIONS !in hidden) core.plans().use { it.subscriptions(today, false).filterNot { row -> row.ended || row.hidden } } else emptyList()
                        val showHeatmap = HomeWidget.TRANSACTION_HEATMAP !in hidden
                        if (mine == version) mutableState.value = primary.copy(recent = recent, categories = categories,
                            recentDisplay = recentDisplay, upcoming = upcoming, customSymbols = customSymbols,
                            displayNetWorth = convertedNetWorth, widgetsLoading = false, widgetsLoaded = true,
                            heatmap = if (showHeatmap) previous.heatmap else emptyList(), heatmapLoading = showHeatmap)
                        // The per-day API is independent and sequential. It must neither hold
                        // activity content hostage nor erase the last successfully loaded grid.
                        if (showHeatmap && mine == version) {
                            val heatmap = core.ledger().use { queryDayCounts(it, key.date.minusWeeks(26).with(java.time.DayOfWeek.MONDAY), key.date) }
                            if (mine == version) mutableState.update { it.copy(heatmap = heatmap, heatmapLoading = false) }
                        }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(_: Exception) { if(mine == version) mutableState.update { it.copy(loading = false,
                        refreshing = false, widgetsLoading = false, heatmapLoading = false, widgetError = R.string.core_complete_widgets_failed) } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (mine == version) mutableState.update { it.copy(loading = false, refreshing = false,
                    widgetsLoading = false, heatmapLoading = false, error = null, errorResource = R.string.core_complete_query_failed) }
            }
        }
    }
}
