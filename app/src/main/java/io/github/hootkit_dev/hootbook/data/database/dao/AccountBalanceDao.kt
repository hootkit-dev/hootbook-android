package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.*
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountCurrencyEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

private const val SOURCE_TRANSACTION_CALCULATED = "TRANSACTION_CALCULATED"
private const val SOURCE_TRANSACTION_SMS_BALANCE = "TRANSACTION_SMS_BALANCE"
private const val SOURCE_MANUAL = "MANUAL"
private const val SOURCE_MANUAL_EDIT = "MANUAL_EDIT"
private const val SOURCE_SMS_BALANCE = "SMS_BALANCE"
const val SOURCE_BALANCE_CALIBRATION = "BALANCE_CALIBRATION"
/** Balance immediately before the oldest known transaction of an account; moves with older back-dated entries. */
const val SOURCE_OPENING_BALANCE = "OPENING_BALANCE"

/**
 * Whether balance row `ab` is the receiving side of transfer `t`: the target account (and, for a
 * transfer between two currencies of one account, the target currency). Transfers saved before
 * accounts had ids fall back to "not the sending account".
 */
/** Columns of [AccountBalanceEntity] for an account: its balance row joined with the account's own details. */
private const val ACCOUNT_ROW = "SELECT ab.id, a.icon_res_id, a.icon_name, a.name AS bank_name, a.last4 AS account_last4, " +
    "ab.balance, ab.timestamp, ab.transaction_id, a.credit_limit, a.is_credit_card, ab.source_type, " +
    "ab.created_at, ab.currency, a.is_wallet, a.color, a.is_sample, a.id AS account_id, ab.sync_id, ab.sync_updated_at " +
    "FROM accounts a JOIN account_balances ab ON ab.account_id = a.id AND ab.currency = a.main_currency"

private const val POCKET_BALANCES = "SELECT ac.account_id AS accountId, ac.currency AS currency, " +
    "COALESCE((SELECT l.balance FROM account_balances l WHERE l.account_id = ac.account_id AND l.currency = ac.currency " +
    "ORDER BY l.timestamp DESC, l.id DESC LIMIT 1), '0') AS balance, " +
    "a.is_credit_card AS isCreditCard, COALESCE(ac.credit_limit, CASE WHEN ac.currency = a.main_currency THEN a.credit_limit END) AS creditLimit " +
    "FROM account_currencies ac JOIN accounts a ON a.id = ac.account_id ORDER BY ac.account_id, ac.created_at, ac.currency"

private const val RECEIVING_SIDE = "((t.to_account_id IS NOT NULL AND ab.account_id = t.to_account_id " +
    "AND (t.to_account_id != COALESCE(t.account_id, -1) OR ab.currency = t.to_currency)) " +
    "OR (t.to_account_id IS NULL AND NOT (t.bank_name = ab.bank_name AND t.account_number = ab.account_last4)))"

