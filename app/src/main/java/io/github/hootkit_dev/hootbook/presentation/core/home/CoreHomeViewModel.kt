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
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.CoreDisplayedMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class CoreHomeUiState(
    val overview: AccountsOverview? = null,
    val summary: LedgerSummary? = null,
    val budgets: List<Budget> = emptyList(),
    val subscriptions: SubscriptionSummary? = null,
    val debts: DebtSummary? = null,
    val displayNetWorth:CoreDisplayedMoney?=null,
    val customSymbols:Map<String,String> = emptyMap(),
    val recent: List<Transaction> = emptyList(),
    val upcoming: List<Subscription> = emptyList(),
    val heatmap: List<CoreDayCount> = emptyList(),
    val widgetsLoading: Boolean = false,
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
class CoreHomeViewModel private constructor(private val session: CoreSession, changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore?, private val widgetQueries: Boolean) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, preferences: CoreUiPreferencesStore) : this(session, changes, preferences, true)
    // Existing NoPointer tests deliberately exercise primary queries only, never missing native overrides.
    internal constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, false)
    private val mutableState = MutableStateFlow(CoreHomeUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var version = 0L
    init {
        viewModelScope.launch { changes.observe(HOME_TABLES).collect { refresh() } }
        if (preferences != null) viewModelScope.launch {
            preferences.ready()
            preferences.state.map { Triple(it.unifiedDisplayEnabled,it.unifiedDisplayCurrency,it.hiddenHomeWidgets) }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        val mine = ++version
        request?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        request = viewModelScope.launch {
            try {
                val core = session.awaitCore()
                val overview = core.accounts().use { it.overview() }
                val summary = core.ledger().use { it.summary(CoreTransactionQuery().toFilter()) }
                val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val prefs = preferences?.ready()
                val display = prefs?.unifiedDisplayCurrency?.takeIf { prefs.unifiedDisplayEnabled }
                val budgets = core.budgets().use { it.list(today, true, display) }
                val (subscriptions, debts) = core.plans().use { it.subscriptionSummary(today) to it.debtSummary(today) }
                if (mine == version) mutableState.value = CoreHomeUiState(overview = overview, summary = summary,
                    budgets = budgets, subscriptions = subscriptions, debts = debts, loading = false, widgetsLoading = widgetQueries)
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
                        val date = java.time.LocalDate.now()
                        val recent = if (HomeWidget.RECENT_TRANSACTIONS !in hidden) core.ledger().use { it.list(emptyCoreFilter(), PageRequest(0u, 6u)).items } else emptyList()
                        val upcoming = if (HomeWidget.UPCOMING_SUBSCRIPTIONS !in hidden) core.plans().use { it.dueSubscriptions(today, date.plusDays(7).coreDate()) } else emptyList()
                        val heatmap = if (HomeWidget.TRANSACTION_HEATMAP !in hidden) core.ledger().use { queryDayCounts(it, date.minusWeeks(26).with(java.time.DayOfWeek.MONDAY), date) } else emptyList()
                        if (mine == version) mutableState.update { it.copy(recent = recent, upcoming = upcoming, heatmap = heatmap, customSymbols=customSymbols,displayNetWorth=convertedNetWorth,widgetsLoading = false) }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(_: Exception) { if(mine == version) mutableState.update { it.copy(widgetsLoading = false, widgetError = R.string.core_complete_widgets_failed) } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (mine == version) mutableState.update { it.copy(loading = false, error = null, errorResource = R.string.core_complete_query_failed) }
            }
        }
    }
}
