package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync

import dev.hootkit.core.*

/** Presentation grouping from Spec06 §7.3. Core alone applies/validates the conflict choice. */
internal fun conflictGroups(conflict: SyncConflict): List<List<ConflictField>> {
    val units = when (conflict.table) {
        Table.ACCOUNTS -> listOf(setOf("name", "last4"), setOf("kind", "credit_limit", "statement_day", "due_day"))
        Table.TRANSACTIONS -> listOf(setOf("kind", "amount", "currency", "account_id", "to_account_id", "to_currency", "to_amount", "receivable", "collected"), setOf("category_id", "subcategory_id"))
        Table.BUDGETS -> listOf(setOf("kind", "amount", "currency"), setOf("period", "anchor_date", "end_date"))
        Table.SUBSCRIPTIONS -> listOf(setOf("amount", "currency"), setOf("every", "unit", "end_date", "next_due", "last_paid"), setOf("category_id", "subcategory_id"))
        Table.DEBTS -> listOf(setOf("kind", "amount", "currency", "due_date"))
        Table.QUICK_TEMPLATES -> listOf(setOf("kind", "category_id", "subcategory_id"), setOf("amount", "prefill_amount"), setOf("account_id", "currency"))
        else -> emptyList()
    }
    val result = mutableListOf<List<ConflictField>>(); val used = mutableSetOf<String>()
    conflict.fields.forEach { field ->
        if (used.add(field.column)) {
            val unit = units.find { field.column in it } ?: setOf(field.column)
            val members = conflict.fields.filter { it.column in unit }; used += members.map { it.column }; result += members
        }
    }
    return result
}
