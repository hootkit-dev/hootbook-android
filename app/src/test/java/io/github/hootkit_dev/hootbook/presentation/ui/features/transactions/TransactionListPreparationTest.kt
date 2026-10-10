package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class TransactionListPreparationTest {
    private fun fixture(id: Long, type: TransactionType = TransactionType.EXPENSE, currency: String = "CNY") =
        TransactionEntity(id = id, amount = BigDecimal(id), merchantName = "Shop $id",
            category = "Shopping", transactionType = type, dateTime = LocalDateTime.now().minusDays(id),
            transactionHash = "test-$id", currency = currency)

    @Test fun thousandItemsSortWithoutMutatingSourceAndKeepStableIds() {
        val rows = (1L..1000L).map { fixture(it) }
        val sorted = sortTransactions(rows, SortOption.AMOUNT_HIGHEST)
        assertEquals(1000L, sorted.first().id)
        assertEquals(1L, rows.first().id)
        assertEquals(rows.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        val groups = groupTransactionsByDate(sorted)
        assertEquals(1000, groups.values.sumOf { it.size })
    }

    @Test fun totalsKeepCurrenciesSeparateAndIncludeLoans() {
        val rows = listOf(fixture(10, TransactionType.BORROWED), fixture(3, TransactionType.LENT),
            fixture(7, TransactionType.EXPENSE, "USD"))
        val totals = calculateCurrencyGroupedTotals(rows)
        assertEquals(BigDecimal(10), totals.totalsByCurrency.getValue("CNY").income)
        assertEquals(BigDecimal(3), totals.totalsByCurrency.getValue("CNY").expenses)
        assertEquals(BigDecimal(7), totals.totalsByCurrency.getValue("USD").expenses)
        assertEquals(3, totals.transactionCount)
        assertTrue(calculateCurrencyGroupedTotals(emptyList()).availableCurrencies.isEmpty())
    }
}
