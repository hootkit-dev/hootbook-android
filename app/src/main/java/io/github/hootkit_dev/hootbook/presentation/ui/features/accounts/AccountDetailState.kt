package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
import java.math.BigDecimal

data class AccountDetailUiState(
    val bankName: String = "",
    val accountLast4: String = "",
    val currentBalance: AccountBalanceEntity? = null,
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val balanceChartData: List<BalancePoint> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val primaryCurrency: String = "CNY",
    val baseCurrency: String = "CNY",
    val hasMultipleCurrencies: Boolean = false,
    // Transactions in the main currency, where a rate is known
    val conversions: Conversions = Conversions(),
    // A credit card's billing dates and where the current statement stands
    val cardDates: io.github.hootkit_dev.hootbook.domain.model.CardDates = io.github.hootkit_dev.hootbook.domain.model.CardDates(),
    val cardStatus: CardStatus? = null,
    val isLoading: Boolean = true
)

/**
 * Where a credit card's bill stands. While a statement is open for payment ([due] not passed),
 * [statementAmount] is what it closed at and [remaining] what of it is still owed; after that,
 * only the next closing and due dates are known.
 */
data class CardStatus(
    val due: java.time.LocalDate,
    val closing: java.time.LocalDate?,
    val statementAmount: BigDecimal?,
    val remaining: BigDecimal?,
    // Past its due date and not paid off
    val overdue: Boolean = false
)

enum class DateRange(val label: String) {
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time")
}
