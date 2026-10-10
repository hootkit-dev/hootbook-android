package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import io.github.hootkit_dev.hootbook.utils.pickerDate

import kotlinx.coroutines.flow.first
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import kotlinx.coroutines.Job
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.QuickTemplateRepository
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowPersonUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.AddSubscriptionUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.AddTransactionUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.GetCategoriesUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.GetLendBorrowPersonsUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.MarkTransactionAsLoanUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.UpdateSubscriptionUseCase
import io.github.hootkit_dev.hootbook.domain.model.PersonCategory
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import io.github.hootkit_dev.hootbook.R

@HiltViewModel
class AddViewModel
@Inject
constructor(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val addEditLendBorrowPersonUseCase: AddEditLendBorrowPersonUseCase,
    private val getLendBorrowPersonsUseCase: GetLendBorrowPersonsUseCase,
    private val markTransactionAsLoanUseCase: MarkTransactionAsLoanUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val subcategoryRepository: SubcategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val quickTemplateRepository: QuickTemplateRepository,
    private val transactionRepository: TransactionRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val accountHoldings: io.github.hootkit_dev.hootbook.data.repository.AccountHoldingsSource,
    val attachmentService: AttachmentService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)


    // General UI State
    private val _uiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()

    // Transaction Tab State
    private val _transactionUiState = MutableStateFlow(TransactionUiState())
    val transactionUiState: StateFlow<TransactionUiState> = _transactionUiState.asStateFlow()

    // Subscription Tab State
    private val _subscriptionUiState = MutableStateFlow(SubscriptionUiState())
    val subscriptionUiState: StateFlow<SubscriptionUiState> = _subscriptionUiState.asStateFlow()

    // Categories for dropdowns
    val categories =
        getCategoriesUseCase
            .execute()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // People available for linking LENT/BORROWED transactions to a Khata person.
    val persons =
        getLendBorrowPersonsUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Quick-add templates shown as chips at the top of the transaction form
    val quickTemplates: StateFlow<List<QuickTemplateEntity>> = quickTemplateRepository.templates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Recent history behind the form's suggestions: common categories, merchant completion
    // and suggested templates. Read once per screen; new entries close the form anyway.
    private val recentHistory: StateFlow<List<io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity>> =
        flow {
            val now = LocalDateTime.now()
            emit(transactionRepository.getVisibleTransactionsBetween(
                now.minusDays(EntrySuggestions.CATEGORY_DAYS), now.plusDays(1)).first())
        }
            .catch { e -> Log.w("AddViewModel", "History for suggestions not loaded", e); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dismissedSuggestions = MutableStateFlow(
        sharedPrefs.getStringSet(DISMISSED_SUGGESTIONS_KEY, emptySet()).orEmpty().toSet()
    )

    /** Merchants used before, for completing the merchant field. */
    val merchantSuggestions: StateFlow<List<MerchantSuggestion>> = recentHistory
        .map { EntrySuggestions.merchants(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Frequent merchant + category pairs offered next to the saved templates. */
    val suggestedTemplates: StateFlow<List<SuggestedTemplate>> =
        combine(recentHistory, quickTemplateRepository.templates, dismissedSuggestions) { history, templates, dismissed ->
            EntrySuggestions.frequentTemplates(history, LocalDateTime.now(), templates, dismissed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The categories used most for the selected transaction type. */
    val commonCategories: StateFlow<List<String>> =
        combine(recentHistory, _transactionUiState.map { it.transactionType }.distinctUntilChanged()) { history, type ->
            EntrySuggestions.topCategories(history, type, LocalDateTime.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Accounts for dropdown
    val accounts = accountBalanceRepository
            .getAllLatestBalances()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // All Subcategories for sheet
    val allSubcategories = subcategoryRepository.subcategoriesMap



    // Subcategories for the selected category
    private val _transactionSubcategories =
        MutableStateFlow<List<SubcategoryEntity>>(
            emptyList()
        )
    val transactionSubcategories:
            StateFlow<List<SubcategoryEntity>> =
        _transactionSubcategories.asStateFlow()

    private val _subscriptionSubcategories =
        MutableStateFlow<List<SubcategoryEntity>>(
            emptyList()
        )
    val subscriptionSubcategories:
            StateFlow<List<SubcategoryEntity>> =
        _subscriptionSubcategories.asStateFlow()

    // Transaction attachments
    private val _transactionAttachments = MutableStateFlow<List<String>>(emptyList())
    val transactionAttachments: StateFlow<List<String>> = _transactionAttachments.asStateFlow()

    // Subscription attachments
    private val _subscriptionAttachments = MutableStateFlow<List<String>>(emptyList())
    val subscriptionAttachments: StateFlow<List<String>> = _subscriptionAttachments.asStateFlow()

    init {
        initializeData()
    }

    private fun initializeData() {
        // Load default subcategories for both tabs
        updateTransactionSubcategories("Miscellaneous")
        updateSubscriptionSubcategories("Subscription")

        // Pre-select main account
        viewModelScope.launch {
            accounts.filter { it.isNotEmpty() }.first().let { availableAccounts ->
                val mainAccountKey = sharedPrefs.getString("main_account", null)
                if (mainAccountKey != null) {
                    val mainAccount = availableAccounts.find { 
                        "${it.bankName}_${it.accountLast4}" == mainAccountKey 
                    }
                    // Only where nothing is chosen yet: a template applied meanwhile keeps its account.
                    if (mainAccount != null) {
                        _transactionUiState.update {
                            if (it.selectedAccount != null) it
                            else it.copy(selectedAccount = mainAccount, currency = mainAccount.currency)
                        }
                        _subscriptionUiState.update {
                            if (it.selectedAccount != null) it
                            else it.copy(selectedAccount = mainAccount, currency = mainAccount.currency)
                        }
                    }
                }
            }
        }
    }

    fun resetAllStates() {
        _amountPadRequested.value = false
        _transactionUiState.value = TransactionUiState()
        _subscriptionUiState.value = SubscriptionUiState()
        _transactionAttachments.value = emptyList()
        _subscriptionAttachments.value = emptyList()
        initializeData()
    }

    // Transaction Tab Functions
    fun updateTransactionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _transactionUiState.value.amount

        _transactionUiState.update { currentState ->
            currentState.copy(amount = validAmount, amountError = validateAmount(validAmount))
        }
        refreshTargetAmount()
    }

    fun updateTransactionType(type: TransactionType) {
        val newCategory = when (type) {
            TransactionType.INCOME -> "Income"
            TransactionType.EXPENSE -> "Miscellaneous"
            TransactionType.INVESTMENT -> "Investment"
            TransactionType.CREDIT -> "Shopping"
            TransactionType.TRANSFER -> "Self Transfer"
            TransactionType.LENT -> "Lent"
            TransactionType.BORROWED -> "Borrowed"
            else -> _transactionUiState.value.category
        }

        _transactionUiState.update { currentState ->
            currentState.copy(
                transactionType = type,
                category = newCategory,
                subcategory = null,
                categoryError = validateCategory(newCategory)
            )
        }

        // Update subcategories for the new default category
        updateTransactionSubcategories(newCategory)
    }

    fun updateTransactionMerchant(merchant: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(merchant = merchant, merchantError = null)
        }
    }

    /** Fill the merchant from history, with the category and account it was last used with. */
    fun applyMerchantSuggestion(suggestion: MerchantSuggestion) {
        val account = accounts.value.firstOrNull {
            it.bankName == suggestion.bankName && it.accountLast4 == suggestion.accountLast4
        }
        _transactionUiState.update { current ->
            current.copy(
                merchant = suggestion.merchant,
                merchantError = null,
                category = suggestion.category,
                subcategory = suggestion.subcategory,
                categoryError = null,
                selectedAccount = account ?: current.selectedAccount,
                currency = account?.currency ?: current.currency
            )
        }
        updateTransactionSubcategories(suggestion.category)
    }

    /** Keep a suggested template as a saved one. */
    fun pinSuggestedTemplate(suggestion: SuggestedTemplate) {
        viewModelScope.launch {
            try { quickTemplateRepository.add(suggestion.toTemplate()) } catch (e: Exception) {
                Log.w("AddViewModel", "Suggested template not saved", e)
            }
        }
    }

    /** Stop suggesting this merchant + category pair. */
    fun dismissSuggestedTemplate(suggestion: SuggestedTemplate) {
        val updated = dismissedSuggestions.value + suggestion.key
        dismissedSuggestions.value = updated
        sharedPrefs.edit { putStringSet(DISMISSED_SUGGESTIONS_KEY, updated) }
    }

    fun updateTransactionCategory(category: String) {
        setTransactionCategoryInternal(category, subcategory = null)
    }

    private fun setTransactionCategoryInternal(category: String, subcategory: String?) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                category = category,
                subcategory = subcategory,
                categoryError = validateCategory(category)
            )
        }
        updateTransactionSubcategories(category)
    }

    fun updateSaveAsQuickTemplate(enabled: Boolean) {
        _transactionUiState.update { it.copy(saveAsQuickTemplate = enabled) }
    }

    // Set when the form should open the number pad for the amount, e.g. after a template
    // without one. The form clears it once the pad is open.
    private val _amountPadRequested = MutableStateFlow(false)
    val amountPadRequested: StateFlow<Boolean> = _amountPadRequested.asStateFlow()

    fun onAmountPadOpened() {
        _amountPadRequested.value = false
    }

    /** Fill the form from the template with this id (launcher shortcut, home-screen widget). */
    fun applyQuickTemplateById(id: Long) {
        viewModelScope.launch {
            val template = quickTemplateRepository.getById(id) ?: return@launch
            // The template names its account; wait for the accounts so it can be matched.
            withTimeoutOrNull(3_000) { accounts.first { it.isNotEmpty() } }
            applyQuickTemplate(template)
        }
    }

    /**
     * Pre-fill the form from a template. The amount is only applied when the template says
     * so; without one, the number pad opens for it.
     */
    fun applyQuickTemplate(template: QuickTemplateEntity) {
        val account = accounts.value.firstOrNull {
            it.bankName == template.bankName && it.accountLast4 == template.accountLast4
        }
        _transactionUiState.update { current ->
            current.copy(
                transactionType = template.transactionType,
                merchant = template.merchantName,
                merchantError = null,
                category = template.category,
                subcategory = template.subcategory,
                categoryError = null,
                notes = template.notes ?: "",
                amount = if (template.prefillAmount && template.amount != null) template.amount.toPlainString() else current.amount,
                amountError = null,
                selectedAccount = account ?: current.selectedAccount,
                currency = account?.currency ?: template.currency ?: current.currency,
                error = null
            )
        }
        updateTransactionSubcategories(template.category)
        if (_transactionUiState.value.amount.isBlank()) _amountPadRequested.value = true
    }

    private suspend fun saveQuickTemplateFromState(state: TransactionUiState) {
        quickTemplateRepository.add(
            QuickTemplateEntity(
                name = state.merchant.trim().ifBlank { io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.display(state.subcategory ?: state.category) },
                merchantName = state.merchant.trim(),
                category = state.category,
                subcategory = state.subcategory,
                transactionType = state.transactionType,
                amount = state.amount.toBigDecimalOrNull(),
                prefillAmount = false,
                bankName = state.selectedAccount?.bankName,
                accountLast4 = state.selectedAccount?.accountLast4,
                currency = state.currency,
                notes = state.notes.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun updateTransactionSubcategories(category: String) {
        viewModelScope.launch {
            val cat = categories.value.find { it.name == category }
            if (cat != null) {
                subcategoryRepository.getSubcategoriesByCategoryId(cat.id).collect {
                    _transactionSubcategories.value = it
                }
            } else {
                _transactionSubcategories.value = emptyList()
            }
        }
    }

    fun updateTransactionSubcategory(subcategory: String?) {
        _transactionUiState.update { currentState -> currentState.copy(subcategory = subcategory) }
    }

    fun updateTransactionDate(dateMillis: Long) {
        val localDate = pickerDate(dateMillis)
        val currentTime = _transactionUiState.value.date.toLocalTime()
        val newDateTime = LocalDateTime.of(localDate, currentTime)

        _transactionUiState.update { currentState -> currentState.copy(date = newDateTime) }
    }

    fun updateTransactionTime(hour: Int, minute: Int) {
        val currentDate = _transactionUiState.value.date.toLocalDate()
        val newDateTime = currentDate.atTime(hour, minute)

        _transactionUiState.update { currentState -> currentState.copy(date = newDateTime) }
    }

    fun updateTransactionNotes(notes: String) {
        _transactionUiState.update { currentState -> currentState.copy(notes = notes) }
    }

    fun updateTransactionRecurring(isRecurring: Boolean) {
        _transactionUiState.update { currentState -> currentState.copy(isRecurring = isRecurring) }
    }

    fun dismissOverdraftWarning() {
        _transactionUiState.update { it.copy(overdraftBalance = null) }
    }

    fun saveTransaction(allowOverdraft: Boolean = false, onSuccess: () -> Unit) {
        val state = _transactionUiState.value
        if (state.isLoading) return

        val isLoanType = state.transactionType == TransactionType.LENT ||
            state.transactionType == TransactionType.BORROWED
        val amountError = validateAmount(state.amount)
        val categoryError = validateCategory(state.category)

        // Additional validation for Transfer transactions
        if (state.transactionType == TransactionType.TRANSFER) {
            if (state.selectedAccount == null || state.targetAccount == null) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        error = context.getString(R.string.err_source_target_required)
                    )
                }
                return
            }
            
            if (state.targetCurrency != state.currency && state.targetAmount.toBigDecimalOrNull() == null) {
                _transactionUiState.update { currentState ->
                    currentState.copy(error = context.getString(R.string.err_transfer_received_amount))
                }
                return
            }

            // One account to itself only as an exchange between two of its currencies
            if (state.selectedAccount?.id == state.targetAccount?.id && state.targetCurrency == state.currency) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        error = context.getString(R.string.err_accounts_different)
                    )
                }
                return
            }
        }

        // The merchant is optional: lists show the category and note when it is blank.
        if (amountError != null || categoryError != null) {
            _transactionUiState.update { currentState ->
                currentState.copy(
                    amountError = amountError,
                    categoryError = categoryError
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                _transactionUiState.update { it.copy(isLoading = true) }

                val amount = BigDecimal(state.amount)

                val account = state.selectedAccount
                val spendsMoney = state.transactionType in setOf(TransactionType.EXPENSE,
                    TransactionType.INVESTMENT, TransactionType.LENT, TransactionType.TRANSFER)
                if (!allowOverdraft && spendsMoney && account != null) {
                    val projected = accountBalanceRepository.projectedExpenseBalance(
                        account.bankName, account.accountLast4, state.date, amount, state.currency)
                    if (projected != null && projected < BigDecimal.ZERO) {
                        _transactionUiState.update { it.copy(overdraftBalance = projected, isLoading = false) }
                        return@launch
                    }
                }
                _transactionUiState.update { it.copy(overdraftBalance = null) }

                val transactionId = addTransactionUseCase.execute(
                    amount = amount,
                    merchant = state.merchant.trim(),
                    category = state.category,
                    subcategory = state.subcategory,
                    type = state.transactionType,
                    date = state.date,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isRecurring = state.isRecurring,
                    bankName = state.selectedAccount?.bankName,
                    accountLast4 = state.selectedAccount?.accountLast4,
                    currency = state.currency,
                    sourceAccountId = state.selectedAccount?.id,
                    targetAccountBankName = state.targetAccount?.bankName,
                    targetAccountLast4 = state.targetAccount?.accountLast4,
                    targetAmount = state.targetAmount.toBigDecimalOrNull()
                        ?.takeIf { state.targetAccount != null && state.targetCurrency != state.currency },
                    targetCurrency = state.targetCurrency,
                    attachments = attachmentService.joinAttachments(_transactionAttachments.value)
                )

                if (state.saveAsQuickTemplate && !isLoanType && state.transactionType != TransactionType.TRANSFER) {
                    try { saveQuickTemplateFromState(state) } catch (e: Exception) {
                        Log.w("AddViewModel", "Quick template not saved", e)
                    }
                }

                if (isLoanType && state.selectedPersonId != null) {
                    val selectedPerson = persons.value.find { it.id == state.selectedPersonId }
                    val loanTitle = selectedPerson?.name ?: state.merchant.trim()
                    markTransactionAsLoanUseCase(
                        transactionId = transactionId,
                        personId = state.selectedPersonId,
                        type = if (state.transactionType == TransactionType.LENT) {
                            LendBorrowType.LENT
                        } else {
                            LendBorrowType.BORROWED
                        },
                        amount = amount,
                        currency = state.currency,
                        title = loanTitle,
                        merchant = loanTitle,
                        category = state.category,
                        date = state.date,
                        dueDate = state.dueDate,
                        accountId = state.selectedAccount?.id
                    )
                }

                onSuccess()
            } catch (e: Exception) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = context.getString(R.string.err_save_transaction)
                    )
                }
            }
        }
    }

    fun updateTransactionAccount(
        account: AccountBalanceEntity?
    ) {
        _transactionUiState.update { currentState -> 
            currentState.copy(
                selectedAccount = account,
                currency = account?.currency ?: "CNY"
            ) 
        }
        refreshTargetAmount()
    }

    /** The currencies an account holds, its main one first. */
    fun currenciesOf(account: AccountBalanceEntity?): List<String> {
        account ?: return emptyList()
        val held = account.accountId?.let { accountHoldings.holdings.value[it] }?.pockets?.map { it.currency }.orEmpty()
        return listOf(account.currency) + held.filter { it != account.currency }
    }

    /** Which of the source account's currencies the transaction is in. */
    fun updateTransactionCurrency(currency: String) {
        _transactionUiState.update { it.copy(currency = currency) }
        refreshTargetAmount()
    }

    fun updateTransactionTargetAccount(
        account: AccountBalanceEntity?
    ) {
        _transactionUiState.update { currentState ->
            currentState.copy(targetAccount = account, targetAmountEdited = false, targetCurrencyPicked = false)
        }
        refreshTargetAmount()
    }

    fun updateTransactionTargetCurrency(currency: String) {
        _transactionUiState.update { it.copy(targetCurrency = currency, targetCurrencyPicked = true, targetAmountEdited = false) }
        refreshTargetAmount()
    }

    /**
     * Adds [currency] to [account] (it starts at zero and can't be removed), then uses it: as the
     * transaction's currency, or with [asTarget] as the currency a transfer goes into.
     */
    fun addCurrencyToAccount(account: AccountBalanceEntity, currency: String, asTarget: Boolean) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            accountBalanceRepository.insertBalance(
                account.copy(
                    id = 0, balance = BigDecimal.ZERO, currency = currency, timestamp = now,
                    transactionId = null, sourceType = "MANUAL", createdAt = now
                )
            )
            // Once the account is known to hold it, so the choice sticks
            kotlinx.coroutines.withTimeoutOrNull(2_000) {
                accountHoldings.holdings.first { held -> held[account.accountId]?.pockets?.any { it.currency == currency } == true }
            }
            if (asTarget) updateTransactionTargetCurrency(currency) else updateTransactionCurrency(currency)
        }
    }

    fun updateTargetAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        _transactionUiState.update { it.copy(targetAmount = filtered, targetAmountEdited = true) }
    }

    private var targetAmountJob: Job? = null

    /** Converts the amount into the target account's currency, until the user types their own. */
    private fun refreshTargetAmount() {
        // Unless picked, a transfer goes into the target's matching currency, else its main one
        _transactionUiState.update { current ->
            val target = current.targetAccount
            current.copy(targetCurrency = when {
                target == null -> null
                current.targetCurrencyPicked && current.targetCurrency in currenciesOf(target) -> current.targetCurrency
                // An exchange inside one account goes into another of its currencies
                target.id == current.selectedAccount?.id -> currenciesOf(target).firstOrNull { it != current.currency }
                current.currency in currenciesOf(target) -> current.currency
                else -> target.currency
            })
        }
        val state = _transactionUiState.value
        val targetCurrency = state.targetCurrency
        if (targetCurrency == null || targetCurrency == state.currency) {
            _transactionUiState.update { it.copy(targetAmount = "", targetAmountEdited = false) }
            return
        }
        if (state.targetAmountEdited) return
        val amount = state.amount.toBigDecimalOrNull() ?: return
        targetAmountJob?.cancel()
        targetAmountJob = viewModelScope.launch {
            val converted = currencyConversionService.convertAmount(amount, state.currency, targetCurrency)
            _transactionUiState.update {
                if (it.targetAmountEdited) it else it.copy(targetAmount = converted.stripTrailingZeros().toPlainString())
            }
        }
    }

    fun updateTransactionPersonId(personId: Long?) {
        _transactionUiState.update { currentState -> currentState.copy(selectedPersonId = personId) }
    }

    fun updateTransactionDueDate(dueDate: LocalDateTime?) {
        _transactionUiState.update { currentState -> currentState.copy(dueDate = dueDate) }
    }

    fun addPerson(
        name: String,
        phone: String?,
        notes: String?,
        color: String,
        avatar: String?,
        category: PersonCategory?
    ) {
        viewModelScope.launch {
            addEditLendBorrowPersonUseCase.addPerson(
                name, phone, notes, color, avatar, category
            )
        }
    }

    // Attachment management for transactions
    fun addTransactionAttachment(path: String) {
        _transactionAttachments.update { it + path }
    }

    fun removeTransactionAttachment(path: String) {
        attachmentService.deleteAttachment(path)
        _transactionAttachments.update { it - path }
    }

    // Attachment management for subscriptions
    fun addSubscriptionAttachment(path: String) {
        _subscriptionAttachments.update { it + path }
    }

    fun removeSubscriptionAttachment(path: String) {
        attachmentService.deleteAttachment(path)
        _subscriptionAttachments.update { it - path }
    }

    // Subscription Edit Loading
    fun loadSubscriptionForEdit(id: Long) {
        viewModelScope.launch {
            try {
                _subscriptionUiState.update { it.copy(isLoading = true) }
                val subscription = subscriptionRepository.getSubscriptionById(id)
                if (subscription != null) {
                    val cycle = subscription.billingCycle ?: "Monthly"
                    val isCustom = cycle.startsWith("custom_")
                    var customCount = 1
                    var customUnit = "month"
                    var customEndDate: LocalDate? = null
                    var displayCycle = cycle

                    if (isCustom) {
                        val parts = cycle.split("_")
                        customCount = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        customUnit = parts.getOrNull(2) ?: "month"
                        val endDateStr = parts.getOrNull(3)
                        if (endDateStr != null && endDateStr != "forever") {
                            customEndDate = try { LocalDate.parse(endDateStr) } catch (e: Exception) { null }
                        }
                        displayCycle = "Custom"
                    }

                    _subscriptionUiState.update { state ->
                        state.copy(
                            subscriptionId = subscription.id,
                            serviceName = subscription.merchantName,
                            amount = subscription.amount.toString(),
                            billingCycle = displayCycle,
                            isCustomCycle = isCustom,
                            customCycleCount = customCount,
                            customCycleUnit = customUnit,
                            customCycleEndDate = customEndDate,
                            nextPaymentDate = subscription.nextPaymentDate ?: LocalDate.now(),
                            category = subscription.category ?: "Subscription",
                            subcategory = subscription.subcategory,
                            currency = subscription.currency,
                            notes = subscription.notes ?: "",
                            isLoading = false
                        )
                    }
                    
                    // Pre-select account if possible
                    accounts.filter { it.isNotEmpty() }.first().let { availableAccounts ->
                        val matchedAccount = availableAccounts.find { 
                            it.bankName == subscription.bankName
                        }
                        if (matchedAccount != null) {
                            _subscriptionUiState.update { it.copy(selectedAccount = matchedAccount) }
                        }
                    }
                    
                    // Update subcategories for the loaded category
                    updateSubscriptionSubcategories(subscription.category ?: "Subscription")
                } else {
                    _subscriptionUiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_subscription_not_found)) }
                }
            } catch (e: Exception) {
                _subscriptionUiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }


    // Subscription Tab Functions
    fun updateSubscriptionService(service: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                serviceName = service,
                serviceError = if (service.isBlank()) context.getString(R.string.err_service_required) else null
            )
        }
    }

    fun updateSubscriptionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _subscriptionUiState.value.amount

        _subscriptionUiState.update { currentState ->
            currentState.copy(amount = validAmount, amountError = validateAmount(validAmount))
        }
    }

    fun updateSubscriptionBillingCycle(cycle: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                billingCycle = cycle, 
                billingCycleError = null,
                isCustomCycle = io.github.hootkit_dev.hootbook.utils.SubscriptionUtils.isCustom(cycle)
            )
        }
    }

    fun updateSubscriptionCustomCycleCount(count: Int) {
        _subscriptionUiState.update { it.copy(customCycleCount = count) }
    }

    fun updateSubscriptionCustomCycleUnit(unit: String) {
        _subscriptionUiState.update { it.copy(customCycleUnit = unit) }
    }

    fun updateSubscriptionCustomCycleEndDate(date: LocalDate?) {
        _subscriptionUiState.update { it.copy(customCycleEndDate = date) }
    }

    fun updateSubscriptionNextPaymentDate(dateMillis: Long) {
        val localDate = pickerDate(dateMillis)

        _subscriptionUiState.update { currentState ->
            currentState.copy(nextPaymentDate = localDate)
        }
    }

    fun updateSubscriptionCategory(category: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                category = category,
                subcategory = null,
                categoryError = validateCategory(category)
            )
        }

        updateSubscriptionSubcategories(category)
    }

    private fun updateSubscriptionSubcategories(category: String) {
        viewModelScope.launch {
            val cat = categories.value.find { it.name == category }
            if (cat != null) {
                subcategoryRepository.getSubcategoriesByCategoryId(cat.id).collect {
                    _subscriptionSubcategories.value = it
                }
            } else {
                _subscriptionSubcategories.value = emptyList()
            }
        }
    }

    fun updateSubscriptionSubcategory(subcategory: String?) {
        _subscriptionUiState.update { currentState -> currentState.copy(subcategory = subcategory) }
    }

    fun updateSubscriptionNotes(notes: String) {
        _subscriptionUiState.update { currentState -> currentState.copy(notes = notes) }
    }

    fun updateSubscriptionAccount(
        account: AccountBalanceEntity?
    ) {
        _subscriptionUiState.update { currentState -> 
            currentState.copy(
                selectedAccount = account,
                currency = account?.currency ?: "CNY"
            ) 
        }
    }

    fun saveSubscription(onSuccess: () -> Unit) {
        val state = _subscriptionUiState.value
        Log.d("AddViewModel", "saveSubscription called with state: $state")

        // Validate all fields
        val serviceError = if (state.serviceName.isBlank()) context.getString(R.string.err_service_required) else null
        val amountError = validateAmount(state.amount)
        val categoryError = validateCategory(state.category)

        Log.d(
            "AddViewModel",
            "Validation - serviceError: $serviceError, amountError: $amountError, categoryError: $categoryError"
        )

        if (serviceError != null || amountError != null || categoryError != null) {
            _subscriptionUiState.update { currentState ->
                currentState.copy(
                    serviceError = serviceError,
                    amountError = amountError,
                    categoryError = categoryError
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                Log.d("AddViewModel", "Starting to save subscription...")
                _subscriptionUiState.update { it.copy(isLoading = true) }

                val amount = BigDecimal(state.amount)

                val billingCycleToSave = if (state.isCustomCycle) {
                    "custom_${state.customCycleCount}_${state.customCycleUnit.lowercase()}_${state.customCycleEndDate ?: "forever"}"
                } else {
                    state.billingCycle
                }

                if (state.subscriptionId != null) {
                    // Update existing subscription
                    val existingSubscription = subscriptionRepository.getSubscriptionById(state.subscriptionId)
                    if (existingSubscription != null) {
                        val updatedSubscription = existingSubscription.copy(
                            merchantName = state.serviceName.trim(),
                            amount = amount,
                            nextPaymentDate = state.nextPaymentDate,
                            billingCycle = billingCycleToSave,
                            category = state.category,
                            subcategory = state.subcategory,
                            bankName = state.selectedAccount?.bankName,
                            currency = state.currency,
                            notes = state.notes.takeIf { it.isNotBlank() },
                            updatedAt = java.time.LocalDateTime.now()
                        )
                        updateSubscriptionUseCase.execute(updatedSubscription)
                        Log.d("AddViewModel", "Subscription updated successfully: ${state.subscriptionId}")
                    } else {
                        throw Exception(context.getString(R.string.err_subscription_not_found))
                    }
                } else {
                    // A subscription is a payment plan, not an actual expense.
                    // Keep the selected due date and leave payment history unset.
                    val subscriptionId =
                        addSubscriptionUseCase.execute(
                            merchantName = state.serviceName.trim(),
                            amount = amount,
                            nextPaymentDate = state.nextPaymentDate,
                            billingCycle = billingCycleToSave,
                            category = state.category,
                            subcategory = state.subcategory,
                            bankName = state.selectedAccount?.bankName,
                            autoRenewal = false, // Not implemented yet
                            paymentReminder = false, // Not implemented yet
                            currency = state.currency,
                            notes = state.notes.takeIf { it.isNotBlank() },
                            lastPaidDate = null
                        )

                    Log.d("AddViewModel", "Subscription saved successfully with ID: $subscriptionId")
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e("AddViewModel", "Error saving subscription", e)
                e.printStackTrace()
                _subscriptionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = context.getString(R.string.err_save_subscription)
                    )
                }
            } finally {
                _subscriptionUiState.update { it.copy(isLoading = false) }
            }
        }
    }
    

    // Validation helpers
    private fun validateAmount(amount: String): String? {
        return when {
            amount.isBlank() -> context.getString(R.string.err_amount_required)
            amount.toDoubleOrNull() == null -> context.getString(R.string.err_invalid_amount)
            amount.toDouble() <= 0 -> context.getString(R.string.err_amount_positive)
            else -> null
        }
    }


    private fun validateCategory(category: String): String? {
        return when {
            category.isBlank() -> context.getString(R.string.err_category_required)
            else -> null
        }
    }
}

