package io.github.hootkit_dev.hootbook.data.repository

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceEffectTest {
    private fun tx(type: TransactionType, amount: String = "100", toAmount: String? = null) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = "m", category = "c", transactionType = type,
        dateTime = LocalDateTime.of(2026, 9, 1, 9, 0), transactionHash = "h", currency = "HKD",
        toAmount = toAmount?.let(::BigDecimal)
    )
    private val bank = "Bank" to "1234"
    private val broker = "Broker" to "0001"

    @Test fun anExpenseMovesItsAccountOnce() {
        assertEquals(
            listOf(BalanceMove("Bank", "1234", BigDecimal("100"), TransactionType.EXPENSE, "HKD")),
            balanceMoves(tx(TransactionType.EXPENSE), bank, null, null)
        )
    }

    @Test fun aTransferLeavesAsSpendingAndArrivesAsIncomeInWhatArrived() {
        assertEquals(
            listOf(
                BalanceMove("Bank", "1234", BigDecimal("100"), TransactionType.EXPENSE, "HKD"),
                BalanceMove("Broker", "0001", BigDecimal("12.8"), TransactionType.INCOME, "USD")
            ),
            balanceMoves(tx(TransactionType.TRANSFER, toAmount = "12.8"), bank, broker, "USD")
        )
    }

    @Test fun aTransferWithoutTargetAndABalanceUpdateMoveNothing() {
        assertEquals(emptyList<BalanceMove>(), balanceMoves(tx(TransactionType.TRANSFER), bank, null, null))
        assertEquals(emptyList<BalanceMove>(), balanceMoves(tx(TransactionType.BALANCE_UPDATE), bank, null, null))
    }
}