@Dao
abstract class AccountBalanceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBalanceRow(balance: AccountBalanceEntity): Long

    @Query("SELECT * FROM accounts WHERE name = :name AND last4 = :last4")
    abstract suspend fun accountFor(name: String, last4: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAccountRow(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCurrencyRow(currency: AccountCurrencyEntity)

    @Update
    abstract suspend fun updateAccountRow(account: AccountEntity)

    @Query("SELECT currency FROM account_currencies WHERE account_id = :accountId")
    abstract suspend fun currenciesOf(accountId: Long): List<String>

    /**
     * Writes a balance row into its account's currency, creating the account (looking like the
     * row) or the currency when the row is the first of either. Every writer goes through here,
     * so no balance exists without its account.
     */
    @Transaction
    open suspend fun insertBalance(balance: AccountBalanceEntity): Long {
        val account = accountFor(balance.bankName, balance.accountLast4) ?: AccountEntity(
            name = balance.bankName,
            last4 = balance.accountLast4,
            mainCurrency = balance.currency,
            isCreditCard = balance.isCreditCard,
            isWallet = balance.isWallet,
            creditLimit = balance.creditLimit,
            iconResId = balance.iconResId,
            iconName = balance.iconName,
            color = balance.color,
            isSample = balance.isSample,
            createdAt = balance.createdAt
        ).let { it.copy(id = insertAccountRow(it)) }
        insertCurrencyRow(AccountCurrencyEntity(account.id, balance.currency, createdAt = balance.createdAt))
        return insertBalanceRow(balance.copy(accountId = account.id))
    }

    /** The currency a transaction in [currency] lands in: that one if the account holds it, else its main one. */
    open suspend fun pocketCurrency(bankName: String, accountLast4: String, currency: String): String {
        val account = accountFor(bankName, accountLast4) ?: return currency
        return if (currency in currenciesOf(account.id)) currency else account.mainCurrency
    }
    
    /** An account as one row: the latest balance of its main currency, with the account's own name and look. */
    @Query("""
        $ACCOUNT_ROW
        WHERE a.name = :bankName AND a.last4 = :accountLast4
        ORDER BY ab.timestamp DESC, ab.id DESC
        LIMIT 1
    """)
    abstract suspend fun getLatestBalance(bankName: String, accountLast4: String): AccountBalanceEntity?

    @Query("""
        SELECT DISTINCT account_last4 FROM account_balances
        WHERE bank_name = :bankName
        AND LENGTH(account_last4) >= 4
        AND account_last4 LIKE '%' || :suffix
    """)
    abstract suspend fun getAccountLast4sEndingWith(bankName: String, suffix: String): List<String>

    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4 AND currency = :currency
        AND timestamp <= :timestamp
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
    """)
    abstract suspend fun getLatestBalanceOnOrBefore(
        bankName: String,
        accountLast4: String,
        currency: String,
        timestamp: LocalDateTime
    ): AccountBalanceEntity?

    @Query("""
        SELECT
            ab.id AS id,
            ab.balance AS balance,
            ab.source_type AS sourceType,
            -- The account's kind now, not the row's: an account turned into a card later runs as one
            COALESCE((SELECT a.is_credit_card FROM accounts a WHERE a.id = ab.account_id), ab.is_credit_card) AS isCreditCard,
            ab.transaction_id AS transactionId,
            -- The receiving side of a transfer moves by what arrived, in its own currency
            CASE WHEN t.transaction_type = 'TRANSFER' AND $RECEIVING_SIDE
                THEN COALESCE(t.to_amount, t.amount) ELSE t.amount END AS transactionAmount,
            CASE WHEN t.transaction_type = 'TRANSFER' THEN
                CASE WHEN $RECEIVING_SIDE THEN 'INCOME' ELSE 'EXPENSE' END
                ELSE t.transaction_type END AS transactionType,
            t.balance_after AS transactionBalanceAfter,
            t.is_deleted AS isDeleted
        FROM account_balances ab
        LEFT JOIN transactions t ON t.id = ab.transaction_id
        WHERE ab.bank_name = :bankName AND ab.account_last4 = :accountLast4 AND ab.currency = :currency
        AND ab.timestamp > :timestamp
        ORDER BY ab.timestamp ASC, ab.id ASC
    """)
    abstract suspend fun getBalancesAfterWithTransactions(
        bankName: String,
        accountLast4: String,
        currency: String,
        timestamp: LocalDateTime
    ): List<AccountBalanceTransactionInfo>

    @Query("SELECT is_deleted FROM transactions WHERE id = :id")
    abstract suspend fun getTransactionDeletedState(id: Long): Boolean?

    @Query("UPDATE transactions SET is_deleted = :deleted WHERE id = :id")
    abstract suspend fun setTransactionDeletedState(id: Long, deleted: Boolean)

    @Query("DELETE FROM transactions WHERE id = :id")
    abstract suspend fun hardDeleteLedgerTransaction(id: Long)

    @Query("SELECT * FROM account_balances WHERE transaction_id = :id ORDER BY timestamp, id")
    abstract suspend fun getBalancesForTransaction(id: Long): List<AccountBalanceEntity>

    /** State changes and both sides of a transfer are committed in one Room transaction. */
    @Transaction
    open suspend fun changeTransactionDeletion(ids: List<Long>, deleted: Boolean, hardDelete: Boolean = false) {
        require(!hardDelete || deleted)
        val affected = mutableListOf<AccountBalanceEntity>()
        val toHardDelete = mutableListOf<Long>()
        for (id in ids.distinct()) {
            val wasDeleted = getTransactionDeletedState(id) ?: continue
            if (wasDeleted != deleted) {
                affected += getBalancesForTransaction(id)
                setTransactionDeletedState(id, deleted)
            }
            if (hardDelete) toHardDelete += id
        }
        // Revisit each affected point: one batch may straddle an authoritative anchor.
        // Read the predecessor after earlier recalculations, never from a stale UI entity.
        for (entry in affected.distinctBy { it.id }.sortedWith(compareBy({ it.timestamp }, { it.id }))) {
            if (entry.sourceType == SOURCE_TRANSACTION_SMS_BALANCE ||
                entry.sourceType == SOURCE_SMS_BALANCE || entry.sourceType == SOURCE_BALANCE_CALIBRATION) continue
            val previous = getBalanceHistoryForAccount(entry.bankName, entry.accountLast4)
                .filter { it.currency == entry.currency && it.timestamp < entry.timestamp }
                .maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
                ?: insertOpeningBalanceBefore(entry)
                ?: continue
            recalculateBalancesAfter(entry.bankName, entry.accountLast4, previous.timestamp, previous.balance, entry.currency)
        }
        // The ledger rows are still needed above to know each entry's amount and type.
        toHardDelete.forEach { hardDeleteLedgerTransaction(it) }
    }

    /**
     * Rows written before opening balances were recorded have no predecessor when they are the
     * oldest entry of an account. Their balance is the opening balance with the transaction
     * applied, so reverse the transaction to recover it and persist it as [SOURCE_OPENING_BALANCE].
     */
    private suspend fun insertOpeningBalanceBefore(entry: AccountBalanceEntity): AccountBalanceEntity? {
        val openingTimestamp = entry.timestamp.minusNanos(1_000_000)
        val info = getBalancesAfterWithTransactions(entry.bankName, entry.accountLast4, entry.currency, openingTimestamp)
            .firstOrNull { it.id == entry.id } ?: return null
        val amount = info.transactionAmount ?: return null
        val transactionType = info.transactionType?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            ?: return null
        val opening = entry.copy(
            id = 0,
            balance = reverseTransactionBalance(entry.balance, amount, transactionType, info.isCreditCard),
            timestamp = openingTimestamp,
            transactionId = null,
            
            sourceType = SOURCE_OPENING_BALANCE
        )
        return opening.copy(id = insertBalance(opening))
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
     */
    @Transaction
    open suspend fun insertTransactionBalance(
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
        val latest = getLatestBalance(bankName, accountLast4)
        // The currency of the account the transaction lands in
        val pocket = pocketCurrency(bankName, accountLast4, currency)
        val previous = getLatestBalanceOnOrBefore(bankName, accountLast4, pocket, timestamp)

        // Fix for manually-created accounts: when the account was set up today (MANUAL entry),
        // backdated transactions have no prior entry. Fall back to the earliest MANUAL balance
        // so the calculation is based on the user's initial balance, not zero.
        val previousForBalance = previous ?: run {
            val earliest = getEarliestBalance(bankName, accountLast4, pocket)
            if (earliest?.sourceType == SOURCE_MANUAL || earliest?.sourceType == SOURCE_OPENING_BALANCE) earliest else null
        }

        val accountCurrency = pocket

        if (previous == null && explicitBalance == null) {
            insertBalance(AccountBalanceEntity(
                bankName = bankName, accountLast4 = accountLast4,
                balance = previousForBalance?.balance ?: BigDecimal.ZERO,
                timestamp = timestamp.minusNanos(1_000_000),
                sourceType = SOURCE_OPENING_BALANCE, currency = accountCurrency,
                isCreditCard = latest?.isCreditCard ?: (isCreditCard || previousForBalance?.isCreditCard == true),
                creditLimit = previousForBalance?.creditLimit ?: latest?.creditLimit,
                iconResId = latest?.iconResId ?: 0, iconName = latest?.iconName ?: "",
                isWallet = latest?.isWallet ?: false, color = latest?.color ?: "#33B5E5"
            ))
        }

        // Which way a card moves comes from the account (what is owed), not from older rows
        val accountIsCreditCard = latest?.isCreditCard ?: (isCreditCard || previousForBalance?.isCreditCard == true)
        val newBalance = explicitBalance ?: calculateTransactionBalance(
            currentBalance = previousForBalance?.balance ?: BigDecimal.ZERO,
            amount = amount,
            transactionType = transactionType,
            isCreditCard = accountIsCreditCard
        )

        val balanceId = insertBalance(
            AccountBalanceEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                balance = newBalance,
                timestamp = timestamp,
                transactionId = transactionId,
                creditLimit = if (accountIsCreditCard) {
                    creditLimit?.add(newBalance) ?: previousForBalance?.creditLimit ?: latest?.creditLimit
                } else {
                    previousForBalance?.creditLimit ?: latest?.creditLimit
                },
                isCreditCard = accountIsCreditCard,
                
                sourceType = if (explicitBalance != null) {
                    SOURCE_TRANSACTION_SMS_BALANCE
                } else {
                    SOURCE_TRANSACTION_CALCULATED
                },
                currency = accountCurrency,
                iconResId = previousForBalance?.iconResId ?: latest?.iconResId ?: 0,
                iconName = previousForBalance?.iconName ?: latest?.iconName ?: "",
                isWallet = previousForBalance?.isWallet ?: latest?.isWallet ?: false,
                color = previousForBalance?.color ?: latest?.color ?: "#33B5E5"
            )
        )

        recalculateBalancesAfter(bankName, accountLast4, timestamp, newBalance, pocket)
        return balanceId
    }

    /** Recalculates one currency of an account after [timestamp]; null [currency] means its main one. */
    open suspend fun recalculateBalancesAfter(
        bankName: String,
        accountLast4: String,
        timestamp: LocalDateTime,
        startingBalance: BigDecimal,
        currency: String? = null
    ) {
        val pocket = currency ?: accountFor(bankName, accountLast4)?.mainCurrency ?: return
        recalculateBalancesAfterInternal(bankName, accountLast4, pocket, timestamp, startingBalance)
    }

    private suspend fun recalculateBalancesAfterInternal(
        bankName: String,
        accountLast4: String,
        currency: String,
        timestamp: LocalDateTime,
        startingBalance: BigDecimal
    ) {
        var runningBalance = startingBalance
        for (row in getBalancesAfterWithTransactions(bankName, accountLast4, currency, timestamp)) {
            val sourceType = row.sourceType

            // MANUAL entries (user-created account setup) are NOT hard stops.
            // When a backdated transaction is added before a MANUAL entry, the cascade
            // must continue past it so that subsequent TRANSACTION_CALCULATED entries
            // (e.g., today's expenses) are correctly updated to reflect the backdated change.
            // The MANUAL entry itself is updated to carry the accumulated delta.
            // An OPENING_BALANCE row is the balance before what used to be the oldest transaction;
            // an even older back-dated entry shifts it the same way.
            if (sourceType == SOURCE_MANUAL || sourceType == SOURCE_OPENING_BALANCE) {
                if (runningBalance != row.balance) {
                    updateAndInvalidate(row.id, runningBalance)
                }
                continue
            }

            // Bank-reported explicit balances (SMS) are authoritative anchors — stop here.
            val isExplicitBalance = row.transactionBalanceAfter != null ||
                    sourceType == SOURCE_TRANSACTION_SMS_BALANCE ||
                    sourceType == SOURCE_SMS_BALANCE ||
                    sourceType == SOURCE_BALANCE_CALIBRATION

            if (isExplicitBalance) {
                break
            }

            if (row.transactionId == null) {
                val isCalculatedSnapshot = sourceType == SOURCE_MANUAL_EDIT ||
                        sourceType == "DELETE_REVERSAL" ||
                        sourceType == "UNDO_REVERSAL"
                if (!isCalculatedSnapshot) {
                    break
                }
                if (runningBalance != row.balance) {
                    updateAndInvalidate(row.id, runningBalance)
                }
                continue
            }

            val amount = row.transactionAmount
            val transactionType = row.transactionType?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            val isDeleted = row.isDeleted == true
            
            val recalculated = if (amount != null && transactionType != null && !isDeleted) {
                calculateTransactionBalance(
                    currentBalance = runningBalance,
                    amount = amount,
                    transactionType = transactionType,
                    isCreditCard = row.isCreditCard
                )
            } else {
                runningBalance
            }

            if (recalculated != row.balance) {
                updateAndInvalidate(row.id, recalculated)
            }
            runningBalance = recalculated
        }
    }

    /**
     * Updates a balance entry by fetching the full entity first, then using @Update
     * so that Room properly invalidates Flow observers and the UI refreshes.
     */
    private suspend fun updateAndInvalidate(id: Long, newBalance: BigDecimal) {
        val entity = getBalanceById(id) ?: return
        updateBalance(entity.copy(balance = newBalance))
    }

    @Query("SELECT * FROM account_balances WHERE id = :id LIMIT 1")
    abstract suspend fun getBalanceById(id: Long): AccountBalanceEntity?
    
    @Query("""
        $ACCOUNT_ROW
        WHERE a.name = :bankName AND a.last4 = :accountLast4
        ORDER BY ab.timestamp DESC, ab.id DESC
        LIMIT 1
    """)
    abstract fun getLatestBalanceFlow(bankName: String, accountLast4: String): Flow<AccountBalanceEntity?>
    
    /** Every account as one row (see [getLatestBalance]). */
    @Query("""
        $ACCOUNT_ROW
        WHERE ab.id = (SELECT l.id FROM account_balances l
            WHERE l.account_id = a.id AND l.currency = a.main_currency
            ORDER BY l.timestamp DESC, l.id DESC LIMIT 1)
        ORDER BY ab.balance DESC
    """)
    abstract fun getAllLatestBalances(): Flow<List<AccountBalanceEntity>>
    
    @Query("SELECT * FROM account_balances ORDER BY timestamp DESC")
    abstract fun getAllBalances(): Flow<List<AccountBalanceEntity>>

    /** The latest balance of every currency of every account. */
    @Query("DELETE FROM account_balances WHERE account_id = :accountId AND currency = :currency")
    abstract suspend fun deleteCurrencyRows(accountId: Long, currency: String)

    @Query("DELETE FROM account_currencies WHERE account_id = :accountId AND currency = :currency")
    abstract suspend fun deleteCurrencyRow(accountId: Long, currency: String)

    /** Takes back a currency just added to an account (undo); the app never removes one otherwise. */
    @Transaction
    open suspend fun removeCurrency(accountId: Long, currency: String) {
        deleteCurrencyRows(accountId, currency)
        deleteCurrencyRow(accountId, currency)
    }

    @Query("SELECT * FROM accounts ORDER BY name, last4")
    abstract fun observeAccountRows(): Flow<List<AccountEntity>>

    @Query(POCKET_BALANCES)
    abstract fun observePocketBalances(): Flow<List<PocketBalance>>

    @Query(POCKET_BALANCES)
    abstract suspend fun getPocketBalances(): List<PocketBalance>
    
    @Query("DELETE FROM account_balances")
    abstract suspend fun deleteAllBalanceRows()

    @Query("DELETE FROM accounts")
    abstract suspend fun deleteAllAccountRows()

    /**
     * Gives transactions without account ids (restored from a backup older than accounts) their
     * accounts, the way the 66→67 migration did: a transfer's target from its own balance rows.
     */
    @Transaction
    open suspend fun linkTransactionsToAccounts() {
        linkTransactionAccounts()
        linkTransferTargets()
        linkTransferCurrencies()
    }

    @Query("""
        UPDATE transactions SET account_id = (SELECT a.id FROM accounts a
            WHERE a.name = transactions.bank_name AND a.last4 = transactions.account_number)
        WHERE account_id IS NULL
    """)
    abstract suspend fun linkTransactionAccounts()

    @Query("""
        UPDATE transactions SET to_account_id = (
            SELECT ab.account_id FROM account_balances ab
            WHERE ab.transaction_id = transactions.id
            AND NOT (ab.bank_name = transactions.bank_name AND ab.account_last4 = transactions.account_number)
            ORDER BY ab.id LIMIT 1)
        WHERE transaction_type = 'TRANSFER' AND to_account_id IS NULL
    """)
    abstract suspend fun linkTransferTargets()

    @Query("""
        UPDATE transactions SET to_currency = (SELECT a.main_currency FROM accounts a WHERE a.id = transactions.to_account_id)
        WHERE to_account_id IS NOT NULL AND to_currency IS NULL
    """)
    abstract suspend fun linkTransferCurrencies()

    @Transaction
    open suspend fun deleteAllBalances() {
        deleteAllBalanceRows()
        deleteAllAccountRows()
    }

    
    @Query("""
        SELECT DISTINCT 
            ab1.id,
            ab1.bank_name,
            ab1.account_last4,
            ab1.balance,
            ab1.timestamp,
            ab1.transaction_id,
            ab1.created_at,
            ab1.credit_limit,
            ab1.is_credit_card,
            ab1.source_type,
            ab1.currency,
            ab1.icon_res_id,
            ab1.icon_name,
            ab1.is_wallet,
            ab1.color,
            ab1.is_sample,
            ab1.sync_id,
            ab1.sync_updated_at
        FROM account_balances ab1
        INNER JOIN (
            SELECT bank_name, account_last4, MAX(timestamp) as max_timestamp
            FROM account_balances
            WHERE strftime('%Y-%m', timestamp/1000, 'unixepoch') = strftime('%Y-%m', 'now')
            GROUP BY bank_name, account_last4
        ) ab2 
        ON ab1.bank_name = ab2.bank_name 
        AND ab1.account_last4 = ab2.account_last4 
        AND ab1.timestamp = ab2.max_timestamp
        ORDER BY ab1.balance DESC
    """)
    abstract fun getCurrentMonthLatestBalances(): Flow<List<AccountBalanceEntity>>
    
    @Query("""
        SELECT SUM(balance) as total FROM (
            SELECT DISTINCT 
                ab1.balance
            FROM account_balances ab1
            INNER JOIN (
                SELECT bank_name, account_last4, MAX(timestamp) as max_timestamp
                FROM account_balances
                GROUP BY bank_name, account_last4
            ) ab2 
            ON ab1.bank_name = ab2.bank_name 
            AND ab1.account_last4 = ab2.account_last4 
            AND ab1.timestamp = ab2.max_timestamp
        )
    """)
    abstract fun getTotalBalance(): Flow<BigDecimal?>
    
    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        AND timestamp >= :startDate AND timestamp <= :endDate
        ORDER BY timestamp DESC
    """)
    abstract fun getBalanceHistory(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<AccountBalanceEntity>>
    
    @Query("""
        SELECT COUNT(DISTINCT bank_name || account_last4) FROM account_balances
    """)
    abstract fun getAccountCount(): Flow<Int>
    
    @Query("DELETE FROM account_balances WHERE timestamp < :beforeDate")
    abstract suspend fun deleteOldBalances(beforeDate: LocalDateTime): Int
    
    @Update
    abstract suspend fun updateBalance(balance: AccountBalanceEntity)
    
    @Delete
    abstract suspend fun deleteBalance(balance: AccountBalanceEntity)
    
    @Query("""SELECT * FROM account_balances 
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        ORDER BY timestamp DESC""")
    abstract suspend fun getBalanceHistoryForAccount(bankName: String, accountLast4: String): List<AccountBalanceEntity>
    
    @Query("DELETE FROM account_balances WHERE id = :id")
    abstract suspend fun deleteBalanceById(id: Long)
    
    @Query("UPDATE account_balances SET balance = :newBalance WHERE id = :id")
    abstract suspend fun updateBalanceById(id: Long, newBalance: BigDecimal)
    
    @Query("""SELECT COUNT(*) FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4""")
    abstract suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int
 
    @Query("DELETE FROM account_balances WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    abstract suspend fun deleteBalanceRowsOf(bankName: String, accountLast4: String): Int

    @Query("DELETE FROM accounts WHERE name = :bankName AND last4 = :accountLast4")
    abstract suspend fun deleteAccountRow(bankName: String, accountLast4: String)

    /** Removes an account with its currencies and their history. */
    @Transaction
    open suspend fun deleteAccount(bankName: String, accountLast4: String): Int {
        deleteAccountRow(bankName, accountLast4)
        return deleteBalanceRowsOf(bankName, accountLast4)
    }

    @Query("UPDATE account_balances SET bank_name = :newBankName WHERE bank_name = :oldBankName AND account_last4 = :accountLast4")
    abstract suspend fun renameBalanceRows(oldBankName: String, accountLast4: String, newBankName: String): Int

    @Query("UPDATE accounts SET name = :newBankName WHERE name = :oldBankName AND last4 = :accountLast4")
    abstract suspend fun renameAccountRow(oldBankName: String, accountLast4: String, newBankName: String)

    @Transaction
    open suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int {
        renameAccountRow(oldBankName, accountLast4, newBankName)
        return renameBalanceRows(oldBankName, accountLast4, newBankName)
    }
 
    @Query("SELECT * FROM account_balances WHERE transaction_id = :transactionId LIMIT 1")
    abstract suspend fun getBalanceByTransactionId(transactionId: Long): AccountBalanceEntity?

    /** Finds the latest account record for a given last-4 digits, regardless of bank name. */
    @Query("""
        SELECT * FROM account_balances
        WHERE account_last4 = :accountLast4
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    abstract suspend fun getAccountByLast4(accountLast4: String): AccountBalanceEntity?

    /** Returns the oldest balance entry for an account, used as a fallback when no prior entry
     *  exists for a backdated transaction (e.g. for manually-created accounts). */
    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4 AND currency = :currency
        ORDER BY timestamp ASC, id ASC
        LIMIT 1
    """)
    abstract suspend fun getEarliestBalance(bankName: String, accountLast4: String, currency: String): AccountBalanceEntity?
}


data class AccountBalanceTransactionInfo(
    val id: Long,
    val balance: BigDecimal,
    val sourceType: String?,
    val isCreditCard: Boolean,
    val transactionId: Long?,
    val transactionAmount: BigDecimal?,
    val transactionType: String?,
    val transactionBalanceAfter: BigDecimal?,
    val isDeleted: Boolean?
)

/** One currency of an account and its latest balance. */
data class PocketBalance(
    val accountId: Long,
    val currency: String,
    val balance: BigDecimal,
    val isCreditCard: Boolean,
    val creditLimit: BigDecimal?
)

/** Inverse of [calculateTransactionBalance]: the balance before [amount] was applied. */
private fun reverseTransactionBalance(
    balanceAfter: BigDecimal,
    amount: BigDecimal,
    transactionType: TransactionType,
    isCreditCard: Boolean
): BigDecimal {
    return when {
        // A card's balance is what is owed: a payment or refund lowered it, anything else raised it
        isCreditCard && (transactionType == TransactionType.INCOME || transactionType == TransactionType.BORROWED) -> balanceAfter + amount
        isCreditCard -> balanceAfter - amount
        transactionType == TransactionType.INCOME || transactionType == TransactionType.CREDIT || transactionType == TransactionType.BORROWED -> balanceAfter - amount
        transactionType == TransactionType.EXPENSE || transactionType == TransactionType.INVESTMENT || transactionType == TransactionType.LENT -> balanceAfter + amount
        else -> balanceAfter
    }
}

private fun calculateTransactionBalance(
    currentBalance: BigDecimal,
    amount: BigDecimal,
    transactionType: TransactionType,
    isCreditCard: Boolean
): BigDecimal {
    return when {
        // Paying more than is owed leaves a credit on the card: a negative balance, not zero
        // Borrowed money arriving on a card pays it down like a payment
        isCreditCard && (transactionType == TransactionType.INCOME || transactionType == TransactionType.BORROWED) -> currentBalance - amount
        isCreditCard -> currentBalance + amount
        transactionType == TransactionType.INCOME || transactionType == TransactionType.CREDIT || transactionType == TransactionType.BORROWED -> currentBalance + amount
        transactionType == TransactionType.EXPENSE || transactionType == TransactionType.INVESTMENT || transactionType == TransactionType.LENT ->
            currentBalance - amount
        else -> currentBalance
    }
}
