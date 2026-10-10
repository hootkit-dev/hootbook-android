package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.domain.model.CardDates
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBillTest {
    // Statement on the 5th, due on the 25th
    private val dates = CardDates(statementDay = 5, dueDay = 25)
    private val card = 7L

    private suspend fun status(today: String, statement: String?, paid: String = "0") =
        cardStatus(dates, LocalDate.parse(today), { statement?.let(::BigDecimal) }, { BigDecimal(paid) })

    @Test fun remainingIsTheStatementLessWhatWasPaidSince() = runTest {
        val s = status("2026-09-10", "1000", paid = "300")!!
        assertEquals(LocalDate.parse("2026-09-05"), s.closing)
        assertEquals(LocalDate.parse("2026-09-25"), s.due)
        assertEquals(BigDecimal("1000"), s.statementAmount)
        assertEquals(BigDecimal("700"), s.remaining)
        assertFalse(s.overdue)
    }

    @Test fun anUnpaidBillStaysShownPastItsDueDate() = runTest {
        val s = status("2026-09-28", "1000")!!
        assertTrue(s.overdue)
        assertEquals(LocalDate.parse("2026-09-25"), s.due)
        assertEquals(BigDecimal("1000"), s.remaining)
    }

    @Test fun aPaidBillGivesWayToTheNextOne() = runTest {
        val s = status("2026-09-28", "1000", paid = "1000")!!
        assertFalse(s.overdue)
        assertEquals(LocalDate.parse("2026-10-05"), s.closing)
        assertEquals(LocalDate.parse("2026-10-25"), s.due)
        assertNull(s.remaining)
    }

    @Test fun overpayingLeavesNothingToPayAndANegativeStatementCountsAsZero() = runTest {
        assertEquals(BigDecimal.ZERO, status("2026-09-10", "100", paid = "250")!!.remaining)
        assertEquals(BigDecimal.ZERO, status("2026-09-10", "-50")!!.statementAmount)
    }

    @Test fun withoutAStatementOrStatementDayOnlyTheDueDateIsKnown() = runTest {
        assertNull(status("2026-09-10", null)!!.statementAmount)
        val dueOnly = cardStatus(CardDates(null, 25), LocalDate.parse("2026-09-26"), { null }, { BigDecimal.ZERO })!!
        assertEquals(LocalDate.parse("2026-10-25"), dueOnly.due)
        assertNull(cardStatus(CardDates(5, null), LocalDate.parse("2026-09-26"), { null }, { BigDecimal.ZERO }))
    }

    private fun tx(
        type: TransactionType, amount: String, at: String, currency: String = "CNY",
        from: Long? = null, to: Long? = null, toAmount: String? = null, toCurrency: String? = null, deleted: Boolean = false
    ) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = "m", category = "c", transactionType = type,
        dateTime = LocalDateTime.parse(at), transactionHash = "$type$amount$at", currency = currency,
        accountId = from, toAccountId = to, toAmount = toAmount?.let(::BigDecimal), toCurrency = toCurrency, isDeleted = deleted
    )

    @Test fun onlyRepaymentsInTheCardsCurrencyAfterClosingCount() {
        val closedAt = LocalDateTime.parse("2026-09-05T23:59:59")
        val paid = paidOntoCard(
            listOf(
                tx(TransactionType.TRANSFER, "500", "2026-09-06T10:00", from = 1, to = card),        // repayment
                tx(TransactionType.TRANSFER, "100", "2026-09-07T10:00", "USD", from = 2, to = card,   // arrived as CNY
                    toAmount = "720", toCurrency = "CNY"),
                tx(TransactionType.INCOME, "30", "2026-09-08T10:00", from = card),                    // refund
                tx(TransactionType.EXPENSE, "80", "2026-09-08T11:00", from = card),                   // a purchase
                tx(TransactionType.TRANSFER, "50", "2026-09-04T10:00", from = 1, to = card),          // before closing
                tx(TransactionType.TRANSFER, "60", "2026-09-09T10:00", from = 1, to = card, deleted = true),
                tx(TransactionType.TRANSFER, "90", "2026-09-09T10:00", "USD", from = 1, to = card),   // in USD
                tx(TransactionType.TRANSFER, "40", "2026-09-09T12:00", from = card, to = 1)           // out of the card
            ),
            card, "CNY", closedAt
        )
        assertEquals(BigDecimal("1250"), paid)
    }
}