private const val DISMISSED_SUGGESTIONS_KEY = "dismissed_template_suggestions"

// UI State Classes
data class AddUiState(val currentTab: Int = 0)

data class TransactionUiState(
    val amount: String = "",
    val amountError: String? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val merchant: String = "",
    val merchantError: String? = null,
    val category: String = "Miscellaneous",
    val subcategory: String? = null,
    val categoryError: String? = null,
    val date: LocalDateTime = LocalDateTime.now(),
    val notes: String = "",
    val isRecurring: Boolean = false,
    val selectedAccount: AccountBalanceEntity? = null,
    val targetAccount: AccountBalanceEntity? = null,
    // A transfer to an account in another currency: what arrives there, and whether the user typed it
    val targetAmount: String = "",
    val targetAmountEdited: Boolean = false,
    // The target account's currency a transfer goes into; picked by the user or resolved from the source's
    val targetCurrency: String? = null,
    val targetCurrencyPicked: Boolean = false,
    val currency: String = "CNY",
    val isLoading: Boolean = false,
    val error: String? = null,
    val overdraftBalance: BigDecimal? = null,
    val selectedPersonId: Long? = null,
    val dueDate: LocalDateTime? = null,
    val saveAsQuickTemplate: Boolean = false
) {
    private val isLoanType: Boolean
        get() = transactionType == TransactionType.LENT ||
            transactionType == TransactionType.BORROWED

    val isValid: Boolean
        get() {
            val baseValid = amount.isNotBlank() &&
                    amount.toDoubleOrNull() != null &&
                    amount.toDouble() > 0 &&
                    category.isNotBlank() &&
                    amountError == null &&
                    categoryError == null
            return if (isLoanType) {
                baseValid && selectedPersonId != null
            } else {
                baseValid && merchantError == null
            }
        }
}

data class SubscriptionUiState(
    val subscriptionId: Long? = null,
    val serviceName: String = "",
    val serviceError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val billingCycle: String = "Monthly",
    val billingCycleError: String? = null,
    val nextPaymentDate: LocalDate = LocalDate.now(), // Next scheduled payment, not a completed payment
    val category: String = "Subscription",
    val subcategory: String? = null,
    val categoryError: String? = null,
    val selectedAccount: AccountBalanceEntity? = null,
    val currency: String = "CNY",
    val notes: String = "",
    val isCustomCycle: Boolean = false,
    val customCycleCount: Int = 1,
    val customCycleUnit: String = "month",
    val customCycleEndDate: LocalDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() =
            serviceName.isNotBlank() &&
                    amount.isNotBlank() &&
                    amount.toDoubleOrNull() != null &&
                    amount.toDouble() > 0 &&
                    billingCycle.isNotBlank() &&
                    category.isNotBlank() &&
                    serviceError == null &&
                    amountError == null &&
                    categoryError == null
}
