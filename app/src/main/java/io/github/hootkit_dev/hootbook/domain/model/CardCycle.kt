package io.github.hootkit_dev.hootbook.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** A credit card's statement closing day and payment due day, as days of the month. */
data class CardDates(val statementDay: Int? = null, val dueDay: Int? = null)

/**
 * A credit card's billing cycle. A day past the end of a short month means its last day, so
 * "the 31st" closes on 30 September and 28 February.
 */
object CardCycle {
    fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The latest statement closing on or before [today]. */
    fun lastClosing(statementDay: Int, today: LocalDate): LocalDate {
        val month = YearMonth.from(today)
        val thisMonth = dayIn(month, statementDay)
        return if (!thisMonth.isAfter(today)) thisMonth else dayIn(month.minusMonths(1), statementDay)
    }

    /** The first statement closing after [today]. */
    fun nextClosing(statementDay: Int, today: LocalDate): LocalDate =
        dayIn(YearMonth.from(lastClosing(statementDay, today)).plusMonths(1), statementDay)

    /**
     * When the statement closing on [closing] is due: the next [dueDay] after it. A due day after
     * the statement day falls in the same month, even when a short month squeezes both onto its
     * last day (statement the 30th, due the 31st, in February: both the 28th).
     */
    fun dueAfter(closing: LocalDate, dueDay: Int, statementDay: Int = closing.dayOfMonth): LocalDate {
        val month = YearMonth.from(closing)
        val sameMonth = dayIn(month, dueDay)
        return if (dueDay > statementDay || sameMonth.isAfter(closing)) sameMonth else dayIn(month.plusMonths(1), dueDay)
    }

    /** The next payment due date on or after [today]. */
    fun nextDue(dueDay: Int, today: LocalDate): LocalDate {
        val month = YearMonth.from(today)
        val thisMonth = dayIn(month, dueDay)
        return if (!thisMonth.isBefore(today)) thisMonth else dayIn(month.plusMonths(1), dueDay)
    }
}
