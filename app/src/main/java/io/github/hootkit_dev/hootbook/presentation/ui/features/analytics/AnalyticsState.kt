package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import java.time.LocalDate as JavaDate

data class AnalyticsUiState(
    val filter: TransactionFilter = emptyAnalyticsFilter(),
    val period: TimePeriod = TimePeriod.THIS_MONTH,
    val customRange: Pair<JavaDate, JavaDate>? = null,
    val types: Set<TransactionTypeFilter> = setOf(TransactionTypeFilter.EXPENSE),
    val selectedCurrency: String? = null,
    val availableCurrencies: List<String> = emptyList(),
    val summary: LedgerSummary? = null,
    val transactionCount: UInt = 0u,
    val categories: Map<String, Category> = emptyMap(),
    val categoryAmounts: Map<String, Money?> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val trend: Map<JavaDate, LedgerSummary> = emptyMap(),
    val trendFilters: Map<JavaDate, TransactionFilter> = emptyMap(),
    val isLoading: Boolean = true,
    val error: Exception? = null,
) {
    val incomeOnly: Boolean
        get() = types == setOf(TransactionTypeFilter.INCOME)

    val categoryBreakdown: List<CategoryTotal>
        get() =
            (if (incomeOnly) summary?.incomeByCategory else summary?.expenseByCategory).orEmpty()

    val currency: String
        get() = selectedCurrency ?: summary?.currency.orEmpty()
}

internal fun emptyAnalyticsFilter() =
    TransactionFilter(
        null,
        null,
        emptyList(),
        emptyList(),
        emptyList(),
        null,
        null,
        DeletedFilter.EXCLUDE,
        SortOrder.NEWEST_FIRST,
    )
