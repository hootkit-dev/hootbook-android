package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import dev.hootkit.core.Transaction
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import java.time.LocalDate

/** Visual sections only. Core supplies ordering, filtering and every total. */
internal fun groupTransactionsByDate(transactions: List<Transaction>): List<Pair<DateGroup, List<Transaction>>> {
    val today = LocalDate.now()
    val sections = mutableListOf<Pair<DateGroup, MutableList<Transaction>>>()
    transactions.forEach { transaction ->
        val date = transaction.dateTime.toJavaDateTime().toLocalDate()
        val group = when {
            date == today -> DateGroup.TODAY
            date == today.minusDays(1) -> DateGroup.YESTERDAY
            date > today.minusWeeks(1) -> DateGroup.THIS_WEEK
            else -> DateGroup.EARLIER
        }
        // Only adjacent rows share a header. This preserves Core's global order for every sort.
        if (sections.lastOrNull()?.first != group) sections += group to mutableListOf<Transaction>()
        sections.last().second += transaction
    }
    return sections
}
