package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import androidx.annotation.StringRes
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import java.math.BigDecimal

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val groupedTransactions: Map<DateGroup, List<TransactionEntity>> = emptyMap(),
    // Transactions in the main currency, where a rate is known
    val conversions: Conversions = Conversions(),
    val isLoading: Boolean = true,
)

data class FilterParams(
    val query: String,
    val period: TimePeriod,
    val category: String?,
    val typeFilter: TransactionTypeFilter
)

enum class DateGroup(@StringRes val labelRes: Int) {
    TODAY(R.string.today_lbl),
    YESTERDAY(R.string.yesterday_lbl),
    THIS_WEEK(R.string.this_week_lbl),
    EARLIER(R.string.earlier_lbl)
}

enum class SortOption(@StringRes val labelRes: Int) {
    DATE_NEWEST(R.string.sort_date_newest),
    DATE_OLDEST(R.string.sort_date_oldest),
    AMOUNT_HIGHEST(R.string.sort_amount_highest),
    AMOUNT_LOWEST(R.string.sort_amount_lowest),
    MERCHANT_AZ(R.string.sort_merchant_az),
    MERCHANT_ZA(R.string.sort_merchant_za)
}

data class FilteredTotals(
    val income: BigDecimal = BigDecimal.ZERO,
    val expenses: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    val transfer: BigDecimal = BigDecimal.ZERO,
    val investment: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0
)
