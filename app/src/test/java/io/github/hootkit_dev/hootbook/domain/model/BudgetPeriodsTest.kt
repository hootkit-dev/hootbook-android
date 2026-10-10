package io.github.hootkit_dev.hootbook.domain.model

import io.github.hootkit_dev.hootbook.data.database.entity.BudgetEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetPeriod
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPeriodsTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun budget(period: BudgetPeriod, start: LocalDate, end: LocalDate = start, accounts: List<String> = emptyList()) =
        BudgetEntity(
            name = "b", amount = BigDecimal.TEN, year = start.year, month = start.monthValue, currency = "CNY",
            startDate = start.atTime(9, 30), endDate = end.atTime(9, 30), periodType = period, accountIds = accounts
        )

    @Test fun monthlyRollsOnFromItsStartDay() {
        val b = budget(BudgetPeriod.MONTHLY, d(8, 5))
        assertEquals(BudgetWindow(d(10, 5), d(11, 4)), BudgetPeriods.current(b, d(10, 6)))
        assertEquals(BudgetWindow(d(9, 5), d(10, 4)), BudgetPeriods.current(b, d(10, 4)))
    }

    @Test fun aShortMonthUsesItsLastDay() {
        val b = budget(BudgetPeriod.MONTHLY, d(1, 31))
        assertEquals(BudgetWindow(d(2, 28), d(3, 30)), BudgetPeriods.current(b, d(3, 1)))
    }

    @Test fun dailyCoversTheWholeDay() {
        val w = BudgetPeriods.current(budget(BudgetPeriod.DAILY, d(10, 1)), d(10, 6))
        assertTrue(LocalDateTime.of(2026, 10, 6, 0, 0) in w)
        assertTrue(LocalDateTime.of(2026, 10, 6, 23, 59, 59) in w)
        assertFalse(LocalDateTime.of(2026, 10, 7, 0, 0) in w)
    }

    @Test fun customIncludesBothEndDays() {
        val w = BudgetPeriods.current(budget(BudgetPeriod.CUSTOM, d(10, 1), d(10, 7)), d(12, 1))
        assertTrue(LocalDateTime.of(2026, 10, 1, 0, 1) in w)
        assertTrue(LocalDateTime.of(2026, 10, 7, 23, 0) in w)
    }

    @Test fun historyGoesBackToTheFirstPeriod() {
        val history = BudgetPeriods.history(budget(BudgetPeriod.MONTHLY, d(8, 1)), d(10, 6))
        assertEquals(listOf(d(10, 1), d(9, 1), d(8, 1)), history.map { it.start })
    }

    @Test fun accountBudgetsCountOnlyTheirAccounts() {
        val b = budget(BudgetPeriod.MONTHLY, d(10, 1), accounts = listOf("招商银行:1234"))
        fun tx(bank: String?, last4: String?, type: TransactionType = TransactionType.EXPENSE) = TransactionEntity(
            amount = BigDecimal.ONE, merchantName = "m", category = "c", transactionType = type,
            dateTime = LocalDateTime.now(), bankName = bank, accountNumber = last4, transactionHash = "h"
        )
        assertTrue(BudgetPeriods.counts(b, tx("招商银行", "1234")))
        assertFalse(BudgetPeriods.counts(b, tx("招商", "1234")))
        assertFalse(BudgetPeriods.counts(b, tx(null, null)))
        assertFalse(BudgetPeriods.counts(b, tx("招商银行", "1234", TransactionType.LENT)))
    }
}
