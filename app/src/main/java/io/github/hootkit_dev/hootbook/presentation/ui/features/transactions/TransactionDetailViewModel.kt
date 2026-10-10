package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.utils.SubscriptionUtils

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionEditor
import io.github.hootkit_dev.hootbook.data.repository.QuickTemplateRepository
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.LendBorrowRepository
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowTransactionItem
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowPersonUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.GetLendBorrowEntryForTransactionUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.GetLendBorrowPersonsUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.MarkTransactionAsLoanUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.UnmarkTransactionAsLoanUseCase
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionState
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import io.github.hootkit_dev.hootbook.core.Constants
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.capitalizeFirst
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionEditor: TransactionEditor,
    private val addTransactionUseCase: io.github.hootkit_dev.hootbook.domain.usecase.AddTransactionUseCase,
    private val accountHoldings: io.github.hootkit_dev.hootbook.data.repository.AccountHoldingsSource,
    private val subscriptionRepository: SubscriptionRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val currencyRepository: CurrencyRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lendBorrowRepository: LendBorrowRepository,
    private val getLendBorrowPersonsUseCase: GetLendBorrowPersonsUseCase,
    private val addEditLendBorrowPersonUseCase: AddEditLendBorrowPersonUseCase,
    private val getLendBorrowEntryForTransactionUseCase: GetLendBorrowEntryForTransactionUseCase,
    private val markTransactionAsLoanUseCase: MarkTransactionAsLoanUseCase,
    private val unmarkTransactionAsLoanUseCase: UnmarkTransactionAsLoanUseCase,
    private val quickTemplateRepository: QuickTemplateRepository,
    val attachmentService: AttachmentService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())

    // The account a transfer was moved to while editing; a transaction keeps only its last 4
    private var selectedTargetAccount: AccountBalanceEntity? = null
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { it.copy(
                    isAmoledMode = prefs.isAmoledMode,
                    darkThemeConfig = prefs.isDarkThemeEnabled
                ) }
            }
        }

        viewModelScope.launch {
            combine(
                lendBorrowRepository.getAllTransactions(),
                lendBorrowRepository.getPersons()
            ) { lbTransactions, persons ->
                val personMap = persons.associateBy { it.id }
                lbTransactions
                    .filter { it.transactionId != null }
                    .associate { lb ->
                        val person = personMap[lb.personId]
                        lb.transactionId!! to PersonInfo(
                            name = person?.name ?: lb.title,
                            color = person?.color ?: "#4CAF50",
                            avatar = person?.avatar
                        )
                    }
            }.collect { mapping ->
                _uiState.update { it.copy(transactionPersonMapping = mapping) }
            }
        }
    }

    // All categories so icon/category lookups stay consistent even when a
    // transaction's category doesn't belong to its type (e.g. a lend/borrow
    // wallet transaction typed EXPENSE with an "Income" category).
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val availableAccounts: StateFlow<List<AccountBalanceEntity>> = accountBalanceRepository.getAllLatestBalances()
        .map { balances -> balances.distinctBy { "${it.bankName}_${it.accountLast4}" } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // People available for linking a transaction to a lend/borrow (loan) record.
    val persons: StateFlow<List<LendBorrowPerson>> = getLendBorrowPersonsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun loadLinkedLendBorrow(transactionId: Long) {
        if (transactionId <= 0L) {
            _uiState.update {
                it.copy(
                    linkedLendBorrow = null,
                    linkedLoanPersonName = null
                )
            }
            return
        }
        viewModelScope.launch {
            val entry = getLendBorrowEntryForTransactionUseCase(transactionId)
            var personName: String? = null
            if (entry != null) {
                personName = lendBorrowRepository.getPersonName(entry.personId)
            }
            _uiState.update {
                it.copy(
                    linkedLendBorrow = entry,
                    linkedLoanPersonName = personName
                )
            }
        }
    }

    fun showMarkAsLoanSheet() {
        _uiState.update { it.copy(showMarkAsLoanSheet = true) }
    }

    fun hideMarkAsLoanSheet() {
        _uiState.update {
            it.copy(
                showMarkAsLoanSheet = false,
                markAsLoanError = null
            )
        }
    }

    fun addPerson(name: String) {
        viewModelScope.launch { addEditLendBorrowPersonUseCase.addPerson(name = name) }
    }

    fun markAsLoan(
        personId: Long,
        loanType: LendBorrowType,
        amount: BigDecimal,
        title: String
    ) {
        viewModelScope.launch {
            try {
                val txn = _uiState.value.transaction
                val txnId = txn?.id ?: 0L
                markTransactionAsLoanUseCase(
                    transactionId = txnId,
                    personId = personId,
                    type = loanType,
                    amount = amount,
                    currency = txn?.currency ?: "",
                    title = title,
                    date = txn?.dateTime ?: LocalDateTime.now()
                )
                _uiState.update {
                    it.copy(
                        showMarkAsLoanSheet = false,
                        showEditLendBorrowSheet = false,
                        markAsLoanSuccess = true,
                        markAsLoanError = null
                    )
                }
                loadLinkedLendBorrow(txnId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        markAsLoanError = context.getString(R.string.msg_link_loan_failed)
                    )
                }
            }
        }
    }

    fun clearMarkAsLoanResult() {
        _uiState.update { it.copy(markAsLoanSuccess = false, markAsLoanError = null) }
    }

    fun showUnmarkLoanConfirm() {
        _uiState.update { it.copy(showUnmarkLoanConfirm = true) }
    }

    fun hideUnmarkLoanConfirm() {
        _uiState.update { it.copy(showUnmarkLoanConfirm = false) }
    }

    fun unmarkAsLoan() {
        viewModelScope.launch {
            val txn = _uiState.value.transaction
            val txnId = txn?.id ?: 0L
            try {
                unmarkTransactionAsLoanUseCase(txnId)
                _uiState.update {
                    it.copy(
                        showUnmarkLoanConfirm = false,
                        linkedLendBorrow = null,
                        linkedLoanPersonName = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showUnmarkLoanConfirm = false,
                        markAsLoanError = context.getString(R.string.msg_unlink_loan_failed)
                    )
                }
            }
        }
    }

    val selectedAccount: StateFlow<AccountBalanceEntity?> = _uiState.map { state ->
        val transaction = state.editableTransaction
        if (transaction == null) return@map null
        availableAccounts.value.find {
            it.bankName == transaction.bankName && it.accountLast4 == transaction.accountNumber
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val targetAccount: StateFlow<AccountBalanceEntity?> = _uiState.map { state ->
        val transaction = state.editableTransaction
        if (transaction == null) return@map null
        val accounts = availableAccounts.value
        transaction.toAccountId?.let { id -> accounts.find { it.accountId == id } }
            ?: accounts.find { it.accountLast4 == transaction.toAccount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    val allSubcategories: StateFlow<Map<Long, List<SubcategoryEntity>>> =
        subcategoryRepository.subcategoriesMap

    // Attachment state for edit mode
    private val _editableAttachments = MutableStateFlow<List<String>>(emptyList())
    val editableAttachments: StateFlow<List<String>> = _editableAttachments.asStateFlow()

    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            _uiState.update { it.copy(transaction = transaction) }
            val accountIconName = transaction?.let { txn ->
                accountBalanceRepository.getLatestBalance(txn.bankName ?: "", txn.accountNumber ?: "")?.iconName
            }
            _uiState.update { it.copy(accountIconName = accountIconName) }
            transaction?.let {
                determinePrimaryCurrency(it)
                calculateConvertedAmount(it)
                findLinkedSubscription(it)
                loadLinkedLendBorrow(it.id)
            }
        }
    }

    private suspend fun findLinkedSubscription(transaction: TransactionEntity) {
        if (transaction.isRecurring) {
            val linked = subscriptionRepository.matchTransactionToSubscription(
                transaction.merchantName,
                transaction.amount
            )
            _uiState.update { it.copy(subscription = linked) }
        } else {
            _uiState.update { it.copy(subscription = null) }
        }
    }

    private suspend fun determinePrimaryCurrency(transaction: TransactionEntity) {
        val effectiveCurrency = currencyRepository.effectiveBaseCurrencyCode.first()
        _uiState.update { it.copy(primaryCurrency = effectiveCurrency) }
    }

    private suspend fun calculateConvertedAmount(transaction: TransactionEntity) {
        val primaryCurrency = _uiState.value.primaryCurrency
        if (transaction.currency.isNotEmpty() && !transaction.currency.equals(
                primaryCurrency,
                ignoreCase = true
            )
        ) {
            // Convert the amount to the primary currency
            val converted = currencyConversionService.convertAmount(
                amount = transaction.amount,
                fromCurrency = transaction.currency,
                toCurrency = primaryCurrency
            )
            _uiState.update { it.copy(convertedAmount = converted) }
        } else {
            // No conversion needed if currencies are the same
            _uiState.update { it.copy(convertedAmount = null) }
        }
    }

    fun enterEditMode() {
        receivedAmountEdited = false
        val currentTransaction = _uiState.value.transaction
        val billingCycle = currentTransaction?.billingCycle
        
        var isCustom = false
        var count = 1
        var unit = "month"
        var endDate: LocalDate? = null

        if (billingCycle?.startsWith("custom_") == true) {
            isCustom = true
            val parts = billingCycle.split("_")
            count = parts.getOrNull(1)?.toIntOrNull() ?: 1
            unit = parts.getOrNull(2) ?: "month"
            val endDateStr = parts.getOrNull(3)
            if (endDateStr != null && endDateStr != "forever") {
                endDate = try { LocalDate.parse(endDateStr) } catch (e: Exception) { null }
            }
        }

        _uiState.update { state ->
            state.copy(
                editableTransaction = state.transaction?.copy(),
                isEditMode = true,
                errorMessage = null,
                isCustomCycle = isCustom,
                customCycleCount = count,
                customCycleUnit = unit,
                customCycleEndDate = endDate
            )
        }
        // Initialize editable attachments from transaction
        _uiState.value.transaction?.let { txn ->
            _editableAttachments.value = attachmentService.parseAttachments(txn.attachments)
        }

        // Load count of other transactions from same merchant (always, not just for SMS)
        _uiState.value.transaction?.let { txn ->
            viewModelScope.launch {
                // A blank merchant (it is optional) would "contain" every other transaction.
                val matches = if (txn.merchantName.isBlank()) emptyList()
                else transactionRepository.getTransactionsByMerchantContains(
                    txn.merchantName,
                    txn.id
                )
                _uiState.update { it.copy(
                    existingTransactionCount = matches.size,
                    matchedTransactions = matches,
                    selectedMatchIds = matches.map { m -> m.id }.toSet()
                ) }
            }
        }
    }

    fun exitEditMode() {
        _uiState.update {
            it.copy(
                editableTransaction = null,
                isEditMode = false,
                errorMessage = null,
                updateExistingTransactions = false,
                existingTransactionCount = 0
            )
        }
        _editableAttachments.value = emptyList()
    }

    fun toggleUpdateExistingTransactions() {
        _uiState.update { it.copy(updateExistingTransactions = !it.updateExistingTransactions) }
    }

    fun showMatchPreviewSheet() {
        _uiState.update { it.copy(showMatchPreviewSheet = true) }
    }

    fun hideMatchPreviewSheet() {
        _uiState.update { it.copy(showMatchPreviewSheet = false) }
    }

    fun toggleMatchSelection(transactionId: Long) {
        val current = _uiState.value.selectedMatchIds
        val updated = if (current.contains(transactionId)) current - transactionId
                      else current + transactionId
        _uiState.update { it.copy(selectedMatchIds = updated) }
    }

    fun selectAllMatches() {
        val allIds = _uiState.value.matchedTransactions.map { it.id }.toSet()
        _uiState.update { it.copy(selectedMatchIds = allIds) }
    }

    fun deselectAllMatches() {
        _uiState.update { it.copy(selectedMatchIds = emptySet()) }
    }

    /** Adds a transaction found via the Transactions search screen to the match preview list. */
    fun addTransactionToMatchList(transaction: TransactionEntity) {
        val current = _uiState.value.matchedTransactions
        if (current.any { it.id == transaction.id }) return
        _uiState.update { state ->
            state.copy(
                matchedTransactions = current + transaction,
                selectedMatchIds = state.selectedMatchIds + transaction.id,
                matchSearchQuery = "",
                matchSearchResults = emptyList()
            )
        }
    }

    fun updateMatchSearchQuery(query: String) {
        _uiState.update { it.copy(matchSearchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(matchSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val results = transactionRepository.searchTransactionsList(query)
                val existingIds = _uiState.value.matchedTransactions.map { it.id }.toSet()
                val currentId = _uiState.value.transaction?.id ?: -1L
                val filtered = results.filter { it.id !in existingIds && it.id != currentId }
                _uiState.update { it.copy(matchSearchResults = filtered) }
            } catch (e: Exception) {
                // Ignore search errors
            }
        }
    }

    /** Applies the current editable category to only the user-selected transactions. */
    fun applyToSelectedMatches() {
        val state = _uiState.value
        val category = state.editableTransaction?.category ?: return
        val ids = state.selectedMatchIds
        if (ids.isEmpty()) return

        viewModelScope.launch {
            try {
                val toUpdate = state.matchedTransactions
                    .filter { it.id in ids }
                    .map { it.copy(category = category) }
                toUpdate.forEach { transactionRepository.updateTransaction(it) }
                _uiState.update { it.copy(showMatchPreviewSheet = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_update_failed, e.message.orEmpty())) }
            }
        }
    }

    fun updateMerchantName(name: String) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(merchantName = name)) }
        validateMerchantName(name)
    }

    fun updateAmount(amountStr: String) {
        val amount = amountStr.toBigDecimalOrNull()
        if (amount != null && amount > BigDecimal.ZERO) {
            _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(amount = amount), errorMessage = null) }
            refreshReceivedAmount()
        } else if (amountStr.isNotEmpty()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.err_amount_positive)) }
        }
    }

    fun updateTransactionType(type: TransactionType) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(transactionType = type)) }
        refreshReceivedAmount()

        // Auto-select category based on type
        val newCategory = when (type) {
            TransactionType.INCOME -> "Salary"
            TransactionType.EXPENSE -> "Miscellaneous"
            TransactionType.CREDIT -> "Credit Bill"
            TransactionType.TRANSFER -> {
                val targetAccount = _uiState.value.editableTransaction?.toAccount
                if (targetAccount == "wallet") "Cash Withdrawal" else "Self Transfer"
            }

            TransactionType.INVESTMENT -> "Investment"
            TransactionType.BALANCE_UPDATE -> "Miscellaneous"
            TransactionType.LENT -> "Lent"
            TransactionType.BORROWED -> "Borrowed"
        }

        updateCategory(newCategory)
    }

    fun addAttachment(path: String) {
        _editableAttachments.update { it + path }
    }

    fun removeAttachment(path: String) {
        attachmentService.deleteAttachment(path)
        _editableAttachments.update { it - path }
    }

    fun updateCategory(category: String) {
        _uiState.update { state ->
            val current = state.editableTransaction
            state.copy(
                editableTransaction = current?.copy(
                    category = category.ifEmpty { "Miscellaneous" },
                    subcategory = if (current.category != category) null else current.subcategory
                )
            )
        }
    }

    fun updateSubcategory(subcategory: String?) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(subcategory = subcategory)) }
    }

    fun updateDateTime(dateTime: LocalDateTime) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(dateTime = dateTime)) }
    }

    fun updateDescription(description: String?) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(description = if (description.isNullOrEmpty()) null else description)) }
    }

    fun updateRecurringStatus(isRecurring: Boolean) {
        _uiState.update { state ->
            val current = state.editableTransaction
            state.copy(
                editableTransaction = current?.copy(
                    isRecurring = isRecurring,
                    billingCycle = if (isRecurring) current.billingCycle ?: "Monthly" else null
                ),
                isCustomCycle = if (!isRecurring) false else state.isCustomCycle
            )
        }
    }

    fun updateBillingCycle(cycle: String) {
        _uiState.update { it.copy(
            editableTransaction = it.editableTransaction?.copy(billingCycle = cycle),
            isCustomCycle = io.github.hootkit_dev.hootbook.utils.SubscriptionUtils.isCustom(cycle)
        ) }
    }

    fun updateSubscriptionCustomCycleCount(count: Int) {
        _uiState.update { it.copy(customCycleCount = count) }
    }

    fun updateSubscriptionCustomCycleUnit(unit: String) {
        _uiState.update { it.copy(customCycleUnit = unit) }
    }

    fun updateSubscriptionCustomCycleEndDate(date: LocalDate?) {
        _uiState.update { it.copy(customCycleEndDate = date) }
    }

    fun updateAccountNumber(accountNumber: String?) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(accountNumber = if (accountNumber.isNullOrEmpty()) null else accountNumber)) }
    }

    fun updateTransactionAccount(account: AccountBalanceEntity?) {
        _uiState.update { state ->
            val current = state.editableTransaction
            state.copy(
                editableTransaction = current?.copy(
                    bankName = account?.bankName ?: "Manual Entry",
                    accountNumber = account?.accountLast4,
                    currency = account?.currency ?: current.currency
                ),
                accountIconName = account?.iconName // Update the icon name in state too
            )
        }
        refreshReceivedAmount()
    }

    /** The currencies [account] holds, its main one first. */
    fun currenciesOf(account: AccountBalanceEntity?): List<String> {
        account ?: return emptyList()
        val held = account.accountId?.let { accountHoldings.holdings.value[it] }?.pockets?.map { it.currency }.orEmpty()
        return listOf(account.currency) + held.filter { it != account.currency }
    }

    /** Which of the account's currencies the transaction is in. */
    fun updateTransactionCurrency(currency: String) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(currency = currency)) }
        _uiState.value.editableTransaction?.let { viewModelScope.launch { calculateConvertedAmount(it) } }
        refreshReceivedAmount()
    }

    /** Which of the target account's currencies a transfer lands in. */
    fun updateTransactionTargetCurrency(currency: String) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(toCurrency = currency)) }
        refreshReceivedAmount()
    }

    /** What reached the target, typed by the user; it then stays as typed. */
    fun updateReceivedAmount(amount: String) {
        val value = amount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return
        receivedAmountEdited = true
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(toAmount = value)) }
    }

    // Set once the user typed what reached the target; the amount then no longer follows the rate
    private var receivedAmountEdited = false
    private var receivedAmountJob: Job? = null

    /**
     * Keeps what reaches a transfer's target in step with the edit: nothing when both sides share a
     * currency, the transfer's own rate while its currencies stay, else today's rate.
     */
    private fun refreshReceivedAmount() {
        if (receivedAmountEdited) return
        receivedAmountJob?.cancel()
        receivedAmountJob = viewModelScope.launch {
            val txn = _uiState.value.editableTransaction ?: return@launch
            val original = _uiState.value.transaction
            val toCurrency = txn.toCurrency
            val received = when {
                txn.transactionType != TransactionType.TRANSFER || toCurrency == null || toCurrency == txn.currency -> null
                original?.toAmount != null && selectedTargetAccount == null &&
                    original.currency == txn.currency && original.toCurrency == toCurrency ->
                    if (original.amount.signum() == 0) original.toAmount
                    else original.toAmount.multiply(txn.amount).divide(original.amount, 2, java.math.RoundingMode.HALF_UP)
                else -> currencyConversionService.convertAmount(txn.amount, txn.currency, toCurrency)
            }
            _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(toAmount = received)) }
        }
    }

    fun updateTransactionTargetAccount(account: AccountBalanceEntity?) {
        selectedTargetAccount = account
        _uiState.update { state ->
            val txn = state.editableTransaction
            // Lands in the currency sent when the target holds it, else in its main one
            val toCurrency = account?.let { target -> txn?.currency?.takeIf { it in currenciesOf(target) } ?: target.currency }
            state.copy(editableTransaction = txn?.copy(
                toAccount = account?.accountLast4, toAccountId = account?.accountId, toCurrency = toCurrency
            ))
        }
        refreshReceivedAmount()

        // Update category if type is TRANSFER
        _uiState.value.editableTransaction?.let { txn ->
            if (txn.transactionType == TransactionType.TRANSFER) {
                val newCategory = if (account?.accountLast4 == "wallet") {
                    "Cash Withdrawal"
                } else {
                    "Self Transfer"
                }
                updateCategory(newCategory)
            }
        }
    }

    fun updateCurrency(currency: String) {
        _uiState.update { it.copy(editableTransaction = it.editableTransaction?.copy(currency = currency)) }
        // Recalculate converted amount when currency changes
        _uiState.value.editableTransaction?.let { transaction ->
            viewModelScope.launch {
                calculateConvertedAmount(transaction)
            }
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val toSave = state.editableTransaction ?: return

        // Validate before saving
        if (toSave.amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.err_amount_positive)) }
            return
        }
        // A transfer moves money only once it knows where to
        if (toSave.transactionType == TransactionType.TRANSFER && selectedTargetAccount == null &&
            toSave.toAccountId == null && toSave.toAccount.isNullOrBlank()
        ) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.err_transfer_needs_target)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Handle custom cycle serialization
                val finalBillingCycle = if (state.isCustomCycle) {
                    val endDateStr = state.customCycleEndDate?.toString() ?: "forever"
                    "custom_${state.customCycleCount}_${state.customCycleUnit}_$endDateStr"
                } else toSave.billingCycle

                // Normalize merchant name before saving
                val normalizedTransaction = toSave.copy(
                    merchantName = normalizeMerchantName(toSave.merchantName),
                    billingCycle = finalBillingCycle,
                    updatedAt = LocalDateTime.now(),
                    attachments = attachmentService.joinAttachments(_editableAttachments.value)
                )

                // Saves it and moves its effect on balances (amount, type, date, account, target)
                val originalTransaction = state.transaction
                if (originalTransaction != null) {
                    receivedAmountJob?.join()
                    val received = _uiState.value.editableTransaction?.toAmount
                    val withTarget = normalizedTransaction.copy(
                        toAmount = received?.takeIf {
                            normalizedTransaction.transactionType == TransactionType.TRANSFER &&
                                normalizedTransaction.toCurrency != normalizedTransaction.currency
                        }
                    )
                    transactionEditor.update(originalTransaction, withTarget, selectedTargetAccount)
                } else {
                    transactionRepository.updateTransaction(normalizedTransaction)
                }
                selectedTargetAccount = null
                receivedAmountEdited = false

                // Sync with subscriptions if recurring
                syncSubscriptionForTransaction(state.transaction, normalizedTransaction)

                // Update existing transactions if checkbox is checked (fuzzy/contains match)
                if (state.updateExistingTransactions && normalizedTransaction.merchantName.isNotBlank()) {
                    transactionRepository.updateCategoryAndSubcategoryForMerchantContains(
                        normalizedTransaction.merchantName,
                        normalizedTransaction.category,
                        normalizedTransaction.subcategory
                    )
                }

                _uiState.update {
                    it.copy(
                        transaction = normalizedTransaction,
                        saveSuccess = true,
                        isEditMode = false,
                        editableTransaction = null,
                        errorMessage = null,
                                updateExistingTransactions = false,
                        existingTransactionCount = 0,
                        matchedTransactions = emptyList(),
                        selectedMatchIds = emptySet(),
                        showMatchPreviewSheet = false
                    )
                }
                findLinkedSubscription(normalizedTransaction) // Refresh linked subscription
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_update_failed, e.message.orEmpty())) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun cancelEdit() {
        selectedTargetAccount = null
        receivedAmountEdited = false
        exitEditMode()
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private fun validateMerchantName(name: String) {
        _uiState.update { it.copy(errorMessage = if (name.isBlank()) context.getString(R.string.msg_merchant_required) else null) }
    }

    /**
     * Normalizes merchant name to consistent format.
     * Converts all-caps to proper case, preserves already mixed case.
     */
    private fun normalizeMerchantName(name: String): String {
        val trimmed = name.trim()

        // If it's all uppercase, convert to proper case
        return if (trimmed == trimmed.uppercase()) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                word.capitalizeFirst()
            }
        } else {
            // Already has mixed case, keep as is
            trimmed
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteTransaction() {
        viewModelScope.launch {
            _uiState.value.transaction?.let { txn ->
                _uiState.update { it.copy(isDeleting = true, showDeleteDialog = false) }

                try {
                    transactionRepository.deleteTransaction(txn)
                    _uiState.update { it.copy(deleteSuccess = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_delete_failed)) }
                } finally {
                    _uiState.update { it.copy(isDeleting = false) }
                }
            }
        }
    }

    /** Store this transaction's merchant, category, type, account and notes as a quick-add template. */
    fun saveAsQuickTemplate() {
        val txn = _uiState.value.transaction ?: return
        viewModelScope.launch {
            try {
                quickTemplateRepository.add(
                    QuickTemplateEntity(
                        name = txn.merchantName.ifBlank { txn.subcategory ?: txn.category },
                        merchantName = txn.merchantName,
                        category = txn.category,
                        subcategory = txn.subcategory,
                        transactionType = txn.transactionType,
                        amount = txn.amount,
                        prefillAmount = false,
                        bankName = txn.bankName,
                        accountLast4 = txn.accountNumber,
                        currency = txn.currency,
                        notes = txn.description
                    )
                )
            } catch (e: Exception) {
                Log.w("TransactionDetailVM", "Quick template not saved", e)
            }
        }
    }

    fun duplicateTransaction() {
        viewModelScope.launch {
            _uiState.value.transaction?.let { txn ->
                try {
                    // A new entry dated now, written the way the add screen writes one, so it
                    // moves the same balances as the original
                    val target = txn.toAccountId?.let { id -> availableAccounts.value.firstOrNull { it.accountId == id } }
                    addTransactionUseCase.execute(
                        amount = txn.amount,
                        merchant = txn.merchantName,
                        category = txn.category,
                        subcategory = txn.subcategory,
                        type = txn.transactionType,
                        date = LocalDateTime.now(),
                        notes = txn.description,
                        bankName = txn.bankName?.takeIf { txn.accountNumber != null },
                        accountLast4 = txn.accountNumber,
                        currency = txn.currency,
                        targetAccountBankName = target?.bankName,
                        targetAccountLast4 = target?.accountLast4,
                        targetAmount = txn.toAmount,
                        targetCurrency = txn.toCurrency,
                        createSubscription = false
                    )
                    _uiState.update { it.copy(duplicateSuccess = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_duplicate_failed)) }
                }
            }
        }
    }

    fun clearDuplicateSuccess() {
        _uiState.update { it.copy(duplicateSuccess = false) }
    }

    /**
     * Keeps the subscription in step with a recurring transaction. Only a transaction that is or
     * was recurring touches one, matched by what it was before the edit: fixing the category of
     * an ordinary payment must not hide a subscription that happens to share merchant and amount.
     */
    private suspend fun syncSubscriptionForTransaction(original: TransactionEntity?, transaction: TransactionEntity) {
        val wasRecurring = original?.isRecurring == true
        if (!wasRecurring && !transaction.isRecurring) return
        val existing = original?.takeIf { wasRecurring }?.let {
            subscriptionRepository.matchTransactionToSubscription(it.merchantName, it.amount)
        } ?: subscriptionRepository.matchTransactionToSubscription(transaction.merchantName, transaction.amount)

        if (!transaction.isRecurring) {
            existing?.let { subscriptionRepository.hideSubscription(it.id) }
            return
        }
        val fromThis = SubscriptionUtils.calculateNextPaymentDate(transaction.dateTime.toLocalDate(), transaction.billingCycle)
        // Editing an older charge must not pull the next payment back
        val nextPaymentDate = existing?.nextPaymentDate?.takeIf { it.isAfter(fromThis) } ?: fromThis
        val subscription = existing?.copy(
            merchantName = transaction.merchantName,
            amount = transaction.amount,
            nextPaymentDate = nextPaymentDate,
            category = transaction.category,
            subcategory = transaction.subcategory,
            bankName = transaction.bankName,
            currency = transaction.currency,
            billingCycle = transaction.billingCycle,
            state = SubscriptionState.ACTIVE,
            updatedAt = LocalDateTime.now()
        ) ?: SubscriptionEntity(
            merchantName = transaction.merchantName,
            amount = transaction.amount,
            nextPaymentDate = nextPaymentDate,
            state = SubscriptionState.ACTIVE,
            bankName = transaction.bankName,
            category = transaction.category,
            subcategory = transaction.subcategory,
            currency = transaction.currency,
            billingCycle = transaction.billingCycle,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        subscriptionRepository.insertSubscription(subscription)
    }

}
