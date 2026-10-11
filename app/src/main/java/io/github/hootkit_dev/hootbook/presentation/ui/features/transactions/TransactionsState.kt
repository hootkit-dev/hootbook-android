package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.annotation.StringRes
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import java.time.LocalDate

internal fun emptyTransactionFilter() =
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

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val total: UInt = 0u,
    val summary: LedgerSummary? = null,
    val categories: Map<String, Category> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val debts: Map<String, DebtEntry> = emptyMap(),
    val people: Map<String, Person> = emptyMap(),
    val convertedAmounts: Map<String, java.math.BigDecimal?> = emptyMap(),
    val currencies: List<String> = emptyList(),
    val displayCurrency: String = "",
    val filter: TransactionFilter = emptyTransactionFilter(),
    val period: TimePeriod = TimePeriod.ALL,
    val customDateRange: Pair<LocalDate, LocalDate>? = null,
    val isLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val writing: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val undoToken: String? = null,
    val error: String? = null,
)

enum class DateGroup(@StringRes val labelRes: Int) {
    TODAY(R.string.today_lbl),
    YESTERDAY(R.string.yesterday_lbl),
    THIS_WEEK(R.string.this_week_lbl),
    EARLIER(R.string.earlier_lbl),
}

enum class SortOption(@StringRes val labelRes: Int, val core: SortOrder) {
    DATE_NEWEST(R.string.sort_date_newest, SortOrder.NEWEST_FIRST),
    DATE_OLDEST(R.string.sort_date_oldest, SortOrder.OLDEST_FIRST),
    AMOUNT_HIGHEST(R.string.sort_amount_highest, SortOrder.LARGEST_FIRST),
}
