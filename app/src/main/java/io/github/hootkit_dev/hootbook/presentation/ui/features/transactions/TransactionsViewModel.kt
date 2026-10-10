package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.common.getDateRangeForPeriod
import io.github.hootkit_dev.hootbook.presentation.common.CurrencyGroupedTotals
import io.github.hootkit_dev.hootbook.presentation.common.CurrencyTotals
import io.github.hootkit_dev.hootbook.core.Constants
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionEditor
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.utils.CurrencyUtils
import io.github.hootkit_dev.hootbook.data.repository.LendBorrowRepository
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionEditor: TransactionEditor,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyConversionService: CurrencyConversionService,
    transactionLookupsSource: io.github.hootkit_dev.hootbook.presentation.common.TransactionLookupsSource,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<Set<String>>(emptySet())
    val categoryFilter: StateFlow<Set<String>> = _categoryFilter.asStateFlow()

    private val _subcategoryFilter = MutableStateFlow<Set<String>>(emptySet())
    val subcategoryFilter: StateFlow<Set<String>> = _subcategoryFilter.asStateFlow()

    private val _amountRangeFilter = MutableStateFlow<Pair<BigDecimal, BigDecimal>?>(null)
    val amountRangeFilter: StateFlow<Pair<BigDecimal, BigDecimal>?> = _amountRangeFilter.asStateFlow()

    private val _accountsFilter = MutableStateFlow<Set<String>>(emptySet())
    val accountsFilter: StateFlow<Set<String>> = _accountsFilter.asStateFlow()

    private val _currenciesFilter = MutableStateFlow<Set<String>>(emptySet())
    val currenciesFilter: StateFlow<Set<String>> = _currenciesFilter.asStateFlow()
    
    private val _transactionTypeFilter = MutableStateFlow<Set<TransactionTypeFilter>>(emptySet())
    val transactionTypeFilter: StateFlow<Set<TransactionTypeFilter>> = _transactionTypeFilter.asStateFlow()
    
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("CNY") // Default to CNY
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    val baseCurrency: StateFlow<String> = currencyRepository.effectiveBaseCurrencyCode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "CNY"
        )

    // Store custom date range as epoch days to survive process death
    // Stored as Pair<Long, Long> (startEpochDay, endEpochDay) in SavedStateHandle
    private val _customDateRangeEpochDays = savedStateHandle.getStateFlow<Pair<Long, Long>?>("customDateRange", null)

    // Expose as LocalDate pair for convenience
    val customDateRange: StateFlow<Pair<LocalDate, LocalDate>?> = _customDateRangeEpochDays
        .map { epochDays ->
            epochDays?.let { (startEpochDay, endEpochDay) ->
                LocalDate.ofEpochDay(startEpochDay) to LocalDate.ofEpochDay(endEpochDay)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val _canLoadData = MutableStateFlow(false)
    
    private val _currencyGroupedTotals = MutableStateFlow(CurrencyGroupedTotals())
    val currencyGroupedTotals: StateFlow<CurrencyGroupedTotals> = _currencyGroupedTotals.asStateFlow()

    // Available currencies for the selected time period and all accounts
    val availableCurrencies: StateFlow<List<String>> = combine(
        selectedPeriod,
        customDateRange,
        accountBalanceRepository.getAllLatestBalances()
    ) { period, customRange, accounts ->
        Triple(period, customRange, accounts)
    }.flatMapLatest { (period, customRange, accounts) ->
        val accountCurrencies = accounts.map { it.currency }.distinct()
        val transactionCurrenciesFlow = if (period == TimePeriod.ALL) {
            transactionRepository.getAllCurrencies()
        } else if (period == TimePeriod.CUSTOM && customRange != null) {
            val (startDate, endDate) = customRange
            val startDateTime = startDate.atStartOfDay()
            val endDateTime = endDate.atTime(23, 59, 59)
            transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
        } else {
            val dateRange = getDateRangeForPeriod(period)
            if (dateRange != null) {
                val (startDate, endDate) = dateRange
                val startDateTime = startDate.atStartOfDay()
                val endDateTime = endDate.atTime(23, 59, 59)
                transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
            } else {
                transactionRepository.getAllCurrencies()
            }
        }
        transactionCurrenciesFlow.map { txCurrencies ->
            (txCurrencies + accountCurrencies).distinct().sortedWith { a, b ->
                when {
                    a == b -> 0
                    a == "CNY" -> -1 // CNY first
                    b == "CNY" -> 1
                    else -> a.compareTo(b) // Alphabetical for others
                }
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Computed property for current selected currency totals
    val filteredTotals: StateFlow<FilteredTotals> = _uiState.map { state ->
        var income = BigDecimal.ZERO
        var expenses = BigDecimal.ZERO
        var credit = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO
        var investment = BigDecimal.ZERO

        state.transactions.forEach { tx ->
            val valAmount = state.conversions.amountOf(tx) ?: tx.amount
            when (tx.transactionType) {
                TransactionType.INCOME -> income += valAmount
                TransactionType.EXPENSE -> expenses += valAmount
                TransactionType.CREDIT -> credit += valAmount
                TransactionType.TRANSFER -> transfer += valAmount
                TransactionType.INVESTMENT -> investment += valAmount
                TransactionType.LENT -> expenses += valAmount
                TransactionType.BORROWED -> income += valAmount
                else -> {}
            }
        }
        val netBalance = income - expenses
        FilteredTotals(
            income = income,
            expenses = expenses,
            credit = credit,
            transfer = transfer,
            investment = investment,
            netBalance = netBalance,
            transactionCount = state.transactions.size
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()
    
    // Selection mode state for bulk deletion
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()
    
    private val _selectedTransactionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTransactionIds: StateFlow<Set<Long>> = _selectedTransactionIds.asStateFlow()
    
    private val _deletedTransactions = MutableStateFlow<List<TransactionEntity>?>(null)
    val deletedTransactions: StateFlow<List<TransactionEntity>?> = _deletedTransactions.asStateFlow()
    
    // Track if initial filters have been applied to prevent resetting on back navigation
    private var hasAppliedInitialFilters = false
    
    // Categories flow - will be used to map category names to colors
    val categories: StateFlow<Map<String, CategoryEntity>> = categoryRepository.getAllCategories()
        .map { categoryList ->
            categoryList.associateBy { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    // Subcategories flow - will be used to map subcategory names to entities
    val subcategories: StateFlow<Map<String, SubcategoryEntity>> = subcategoryRepository.getAllSubcategories()
        .map { subcategoryList ->
            subcategoryList.associateBy { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val allSubcategoriesByCategoryId: StateFlow<Map<Long, List<SubcategoryEntity>>> = subcategoryRepository.getAllSubcategories()
        .map { subcategoryList ->
            subcategoryList.groupBy { it.categoryId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Category, account and lend/borrow person lookups for the transaction rows
    val lookups = transactionLookupsSource.lookups

    val accountsMap: StateFlow<Map<String, AccountBalanceEntity>> = accountBalanceRepository.getAllLatestBalances()
        .map { accountList ->
            accountList.associateBy { "${it.bankName}_${it.accountLast4}" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
        
    // Calculate the absolute maximum transaction amount across all transactions
    val maxTransactionAmount: StateFlow<Float> = transactionRepository.getAllTransactions()
        .map { transactions ->
            transactions.maxOfOrNull<io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity, java.math.BigDecimal> { tx -> tx.amount }?.toFloat() ?: 0f
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )
    
    init {
        // Observe the effective base currency dynamically
        viewModelScope.launch {
            var lastBaseCurrency: String? = null
            baseCurrency.collectLatest { effectiveCurrency ->
                if (lastBaseCurrency == null || effectiveCurrency != lastBaseCurrency) {
                    _selectedCurrency.value = effectiveCurrency
                    lastBaseCurrency = effectiveCurrency
                }
            }
        }

        _canLoadData
            .filter { it }
            .flatMapLatest {
                merge(
                    // debounce(300) also delayed the initial empty query by 300 ms, which
                    // restarted the pipeline and loaded and drew the list a second time.
                    searchQuery.debounce { if (it.isEmpty()) 0L else 300L }.map { "search" },
                    selectedPeriod.map { "period" },
                    categoryFilter.map { "category" },
                    subcategoryFilter.map { "subcategory" },
                    amountRangeFilter.map { "amount" },
                    accountsFilter.map { "accounts" },
                    currenciesFilter.map { "currencies" },
                    transactionTypeFilter.map { "typeFilter" },
                    selectedCurrency.map { "currency" },
                    sortOption.map { "sort" },
                    customDateRange.map { "customDate" },
                    baseCurrency.map { "baseCurrency" },
                    currencyConversionService.rateChangeTrigger.map { "rates" }
                    // Lend/borrow changes arrive through the combine below. As a trigger, the
                    // Room query's first result lands after the list is already published
                    // and made it load and draw the whole list a second time.
                )
            }
            .transformLatest { trigger ->
                // Benchmark traces name what restarted the list and which source re-emitted.
                androidx.core.os.trace("TransactionsList.restart:$trigger") {}
                // Get current values from all StateFlows
                val query = searchQuery.value
                val period = selectedPeriod.value
                val category = categoryFilter.value
                val subcategory = subcategoryFilter.value
                val amountRange = amountRangeFilter.value
                val accounts = accountsFilter.value
                val filterCurrencies = currenciesFilter.value
                val typeFilter = transactionTypeFilter.value
                val currency = selectedCurrency.value
                val baseCurrencyCode = baseCurrency.value
                val sort = sortOption.value
 
                 // Get filtered transactions. Categories, accounts and lend/borrow people come
                 // from the shared lookups the screen reads, so they never republish the list.
                 getFilteredTransactions(query, period, category, subcategory, amountRange, accounts, filterCurrencies, typeFilter)
                     .flowOn(Dispatchers.Default)
                     .onEach { androidx.core.os.trace("TransactionsList.source:transactions") {} }
                     .map { transactions ->
                     // No longer filtering by selectedCurrency. Show all unless explicitly filtered via filter sheet
                     val currencyFilteredTransactions = transactions
                     
                     // Converted amounts from rates known now; missing ones are fetched in the
                     // background and restart the list through rateChangeTrigger when they land.
                     val conversions = currencyConversionService.convert(currencyFilteredTransactions, baseCurrencyCode)

                     withContext(Dispatchers.Default) {
                         val sorted = sortTransactions(currencyFilteredTransactions, sort)
                         PreparedTransactionList(
                             sorted, groupTransactionsByDate(sorted), conversions,
                             calculateCurrencyGroupedTotals(sorted)
                         )
                     }
                 }.collect { prepared ->
                     emit(prepared)
                 }
            }
            .onEach { (transactions, groups, conversions, totals) ->
                // Counted by the Macrobenchmark suite: each publish recomposes the list.
                androidx.core.os.trace("TransactionsList.publish") {}
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    groupedTransactions = groups,
                    conversions = conversions,
                    isLoading = false
                )
                // Calculate totals for filtered transactions
                _currencyGroupedTotals.value = totals

                // Auto-select primary currency if not already selected or if current currency no longer exists
                val currentCurrency = selectedCurrency.value
                if (!_currencyGroupedTotals.value.availableCurrencies.contains(currentCurrency) && _currencyGroupedTotals.value.hasAnyCurrency()) {
                    _selectedCurrency.value = _currencyGroupedTotals.value.getPrimaryCurrency(currentCurrency)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Call this when the transition is finished to start loading heavy data.
     */
    fun startLoading() {
        _canLoadData.value = true
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }
    
    fun setCategoryFilter(category: String) {
        val current = _categoryFilter.value
        _categoryFilter.value = if (current.contains(category)) {
            current - category
        } else {
            current + category
        }
    }
    
    fun clearCategoryFilter() {
        _categoryFilter.value = emptySet()
    }

    fun setSubcategoryFilter(subcategory: String?) {
        if (subcategory == null) {
            _subcategoryFilter.value = emptySet()
            return
        }
        val current = _subcategoryFilter.value
        _subcategoryFilter.value = if (current.contains(subcategory)) {
            current - subcategory
        } else {
            current + subcategory
        }
    }

    fun toggleAccountFilter(accountKey: String) {
        val current = _accountsFilter.value
        _accountsFilter.value = if (current.contains(accountKey)) {
            current - accountKey
        } else {
            current + accountKey
        }
    }

    fun toggleCurrencyFilter(currencyCode: String) {
        val current = _currenciesFilter.value
        _currenciesFilter.value = if (current.contains(currencyCode)) {
            current - currencyCode
        } else {
            current + currencyCode
        }
    }

    fun setAmountRangeFilter(min: BigDecimal, max: BigDecimal) {
        _amountRangeFilter.value = Pair(min, max)
    }

    fun clearExtendedFilters() {
        _subcategoryFilter.value = emptySet()
        _amountRangeFilter.value = null
        _accountsFilter.value = emptySet()
        _currenciesFilter.value = emptySet()
    }
    
    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        if (filter == TransactionTypeFilter.ALL) {
            _transactionTypeFilter.value = emptySet()
            return
        }
        val current = _transactionTypeFilter.value
        _transactionTypeFilter.value = if (current.contains(filter)) {
            current - filter
        } else {
            current + filter
        }
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    /**
     * Sets a custom date range filter and switches the period to CUSTOM.
     * Date range is persisted in SavedStateHandle to survive process death.
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @throws IllegalArgumentException if startDate > endDate
     */
    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        require(startDate <= endDate) {
            "Start date ($startDate) must be before or equal to end date ($endDate)"
        }
        // Store as epoch days for process death survival
        savedStateHandle["customDateRange"] = startDate.toEpochDay() to endDate.toEpochDay()
        _selectedPeriod.value = TimePeriod.CUSTOM
    }

    /**
     * Clears the custom date range and resets to THIS_MONTH period.
     * Always safe to call - ensures we never have CUSTOM period with null dates.
     */
    fun clearCustomDateRange() {
        savedStateHandle["customDateRange"] = null
        // Always reset to a valid period to prevent CUSTOM with null dates
        if (_selectedPeriod.value == TimePeriod.CUSTOM) {
            _selectedPeriod.value = TimePeriod.THIS_MONTH
        }
    }

    private val _balanceOperationError = MutableStateFlow<String?>(null)
    val balanceOperationError = _balanceOperationError.asStateFlow()

    fun clearBalanceOperationError() { _balanceOperationError.value = null }

    private fun launchBalanceOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TransactionsViewModel", "Ledger operation rolled back", e)
                _balanceOperationError.value = context.getString(io.github.hootkit_dev.hootbook.R.string.balance_operation_failed)
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        launchBalanceOperation {
            transactionRepository.deleteTransaction(transaction)
            _deletedTransaction.value = transaction
        }
    }
    
    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            launchBalanceOperation {
                transactionRepository.undoDeleteTransaction(transaction)
                _deletedTransaction.value = null
            }
        }
    }
    
    fun undoDeleteTransaction(transaction: TransactionEntity) {
        launchBalanceOperation {
            transactionRepository.undoDeleteTransaction(transaction)
        }
    }
    
    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }
    
    // Selection mode methods for bulk deletion
    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) {
            // Clear selections when exiting selection mode
            _selectedTransactionIds.value = emptySet()
        }
    }
    
    fun toggleTransactionSelection(transactionId: Long) {
        _selectedTransactionIds.value = if (_selectedTransactionIds.value.contains(transactionId)) {
            _selectedTransactionIds.value - transactionId
        } else {
            _selectedTransactionIds.value + transactionId
        }
    }
    
    fun selectAllTransactions() {
        val allTransactionIds = _uiState.value.transactions.map { it.id }.toSet()
        _selectedTransactionIds.value = allTransactionIds
    }
    
    fun clearSelection() {
        _selectedTransactionIds.value = emptySet()
    }
    
    fun deleteSelectedTransactions() {
        launchBalanceOperation {
            val selectedIds = _selectedTransactionIds.value
            if (selectedIds.isEmpty()) return@launchBalanceOperation
            
            val transactionsToDelete = _uiState.value.transactions.filter { it.id in selectedIds }
            transactionRepository.deleteTransactions(transactionsToDelete)
            _deletedTransactions.value = transactionsToDelete
            
            // Clear selection and exit selection mode
            _selectedTransactionIds.value = emptySet()
            _selectionMode.value = false
        }
    }
    
    fun undoDeleteTransactions(transactions: List<TransactionEntity>) {
        launchBalanceOperation {
            transactionRepository.undoDeleteTransactions(transactions)
        }
    }
    
    fun clearDeletedTransactions() {
        _deletedTransactions.value = null
    }

    fun selectTransactionSet(ids: Set<Long>) {
        _selectedTransactionIds.value = ids
    }

    fun batchUpdateTransactions(
        selectedIds: Set<Long>,
        newDate: LocalDate?,
        newTime: LocalTime?,
        newCategory: String?,
        newSubcategory: String?,
        newAmount: BigDecimal?,
        newNote: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentTransactions = _uiState.value.transactions.filter { it.id in selectedIds }
            if (currentTransactions.isEmpty()) {
                onComplete()
                return@launch
            }

            for (txn in currentTransactions) {
                val oldDateTime = txn.dateTime ?: LocalDateTime.now()
                val targetDate = newDate ?: oldDateTime.toLocalDate()
                val targetTime = newTime ?: oldDateTime.toLocalTime()
                val updatedDateTime = LocalDateTime.of(targetDate, targetTime)

                val updatedCategory = newCategory ?: txn.category
                val updatedSubcategory = if (newCategory != null) newSubcategory else txn.subcategory
                val updatedAmount = newAmount ?: txn.amount
                val updatedDescription = if (newNote != null) {
                    if (newNote.isBlank()) null else newNote.trim()
                } else {
                    txn.description
                }

                val updatedTxn = txn.copy(
                    dateTime = updatedDateTime,
                    category = updatedCategory,
                    subcategory = updatedSubcategory,
                    amount = updatedAmount,
                    description = updatedDescription,
                    updatedAt = LocalDateTime.now()
                )

                // A new amount or date moves the transaction's effect on balances with it
                if (updatedTxn.amount.compareTo(txn.amount) != 0 || updatedTxn.dateTime != txn.dateTime) {
                    transactionEditor.update(txn, updatedTxn)
                } else {
                    transactionRepository.updateTransaction(updatedTxn)
                }
            }

            _selectedTransactionIds.value = emptySet()
            _selectionMode.value = false
            onComplete()
        }
    }

    fun resetFilters() {
        hasAppliedInitialFilters = false
        clearCategoryFilter()
        clearExtendedFilters()
        updateSearchQuery("")
        clearCustomDateRange()
        selectPeriod(TimePeriod.THIS_MONTH)
        setTransactionTypeFilter(TransactionTypeFilter.ALL)
        setSortOption(SortOption.DATE_NEWEST)
        // Don't reset currency as it might be user preference
    }
    
    fun applyInitialFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        type: String?
    ) {
        if (!hasAppliedInitialFilters) {
            // Only apply filters once, when first navigating to the screen
            clearCategoryFilter()
            updateSearchQuery("")
            selectPeriod(TimePeriod.THIS_MONTH)
            setTransactionTypeFilter(TransactionTypeFilter.ALL)
            setSortOption(SortOption.DATE_NEWEST)

            category?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    URLDecoder.decode(it, "UTF-8")
                } else it
                _categoryFilter.value = setOf(decoded)
            }

            merchant?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    URLDecoder.decode(it, "UTF-8")
                } else it
                updateSearchQuery(decoded)
            }

            period?.let { periodName ->
                val timePeriod = when (periodName) {
                    "THIS_MONTH" -> TimePeriod.THIS_MONTH
                    "LAST_MONTH" -> TimePeriod.LAST_MONTH
                    "CURRENT_FY" -> TimePeriod.CURRENT_FY
                    "ALL" -> TimePeriod.ALL
                    else -> null
                }
                timePeriod?.let { selectPeriod(it) }
            }

            type?.let { typeName ->
                val typeFilter = when (typeName) {
                    "INCOME" -> TransactionTypeFilter.INCOME
                    "EXPENSE" -> TransactionTypeFilter.EXPENSE
                    "CREDIT" -> TransactionTypeFilter.CREDIT
                    "TRANSFER" -> TransactionTypeFilter.TRANSFER
                    "INVESTMENT" -> TransactionTypeFilter.INVESTMENT
                    else -> TransactionTypeFilter.ALL
                }
                _transactionTypeFilter.value = if (typeFilter == TransactionTypeFilter.ALL) emptySet() else setOf(typeFilter)
            }

            // Only set currency if it's provided (from navigation)
            currency?.let { selectCurrency(it) }

            hasAppliedInitialFilters = true
        }
    }

    fun applyNavigationFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        type: String?
    ) {
        // This function can be called multiple times for navigation updates
        clearCategoryFilter()
        updateSearchQuery("")
        selectPeriod(TimePeriod.THIS_MONTH)
        setTransactionTypeFilter(TransactionTypeFilter.ALL)
        setSortOption(SortOption.DATE_NEWEST)

        category?.let {
            val decoded = if (it.contains("+") || it.contains("%")) {
                URLDecoder.decode(it, "UTF-8")
            } else it
            _categoryFilter.value = setOf(decoded)
        }

        merchant?.let {
            val decoded = if (it.contains("+") || it.contains("%")) {
                URLDecoder.decode(it, "UTF-8")
            } else it
            updateSearchQuery(decoded)
        }

        period?.let { periodName ->
            val timePeriod = when (periodName) {
                "THIS_MONTH" -> TimePeriod.THIS_MONTH
                "LAST_MONTH" -> TimePeriod.LAST_MONTH
                "CURRENT_FY" -> TimePeriod.CURRENT_FY
                "ALL" -> TimePeriod.ALL
                else -> null
            }
            timePeriod?.let { selectPeriod(it) }
        }

        type?.let { typeName ->
            val typeFilter = when (typeName) {
                "INCOME" -> TransactionTypeFilter.INCOME
                "EXPENSE" -> TransactionTypeFilter.EXPENSE
                "CREDIT" -> TransactionTypeFilter.CREDIT
                "TRANSFER" -> TransactionTypeFilter.TRANSFER
                "INVESTMENT" -> TransactionTypeFilter.INVESTMENT
                else -> TransactionTypeFilter.ALL
            }
            _transactionTypeFilter.value = if (typeFilter == TransactionTypeFilter.ALL) emptySet() else setOf(typeFilter)
        }

        // Only set currency if it's provided (from navigation)
        currency?.let { selectCurrency(it) }
    }
    
    private fun getFilteredTransactions(
        searchQuery: String,
        period: TimePeriod,
        category: Set<String>,
        subcategory: Set<String>,
        amountRange: Pair<BigDecimal, BigDecimal>?,
        accounts: Set<String>,
        filterCurrencies: Set<String>,
        typeFilter: Set<TransactionTypeFilter>
    ): Flow<List<TransactionEntity>> {
        // Date-bounded periods are filtered in SQL so only the range is loaded.
        fun between(startDate: LocalDate, endDate: LocalDate) =
            transactionRepository.getVisibleTransactionsBetween(
                startDate.atStartOfDay(), endDate.atTime(23, 59, 59)
            )

        val periodFilteredFlow = when (period) {
            TimePeriod.ALL -> transactionRepository.getAllTransactions()
            TimePeriod.CUSTOM -> {
                val customRange = customDateRange.value
                // Guard against invalid state: CUSTOM period must have a date range
                // This should never happen due to clearCustomDateRange() logic, but be defensive
                if (customRange == null) {
                    Log.e("TransactionsViewModel",
                        "CUSTOM period selected but no date range set - falling back to THIS_MONTH")
                    // Auto-correct the invalid state
                    _selectedPeriod.value = TimePeriod.THIS_MONTH
                    val (startDate, endDate) = getDateRangeForPeriod(TimePeriod.THIS_MONTH)!!
                    between(startDate, endDate)
                } else {
                    val (startDate, endDate) = customRange
                    between(startDate, endDate)
                }
            }
            else -> {
                val dateRange = getDateRangeForPeriod(period)
                if (dateRange != null) {
                    between(dateRange.first, dateRange.second)
                } else {
                    transactionRepository.getAllTransactions()
                }
            }
        }
        
        // Apply transaction type filter
        val typeFilteredFlow = periodFilteredFlow.map { transactions ->
            if (typeFilter.isEmpty()) {
                transactions
            } else {
                transactions.filter { tx ->
                    typeFilter.any { filter ->
                        when (filter) {
                             TransactionTypeFilter.ALL -> true
                             TransactionTypeFilter.INCOME -> tx.transactionType == TransactionType.INCOME
                             TransactionTypeFilter.EXPENSE -> tx.transactionType == TransactionType.EXPENSE
                             TransactionTypeFilter.CREDIT -> tx.transactionType == TransactionType.CREDIT
                             TransactionTypeFilter.TRANSFER -> tx.transactionType == TransactionType.TRANSFER
                             TransactionTypeFilter.INVESTMENT -> tx.transactionType == TransactionType.INVESTMENT
                             TransactionTypeFilter.LENT -> tx.transactionType == TransactionType.LENT
                             TransactionTypeFilter.BORROWED -> tx.transactionType == TransactionType.BORROWED
                        }
                    }
                }
            }
        }

        // Apply new extended filters (Category, Subcategory, Amount Range, Accounts, Currencies)
        val extendedFilteredFlow = typeFilteredFlow.map { transactions ->
            transactions.filter { tx ->
                val matchesCategory = category.isEmpty() || (tx.category != null && category.contains(tx.category))
                val matchesSubcat = subcategory.isEmpty() || (tx.subcategory != null && subcategory.contains(tx.subcategory))
                val matchesAmount = amountRange == null || (tx.amount >= amountRange.first && tx.amount <= amountRange.second)
                val txAccountKey = "${tx.bankName}_${tx.accountNumber}"
                val matchesAccount = accounts.isEmpty() || accounts.contains(txAccountKey)
                val matchesCurrencyOpts = filterCurrencies.isEmpty() || filterCurrencies.contains(tx.currency)
                
                matchesCategory && matchesSubcat && matchesAmount && matchesAccount && matchesCurrencyOpts
            }
        }
        
        // Apply search filter
        return if (searchQuery.isBlank()) {
            extendedFilteredFlow
        } else {
            extendedFilteredFlow.map { transactions ->
                transactions.filter { transaction ->
                    // Check merchant name and description
                    val matchesMerchant = transaction.merchantName.contains(searchQuery, ignoreCase = true)
                    val matchesDescription = transaction.description?.contains(searchQuery, ignoreCase = true) == true
                    
                    // Check if search query matches amount
                    val matchesAmount = try {
                        // Remove commas and spaces from search query for number parsing
                        val cleanedQuery = searchQuery.replace(",", "").replace(" ", "").trim()
                        
                        // Check if it's a valid number and matches the amount
                        if (cleanedQuery.isNotEmpty() && cleanedQuery.all { it.isDigit() || it == '.' }) {
                            val amountString = transaction.amount.toPlainString()
                            // Support both exact and partial matches
                            amountString.contains(cleanedQuery) || 
                            // Also match formatted amount (e.g., "1,000" matches "1000")
                            amountString.replace(",", "").contains(cleanedQuery)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    
                    matchesMerchant || matchesDescription || matchesAmount
                }
            }
        }
    }
    
    
}

private data class PreparedTransactionList(
    val transactions: List<TransactionEntity>,
    val groups: Map<DateGroup, List<TransactionEntity>>,
    val conversions: Conversions,
    val totals: CurrencyGroupedTotals
)
