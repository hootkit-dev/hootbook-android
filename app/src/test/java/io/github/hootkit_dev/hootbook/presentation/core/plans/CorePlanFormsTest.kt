package io.github.hootkit_dev.hootbook.presentation.core.plans

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CorePlanFormsTest {
    @Test fun subscriptionDraftPreservesCoreIdsDecimalAndCycleWithoutProgression() {
        val draft = CoreSubscriptionForm(name = "  季度会员  ", amount = "30.12345", currency = "USD", every = "3",
            nextDue = "2026-01-31", endDate = "2027-06-30", accountId = "account-uuid", categoryId = "cat-uuid", subcategoryId = "sub-uuid").toDraft()
        assertEquals("季度会员", draft.name); assertEquals(BigDecimal("30.12345"), draft.amount.amount)
        assertEquals(Cycle(3u, CycleUnit.MONTH), draft.cycle)
        assertEquals(LocalDate(2026, 1u, 31u), draft.nextDue); assertEquals("account-uuid", draft.accountId)
        assertEquals("sub-uuid", draft.subcategoryId)
    }
    @Test fun blankSubscriptionDatesAndNotesBecomeNullNotToday() {
        val draft = CoreSubscriptionForm(amount = "2", nextDue = " ", endDate = "", notes = " ").toDraft()
        assertNull(draft.nextDue); assertNull(draft.endDate); assertNull(draft.notes)
    }
    @Test fun zeroCycleIsSentToCoreForValidationNotSilentlyReplaced() {
        assertEquals(0u, CoreSubscriptionForm(amount = "2", every = "0").toDraft().cycle.every)
    }
    @Test fun invalidDateIsRejectedAtInputBoundary() {
        assertThrows(java.time.DateTimeException::class.java) { CoreSubscriptionForm(amount = "2", nextDue = "2026-02-30").toDraft() }
    }
    @Test fun unknownCycleIsPreservedForExplicitUserCorrection() {
        val row = planSubscription().copy(cycle = Cycle(2u, CycleUnit.UNKNOWN))
        assertEquals(CycleUnit.UNKNOWN, CoreSubscriptionForm.from(row).unit)
        assertEquals("2", CoreSubscriptionForm.from(row).every)
    }
    @Test fun templateDraftKeepsAmountAndPrefillFlagIndependent() {
        val draft = CoreTemplateForm(name = "早餐", merchant = "商户", amount = "21.50", prefillAmount = false,
            accountId = "uuid", categoryId = "cat-uuid", subcategoryId = "sub-uuid").toDraft()
        assertEquals(BigDecimal("21.50"), draft.amount); assertFalse(draft.prefillAmount)
        assertEquals("uuid", draft.accountId); assertEquals("sub-uuid", draft.subcategoryId); assertNull(draft.currency)
    }
    @Test fun suggestionOpensDraftWithoutCreatingOrDismissingAnything() {
        val row = planSuggestion()
        val draft = CoreTemplateForm.from(row)
        assertNull(draft.id); assertEquals(row.amount!!.toPlainString(), draft.amount); assertTrue(draft.prefillAmount)
        assertEquals(row.categoryId, draft.categoryId)
    }
    @Test fun templateReorderingUsesStringIdSequenceAndDoesNotMutateRecords() {
        val rows = listOf(planTemplate("a"), planTemplate("b"), planTemplate("c"))
        assertEquals(listOf("b", "a", "c"), moveTemplateIds(rows, "b", -1))
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
        assertNull(moveTemplateIds(rows, "a", -1)); assertNull(moveTemplateIds(rows, "missing", 1))
    }
}
