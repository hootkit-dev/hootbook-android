package io.github.hootkit_dev.hootbook.data.database.dao

import androidx.room.*
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetCategoryLimitEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    // Budget operations
    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY year DESC, month DESC")
    fun getActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    suspend fun getBudgetById(budgetId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month LIMIT 1")
    suspend fun getBudgetByYearMonth(year: Int, month: Int): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month AND is_active = 1")
    fun getActiveBudgetsForMonth(year: Int, month: Int): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudget(budgetId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    // Category limit operations
    @Query("SELECT * FROM budget_category_limits")
    fun getAllCategoryLimits(): Flow<List<BudgetCategoryLimitEntity>>

    @Query("SELECT * FROM budget_category_limits WHERE budget_id = :budgetId")
    fun getCategoryLimitsForBudget(budgetId: Long): Flow<List<BudgetCategoryLimitEntity>>

    @Query("SELECT * FROM budget_category_limits WHERE budget_id = :budgetId")
    suspend fun getCategoryLimitsForBudgetSync(budgetId: Long): List<BudgetCategoryLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryLimit(limit: BudgetCategoryLimitEntity): Long

    @Update
    suspend fun updateCategoryLimit(limit: BudgetCategoryLimitEntity)

    @Query("DELETE FROM budget_category_limits WHERE id = :limitId")
    suspend fun deleteCategoryLimit(limitId: Long)

    @Query("DELETE FROM budget_category_limits WHERE budget_id = :budgetId")
    suspend fun deleteCategoryLimitsForBudget(budgetId: Long)

}
