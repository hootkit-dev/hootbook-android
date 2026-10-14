package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

data class BudgetUiState(
    val isLoading: Boolean = true,
    val budgets: List<Budget> = emptyList(),
    val selectedBudget: Budget? = null,
    val selectedCategoryId: String? = null,
    val transactionsPage: BudgetTransactionPage? = null,
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val baseCurrency: String = "CNY",
    val saving: Boolean = false,
    val error: Int? = null,
)

/** Editable inputs only. Amounts/progress in BudgetUiState always come from Core. */
data class EditBudgetState(
    val budgetId: String? = null,
    val name: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val startDate: LocalDateTime = LocalDateTime.now(),
    val endDate: LocalDateTime = LocalDateTime.now(),
    val originalStart: dev.hootkit.core.LocalDate? = null,
    val periodType: BudgetPeriod = BudgetPeriod.MONTHLY,
    val budgetType: BudgetKind = BudgetKind.EXPENSE,
    val accountIds: List<String>? = null,
    val color: String = "#4CAF50",
    val currency: String = "CNY",
    val categoryLimits: List<NewBudgetLimit> = emptyList(),
) {
    val isNewBudget: Boolean get() = budgetId == null
    fun getDefaultName(): String = defaultBudgetName(periodType)
}

fun defaultBudgetName(period: BudgetPeriod, locale: Locale = Locale.getDefault()): String {
    val zh = locale.language == "zh"
    return when (period) {
        BudgetPeriod.MONTHLY -> if (zh) "月度预算" else "Monthly budget"
        BudgetPeriod.YEARLY -> if (zh) "年度预算" else "Yearly budget"
        BudgetPeriod.DAILY -> if (zh) "每日预算" else "Daily budget"
        BudgetPeriod.WEEKLY -> if (zh) "每周预算" else "Weekly budget"
        BudgetPeriod.CUSTOM, BudgetPeriod.UNKNOWN -> if (zh) "自定义预算" else "Custom budget"
    }
}

fun budgetDisplayName(name: String, period: BudgetPeriod, locale: Locale = Locale.getDefault()): String = name

// Visual ratio only; never used to derive monetary/business values.
internal fun budgetProgressFraction(spent: BigDecimal, amount: BigDecimal): Float =
    if (amount.signum() <= 0) 0f else spent.divide(amount, java.math.MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
internal fun java.time.LocalDate.coreBudgetDate() = dev.hootkit.core.LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())
internal fun dev.hootkit.core.LocalDate.javaBudgetDate() = java.time.LocalDate.of(year, month.toInt(), day.toInt())
internal fun budgetToday() = java.time.LocalDate.now().coreBudgetDate()
internal val budgetTables = setOf(Table.BUDGETS, Table.BUDGET_ACCOUNTS, Table.BUDGET_LIMITS,
    Table.TRANSACTIONS, Table.TAGS, Table.TRANSACTION_TAGS, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.CATEGORIES,
    Table.EXCHANGE_RATES, Table.SETTINGS)
