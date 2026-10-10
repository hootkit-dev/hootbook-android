package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.domain.usecase.netWorthByDay
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.utils.sumOfBigDecimal
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.BudgetRepository
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.domain.usecase.netWorthIn
import io.github.hootkit_dev.hootbook.domain.usecase.owedAcrossCurrencies
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.investmentSnapshotsOrEmpty
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val currencyRepository: CurrencyRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val lendBorrowRepository: io.github.hootkit_dev.hootbook.data.repository.LendBorrowRepository,
    private val transactionLookupsSource: io.github.hootkit_dev.hootbook.presentation.common.TransactionLookupsSource,
    private val brokerageRepository: BrokerageRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()

    private val _homeWidgets = MutableStateFlow<List<HomeWidgetUiModel>>(emptyList())
    val homeWidgets: StateFlow<List<HomeWidgetUiModel>> = _homeWidgets.asStateFlow()

    /** The nearest payment due date among credit cards that owe something. */
    val nextCardDue: StateFlow<java.time.LocalDate?> = combine(
        accountBalanceRepository.observeAccounts(),
        _uiState.map { it.creditCards }.distinctUntilChanged()
    ) { accounts, cards ->
        val today = java.time.LocalDate.now()
        val owing = cards.filter { it.balance.signum() > 0 }.map { it.bankName to it.accountLast4 }.toSet()
        accounts.filter { it.isCreditCard && it.dueDay != null && (it.name to it.last4) in owing }
            .minOfOrNull { io.github.hootkit_dev.hootbook.domain.model.CardCycle.nextDue(it.dueDay!!, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categoriesMap = categoryRepository.getAllCategories()
        .map { cats -> cats.associateBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val subcategoriesMap = subcategoryRepository.getAllSubcategories()
        .map { subcats -> subcats.associateBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Category, account and lend/borrow person lookups for the Recent rows
    val lookups = transactionLookupsSource.lookups

    // Store currency breakdown maps for quick access when switching currencies
    private var currentMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> =
        emptyMap()
    private var lastMonthBreakdownMap: Map<String, TransactionRepository.MonthlyBreakdown> =
        emptyMap()

    private val baseCurrency = currencyRepository.effectiveBaseCurrencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CNY")

    // Today, moved on at midnight and when Home comes back: "this month" and the like follow it
    private val today = MutableStateFlow(LocalDate.now())

    /** Picks up a new day (Home shown again after the app sat in the background). */
    fun refreshDate() {
        today.value = LocalDate.now()
    }

    private val _selectedCurrency = MutableStateFlow<String?>(null)
    private val selectedCurrencyCombined = combine(
        baseCurrency,
        _selectedCurrency
    ) { base, selected ->
        selected ?: base
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CNY")

    init {
        viewModelScope.launch { runCatching { brokerageRepository.load() } }
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(java.time.Duration.between(now, midnight).toMillis() + 1_000)
                refreshDate()
            }
        }
        loadHomeData()
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                _uiState.value = _uiState.value.copy(
                    userName = preferences.userName,
                    profileImageUri = preferences.profileImageUri?.toUri(),
                    profileBackgroundColor = Color(preferences.profileBackgroundColor),
                    bannerImageUri = preferences.bannerImageUri?.toUri(),
                    showBannerImage = preferences.showBannerImage
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.homeWidgetsOrder,
                userPreferencesRepository.hiddenHomeWidgets
            ) { order, hidden ->
                val allWidgets = HomeWidget.entries.toMutableList()
                
                // Construct the final list based on saved order
                val orderedWidgets = mutableListOf<HomeWidgetUiModel>()
                
                // Net Worth (Always first)
                orderedWidgets.add(HomeWidgetUiModel(HomeWidget.NETWORTH_SUMMARY, true))
                
                //ordered widgets
                order.forEach { widget ->
                     if (widget != HomeWidget.NETWORTH_SUMMARY) { // Prevent duplicates just in case
                         orderedWidgets.add(HomeWidgetUiModel(widget, !hidden.contains(widget)))
                         allWidgets.remove(widget)
                     }
                }
                allWidgets.remove(HomeWidget.NETWORTH_SUMMARY)

                // any remaining widgets (newly added ones in enum)
                allWidgets.sortedBy { it.defaultOrder }.forEach { widget ->
                    orderedWidgets.add(HomeWidgetUiModel(widget, !hidden.contains(widget)))
                }
                
                orderedWidgets
            }.collect { widgets ->
                _homeWidgets.value = widgets
            }
        }
    }

    private fun loadHomeData() {
        // Observe base currency changes
        viewModelScope.launch {
            baseCurrency.collect { mainAccountCurrency ->
                val prevCurrency = _uiState.value.selectedCurrency
                if (mainAccountCurrency != prevCurrency) {
                    _uiState.update { it.copy(
                        selectedCurrency = mainAccountCurrency,
                        baseCurrency = mainAccountCurrency
                    ) }
                    _selectedCurrency.value = mainAccountCurrency
                    // Recalculate breakdown totals and account balances for the new currency
                    val availableCurrencies = _uiState.value.availableCurrencies
                    if (availableCurrencies.isNotEmpty()) {
                        updateUIStateForCurrency(mainAccountCurrency, availableCurrencies)
                    }
                }
            }
        }

        viewModelScope.launch {
            // Load current month breakdown by currency
            combine(
                today.flatMapLatest { transactionRepository.getCurrentMonthBreakdownByCurrency(it) },
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger
            ) { breakdownByCurrency, selectedCurrency, _ ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, period = FinancialPeriod.CURRENT_MONTH, selectedCurrency = selectedCurrency)
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Load account balances and react to currency changes
            combine(
                accountBalanceRepository.getAllLatestBalances(),
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger,
                brokerageRepository.connections
            ) { allBalances, selectedCurrency, _, connections ->
                val balances = allBalances
                // Keep zero-balance accounts discoverable in the grouped account list.
                val regularAccounts =
                    balances.filter { !it.isCreditCard }
                val creditCards = balances.filter { it.isCreditCard }

                // Account loading completed
                Log.d("HomeViewModel", "Loaded ${balances.size} account(s)")

                // Check if we have multiple currencies and refresh exchange rates if needed
                val accountCurrencies = regularAccounts.map { it.currency }.distinct()
                val hasMultipleCurrencies = accountCurrencies.size > 1

                if (hasMultipleCurrencies && accountCurrencies.isNotEmpty()) {
                    currencyConversionService.refreshExchangeRatesForAccount(accountCurrencies)
                }

                val totalBalanceInSelectedCurrency = balances.netWorthIn(
                    selectedCurrency,
                    currencyConversionService,
                    investmentSnapshots = connections.investmentSnapshotsOrEmpty(),
                    pockets = accountBalanceRepository.pocketBalances()
                )

                var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
                val cardPockets = accountBalanceRepository.pocketBalances()
                for (card in creditCards) {
                    val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) -
                        card.owedAcrossCurrencies(cardPockets, currencyConversionService)
                    val amt = if (card.currency == selectedCurrency) {
                        availableInCardCurrency
                    } else {
                        currencyConversionService.convertAmount(
                            amount = availableInCardCurrency,
                            fromCurrency = card.currency,
                            toCurrency = selectedCurrency
                        )
                    }
                    totalAvailableCreditInSelectedCurrency = totalAvailableCreditInSelectedCurrency.add(amt)
                }

                _uiState.update { 
                    it.copy(
                        accountBalances = regularAccounts,
                        creditCards = creditCards,
                        totalBalance = totalBalanceInSelectedCurrency,
                        totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                        selectedCurrency = selectedCurrency
                    )
                }
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Load heatmap data (last 26 weeks)
            today.flatMapLatest { day ->
                transactionRepository.getTransactionsBetweenDates(
                    startDate = day.minusWeeks(26).with(java.time.DayOfWeek.MONDAY),
                    endDate = day
                )
            }.map { transactions ->
                transactions.groupBy { it.dateTime.toLocalDate() }
                    .mapValues { it.value.size }
            }.flowOn(Dispatchers.Default).collect { heatmap ->
                _uiState.update { it.copy(transactionHeatmap = heatmap) }
            }
        }

        viewModelScope.launch {
            // Load last month breakdown by currency
            combine(
                today.flatMapLatest { transactionRepository.getLastMonthBreakdownByCurrency(it) },
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger
            ) { breakdownByCurrency, selectedCurrency, _ ->
                updateBreakdownForSelectedCurrency(breakdownByCurrency, period = FinancialPeriod.LAST_MONTH, selectedCurrency = selectedCurrency)
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Load recent transactions (last 3) and react to base currency changes
            combine(
                transactionRepository.getRecentTransactions(limit = 3),
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger
            ) { transactions, selectedCurrency, _ ->
                // Amounts stay in their own currency; rates not stored yet are fetched in the
                // background (rateChangeTrigger brings them here) and never hold up the rows.
                val conversions = currencyConversionService.convert(transactions, selectedCurrency)
                _uiState.update { it.copy(
                    recentTransactions = transactions,
                    conversions = conversions,
                    isLoading = false
                ) }
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Load all active subscriptions and react to currency changes
            combine(
                subscriptionRepository.getActiveSubscriptions(),
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger
            ) { subscriptions, targetCurrency, _ ->
                // Check if we need to refresh rates for subscription currencies
                val subscriptionCurrencies = subscriptions.map { it.currency }.distinct()
                if (subscriptionCurrencies.any { it != targetCurrency }) {
                    currencyConversionService.refreshExchangeRatesForAccount(subscriptionCurrencies + targetCurrency)
                }

                var totalAmount = BigDecimal.ZERO
                for (subscription in subscriptions) {
                    val amt = if (subscription.currency == targetCurrency) {
                        subscription.amount
                    } else {
                        currencyConversionService.convertAmount(
                            amount = subscription.amount,
                            fromCurrency = subscription.currency,
                            toCurrency = targetCurrency
                        )
                    }
                    totalAmount = totalAmount.add(amt)
                }

                _uiState.update {
                    it.copy(
                        upcomingSubscriptions = subscriptions,
                        upcomingSubscriptionsTotal = totalAmount,
                        upcomingSubscriptionsCurrency = targetCurrency
                    )
                }
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Load active budgets for current month and react to currency changes
            combine(
                today.flatMapLatest { budgetRepository.getBudgetsWithSpendingForMonth(it.year, it.monthValue) },
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger
            ) { budgets, targetCurrency, _ ->
                // Convert budgets to match the selected main currency for display
                val convertedBudgets = budgets.map { budgetWithSpending ->
                    if (budgetWithSpending.budget.currency != targetCurrency) {
                        val convertedAmount = currencyConversionService.convertAmount(
                            budgetWithSpending.budget.amount,
                            budgetWithSpending.budget.currency,
                            targetCurrency
                        ) ?: budgetWithSpending.budget.amount

                        val convertedSpending = currencyConversionService.convertAmount(
                            budgetWithSpending.currentSpending,
                            budgetWithSpending.budget.currency,
                            targetCurrency
                        ) ?: budgetWithSpending.currentSpending

                        budgetWithSpending.copy(
                            budget = budgetWithSpending.budget.copy(
                                amount = convertedAmount,
                                currency = targetCurrency
                            ),
                            currentSpending = convertedSpending
                        )
                    } else {
                        budgetWithSpending
                    }
                }
                
                _uiState.update { 
                    it.copy(activeBudgets = convertedBudgets)
                }
            }.flowOn(Dispatchers.Default).collectLatest { }
        }

        viewModelScope.launch {
            // Net worth over the last 180 days, ending at the figure shown above it: every account
            // currency carried forward day by day, cards subtracted, investments at their value now
            combine(
                accountBalanceRepository.getAllBalances(),
                accountBalanceRepository.observeAccounts(),
                selectedCurrencyCombined,
                currencyConversionService.rateChangeTrigger,
                brokerageRepository.connections,
                today
            ) { args: Array<Any?> -> args }.collectLatest { args ->
                @Suppress("UNCHECKED_CAST") val allBalances = args[0] as List<AccountBalanceEntity>
                @Suppress("UNCHECKED_CAST") val accounts = args[1] as List<io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity>
                val selectedCurrency = args[2] as String
                @Suppress("UNCHECKED_CAST") val connections = args[4] as List<io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection>
                val day = args[5] as LocalDate
                val history = withContext(Dispatchers.Default) {
                    val cards = accounts.filter { it.isCreditCard }.map { it.id }.toSet()
                    val rates = allBalances.map { it.currency }.distinct().associateWith { currency ->
                        if (currency == selectedCurrency) BigDecimal.ONE
                        else currencyConversionService.convertAmount(BigDecimal.ONE, currency, selectedCurrency)
                    }
                    var investments = BigDecimal.ZERO
                    for ((currency, value) in connections.investmentSnapshotsOrEmpty()) {
                        investments += currencyConversionService.convertAmount(value, currency, selectedCurrency)
                    }
                    netWorthByDay(allBalances, day.minusDays(180), day, rates) { row ->
                        row.accountId?.let { it in cards } ?: row.isCreditCard
                    }.map { (date, total) -> BalancePoint(date.atStartOfDay(), total + investments, selectedCurrency) }
                }
                _uiState.update { it.copy(balanceHistory = history) }
            }
        }

        viewModelScope.launch {
            lendBorrowRepository.getSummary().collect { summary ->
                _uiState.update { it.copy(lendBorrowSummary = summary) }
            }
        }

    }

    fun refreshAccountBalances() {
        viewModelScope.launch {
            // Force refresh the account balances by fetching once (prevents memory leaks)
            val allBalances = accountBalanceRepository.getAllLatestBalances().first()
            val balances = allBalances
            val regularAccounts = balances.filter { !it.isCreditCard }
            val creditCards = balances.filter { it.isCreditCard }

            val accountCurrencies = regularAccounts.map { it.currency }.distinct()
            val creditCardCurrencies = creditCards.map { it.currency }.distinct()
            val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()
            val hasMultipleCurrencies = allAccountCurrencies.size > 1

            if (hasMultipleCurrencies && allAccountCurrencies.isNotEmpty()) {
                currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
            }

            val currentAvailableCurrencies = _uiState.value.availableCurrencies.toSet()
            val updatedAvailableCurrencies = (currentAvailableCurrencies + allAccountCurrencies)
                .sortedWith { a, b ->
                    when {
                        a == b -> 0
                        a == "CNY" -> -1
                        b == "CNY" -> 1
                        else -> a.compareTo(b)
                    }
                }

            val selectedCurrency = _selectedCurrency.value ?: baseCurrency.value
            val totalBalanceInSelectedCurrency = balances.netWorthIn(
                selectedCurrency,
                currencyConversionService,
                investmentSnapshots = brokerageRepository.connections.value.investmentSnapshotsOrEmpty(),
                pockets = accountBalanceRepository.pocketBalances()
            )
            var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
            val cardPockets = accountBalanceRepository.pocketBalances()
            for (card in creditCards) {
                val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) -
                    card.owedAcrossCurrencies(cardPockets, currencyConversionService)
                val amt = if (card.currency == selectedCurrency) availableInCardCurrency
                else currencyConversionService.convertAmount(availableInCardCurrency, card.currency, selectedCurrency)
                totalAvailableCreditInSelectedCurrency = totalAvailableCreditInSelectedCurrency.add(amt)
            }

            _uiState.update { 
                it.copy(
                    accountBalances = regularAccounts,
                    creditCards = creditCards,
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                    availableCurrencies = updatedAvailableCurrencies,
                    selectedCurrency = selectedCurrency
                )
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            _deletedTransaction.value = transaction
            transactionRepository.deleteTransaction(transaction)
        }
    }

    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                transactionRepository.undoDeleteTransaction(transaction)
                _deletedTransaction.value = null
            }
        }
    }

    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.undoDeleteTransaction(transaction)
        }
    }

    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    private suspend fun updateBreakdownForSelectedCurrency(
        breakdownByCurrency: Map<String, TransactionRepository.MonthlyBreakdown>,
        period: FinancialPeriod,
        selectedCurrency: String
    ) {
        // Store the breakdown map for later use when switching currencies
        when (period) {
            FinancialPeriod.CURRENT_MONTH -> currentMonthBreakdownMap = breakdownByCurrency
            FinancialPeriod.LAST_MONTH -> lastMonthBreakdownMap = breakdownByCurrency
        }

        // Update available currencies from all stored data and include the effective base currency
        val effectiveCurrency = currencyRepository.effectiveBaseCurrencyCode.first()
        val allCurrencies = (currentMonthBreakdownMap.keys + lastMonthBreakdownMap.keys + effectiveCurrency).distinct()
        val availableCurrencies = allCurrencies.sortedWith { a, b ->
            when {
                a == b -> 0
                a == "CNY" -> -1 // CNY first
                b == "CNY" -> 1
                else -> a.compareTo(b) // Alphabetical for others
            }
        }

        // Update UI state with values for selected currency
        updateUIStateForCurrency(selectedCurrency, availableCurrencies)
    }

    private suspend fun calculateAggregatedBreakdown(
        selectedCurrency: String,
        breakdownMap: Map<String, TransactionRepository.MonthlyBreakdown>
    ): TransactionRepository.MonthlyBreakdown {
        var total = BigDecimal.ZERO
        var income = BigDecimal.ZERO
        var expenses = BigDecimal.ZERO

        breakdownMap.forEach { (currency, breakdown) ->
            if (currency == selectedCurrency) {
                total += breakdown.total
                income += breakdown.income
                expenses += breakdown.expenses
            } else {
                total += currencyConversionService.convertAmount(breakdown.total, currency, selectedCurrency)
                income += currencyConversionService.convertAmount(breakdown.income, currency, selectedCurrency)
                expenses += currencyConversionService.convertAmount(breakdown.expenses, currency, selectedCurrency)
            }
        }
        return TransactionRepository.MonthlyBreakdown(total, income, expenses)
    }

    private suspend fun updateUIStateForCurrency(selectedCurrency: String, availableCurrencies: List<String>) {
        // Calculate aggregated breakdown across all currencies by converting to selected currency
        val currentBreakdown = calculateAggregatedBreakdown(selectedCurrency, currentMonthBreakdownMap)
        val lastBreakdown = calculateAggregatedBreakdown(selectedCurrency, lastMonthBreakdownMap)

        _uiState.value = _uiState.value.copy(
            currentMonthIncome = currentBreakdown.income,
            currentMonthExpenses = currentBreakdown.expenses,
            lastMonthIncome = lastBreakdown.income,
            lastMonthExpenses = lastBreakdown.expenses,
            selectedCurrency = selectedCurrency,
            availableCurrencies = availableCurrencies
        )
    }

    private enum class FinancialPeriod {
        CURRENT_MONTH,
        LAST_MONTH
    }

    fun toggleBannerImage() {
        viewModelScope.launch {
            userPreferencesRepository.updateShowBannerImage(!_uiState.value.showBannerImage)
        }
    }

    fun resetWidgetsLayout() {
        saveOrderJob?.cancel()
        viewModelScope.launch { userPreferencesRepository.resetHomeWidgetsLayout() }
    }

    fun toggleHomeWidgetVisibility(widget: HomeWidget, visible: Boolean) {
        viewModelScope.launch {
            val currentHidden = userPreferencesRepository.hiddenHomeWidgets.first().toMutableSet()
            if (visible) {
                currentHidden.remove(widget)
            } else {
                currentHidden.add(widget)
            }
            userPreferencesRepository.updateHiddenHomeWidgets(currentHidden)
        }
    }

    private var saveOrderJob: kotlinx.coroutines.Job? = null

    fun updateWidgetsOrder(widgets: List<HomeWidget>) {
        saveOrderJob?.cancel()
        saveOrderJob = viewModelScope.launch {
            // Debounce writes to avoid rapid DataStore updates and UI jank
            kotlinx.coroutines.delay(500)
            userPreferencesRepository.updateHomeWidgetsOrder(widgets)
        }
    }
}
