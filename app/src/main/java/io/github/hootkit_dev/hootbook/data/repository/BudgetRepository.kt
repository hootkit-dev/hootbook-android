package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.dao.BudgetDao
import io.github.hootkit_dev.hootbook.data.database.dao.TransactionDao
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetCategoryLimitEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetType
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetWithSpending(
    val budget: BudgetEntity,
    val currentSpending: BigDecimal,
    val categoryLimits: List<BudgetCategoryLimitEntity>,
    val categorySpending: Map<String, BigDecimal>,
    val daysRemaining: Int,
    val daysInMonth: Int
) {
    val remaining: BigDecimal get() = budget.amount - currentSpending
    val percentUsed: Float get() = if (budget.amount > BigDecimal.ZERO) {
        (currentSpending.toFloat() / budget.amount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isOverBudget: Boolean get() = currentSpending > budget.amount
    val spendingPerDay: BigDecimal get() {
        val daysPassed = daysInMonth - daysRemaining
        return if (daysPassed > 0) {
            currentSpending.divide(BigDecimal(daysPassed), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }
    val recommendedDailySpending: BigDecimal get() {
        return if (daysRemaining > 0 && remaining > BigDecimal.ZERO) {
            remaining.divide(BigDecimal(daysRemaining), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
    }
}

data class CategoryLimitWithSpending(
    val limit: BudgetCategoryLimitEntity,
    val currentSpending: BigDecimal
) {
    val remaining: BigDecimal get() = limit.limitAmount - currentSpending
    val percentUsed: Float get() = if (limit.limitAmount > BigDecimal.ZERO) {
        (currentSpending.toFloat() / limit.limitAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isOverLimit: Boolean get() = currentSpending > limit.limitAmount
}

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val currencyConversionService: CurrencyConversionService,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    val allBudgets: StateFlow<List<BudgetEntity>> = budgetDao.getAllBudgets()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun getAllBudgets(): Flow<List<BudgetEntity>> {
        return budgetDao.getAllBudgets()
    }

    fun getActiveBudgets(): Flow<List<BudgetEntity>> {
        return budgetDao.getActiveBudgets()
    }

    suspend fun getBudgetById(budgetId: Long): BudgetEntity? {
        return budgetDao.getBudgetById(budgetId)
    }

    suspend fun getBudgetByYearMonth(year: Int, month: Int): BudgetEntity? {
        return budgetDao.getBudgetByYearMonth(year, month)
    }

    fun getActiveBudgetsForMonth(year: Int, month: Int): Flow<List<BudgetEntity>> {
        return budgetDao.getActiveBudgetsForMonth(year, month)
    }

    suspend fun insertBudget(budget: BudgetEntity): Long {
        return budgetDao.insertBudget(budget)
    }

    suspend fun updateBudget(budget: BudgetEntity) {
        budgetDao.updateBudget(budget.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun deleteBudget(budgetId: Long) {
        budgetDao.deleteBudget(budgetId)
    }

    suspend fun deleteAllBudgets() {
        budgetDao.deleteAllBudgets()
    }

    fun getCategoryLimitsForBudget(budgetId: Long): Flow<List<BudgetCategoryLimitEntity>> {
        return budgetDao.getCategoryLimitsForBudget(budgetId)
    }

    suspend fun getCategoryLimitsForBudgetSync(budgetId: Long): List<BudgetCategoryLimitEntity> {
        return budgetDao.getCategoryLimitsForBudgetSync(budgetId)
    }

    suspend fun addCategoryLimit(
        budgetId: Long,
        categoryName: String,
        limitAmount: BigDecimal
    ): Long {
        val limit = BudgetCategoryLimitEntity(
            budgetId = budgetId,
            categoryName = categoryName,
            limitAmount = limitAmount,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        return budgetDao.insertCategoryLimit(limit)
    }

    suspend fun updateCategoryLimit(limit: BudgetCategoryLimitEntity) {
        budgetDao.updateCategoryLimit(limit.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun deleteCategoryLimit(limitId: Long) {
        budgetDao.deleteCategoryLimit(limitId)
    }

    suspend fun deleteCategoryLimitsForBudget(budgetId: Long) {
        budgetDao.deleteCategoryLimitsForBudget(budgetId)
    }

    // Spending calculation methods
    suspend fun getBudgetWithSpending(budget: BudgetEntity): BudgetWithSpending {
        val window = io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.current(budget)
        var transactions = transactionDao.getTransactionsBetweenDatesList(window.from, window.to)
            .filter { io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.counts(budget, it) }
        
        // Convert currencies to match the budget's currency
        transactions = transactions.map { txn ->
            if (txn.currency != budget.currency) {
                val convertedAmount = currencyConversionService.convertAmount(txn.amount, txn.currency, budget.currency)
                txn.copy(amount = convertedAmount ?: txn.amount, currency = budget.currency)
            } else {
                txn
            }
        }

        var totalSpending = BigDecimal.ZERO
        for (txn in transactions) {
            totalSpending += txn.amount
        }

        // Calculate spending per category
        val categorySpending = transactions
            .groupBy { it.category }
            .mapValues { (_, txns) ->
                var sum = BigDecimal.ZERO
                for (t in txns) sum += t.amount
                sum
            }

        val categoryLimits = budgetDao.getCategoryLimitsForBudgetSync(budget.id)

        // Days left in the current period, today included
        val today = java.time.LocalDate.now()
        val totalDays = window.days
        val daysRemaining = when {
            today.isBefore(window.start) -> totalDays
            today.isAfter(window.end) -> 0
            else -> (java.time.temporal.ChronoUnit.DAYS.between(today, window.end) + 1).toInt()
        }

        return BudgetWithSpending(
            budget = budget,
            currentSpending = totalSpending,
            categoryLimits = categoryLimits,
            categorySpending = categorySpending,
            daysRemaining = daysRemaining,
            daysInMonth = totalDays
        )
    }

    fun getBudgetsWithSpendingForMonth(year: Int, month: Int): Flow<List<BudgetWithSpending>> {
        val startOfMonth = YearMonth.of(year, month).atDay(1).atStartOfDay()
        val endOfMonth = YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59)
        
        return combine(
            budgetDao.getAllBudgets(),
            transactionDao.getAllTransactions(),
            budgetDao.getAllCategoryLimits()
        ) { budgets, transactions, categoryLimits ->
            budgets.filter { budget ->
                // A repeating budget always has a current period; a custom one counts while it overlaps
                val window = io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.current(budget)
                budget.isActive && !window.from.isAfter(endOfMonth) && !window.to.isBefore(startOfMonth)
            }.map { budget ->
                calculateSpending(budget, transactions, categoryLimits)
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    fun getAllBudgetsWithSpending(): Flow<List<BudgetWithSpending>> {
        return combine(
            budgetDao.getAllBudgets(),
            transactionDao.getAllTransactions(),
            budgetDao.getAllCategoryLimits()
        ) { budgets, transactions, categoryLimits ->
            budgets.map { budget ->
                calculateSpending(budget, transactions, categoryLimits)
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    private suspend fun calculateSpending(
        budget: BudgetEntity,
        allTransactions: List<TransactionEntity>,
        allCategoryLimits: List<BudgetCategoryLimitEntity>
    ): BudgetWithSpending {
        val window = io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.current(budget)
        var transactions = allTransactions.filter { it.dateTime in window && io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.counts(budget, it) }

        // Convert currencies to match the budget's currency
        transactions = transactions.map { txn ->
            if (txn.currency != budget.currency) {
                val convertedAmount = currencyConversionService.convertAmount(txn.amount, txn.currency, budget.currency)
                txn.copy(amount = convertedAmount ?: txn.amount, currency = budget.currency)
            } else {
                txn
            }
        }

        // Calculate total spending
        var totalSpending = BigDecimal.ZERO
        for (txn in transactions) {
            totalSpending += txn.amount
        }

        // Calculate spending per category
        val categorySpending = transactions
            .groupBy { it.category }
            .mapValues { (_, txns) ->
                var sum = BigDecimal.ZERO
                for (t in txns) sum += t.amount
                sum
            }

        // Get category limits for this budget
        val categoryLimits = allCategoryLimits.filter { it.budgetId == budget.id }

        // Days left in the current period, today included
        val today = java.time.LocalDate.now()
        val totalDays = window.days
        val daysRemaining = when {
            today.isBefore(window.start) -> totalDays
            today.isAfter(window.end) -> 0
            else -> (java.time.temporal.ChronoUnit.DAYS.between(today, window.end) + 1).toInt()
        }

        return BudgetWithSpending(
            budget = budget,
            currentSpending = totalSpending,
            categoryLimits = categoryLimits,
            categorySpending = categorySpending,
            daysRemaining = daysRemaining,
            daysInMonth = totalDays
        )
    }

    fun getTransactionsForBudget(budget: BudgetEntity): Flow<List<TransactionEntity>> {
        val window = io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.current(budget)

        return transactionDao.getTransactionsBetweenDates(window.from, window.to)
            .map { transactions ->
                var filtered = transactions.filter { io.github.hootkit_dev.hootbook.domain.model.BudgetPeriods.counts(budget, it) }

                // Convert currencies to match the budget's currency
                filtered = filtered.map { txn ->
                    if (txn.currency != budget.currency) {
                        val convertedAmount = currencyConversionService.convertAmount(txn.amount, txn.currency, budget.currency)
                        txn.copy(amount = convertedAmount ?: txn.amount, currency = budget.currency)
                    } else {
                        txn
                    }
                }
                
                filtered
            }
    }
}
