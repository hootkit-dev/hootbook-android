package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.dao.TransactionDao
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.YearMonth
import java.time.LocalTime
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
        .map { list -> list.filter { it.transactionType != TransactionType.BALANCE_UPDATE } }

    fun getTransactionCount(): Flow<Int> = transactionDao.getTransactionCount()

    /** Same rows as [getAllTransactions], counted in SQL. */
    suspend fun countVisibleTransactions(): Int = transactionDao.countVisibleTransactions()

    /** Same rows as [getAllTransactions] limited to a date range, filtered in SQL. */
    fun getVisibleTransactionsBetween(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> =
            transactionDao.getVisibleTransactionsBetweenDates(startDate, endDate)

    suspend fun getTransactionById(id: Long): TransactionEntity? =
            transactionDao.getTransactionById(id)

    suspend fun getTransactionsUpdatedBetween(
            updatedAfter: LocalDateTime,
            updatedBefore: LocalDateTime,
            currency: String
    ): List<TransactionEntity> =
            transactionDao.getTransactionsUpdatedBetween(updatedAfter, updatedBefore, currency)

    suspend fun getTransactionsBetweenDatesByCurrency(
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            currency: String
    ): List<TransactionEntity> =
            transactionDao.getTransactionsBetweenDatesByCurrency(startDate, endDate, currency)

    fun getTransactionsBetweenDates(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> =
            transactionDao.getTransactionsBetweenDates(startDate, endDate)

    fun getTransactionsBetweenDates(
            startDate: LocalDate,
            endDate: LocalDate
    ): Flow<List<TransactionEntity>> =
            transactionDao.getTransactionsBetweenDates(
                    startDate.atStartOfDay(),
                    endDate.atTime(23, 59, 59)
            )

    /**
     * Gets transactions filtered at the database level for better performance. Combines date range,
     * currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions
     */
    fun getTransactionsFiltered(
            startDate: LocalDate,
            endDate: LocalDate,
            currency: String,
            transactionType: TransactionType? = null
    ): Flow<List<TransactionEntity>> =
            transactionDao.getTransactionsFiltered(
                    startDate.atStartOfDay(),
                    endDate.atTime(23, 59, 59),
                    currency,
                    transactionType
            )

    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> =
            transactionDao.getTransactionsByType(type)

    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>> =
            transactionDao.getTransactionsByCategory(category)

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
            transactionDao.searchTransactions(query)

    fun getAllCurrencies(): Flow<List<String>> = transactionDao.getAllCurrencies()

    fun getCurrenciesForPeriod(
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<String>> = transactionDao.getCurrenciesForPeriod(startDate, endDate)

    fun getAllCategories(): Flow<List<String>> = transactionDao.getAllCategories()

    /**
     * Gets the top N categories by usage count (number of transactions). Useful for showing user's
     * most frequently used categories in notifications.
     *
     * @param limit Maximum number of categories to return (default: 3)
     * @return List of category names ordered by usage count (most used first)
     */
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String> =
            transactionDao.getTopCategoriesByUsage(limit)

    fun getAllMerchants(): Flow<List<String>> = transactionDao.getAllMerchants()

    suspend fun getTotalAmountByTypeAndPeriod(
            type: TransactionType,
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Double? = transactionDao.getTotalAmountByTypeAndPeriod(type, startDate, endDate)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
            transactionDao.insertTransaction(transaction)

    suspend fun insertTransactions(transactions: List<TransactionEntity>) =
            transactionDao.insertTransactions(transactions)

    suspend fun updateTransaction(transaction: TransactionEntity) =
            transactionDao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean = false) {
        accountBalanceRepository.changeTransactionDeletion(listOf(transaction.id), true, hardDelete)
    }

    suspend fun deleteTransactionById(id: Long, hardDelete: Boolean = false) {
        accountBalanceRepository.changeTransactionDeletion(listOf(id), true, hardDelete)
    }

    suspend fun deleteAllTransactions() = transactionDao.deleteAllTransactions()

    // Helper method to check if transaction exists by hash
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
            transactionDao.getTransactionByHash(transactionHash)

    suspend fun getTransactionsByReferenceAndAmount(
        reference: String,
        amount: BigDecimal,
        accountLast4: String?,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity> =
        transactionDao.getTransactionsByReferenceAndAmount(reference, amount, accountLast4, startDate, endDate)

    suspend fun undoDeleteTransaction(transaction: TransactionEntity) {
        accountBalanceRepository.changeTransactionDeletion(listOf(transaction.id), false)
    }

    suspend fun deleteTransactions(transactions: List<TransactionEntity>, hardDelete: Boolean = false) {
        accountBalanceRepository.changeTransactionDeletion(transactions.map { it.id }, true, hardDelete)
    }

    suspend fun undoDeleteTransactions(transactions: List<TransactionEntity>) {
        accountBalanceRepository.changeTransactionDeletion(transactions.map { it.id }, false)
    }

    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String) {
        transactionDao.updateCategoryForMerchant(merchantName, newCategory)
    }

    suspend fun updateCategoryAndSubcategoryForMerchantContains(merchantName: String, newCategory: String, newSubcategory: String?) {
        transactionDao.updateCategoryAndSubcategoryForMerchantContains(merchantName, newCategory, newSubcategory)
    }

    /** Returns non-deleted transactions whose merchant name contains the given keyword, excluding one id. */
    suspend fun getTransactionsByMerchantContains(
        merchantName: String,
        excludeId: Long
    ): List<TransactionEntity> {
        return transactionDao.getTransactionsByMerchantContains(merchantName, excludeId)
    }

    suspend fun searchTransactionsList(searchQuery: String): List<TransactionEntity> {
        return transactionDao.searchTransactionsList(searchQuery)
    }

    // Additional methods for Home screen
    data class MonthlyBreakdown(
            val total: BigDecimal,
            val income: BigDecimal,
            val expenses: BigDecimal
    )

    fun getCurrentMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1).atStartOfDay()
        val endDate = now.atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate).map { transactions ->
            val income =
                    transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.BORROWED }.fold(
                                    BigDecimal.ZERO
                            ) { acc, transaction -> acc + transaction.amount }
            val expenses =
                    transactions.filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.LENT }.fold(
                                    BigDecimal.ZERO
                            ) { acc, transaction -> acc + transaction.amount }
            MonthlyBreakdown(total = income - expenses, income = income, expenses = expenses)
        }
    }

    fun getLastMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val dayOfMonth = now.dayOfMonth
        val lastMonth = now.minusMonths(1)

        // Compare same period: if today is 10th, compare 1st-10th of last month
        val startDate = lastMonth.withDayOfMonth(1).atStartOfDay()
        val lastMonthMaxDay = min(dayOfMonth, lastMonth.lengthOfMonth())
        val endDate = lastMonth.withDayOfMonth(lastMonthMaxDay).atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate).map { transactions ->
            val income =
                    transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.BORROWED }.fold(
                                    BigDecimal.ZERO
                            ) { acc, transaction -> acc + transaction.amount }
            val expenses =
                    transactions.filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.LENT }.fold(
                                    BigDecimal.ZERO
                            ) { acc, transaction -> acc + transaction.amount }
            MonthlyBreakdown(total = income - expenses, income = income, expenses = expenses)
        }
    }

    // Currency-grouped breakdown methods
    /** This month to date, by currency. */
    fun getCurrentMonthBreakdownByCurrency(today: LocalDate = LocalDate.now()): Flow<Map<String, MonthlyBreakdown>> =
        breakdownBetween(today.withDayOfMonth(1).atStartOfDay(), today.atTime(LocalTime.MAX))

    /** All of last month, by currency. */
    fun getLastMonthBreakdownByCurrency(today: LocalDate = LocalDate.now()): Flow<Map<String, MonthlyBreakdown>> {
        val lastMonth = YearMonth.from(today).minusMonths(1)
        return breakdownBetween(lastMonth.atDay(1).atStartOfDay(), lastMonth.atEndOfMonth().atTime(LocalTime.MAX))
    }

    /**
     * Income and spending between two times, by currency. Loans are neither: lending is not
     * spending and borrowing is not income; card purchases (CREDIT) are spending.
     */
    private fun breakdownBetween(start: LocalDateTime, end: LocalDateTime): Flow<Map<String, MonthlyBreakdown>> =
        transactionDao.getTransactionsBetweenDates(start, end).map { transactions ->
            transactions.groupBy { it.currency }.mapValues { (_, rows) ->
                val income = rows.filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                val expenses = rows.filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                MonthlyBreakdown(total = income - expenses, income = income, expenses = expenses)
            }
        }

    fun getRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>> =
            transactionDao.getRecentVisibleTransactions(limit)

    fun getTransactionsByAccount(
            bankName: String,
            accountLast4: String
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(bankName, accountLast4)
    }

    fun getTransactionsByAccountAndDateRange(
            bankName: String,
            accountLast4: String,
            startDate: LocalDateTime,
            endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccountAndDateRange(
                bankName,
                accountLast4,
                startDate,
                endDate
        )
    }

    // Methods for batch rule application
    suspend fun getAllTransactionsList(): List<TransactionEntity> {
        // Get all non-deleted transactions as a list (not Flow) for batch processing
        // Use a large date range to get all transactions
        val startDate = LocalDateTime.of(2000, 1, 1, 0, 0)
        val endDate = LocalDateTime.now().plusYears(10)
        return transactionDao.getTransactionsBetweenDatesList(startDate, endDate)
    }

    suspend fun updateAccountForTransactions(
            oldBankName: String,
            oldAccountNumber: String,
            newBankName: String,
            newAccountNumber: String
    ) {
        transactionDao.updateAccountForTransactions(
                oldBankName,
                oldAccountNumber,
                newBankName,
                newAccountNumber
        )
    }

    suspend fun retargetTransfers(oldAccountId: Long, newAccountId: Long, newLast4: String) =
        transactionDao.retargetTransfers(oldAccountId, newAccountId, newLast4)

    suspend fun updateTransactionsCategory(oldCategory: String, newCategory: String, newSubcategory: String?) {
        transactionDao.updateTransactionsCategory(oldCategory, newCategory, newSubcategory)
    }

    suspend fun getTransactionCountByCategory(category: String): Int {
        return transactionDao.getTransactionCountByCategory(category)
    }

    suspend fun findPotentialDuplicates(
        amount: BigDecimal,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity> {
        return transactionDao.findPotentialDuplicates(startDate, endDate)
            .filter { it.amount.compareTo(amount) == 0 }
    }
}
