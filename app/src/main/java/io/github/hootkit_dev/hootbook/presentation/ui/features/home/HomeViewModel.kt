package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.emptyTransactionFilter
import java.time.LocalDate as JavaDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val HOME_TABLES = setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
    Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE,
    Table.BUDGETS, Table.BUDGET_ACCOUNTS, Table.BUDGET_LIMITS, Table.CATEGORIES, Table.SUBCATEGORIES,
    Table.SUBSCRIPTIONS, Table.PEOPLE, Table.DEBTS)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: CoreSession,
    changes: ChangeBus,
    val preferences: CoreUiPreferencesStore,
    val assets: CorePlatformAssets,
) : ViewModel() {
    private val mutable = MutableStateFlow(HomeUiState())
    val uiState = mutable.asStateFlow()
    private val date = MutableStateFlow(JavaDate.now())
    private val preferencesMutex = Mutex()
    private val reload = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            preferences.ready()
            combine(preferences.state.map { Triple(it.unifiedDisplayEnabled, it.unifiedDisplayCurrency, it.hiddenHomeWidgets) }.distinctUntilChanged(), date, reload) { prefs, today, _ -> prefs to today }
                .flatMapLatest { (prefs, today) ->
                    session.observe(changes, HOME_TABLES) { core -> coreQuery {
                        query(core, today, prefs.second.takeIf { prefs.first }, prefs.third)
                    } }
                }.collect { result -> result.fold(
                    onSuccess = { next -> mutable.update { next.copy(submitting = it.submitting) } },
                    onFailure = { failure -> mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } },
                ) }
        }
    }

    private suspend fun query(core: HootKitCoreInterface, today: JavaDate, display: String?, hidden: Set<HomeWidget>): HomeUiState {
        val overview = core.accounts().use { it.overview() }
        val connections = core.brokerage().use { it.connections() }
        val month = today.withDayOfMonth(1)
        fun period(from: JavaDate, to: JavaDate) = emptyTransactionFilter().copy(from = from.atStartOfDay().coreTime(), to = to.atStartOfDay().coreTime())
        val (summary, lastSummary, activity) = core.ledger().use { ledger ->
            val summary = ledger.summary(period(month, month.plusMonths(1)))
            val last = ledger.summary(period(month.minusMonths(1), month))
            val recent = if (HomeWidget.RECENT_TRANSACTIONS in hidden) emptyList() else ledger.list(emptyTransactionFilter(), PageRequest(0u, 6u)).items
            val heatmap = if (HomeWidget.TRANSACTION_HEATMAP in hidden) emptyMap() else queryDayCounts(ledger, today.minusWeeks(26).with(java.time.DayOfWeek.MONDAY), today)
            Triple(summary, last, recent to heatmap)
        }
        val (recent, heatmap) = activity
        val budgets = core.budgets().use { it.list(today.coreDate(), true, display) }
        val (subscriptions, debts, upcoming) = core.plans().use { plans ->
            Triple(plans.subscriptionSummary(today.coreDate()), plans.debtSummary(today.coreDate()),
                if (HomeWidget.UPCOMING_SUBSCRIPTIONS in hidden) emptyList() else plans.dueSubscriptions(today.coreDate(), today.plusDays(7).coreDate()))
        }
        val (categories, subcategories) = core.categories().use { api ->
            val categories = api.list(null, true)
            categories.associateBy { it.id } to categories.flatMap { it.subcategories }.associateBy { it.id }
        }
        val target = display ?: overview.netWorth.currency
        val (netWorth, conversions, symbols) = core.currency().use { api ->
            val net = display?.takeIf { it != overview.netWorth.currency }?.let {
                queryConvertedMoney(api, overview.netWorth.amount, overview.netWorth.currency, it)
            }
            val conversions = recent.filter { it.currency != target }.associate { tx ->
                tx.id to queryConvertedMoney(api, tx.amount, tx.currency, target)
            }
            Triple(net, conversions, api.customCurrencies().associate { it.code to it.symbol })
        }
        val currencies = (overview.accounts.flatMap { it.pockets.map { p -> p.currency } } +
            summary.perCurrency.map { it.currency } + lastSummary.perCurrency.map { it.currency } +
            upcoming.map { it.amount.currency } + symbols.keys + overview.netWorth.currency + target).distinct()
        val totals = core.currency().use { api ->
            listOf("income" to Money(summary.income, summary.currency), "expense" to Money(summary.expense, summary.currency), "net" to Money(summary.net, summary.currency),
                "lastIncome" to Money(lastSummary.income, lastSummary.currency), "lastExpense" to Money(lastSummary.expense, lastSummary.currency), "lastNet" to Money(lastSummary.net, lastSummary.currency),
                "monthly" to Money(subscriptions.monthly, subscriptions.currency)).filter { it.second.currency != target }.associate { (key, money) ->
                    key to queryConvertedMoney(api, money.amount, money.currency, target)
                }
        }
        return HomeUiState(
            overview = overview, connections = connections, summary = summary, lastSummary = lastSummary,
            budgets = budgets, subscriptions = subscriptions, debts = debts,
            recent = recent, upcoming = upcoming, heatmap = heatmap,
            accounts = overview.accounts.associateBy { it.id }, categories = categories, subcategories = subcategories,
            displayNetWorth = netWorth, recentConversions = conversions, availableCurrencies = currencies,
            loading = false, displayTotals = totals, customSymbols = symbols,
        )
    }

    fun refresh() { reload.value++ }
    fun refreshDate() { date.value = JavaDate.now() }
    fun selectCurrency(currency: String) = updatePreferences { it.copy(unifiedDisplayEnabled = true, unifiedDisplayCurrency = currency) }
    fun toggleHomeWidgetVisibility(widget: HomeWidget, visible: Boolean) = updatePreferences {
        it.copy(hiddenHomeWidgets = if (visible) it.hiddenHomeWidgets - widget else it.hiddenHomeWidgets + widget)
    }
    fun updateWidgetsOrder(order: List<HomeWidget>) = updatePreferences {
        it.copy(homeOrder = listOf(HomeWidget.NETWORTH_SUMMARY, HomeWidget.ACCOUNT_CAROUSEL) + order)
    }
    fun resetWidgetsLayout() = submitPreferences { it.copy(homeOrder = HomeWidget.entries.sortedBy { w -> w.defaultOrder }, hiddenHomeWidgets = emptySet()) }
    fun toggleBannerImage() = submitPreferences { it.copy(showBanner = !it.showBanner) }

    // Explicit setters can queue safely; toggles and reset reject repeated taps while saving.
    private fun updatePreferences(transform: (CoreUiPreferences) -> CoreUiPreferences) {
        viewModelScope.launch {
            preferencesMutex.withLock {
                coreQuery { preferences.update(transform) }.fold(
                    onSuccess = { mutable.update { it.copy(error = null) } },
                    onFailure = { failure -> mutable.update { it.copy(error = coreFailureText(failure)) } },
                )
            }
        }
    }
    private fun submitPreferences(transform: (CoreUiPreferences) -> CoreUiPreferences) {
        if (mutable.value.submitting) return
        mutable.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try { coreQuery { preferencesMutex.withLock { preferences.update(transform) } }.onFailure { failure -> mutable.update { it.copy(error = coreFailureText(failure)) } } }
            finally { mutable.update { it.copy(submitting = false) } }
        }
    }
}
