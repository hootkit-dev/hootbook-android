package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Internal state for combining all filter parameters.
 * Used in reactive Flow to trigger data reload when any filter changes.
 */
data class FilterState(
    val period: TimePeriod,
    val customRange: Pair<LocalDate, LocalDate>?,
    val typeFilter: Set<TransactionTypeFilter>,
    val currency: String?
)

data class AnalyticsUiState(
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    // Income in the period when several types are shown: kept apart from the spending total
    val totalIncome: BigDecimal? = null,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val topMerchants: List<MerchantData> = emptyList(),
    val transactionCount: Int = 0,
    val averageAmount: BigDecimal = BigDecimal.ZERO,
    val topCategory: String? = null,
    val topCategoryPercentage: Float = 0f,
    val currency: String = "CNY",
    val baseCurrency: String = "CNY",
    val isLoading: Boolean = true,
    val spendingTrend: List<BalancePoint> = emptyList(),
    val convertedMerchantAmounts: Map<String, BigDecimal> = emptyMap()
)

data class CategoryData(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class MerchantData(
    val name: String,
    val amount: BigDecimal,
    val transactionCount: Int,
    val isSubscription: Boolean,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val accountIconName: String? = null
)
