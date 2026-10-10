package io.github.hootkit_dev.hootbook.domain.usecase

import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthByDayTest {
    private val start = LocalDate.of(2026, 10, 1)
    private var id = 0L
    private fun row(account: Long, day: Int, balance: String, currency: String = "CNY", card: Boolean = false) =
        AccountBalanceEntity(
            id = ++id, bankName = "b$account", accountLast4 = "000$account", balance = BigDecimal(balance),
            timestamp = start.plusDays(day.toLong()).atTime(12, 0), currency = currency, isCreditCard = card,
            accountId = account
        )

    @Test fun accountsCarryForwardAndCardsSubtract() {
        val rows = listOf(
            row(1, 0, "1000"),
            row(2, 0, "100", currency = "USD"),
            row(3, 1, "300", card = true),
            row(1, 2, "800")
        )
        val days = netWorthByDay(rows, start, start.plusDays(3), mapOf("USD" to BigDecimal("7")))
            .map { it.second.stripTrailingZeros().toPlainString() }
        // Day 0: 1000 + 700; day 1: card owes 300; day 2: account 1 down to 800; day 3: unchanged
        assertEquals(listOf("1700", "1400", "1200", "1200"), days)
    }
}
