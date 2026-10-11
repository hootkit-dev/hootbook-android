package io.github.hootkit_dev.hootbook.presentation.core.sync

import dev.hootkit.core.*
import org.junit.Assert.*
import org.junit.Test

class CoreConflictPresentationTest {
    private fun conflict(table: Table, vararg fields: String) = SyncConflict(1, LocalDateTime(2026, 10u, 11u, 0u, 0u, 0u, 0u), table,
        "record-string", ConflictKind.BOTH_EDITED, true, null, fields.map { ConflictField(it, "base", "mine", "theirs") }, "{}", "{}")
    @Test fun transactionAmountCurrencyAndAccountsStayOneDisplayChoice() {
        val groups = conflictGroups(conflict(Table.TRANSACTIONS, "amount", "currency", "account_id", "merchant", "category_id", "subcategory_id"))
        assertEquals(listOf(listOf("amount", "currency", "account_id"), listOf("merchant"), listOf("category_id", "subcategory_id")), groups.map { it.map { f -> f.column } })
    }
    @Test fun accountIdentityAndCreditConfigurationAreSeparateGroups() {
        val groups = conflictGroups(conflict(Table.ACCOUNTS, "last4", "name", "kind", "due_day", "icon"))
        assertEquals(listOf(2, 2, 1), groups.map { it.size })
    }
    @Test fun allSpecifiedTablesPreserveEveryCoreFieldExactlyOnce() {
        for (table in listOf(Table.BUDGETS, Table.SUBSCRIPTIONS, Table.DEBTS, Table.QUICK_TEMPLATES, Table.PEOPLE)) {
            val fields = listOf("kind", "amount", "currency", "period", "anchor_date", "end_date", "name", "unknown_future_column")
            val groups = conflictGroups(conflict(table, *fields.toTypedArray())).flatten()
            assertEquals(fields.toSet(), groups.map { it.column }.toSet()); assertEquals(fields.size, groups.size)
        }
    }
    @Test fun unrecognizedColumnsRemainIndependentAndNoValuesAreMergedInUi() {
        val original = conflict(Table.PEOPLE, "name", "new_column"); val groups = conflictGroups(original)
        assertEquals(2, groups.size); assertEquals(original.fields, groups.flatten())
    }
}
