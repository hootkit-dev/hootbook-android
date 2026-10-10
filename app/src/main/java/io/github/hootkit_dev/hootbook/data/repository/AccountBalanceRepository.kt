package io.github.hootkit_dev.hootbook.data.repository

import android.content.Context
import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceDao
import io.github.hootkit_dev.hootbook.data.database.dao.SOURCE_OPENING_BALANCE
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountCurrencyEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountBalanceRepository @Inject constructor(
    private val accountBalanceDao: AccountBalanceDao,
    @ApplicationContext private val context: Context
) {
    suspend fun changeTransactionDeletion(ids: List<Long>, deleted: Boolean, hardDelete: Boolean = false) {
        accountBalanceDao.changeTransactionDeletion(ids, deleted, hardDelete)
    }

    suspend fun projectedExpenseBalance(
        bankName: String,
        accountLast4: String,
        timestamp: LocalDateTime,
        amount: BigDecimal,
        currency: String? = null
    ): BigDecimal? {
        val pocket = pocketCurrency(bankName, accountLast4, currency)
            ?: accountBalanceDao.getLatestBalance(bankName, accountLast4)?.currency ?: return null
        val previous = accountBalanceDao.getLatestBalanceOnOrBefore(bankName, accountLast4, pocket, timestamp)
            ?: accountBalanceDao.getEarliestBalance(bankName, accountLast4, pocket)?.takeIf { it.sourceType == "MANUAL" || it.sourceType == SOURCE_OPENING_BALANCE }
        val account = previous ?: accountBalanceDao.getLatestBalance(bankName, accountLast4)
        if (account?.isCreditCard == true) return null
        return (previous?.balance ?: BigDecimal.ZERO) - amount
    }

    fun observePocketBalances(): Flow<List<io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance>> =
        accountBalanceDao.observePocketBalances()

    /** The latest balance of every currency of every account. */
    suspend fun pocketBalances(): List<io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance> =
        accountBalanceDao.getPocketBalances()

    /** Every account's own details (name, kind, limit, card dates…). */
    fun observeAccounts() = accountBalanceDao.observeAccountRows()

    /** Takes back a currency just added to an account; see [AccountBalanceDao.removeCurrency]. */
    suspend fun removeCurrency(accountId: Long, currency: String) = accountBalanceDao.removeCurrency(accountId, currency)

    suspend fun account(bankName: String, accountLast4: String): AccountEntity? =
        accountBalanceDao.accountFor(bankName, accountLast4)

    /** Changes an account's own details (kind, limit, look, main currency); returns them as they were. */
    suspend fun updateAccount(bankName: String, accountLast4: String, change: (AccountEntity) -> AccountEntity): AccountEntity? {
        val before = accountBalanceDao.accountFor(bankName, accountLast4) ?: return null
        val after = change(before)
        if (after.mainCurrency != before.mainCurrency) {
            accountBalanceDao.insertCurrencyRow(AccountCurrencyEntity(before.id, after.mainCurrency))
        }
        accountBalanceDao.updateAccountRow(after.copy(id = before.id))
        return before
    }

    /** Puts back details saved by [updateAccount]. */
    suspend fun restoreAccount(account: AccountEntity) = accountBalanceDao.updateAccountRow(account)

    /** The currency of the account a transaction in [currency] lands in; null [currency] means its main one. */
    suspend fun pocketCurrency(bankName: String, accountLast4: String, currency: String?): String? =
        if (currency != null) accountBalanceDao.pocketCurrency(bankName, accountLast4, currency)
        else accountBalanceDao.accountFor(bankName, accountLast4)?.mainCurrency

    suspend fun insertBalance(balance: AccountBalanceEntity): Long {
        val balanceWithIconName = if (balance.iconName.isEmpty() && balance.iconResId != 0) {
            balance.copy(iconName = IconResolutionUtils.resIdToName(context, balance.iconResId))
        } else {
            balance
        }
        return accountBalanceDao.insertBalance(balanceWithIconName)
    }
    
    /**
     * Sets one currency of an account to [balance] (a card's amount owed) with a new
     * BALANCE_CALIBRATION row, keeping the account's look and limit.
     */
    suspend fun calibrate(bankName: String, accountLast4: String, balance: BigDecimal, currency: String): Long? {
        val latest = getLatestBalance(bankName, accountLast4) ?: return null
        val now = LocalDateTime.now()
        return insertBalance(
            latest.copy(
                id = 0, balance = balance, currency = currency, timestamp = now, transactionId = null,
                sourceType = "BALANCE_CALIBRATION", createdAt = now
            )
        )
    }

    suspend fun getLatestBalance(bankName: String, accountLast4: String): AccountBalanceEntity? {
        return accountBalanceDao.getLatestBalance(bankName, accountLast4)
    }

    suspend fun getLatestBalanceOnOrBefore(
        bankName: String,
        accountLast4: String,
        timestamp: LocalDateTime,
        currency: String? = null
    ): AccountBalanceEntity? {
        val pocket = pocketCurrency(bankName, accountLast4, currency) ?: return null
        return accountBalanceDao.getLatestBalanceOnOrBefore(bankName, accountLast4, pocket, timestamp)
    }

    suspend fun resolveAccountLast4(bankName: String, accountLast4: String): String {
        if (accountLast4.isBlank()) {
            return accountLast4
        }

        if (!accountLast4.all { it.isDigit() }) {
            return accountLast4
        }

        if (accountLast4.length >= 4) {
            return accountLast4.takeLast(4)
        }

        val matches = accountBalanceDao.getAccountLast4sEndingWith(bankName, accountLast4)
        return if (matches.size == 1) matches.first() else accountLast4
    }

    fun getLatestBalanceFlow(bankName: String, accountLast4: String): Flow<AccountBalanceEntity?> {
        return accountBalanceDao.getLatestBalanceFlow(bankName, accountLast4)
    }
    
    fun getAllLatestBalances(): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getAllLatestBalances()
    }
    
    fun getTotalBalance(): Flow<BigDecimal?> {
        return accountBalanceDao.getTotalBalance()
    }
    
    fun getBalanceHistory(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getBalanceHistory(bankName, accountLast4, startDate, endDate)
    }
    
    fun getAccountCount(): Flow<Int> {
        return accountBalanceDao.getAccountCount()
    }
    
    suspend fun deleteOldBalances(beforeDate: LocalDateTime): Int {
        return accountBalanceDao.deleteOldBalances(beforeDate)
    }
    
    suspend fun updateBalance(balance: AccountBalanceEntity) {
        accountBalanceDao.updateBalance(balance)
    }
    
    fun getAllBalances(): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getAllBalances()
    }

    suspend fun deleteBalance(balance: AccountBalanceEntity) {
        accountBalanceDao.deleteBalance(balance)
    }

    /**
     * Inserts a balance entry linked to a transaction, and sequentially recalculates succeeding balances.
     *
     * @param bankName The name of the bank.
     * @param accountLast4 The last 4 digits of the account number.
     * @param amount The transaction amount.
     * @param transactionType The transaction type.
     * @param explicitBalance The bank-reported explicit balance (if any).
     * @param timestamp The transaction timestamp.
     * @param transactionId The associated transaction ID.
     * @param creditLimit Optionally, a custom credit limit parsed from SMS.
     * @param isCreditCard Whether this account is a credit card.
     * @param currency The transaction currency.
     * @return The ID of the inserted balance record.
     */
    suspend fun insertTransactionBalance(
        bankName: String,
        accountLast4: String,
        amount: BigDecimal,
        transactionType: TransactionType,
        explicitBalance: BigDecimal?,
        timestamp: LocalDateTime,
        transactionId: Long?,
        creditLimit: BigDecimal?,
        isCreditCard: Boolean,
        currency: String
    ): Long {
        return accountBalanceDao.insertTransactionBalance(
            bankName = bankName,
            accountLast4 = accountLast4,
            amount = amount,
            transactionType = transactionType,
            explicitBalance = explicitBalance,
            timestamp = timestamp,
            transactionId = transactionId,
            creditLimit = creditLimit,
            isCreditCard = isCreditCard,
            
            currency = currency
        )
    }

    suspend fun insertBalanceUpdate(
        bankName: String,
        accountLast4: String,
        balance: BigDecimal,
        timestamp: LocalDateTime,
        sourceType: String? = null,
        currency: String
    ): Long {
        val latest = getLatestBalance(bankName, accountLast4)
        val balanceEntity = AccountBalanceEntity(
            bankName = bankName,
            accountLast4 = accountLast4,
            balance = balance,
            timestamp = timestamp,
            transactionId = null,
             // Limit to 500 chars
            sourceType = sourceType,
            currency = currency,
            iconResId = latest?.iconResId ?: 0,
            iconName = latest?.iconName ?: "",
            isWallet = latest?.isWallet ?: false,
            isCreditCard = latest?.isCreditCard ?: false,
            creditLimit = latest?.creditLimit,
            color = latest?.color ?: "#33B5E5"
        )
        return insertBalance(balanceEntity)
    }
    
    suspend fun getBalanceHistoryForAccount(bankName: String, accountLast4: String): List<AccountBalanceEntity> {
        return accountBalanceDao.getBalanceHistoryForAccount(bankName, accountLast4)
    }
    
    suspend fun deleteBalanceById(id: Long) {
        accountBalanceDao.deleteBalanceById(id)
    }
    
    suspend fun updateBalanceById(id: Long, newBalance: BigDecimal) {
        accountBalanceDao.updateBalanceById(id, newBalance)
    }
    
    suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int {
        return accountBalanceDao.getBalanceCountForAccount(bankName, accountLast4)
    }

    suspend fun deleteAccount(bankName: String, accountLast4: String): Int {
        return accountBalanceDao.deleteAccount(bankName, accountLast4)
    }

    suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int {
        return accountBalanceDao.updateAccountBankName(oldBankName, accountLast4, newBankName)
    }

    suspend fun deleteAllBalances() {
        accountBalanceDao.deleteAllBalances()
    }

    suspend fun getAccountByLast4(accountLast4: String): AccountBalanceEntity? {
        return accountBalanceDao.getAccountByLast4(accountLast4)
    }

    suspend fun getBalanceByTransactionId(transactionId: Long): AccountBalanceEntity? {
        return accountBalanceDao.getBalanceByTransactionId(transactionId)
    }

    suspend fun getBalanceById(id: Long): AccountBalanceEntity? {
        return accountBalanceDao.getBalanceById(id)
    }

    suspend fun recalculateBalancesAfter(
        bankName: String,
        accountLast4: String,
        timestamp: LocalDateTime,
        startingBalance: BigDecimal,
        currency: String? = null
    ) {
        accountBalanceDao.recalculateBalancesAfter(bankName, accountLast4, timestamp, startingBalance, currency)
    }
}
