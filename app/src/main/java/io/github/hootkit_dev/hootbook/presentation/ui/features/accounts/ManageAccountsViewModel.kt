package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardType
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountRenamer
import io.github.hootkit_dev.hootbook.data.repository.CardRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import androidx.core.content.edit

data class ManageAccountsUiState(
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val linkedCards: Map<String, List<CardEntity>> = emptyMap(),
    val orphanedCards: List<CardEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSavingAccount: Boolean = false,
    val accountSaveError: String? = null,
    val mainAccountKey: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class AccountFormState(
    val bankName: String = "",
    val accountLast4: String = "",
    val balance: String = "",
    val creditLimit: String = "",
    val accountType: AccountType = AccountType.SAVINGS,
    val iconResId: Int = 0,
    val iconName: String = "",
    val currency: String = "CNY",
    val isValid: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

enum class AccountType {
    SAVINGS,
    CURRENT,
    CREDIT,
    WALLET
}

@HiltViewModel
class ManageAccountsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val brokerageRepository: BrokerageRepository,
    private val accountRenamer: AccountRenamer
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ManageAccountsUiState())
    val uiState: StateFlow<ManageAccountsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()
    val brokerageConnections = brokerageRepository.connections

    val defaultCurrencyForNewAccounts: StateFlow<String> = combine(
        userPreferencesRepository.defaultCurrencyEnabled,
        userPreferencesRepository.defaultCurrencyCode,
        userPreferencesRepository.baseCurrency
    ) { enabled, code, baseCurrency ->
        if (enabled && !code.isNullOrBlank()) code else baseCurrency
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CNY")

    init {
        loadAccounts()
        loadMainAccount()
        loadCards()
        initializeDefaultWallet()
        initFormCurrency()
        viewModelScope.launch { runCatching { brokerageRepository.load() } }
    }

    fun disconnectBrokerage(id: String) {
        viewModelScope.launch { runCatching { brokerageRepository.disconnect(id) } }
    }

    private fun initFormCurrency() {
        viewModelScope.launch {
            _formState.update { it.copy(currency = resolveDefaultCurrency()) }
        }
    }

    private suspend fun resolveDefaultCurrency(): String {
        if (userPreferencesRepository.defaultCurrencyEnabled.first()) {
            userPreferencesRepository.defaultCurrencyCode.first()?.let { code ->
                if (code.isNotBlank()) return code
            }
        }
        return userPreferencesRepository.baseCurrency.first()
    }

    /**
     * A Cash wallet to start with, created once: renamed or deleted, it stays that way.
     */
    private fun initializeDefaultWallet() {
        if (sharedPrefs.getBoolean(KEY_WALLET_SEEDED, false)) return
        sharedPrefs.edit { putBoolean(KEY_WALLET_SEEDED, true) }
        viewModelScope.launch {
            // An install that already has accounts was seeded before this flag existed
            val hasAccounts = accountBalanceRepository.getAllLatestBalances().first().isNotEmpty()
            if (!hasAccounts) {
                val baseCurrency = userPreferencesRepository.userPreferences.first().baseCurrency
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = "Cash",
                        accountLast4 = "wallet",
                        balance = BigDecimal.ZERO,
                        timestamp = LocalDateTime.now(),
                        sourceType = "MANUAL",
                        isWallet = true,
                        iconResId = R.drawable.type_finance_dollar_banknote,
                        iconName = "type_finance_dollar_banknote",
                        color = "#4CAF50",
                        currency = baseCurrency
                    )
                )
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }

    private fun loadCards() {
        viewModelScope.launch {
            cardRepository.getAllCards().collect { allCards ->
                // Group cards by linked account
                val linkedCardsMap = mutableMapOf<String, MutableList<CardEntity>>()
                val orphaned = mutableListOf<CardEntity>()

                for (card in allCards) {
                    when {
                        card.cardType == CardType.DEBIT && card.accountLast4 != null -> {
                            linkedCardsMap.getOrPut(card.accountLast4) { mutableListOf() }.add(card)
                        }
                        card.cardType == CardType.DEBIT && card.accountLast4 == null -> {
                            orphaned.add(card)
                        }
                    // Credit cards are not orphaned, they're standalone
                    }
                }

                _uiState.update { it.copy(linkedCards = linkedCardsMap, orphanedCards = orphaned) }
            }
        }
    }

    private fun loadMainAccount() {
        val main = sharedPrefs.getString("main_account", null)
        _uiState.update { it.copy(mainAccountKey = main) }
    }

    /** No account is preselected for new entries any more. */
    fun clearMainAccount() {
        sharedPrefs.edit { remove("main_account") }
        _uiState.update { it.copy(mainAccountKey = null) }
    }

    /**
     * The account new entries start with. It only preselects the account: the app's main
     * currency is its own setting, and no other account changes.
     */
    fun setAsMainAccount(bankName: String, accountLast4: String) {
        val key = "${bankName}_${accountLast4}"
        sharedPrefs.edit { putString("main_account", key) }
        _uiState.update { it.copy(mainAccountKey = key) }
    }

    fun updateBankName(name: String) {
        _formState.update {
            it.copy(bankName = name, isValid = validateForm(name, it.accountLast4, it.balance))
        }
    }

    fun updateAccountLast4(last4: String) {
        // Only allow 4 characters
        if (last4.length <= 4) {
            _formState.update {
                it.copy(
                    accountLast4 = last4,
                    isValid = validateForm(it.bankName, last4, it.balance)
                )
            }
        }
    }

    fun updateBalance(balance: String) {
        // Only allow valid numeric input
        if (balance.isEmpty() || balance.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update {
                it.copy(
                    balance = balance,
                    isValid = validateForm(it.bankName, it.accountLast4, balance)
                )
            }
        }
    }

    fun updateCreditLimit(limit: String) {
        // Only allow valid numeric input
        if (limit.isEmpty() || limit.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(creditLimit = limit) }
        }
    }

    fun updateAccountType(type: AccountType) {
        _formState.update { it.copy(accountType = type) }
    }

    fun updateIcon(iconName: String) {
        val iconResId = IconResolutionUtils.nameToResId(context, iconName)
        _formState.update { it.copy(iconResId = iconResId, iconName = iconName) }
    }

    fun updateCurrency(currency: String) {
        _formState.update { it.copy(currency = currency) }
    }

    private fun validateForm(bankName: String, last4: String, balance: String): Boolean {
        return bankName.isNotBlank() &&
                last4.length == 4 &&
                balance.isNotBlank() &&
                balance.toBigDecimalOrNull() != null
    }

    fun clearAccountSaveError() {
        _uiState.update { it.copy(accountSaveError = null) }
    }

    private suspend fun accountExists(bankName: String, last4: String): Boolean =
        accountBalanceRepository.getAllLatestBalances().first().any {
            it.bankName.trim() == bankName.trim() && it.accountLast4 == last4
        }

    fun addAccount(
        bankName: String,
        balance: BigDecimal,
        accountLast4: String,
        iconResId: Int,
        iconName: String,
        colorHex: String,
        isCreditCard: Boolean = false,
        isWallet: Boolean = false,
        creditLimit: BigDecimal? = null,
        currency: String = "CNY",
        addedCurrencies: Map<String, BigDecimal> = emptyMap(),
        cardDates: io.github.hootkit_dev.hootbook.domain.model.CardDates = io.github.hootkit_dev.hootbook.domain.model.CardDates(),
        onSaved: () -> Unit = {}
    ) {
        if (_uiState.value.isSavingAccount) return
        val normalizedName = bankName.trim()
        if (normalizedName.isEmpty() || (!isWallet && accountLast4.length != 4)) return
        // Set synchronously so rapid taps cannot launch duplicate insertions.
        _uiState.update { it.copy(isSavingAccount = true, accountSaveError = null) }
        viewModelScope.launch {
            var saved = false
            try {
                if (accountExists(normalizedName, accountLast4)) {
                    _uiState.update { it.copy(accountSaveError = context.getString(R.string.account_already_exists)) }
                    return@launch
                }
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = normalizedName,
                        accountLast4 = accountLast4,
                        balance = balance,
                        creditLimit = creditLimit,
                        timestamp = LocalDateTime.now(),
                        isCreditCard = isCreditCard,
                        isWallet = isWallet,
                        iconResId = iconResId,
                        iconName = iconName,
                        sourceType = "MANUAL",
                        currency = currency,
                        color = colorHex
                    )
                )
                addCurrencies(normalizedName, accountLast4, addedCurrencies)
                if (isCreditCard) {
                    accountBalanceRepository.updateAccount(normalizedName, accountLast4) {
                        it.copy(statementDay = cardDates.statementDay, dueDay = cardDates.dueDay)
                    }
                }
                saved = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ManageAccountsViewModel", "Failed to save account", e)
                _uiState.update { it.copy(accountSaveError = context.getString(R.string.account_save_failed)) }
            } finally {
                _uiState.update { it.copy(isSavingAccount = false) }
            }
            if (saved) onSaved()
        }
    }

    /** Form submission owns its entire save lifecycle; navigation happens after persistence. */
    fun addAccount(onSaved: () -> Unit = {}) {
        val state = _formState.value
        if (!state.isValid || state.isSaving) return
        if (state.accountType == AccountType.CREDIT && state.creditLimit.isNotBlank() && state.creditLimit.toBigDecimalOrNull() == null) {
            _formState.update { it.copy(errorMessage = context.getString(R.string.err_invalid_amount)) }
            return
        }
        _formState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            var saved = false
            try {
                val creditLimit = if (state.accountType == AccountType.CREDIT && state.creditLimit.isNotBlank()) {
                    state.creditLimit.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException(context.getString(R.string.err_invalid_amount))
                } else null
                if (accountExists(state.bankName, state.accountLast4)) {
                    _formState.update { it.copy(errorMessage = context.getString(R.string.account_already_exists)) }
                    return@launch
                }
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = state.bankName.trim(),
                        accountLast4 = state.accountLast4,
                        balance = BigDecimal(state.balance),
                        creditLimit = creditLimit,
                        timestamp = LocalDateTime.now(),
                        isCreditCard = state.accountType == AccountType.CREDIT,
                        isWallet = state.accountType == AccountType.WALLET,
                        iconResId = state.iconResId,
                        iconName = state.iconName,
                        sourceType = "MANUAL",
                        currency = state.currency,
                        color = "#33B5E5"
                    )
                )
                // Keep the submitted currency for the reset; no second asynchronous save/reset race.
                _formState.value = AccountFormState(currency = state.currency)
                saved = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ManageAccountsViewModel", "Failed to save account", e)
                _formState.update { it.copy(errorMessage = context.getString(R.string.account_save_failed)) }
            } finally {
                _formState.update { it.copy(isSaving = false) }
            }
            if (saved) onSaved()
        }
    }

    /** Adds currencies to an account, each starting at its own balance. */
    private suspend fun addCurrencies(bankName: String, accountLast4: String, currencies: Map<String, BigDecimal>) {
        val account = accountBalanceRepository.getLatestBalance(bankName, accountLast4) ?: return
        val now = LocalDateTime.now()
        currencies.forEach { (currency, opening) ->
            accountBalanceRepository.insertBalance(
                account.copy(
                    id = 0,
                    balance = opening,
                    currency = currency,
                    timestamp = now,
                    transactionId = null,
                    
                    sourceType = "MANUAL",
                    createdAt = now
                )
            )
        }
    }

    fun updateAccountBalance(bankName: String, accountLast4: String, newBalance: BigDecimal, currency: String? = null) {
        viewModelScope.launch {
            // Get the latest balance to preserve credit limit
            val latestBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)

            accountBalanceRepository.insertBalance(AccountBalanceEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                balance = newBalance,
                creditLimit = latestBalance?.creditLimit,
                timestamp = LocalDateTime.now(),
                iconResId = latestBalance?.iconResId ?: 0,
                iconName = latestBalance?.iconName ?: "",
                isWallet = latestBalance?.isWallet ?: false,
                sourceType = "BALANCE_CALIBRATION",
                currency = currency ?: latestBalance?.currency ?: resolveDefaultCurrency(),
                color = latestBalance?.color ?: "#33B5E5"
            )
            )
        }
    }

    fun updateCreditCard(
            bankName: String,
            accountLast4: String,
            newBalance: BigDecimal,
            newLimit: BigDecimal,
            currency: String? = null
    ) {
        viewModelScope.launch {
            val latestBalance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
            accountBalanceRepository.insertBalance(
                AccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = accountLast4,
                    balance = newBalance,
                    creditLimit = newLimit,
                    timestamp = LocalDateTime.now(),
                    isCreditCard = true,
                    sourceType = "BALANCE_CALIBRATION",
                    iconResId = latestBalance?.iconResId ?: 0,
                    iconName = latestBalance?.iconName ?: "type_finance_credit_card",
                    currency = currency ?: latestBalance?.currency ?: resolveDefaultCurrency(),
                    color = latestBalance?.color ?: "#E91E63"
                )
            )
            accountBalanceRepository.updateAccount(bankName, accountLast4) {
                it.copy(isCreditCard = true, isWallet = false, creditLimit = newLimit)
            }
        }
    }

    fun clearError() {
        _formState.update { it.copy(errorMessage = null) }
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadBalanceHistory(bankName: String, accountLast4: String) {
        viewModelScope.launch {
            val history =
                    accountBalanceRepository.getBalanceHistoryForAccount(bankName, accountLast4)
            _uiState.update { it.copy(balanceHistory = history) }
        }
    }

    fun deleteBalanceRecord(id: Long, bankName: String, accountLast4: String) {
        viewModelScope.launch {
            val record = accountBalanceRepository.getBalanceById(id) ?: return@launch
            // A transaction's row goes with the transaction; the opening balance stays
            if (record.transactionId != null || record.sourceType == "OPENING_BALANCE") return@launch

            // Check if this is the only record
            val count = accountBalanceRepository.getBalanceCountForAccount(bankName, accountLast4)
            if (count > 1) {
                accountBalanceRepository.deleteBalanceById(id)
                // Later rows run on from what was there before it
                val before = accountBalanceRepository.getLatestBalanceOnOrBefore(
                    bankName, accountLast4, record.timestamp, record.currency
                )?.balance ?: BigDecimal.ZERO
                accountBalanceRepository.recalculateBalancesAfter(bankName, accountLast4, record.timestamp, before, record.currency)
                // Reload history and accounts
                loadBalanceHistory(bankName, accountLast4)
                loadAccounts()
            }
        }
    }

    fun updateBalanceRecord(
            id: Long,
            newBalance: BigDecimal,
            bankName: String,
            accountLast4: String
    ) {
        viewModelScope.launch {
            val record = accountBalanceRepository.getBalanceById(id) ?: return@launch
            // A transaction's row follows the transaction: edit the transaction instead
            if (record.transactionId != null) return@launch

            accountBalanceRepository.updateBalanceById(id, newBalance)
            // Later rows run on from the corrected figure
            accountBalanceRepository.recalculateBalancesAfter(bankName, accountLast4, record.timestamp, newBalance, record.currency)
            // Reload history and accounts
            loadBalanceHistory(bankName, accountLast4)
            loadAccounts()
        }
    }

    fun clearBalanceHistory() {
        _uiState.update { it.copy(balanceHistory = emptyList()) }
    }

    fun linkCardToAccount(cardId: Long, accountLast4: String) {
        viewModelScope.launch {
            try {
                Log.d(
                        "ManageAccountsViewModel",
                        "Starting to link card $cardId to account $accountLast4"
                )

                // Get card info first to know if there's a balance to copy
                val card = cardRepository.getCardById(cardId)
                val hasBalance = card?.lastBalance != null

                // Link the card to the account
                cardRepository.linkCardToAccount(cardId, accountLast4)

                // If card had a balance, copy it to the account
                if (card != null && hasBalance) {
                    try {
                        val insertedId =
                                accountBalanceRepository.insertBalanceUpdate(
                                        bankName = card.bankName,
                                        accountLast4 = accountLast4,
                                        balance = card.lastBalance!!,
                                        timestamp = card.lastBalanceDate ?: LocalDateTime.now(),
                                        
                                        sourceType = "CARD_LINK",
                                        currency = card.currency
                                )
                        Log.d(
                                "ManageAccountsViewModel",
                                "Balance copied to account. Insert ID: $insertedId"
                        )

                        // Show success message with balance
                        _uiState.update { it.copy(successMessage = context.getString(R.string.msg_card_linked)) }
                    } catch (e: Exception) {
                        Log.e(
                                "ManageAccountsViewModel",
                                "Failed to copy balance: ${e.message}",
                                e
                        )
                        // Still show success for linking, but note the balance issue
                        _uiState.update {
                            it.copy(
                                    successMessage =
                                            "Card linked successfully (balance update failed)"
                            )
                        }
                    }
                } else {
                    // No balance to copy, just show link success
                    _uiState.update { it.copy(successMessage = context.getString(R.string.msg_card_linked)) }
                }

                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }

                loadCards()
                loadAccounts()
            } catch (e: Exception) {
                Log.e("ManageAccountsViewModel", "Failed to link card", e)
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_card_link_failed, e.message.orEmpty())) }
            }
        }
    }

    fun unlinkCard(cardId: Long) {
        viewModelScope.launch {
            cardRepository.unlinkCard(cardId)
            loadCards()
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            try {
                Log.d("ManageAccountsViewModel", "Deleting card with ID: $cardId")
                cardRepository.deleteCard(cardId)
                _uiState.update { it.copy(successMessage = context.getString(R.string.msg_card_deleted)) }

                // Clear message after delay
                delay(2000)
                _uiState.update { it.copy(successMessage = null) }

                loadCards()
            } catch (e: Exception) {
               Log.e("ManageAccountsViewModel", "Failed to delete card", e)
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_card_delete_failed, e.message.orEmpty())) }
            }
        }
    }

    fun setCardActive(cardId: Long, isActive: Boolean) {
        viewModelScope.launch {
            cardRepository.setCardActive(cardId, isActive)
            loadCards()
        }
    }

    fun deleteAccount(bankName: String, accountLast4: String) {
        viewModelScope.launch {
            try {
                // Unlink any cards linked to this account
                val linkedCards = _uiState.value.linkedCards[accountLast4] ?: emptyList()
                linkedCards.forEach { card -> cardRepository.unlinkCard(card.id) }

                // Delete all balance records for this account
                accountBalanceRepository.deleteAccount(bankName, accountLast4)

                val key = "${bankName}_${accountLast4}"

                // Remove from main account if present
                if (_uiState.value.mainAccountKey == key) {
                    sharedPrefs.edit().remove("main_account").apply()
                }

                _uiState.update {
                    it.copy(
                            mainAccountKey = if (it.mainAccountKey == key) null else it.mainAccountKey,
                            successMessage = context.getString(R.string.msg_account_deleted)
                    )
                }

                // Clear message after delay
                delay(3000)
                _uiState.update { it.copy(successMessage = null) }

                loadCards() // Reload cards to update UI
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_account_delete_failed, e.message.orEmpty())) }
            }
        }
    }

    fun editAccount(
            oldBankName: String,
            accountLast4: String,
            newBankName: String,
            newBalance: BigDecimal,
            newCreditLimit: BigDecimal?,
            isCreditCard: Boolean,
            isWallet: Boolean,
            newIconResId: Int,
            newIconName: String,
            newColorHex: String,
            newCurrency: String? = null,
            addedCurrencies: Map<String, BigDecimal> = emptyMap(),
            cardDates: io.github.hootkit_dev.hootbook.domain.model.CardDates = io.github.hootkit_dev.hootbook.domain.model.CardDates(),
            // Called once everything is saved, e.g. to open the renamed account
            onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val latestBalance = accountBalanceRepository.getLatestBalance(oldBankName, accountLast4)
                val effectiveCurrency = newCurrency?.takeIf { it.isNotBlank() }
                    ?: latestBalance?.currency
                    ?: resolveDefaultCurrency()

                // A new name moves everything that refers to the account: its transactions,
                // balances, cards, templates, budgets and the default-account preference
                if (newBankName != oldBankName) {
                    accountRenamer.rename(oldBankName, accountLast4, newBankName)
                    _uiState.update {
                        it.copy(
                            mainAccountKey = sharedPrefs.getString("main_account", null)
                        )
                    }
                }

                // Insert new balance record with updated values
                accountBalanceRepository.insertBalance(
                    AccountBalanceEntity(
                        bankName = newBankName,
                        accountLast4 = accountLast4,
                        balance = newBalance,
                        creditLimit = newCreditLimit,
                        timestamp = LocalDateTime.now(),
                        isCreditCard = isCreditCard,
                        isWallet = isWallet,
                        sourceType = "MANUAL",
                        iconResId = newIconResId,
                        iconName = newIconName,
                        currency = effectiveCurrency,
                        color = newColorHex
                    )
                )
                addCurrencies(newBankName, accountLast4, addedCurrencies)
                // The account's own details: what every screen shows
                accountBalanceRepository.updateAccount(newBankName, accountLast4) {
                    it.copy(
                        mainCurrency = effectiveCurrency,
                        isCreditCard = isCreditCard,
                        isWallet = isWallet,
                        creditLimit = newCreditLimit,
                        iconResId = newIconResId,
                        iconName = newIconName,
                        color = newColorHex,
                        statementDay = cardDates.statementDay.takeIf { isCreditCard },
                        dueDay = cardDates.dueDay.takeIf { isCreditCard }
                    )
                }

                _uiState.update { it.copy(successMessage = context.getString(R.string.msg_account_updated)) }
                onSaved()

                delay(3000)
                _uiState.update { it.copy(successMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.msg_account_update_failed, e.message.orEmpty())) }
            }
        }
    }

    private companion object {
        const val KEY_WALLET_SEEDED = "default_wallet_seeded"
    }
}
