package io.github.hootkit_dev.hootbook.domain.usecase

import io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthPocketsTest {
    private fun account(id: Long, currency: String, balance: String) = AccountBalanceEntity(
        bankName = "b$id", accountLast4 = "000$id", balance = BigDecimal(balance), timestamp = LocalDateTime.now(),
        currency = currency, accountId = id
    )

    @Test fun anAccountCountsEveryCurrencyItHolds() {
        val broker = account(1, "USD", "890")
        val bank = account(2, "CNY", "5000")
        val pockets = listOf(
            PocketBalance(1, "USD", BigDecimal("890"), false, null),
            PocketBalance(1, "HKD", BigDecimal("15000"), false, null)
        )
        val expanded = listOf(broker, bank).withPockets(pockets)
        assertEquals(listOf("USD" to "890", "HKD" to "15000", "CNY" to "5000"), expanded.map { it.currency to it.balance.toPlainString() })
        // Without pockets (not loaded yet) accounts count as they are
        assertEquals(2, listOf(broker, bank).withPockets(emptyList()).size)
    }
}
