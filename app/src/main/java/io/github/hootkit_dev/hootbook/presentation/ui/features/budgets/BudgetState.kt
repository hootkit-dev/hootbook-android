package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.repository.BudgetWithSpending
import io.github.hootkit_dev.hootbook.data.repository.CategoryLimitWithSpending
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetPeriod
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetTrackType
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetType
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.time.format.DateTimeFormatter

data class BudgetUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetWithSpending> = emptyList(),
    val selectedBudget: BudgetWithSpending? = null,
    val categoryLimitsWithSpending: List<CategoryLimitWithSpending> = emptyList(),
    val selectedBudgetTransactions: List<TransactionEntity> = emptyList(),
    val allAccounts: List<AccountBalanceEntity> = emptyList(),
    // Transactions in the main currency, where a rate is known
    val conversions: Conversions = Conversions(),
    val baseCurrency: String = "CNY",
    val error: String? = null
)

data class EditBudgetState(
    val budgetId: Long? = null,
    val name: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val year: Int = LocalDateTime.now().year,
    val month: Int = LocalDateTime.now().monthValue,
    val startDate: LocalDateTime = LocalDateTime.now(),
    val endDate: LocalDateTime = LocalDateTime.now().plusMonths(1).minusDays(1),
    val periodType: BudgetPeriod = BudgetPeriod.MONTHLY,
    val trackType: BudgetTrackType = BudgetTrackType.ALL_TRANSACTIONS,
    val budgetType: BudgetType = BudgetType.EXPENSE,
    val accountIds: List<String> = emptyList(),
    val color: String = "#4CAF50",
    val currency: String = "CNY",
    val categoryLimits: List<EditCategoryLimit> = emptyList()
) {
    val isNewBudget: Boolean get() = budgetId == null
    
    /** A name for a new budget; it rolls on to the next period, so it names no date. */
    fun getDefaultName(): String = defaultBudgetName(periodType)
}

data class EditCategoryLimit(
    val id: Long? = null,
    val categoryName: String,
    val limitAmount: BigDecimal
)

/** The name a new budget of [period] gets, in the app's language. */
fun defaultBudgetName(period: BudgetPeriod, locale: Locale = Locale.getDefault()): String {
    val zh = locale.language == "zh"
    return when (period) {
        BudgetPeriod.MONTHLY -> if (zh) "月度预算" else "Monthly budget"
        BudgetPeriod.YEARLY -> if (zh) "年度预算" else "Yearly budget"
        BudgetPeriod.DAILY -> if (zh) "每日预算" else "Daily budget"
        BudgetPeriod.WEEKLY -> if (zh) "每周预算" else "Weekly budget"
        BudgetPeriod.CUSTOM -> if (zh) "自定义预算" else "Custom budget"
    }
}

/**
 * The name to show for a budget. Older versions named a monthly budget after the month it was
 * made in ("September 2026", "九月 2026"); budgets roll on now, so such a name shows as the plain
 * monthly default instead of a month long past.
 */
fun budgetDisplayName(name: String, period: BudgetPeriod, locale: Locale = Locale.getDefault()): String {
    if (period != BudgetPeriod.MONTHLY) return name
    val parts = name.trim().split(' ')
    if (parts.size != 2 || parts[1].length != 4 || parts[1].toIntOrNull() == null) return name
    val months = java.time.Month.entries.flatMap { m ->
        listOf(Locale.ENGLISH, Locale.CHINESE, locale).map { m.getDisplayName(TextStyle.FULL, it) }
    }
    return if (parts[0] in months) defaultBudgetName(period, locale) else name
}
