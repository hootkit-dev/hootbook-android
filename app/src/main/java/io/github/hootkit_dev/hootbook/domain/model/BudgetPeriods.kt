package io.github.hootkit_dev.hootbook.domain.model

import io.github.hootkit_dev.hootbook.data.database.entity.BudgetEntity
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetPeriod
import io.github.hootkit_dev.hootbook.data.database.entity.BudgetType
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A budget period: whole days, from the start of [start] to the end of [end]. */
data class BudgetWindow(val start: LocalDate, val end: LocalDate) {
    val from: LocalDateTime get() = start.atStartOfDay()
    val to: LocalDateTime get() = end.atTime(LocalTime.MAX)
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(time: LocalDateTime) = !time.toLocalDate().isBefore(start) && !time.toLocalDate().isAfter(end)
}

/**
 * Which days a budget covers. A repeating budget rolls on from the day it starts: a monthly
 * one begun on the 5th runs 5th to 4th, month after month (a short month uses its last day),
 * so it never expires. A custom budget covers its own dates, both included.
 */
object BudgetPeriods {
    private fun anchor(budget: BudgetEntity) = budget.startDate.toLocalDate()

    private fun startOf(budget: BudgetEntity, n: Long): LocalDate {
        val anchor = anchor(budget)
        fun monthsOn(months: Long): LocalDate {
            val month = YearMonth.from(anchor).plusMonths(months)
            return month.atDay(anchor.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        }
        return when (budget.periodType) {
            BudgetPeriod.DAILY -> anchor.plusDays(n)
            BudgetPeriod.WEEKLY -> anchor.plusWeeks(n)
            BudgetPeriod.MONTHLY -> monthsOn(n)
            BudgetPeriod.YEARLY -> monthsOn(n * 12)
            BudgetPeriod.CUSTOM -> anchor
        }
    }

    /** The period that holds [day]; days before the budget starts get its first period. */
    fun containing(budget: BudgetEntity, day: LocalDate): BudgetWindow {
        val anchor = anchor(budget)
        if (budget.periodType == BudgetPeriod.CUSTOM) {
            return BudgetWindow(anchor, budget.endDate.toLocalDate().coerceAtLeast(anchor))
        }
        var n = when (budget.periodType) {
            BudgetPeriod.DAILY -> ChronoUnit.DAYS.between(anchor, day)
            BudgetPeriod.WEEKLY -> ChronoUnit.WEEKS.between(anchor, day)
            BudgetPeriod.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(day))
            else -> ChronoUnit.YEARS.between(YearMonth.from(anchor), YearMonth.from(day))
        }.coerceAtLeast(0)
        while (n > 0 && startOf(budget, n).isAfter(day)) n--
        while (!startOf(budget, n + 1).isAfter(day)) n++
        return BudgetWindow(startOf(budget, n), startOf(budget, n + 1).minusDays(1))
    }

    fun current(budget: BudgetEntity, today: LocalDate = LocalDate.now()) = containing(budget, today)

    /** The current period and those before it, newest first, back to the first (at most [max]). */
    fun history(budget: BudgetEntity, today: LocalDate = LocalDate.now(), max: Int = 24): List<BudgetWindow> {
        val periods = mutableListOf(current(budget, today))
        while (periods.size < max && budget.periodType != BudgetPeriod.CUSTOM) {
            val first = periods.last().start
            if (!first.isAfter(anchor(budget))) break
            periods += containing(budget, first.minusDays(1))
        }
        return periods
    }

    /** Whether [transaction] counts towards [budget] (period aside). */
    fun counts(budget: BudgetEntity, transaction: TransactionEntity): Boolean {
        if (transaction.isDeleted) return false
        val typeMatches = when (budget.budgetType) {
            // Lending is not spending, and borrowing is not income
            BudgetType.EXPENSE -> transaction.transactionType == TransactionType.EXPENSE ||
                transaction.transactionType == TransactionType.CREDIT
            BudgetType.SAVINGS -> transaction.transactionType == TransactionType.INCOME
        }
        if (!typeMatches) return false
        if (budget.accountIds.isNotEmpty()) {
            // Exactly the chosen accounts: an entry with no account is in none of them
            val bank = transaction.bankName ?: return false
            val last4 = transaction.accountNumber?.takeLast(4) ?: return false
            if ("$bank:$last4" !in budget.accountIds) return false
        }
        return true
    }
}
