package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.*
import java.math.BigDecimal

data class AccountDetailUiState(
    val account: Account? = null,
    val currency: String? = null,
    val anchors: List<BalanceAnchor> = emptyList(),
    val balanceSeries: List<BalanceDay> = emptyList(),
    val cardStatus: CardStatus? = null,
    val transactions: List<Transaction> = emptyList(),
    val total: UInt = 0u,
    val accountTransactionCount: UInt = 0u,
    val summary: LedgerSummary? = null,
    val categories: Map<String, Category> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val tags: Map<String, Tag> = emptyMap(),
    val people: Map<String, Person> = emptyMap(),
    val debts: Map<String, DebtEntry> = emptyMap(),
    val convertedAmounts: Map<String, BigDecimal?> = emptyMap(),
    val selectedDateRange: DateRange = DateRange.LAST_30_DAYS,
    val isLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val saving: Boolean = false,
    val error: Int? = null,
    val commandRevision: Long = 0,
)

enum class DateRange {
    LAST_7_DAYS, LAST_30_DAYS, LAST_3_MONTHS, LAST_6_MONTHS, LAST_YEAR, ALL_TIME
}
