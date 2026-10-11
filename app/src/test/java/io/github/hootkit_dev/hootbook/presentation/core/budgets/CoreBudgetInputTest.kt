package io.github.hootkit_dev.hootbook.presentation.core.budgets

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreBudgetInputTest {
    @Test fun presentationProgressIsClampedWithoutRoundingMonetaryValues() {
        assertEquals(0.5f, budgetProgressFraction(BigDecimal("50"), BigDecimal("100")))
        assertEquals(1f, budgetProgressFraction(BigDecimal("200"), BigDecimal("100")))
        assertEquals(0f, budgetProgressFraction(BigDecimal("-1"), BigDecimal("100")))
        assertEquals(0f, budgetProgressFraction(BigDecimal.ONE, BigDecimal.ZERO))
    }
    @Test fun creationPreservesExactMoneyStringIdsAndDefaultName() {
        val input = CoreBudgetDraft(name = " ", amount = "123456789012345.123456789", currency = " usd ",
            start = "2026-10-10", allAccounts = false, accountIds = setOf("account-uuid")).newBudget("月度预算")
        assertEquals("月度预算", input.name)
        assertEquals("USD", input.amount.currency)
        assertEquals(BigDecimal("123456789012345.123456789"), input.amount.amount)
        assertEquals(listOf("account-uuid"), input.accountIds)
        assertNull(input.end)
    }
    @Test fun unchangedAnchorNotPassedButExplicitDateChangeIs() {
        val draft = CoreBudgetDraft(id = "budget-uuid", amount = "100", start = "2026-01-31", originalStart = "2026-01-31")
        assertNull(draft.patch("预算").start)
        assertEquals(budgetDate("2026-02-28"), draft.copy(start = "2026-02-28").patch("预算").start)
    }
    @Test fun customEndIncludedAndRepeatingEndOmitted() {
        val draft = CoreBudgetDraft(amount = "100", start = "2026-10-01", end = "2026-10-31", period = BudgetPeriod.CUSTOM)
        assertEquals(budgetDate("2026-10-31"), draft.newBudget("预算").end)
        assertEquals(budgetDate("2026-10-31"), draft.patch("预算").end)
        assertNull(draft.copy(period = BudgetPeriod.DAILY, end = "not-a-date").patch("预算").end)
    }
    @Test fun allAccountScopeIsExplicitAndEmptySelectedScopeNotChangedToAll() {
        val draft = CoreBudgetDraft(amount = "100")
        assertNull(draft.newBudget("预算").accountIds)
        assertTrue(draft.patch("预算").allAccounts)
        val empty = draft.copy(allAccounts = false)
        assertEquals(emptyList<String>(), empty.newBudget("预算").accountIds)
        assertFalse(empty.patch("预算").allAccounts)
        assertEquals(emptyList<String>(), empty.patch("预算").accountIds)
    }
    @Test fun invalidBoundaryTextFailsInsteadOfCoercingMoneyOrDates() {
        assertThrows(IllegalArgumentException::class.java) { budgetAmount("garbage") }
        assertThrows(IllegalArgumentException::class.java) { budgetDate("2026-02-30") }
        assertThrows(IllegalArgumentException::class.java) { budgetDate(" ") }
        assertEquals(BigDecimal("-4"), budgetAmount("-4")) // Core, not UI, rejects non-positive budget amounts.
    }
}
