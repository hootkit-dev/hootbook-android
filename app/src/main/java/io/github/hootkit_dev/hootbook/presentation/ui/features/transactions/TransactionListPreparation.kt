package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.presentation.common.CurrencyGroupedTotals
import io.github.hootkit_dev.hootbook.presentation.common.CurrencyTotals
import io.github.hootkit_dev.hootbook.utils.CurrencyUtils
import java.math.BigDecimal
import java.time.LocalDate

// Pure data transformations: callers run these on Dispatchers.Default, never build UI here.
internal fun sortTransactions(transactions: List<TransactionEntity>, sortOption: SortOption): List<TransactionEntity> {
    return when (sortOption) {
        SortOption.DATE_NEWEST -> transactions.sortedByDescending { it.dateTime }
        SortOption.DATE_OLDEST -> transactions.sortedBy { it.dateTime }
        SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending { it.amount }
        SortOption.AMOUNT_LOWEST -> transactions.sortedBy { it.amount }
        SortOption.MERCHANT_AZ -> transactions.sortedBy { it.merchantName.lowercase() }
        SortOption.MERCHANT_ZA -> transactions.sortedByDescending { it.merchantName.lowercase() }
    }
}

internal fun groupTransactionsByDate(
    transactions: List<TransactionEntity>
): Map<DateGroup, List<TransactionEntity>> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val weekStart = today.minusWeeks(1)
    
    return transactions.groupBy { transaction ->
        val transactionDate = transaction.dateTime.toLocalDate()
        when {
            transactionDate == today -> DateGroup.TODAY
            transactionDate == yesterday -> DateGroup.YESTERDAY
            transactionDate > weekStart -> DateGroup.THIS_WEEK
            else -> DateGroup.EARLIER
        }
    }
}

internal fun calculateCurrencyGroupedTotals(transactions: List<TransactionEntity>): CurrencyGroupedTotals {
    // Group transactions by currency
    val transactionsByCurrency = transactions.groupBy { it.currency }

    val totalsByCurrency = transactionsByCurrency.mapValues { (currency, currencyTransactions) ->
        val income = currencyTransactions
            .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.BORROWED }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val expenses = currencyTransactions
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.LENT }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val credit = currencyTransactions
            .filter { it.transactionType == TransactionType.CREDIT }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val transfer = currencyTransactions
            .filter { it.transactionType == TransactionType.TRANSFER }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val investment = currencyTransactions
            .filter { it.transactionType == TransactionType.INVESTMENT }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        CurrencyTotals(
            currency = currency,
            income = income,
            expenses = expenses,
            credit = credit,
            transfer = transfer,
            investment = investment,
            transactionCount = currencyTransactions.size
        )
    }

    val filteredAvailableCurrencies = CurrencyUtils.sortCurrencies(
        totalsByCurrency.keys.toList()
    )

    return CurrencyGroupedTotals(
        totalsByCurrency = totalsByCurrency,
        availableCurrencies = filteredAvailableCurrencies,
        transactionCount = transactions.size
    )
}

