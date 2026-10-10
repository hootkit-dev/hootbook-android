package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.*
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date_time DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0")
    fun getTransactionCount(): Flow<Int>

    @Query(
            """
        SELECT COUNT(*) FROM transactions
        WHERE is_deleted = 0 AND transaction_type != 'BALANCE_UPDATE'
    """
    )
    suspend fun countVisibleTransactions(): Int

    @Query(
            """
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND transaction_type != 'BALANCE_UPDATE'
        ORDER BY date_time DESC
        LIMIT :limit
    """
    )
    fun getRecentVisibleTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND transaction_type != 'BALANCE_UPDATE'
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """
    )
    fun getVisibleTransactionsBetweenDates(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Query(
            """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsBetweenDates(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * Optimized query that filters transactions at the database level. Combines date range,
     * currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions ordered by date descending
     */
    @Query(
            """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
        AND currency = :currency
        AND (:transactionType IS NULL OR transaction_type = :transactionType)
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsFiltered(
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            currency: String,
            transactionType: TransactionType?
    ): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND transaction_type = :type 
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND category = :category 
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND (merchant_name LIKE '%' || :searchQuery || '%' 
        OR description LIKE '%' || :searchQuery || '%') 
        ORDER BY date_time DESC
    """
    )
    fun searchTransactions(searchQuery: String): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND (merchant_name LIKE '%' || :searchQuery || '%' 
        OR description LIKE '%' || :searchQuery || '%') 
        ORDER BY date_time DESC
        LIMIT 20
    """
    )
    suspend fun searchTransactionsList(searchQuery: String): List<TransactionEntity>

    @Query("SELECT DISTINCT category FROM transactions WHERE is_deleted = 0 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query(
            """
        SELECT category FROM transactions
        WHERE is_deleted = 0
        GROUP BY category
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """
    )
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String>

    @Query(
            "SELECT DISTINCT merchant_name FROM transactions WHERE is_deleted = 0 ORDER BY merchant_name ASC"
    )
    fun getAllMerchants(): Flow<List<String>>

    @Query(
            """
        SELECT SUM(amount) FROM transactions 
        WHERE is_deleted = 0 
        AND transaction_type = :type 
        AND date_time BETWEEN :startDate AND :endDate
    """
    )
    suspend fun getTotalAmountByTypeAndPeriod(
            type: TransactionType,
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Double?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("DELETE FROM transactions") suspend fun deleteAllTransactions()
    

    @Query("UPDATE transactions SET category = :newCategory WHERE merchant_name = :merchantName")
    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String)

    /** Updates category and subcategory for all non-deleted transactions whose merchant name CONTAINS the keyword */
    @Query("UPDATE transactions SET category = :newCategory, subcategory = :newSubcategory WHERE is_deleted = 0 AND merchant_name LIKE '%' || :merchantName || '%'")
    suspend fun updateCategoryAndSubcategoryForMerchantContains(merchantName: String, newCategory: String, newSubcategory: String?)

    @Query(
            "SELECT COUNT(*) FROM transactions WHERE merchant_name = :merchantName AND id != :excludeId"
    )
    suspend fun getTransactionCountForMerchant(merchantName: String, excludeId: Long): Int

    /** Returns all non-deleted transactions whose merchant name CONTAINS the keyword, excluding one id */
    @Query(
        """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND merchant_name LIKE '%' || :merchantName || '%'
        AND id != :excludeId
        ORDER BY date_time DESC
        """
    )
    suspend fun getTransactionsByMerchantContains(
        merchantName: String,
        excludeId: Long
    ): List<TransactionEntity>

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 ORDER BY currency")
    fun getAllCurrencies(): Flow<List<String>>

    @Query(
            "SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 AND date_time BETWEEN :startDate AND :endDate ORDER BY currency"
    )
    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>>

    // Soft delete methods
    @Query("UPDATE transactions SET is_deleted = 1 WHERE id = :transactionId")
    suspend fun softDeleteTransaction(transactionId: Long)

    @Query("UPDATE transactions SET is_deleted = 1 WHERE transaction_hash = :transactionHash")
    suspend fun softDeleteByHash(transactionHash: String)

    @Query("UPDATE transactions SET is_deleted = 1 WHERE id IN (:transactionIds)")
    suspend fun softDeleteTransactions(transactionIds: List<Long>)

    @Query("DELETE FROM transactions WHERE id IN (:transactionIds)")
    suspend fun deleteTransactionsByIds(transactionIds: List<Long>)

    // Method to check if transaction exists by hash (including deleted)
    @Query("SELECT * FROM transactions WHERE transaction_hash = :transactionHash LIMIT 1")
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND date_time BETWEEN :startDate AND :endDate 
        ORDER BY date_time DESC
    """
    )
    suspend fun getTransactionsBetweenDatesList(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): List<TransactionEntity>

    /**
     * An account's transactions: by its id, the side it is on; older rows without ids by exact
     * name and last 4, or by the balance rows they wrote. Every wallet's last 4 is "wallet", so
     * matching a transfer's target by last 4 alone listed it under every wallet.
     */
    @Query(
            """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND (
            account_id = (SELECT id FROM accounts WHERE name = :bankName AND last4 = :accountLast4)
            OR to_account_id = (SELECT id FROM accounts WHERE name = :bankName AND last4 = :accountLast4)
            OR (account_id IS NULL AND bank_name = :bankName AND account_number = :accountLast4)
            OR (to_account_id IS NULL AND EXISTS (
                SELECT 1 FROM account_balances ab WHERE ab.transaction_id = transactions.id
                AND ab.bank_name = :bankName AND ab.account_last4 = :accountLast4))
        )
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsByAccount(
            bankName: String,
            accountLast4: String
    ): Flow<List<TransactionEntity>>

    @Query(
            """
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND date_time BETWEEN :startDate AND :endDate
        AND (
            (bank_name = :bankName AND account_number = :accountLast4)
            OR
            (transaction_type = 'TRANSFER' AND to_account = :accountLast4)
        )
        ORDER BY date_time DESC
    """
    )
    fun getTransactionsByAccountAndDateRange(
            bankName: String,
            accountLast4: String,
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * Transfers into account [oldAccountId] go to [newAccountId] instead (accounts merged), in
     * the same currency when that account holds it, else in its main one.
     */
    @Query("""
        UPDATE transactions SET to_account_id = :newAccountId, to_account = :newLast4,
            to_currency = CASE
                WHEN to_currency IN (SELECT currency FROM account_currencies WHERE account_id = :newAccountId) THEN to_currency
                ELSE (SELECT main_currency FROM accounts WHERE id = :newAccountId) END
        WHERE to_account_id = :oldAccountId
    """)
    suspend fun retargetTransfers(oldAccountId: Long, newAccountId: Long, newLast4: String)

    @Query(
            "UPDATE transactions SET bank_name = :newBankName, account_number = :newAccountNumber, " +
                "account_id = COALESCE((SELECT id FROM accounts WHERE name = :newBankName AND last4 = :newAccountNumber), account_id) " +
                "WHERE bank_name = :oldBankName AND account_number = :oldAccountNumber"
    )
    suspend fun updateAccountForTransactions(
            oldBankName: String,
            oldAccountNumber: String,
            newBankName: String,
            newAccountNumber: String
    )

    @Query("UPDATE transactions SET category = :newCategory, subcategory = :newSubcategory WHERE category = :oldCategory")
    suspend fun updateTransactionsCategory(oldCategory: String, newCategory: String, newSubcategory: String?)

    @Query("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0 AND category = :category")
    suspend fun getTransactionCountByCategory(category: String): Int

    @Query(
        """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND reference = :reference
        AND amount = :amount
        AND (:accountLast4 IS NULL OR account_number = :accountLast4)
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
        """
    )
    suspend fun getTransactionsByReferenceAndAmount(
        reference: String,
        amount: java.math.BigDecimal,
        accountLast4: String?,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
    """
    )
    suspend fun findPotentialDuplicates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE updated_at > :updatedAfter
        AND updated_at <= :updatedBefore
        AND currency = :currency
        AND is_sample = 0
        ORDER BY updated_at ASC, id ASC
        """
    )
    suspend fun getTransactionsUpdatedBetween(
        updatedAfter: LocalDateTime,
        updatedBefore: LocalDateTime,
        currency: String
    ): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND is_sample = 0
        AND currency = :currency
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
        """
    )
    suspend fun getTransactionsBetweenDatesByCurrency(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        currency: String
    ): List<TransactionEntity>
}
