package io.github.hootkit_dev.hootbook.presentation.core.debts

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreDebtFormsTest {
    private val at = LocalDateTime(2026, 10u, 10u, 12u, 30u, 0u, 0u)
    @Test fun personEditPreservesEveryEditableField() {
        val row = Person("person-uuid", "名字", "123", "备注", "#AABBCC", "avatar/existing.png", PersonRelation.COLLEAGUE,
            true, PersonTotals("CNY", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, false, true, emptyList()), at)
        assertEquals(PersonDraft(row.name, row.phone, row.notes, row.color, row.avatar, row.relation), CorePersonForm.from(row).toDraft())
        assertEquals(row.id, CorePersonForm.from(row).id)
    }
    @Test fun debtEditPreservesLinksAndAttachmentsWithoutIntegerIdMapping() {
        val row = DebtEntry("debt-uuid", "person-uuid", DebtKind.LENT, Money(BigDecimal("12.34"), "USD"), at,
            LocalDate(2027, 1u, 2u), "现有", "transaction-uuid", "account-uuid", listOf("existing/a.png", "existing/b.pdf"))
        val form = CoreDebtForm.from(row)
        val draft = form.copy(title = "更新标题").toDraft()
        assertEquals(row.id, form.id)
        assertEquals(row.personId, draft.personId)
        assertEquals(row.accountId, draft.accountId)
        assertEquals(row.amount.amount, draft.amount)
        assertEquals(row.amount.currency, draft.currency)
        assertEquals(row.date, draft.date)
        assertEquals(row.dueDate, draft.dueDate)
        assertEquals(row.attachments, draft.attachments)
    }
    @Test fun inputParsingAllowsBlankCurrencyForCoreDecision() {
        val draft = CoreDebtForm(personId = "p", amount = " 17.25 ", currency = " ", date = "2026-10-10T12:30", title = "借款").toDraft()
        assertNull(draft.currency)
        assertNull(draft.accountId)
        assertNull(draft.dueDate)
        assertEquals(BigDecimal("17.25"), draft.amount)
    }
    @Test fun fullSettlementDelegatesAllAmountAndDirectionToCore() {
        val draft = CoreSettlementForm("p", amount = "not-a-number", currency = "CNY", accountId = "account-uuid", title = "结清").toDraft()
        assertNull(draft.amount)
        assertEquals("account-uuid", draft.accountId)
    }
    @Test fun partialSettlementPreservesExplicitAmountCurrency() {
        val draft = CoreSettlementForm("p", fullAmount = false, amount = "2.30", currency = "USD", title = "偿还").toDraft()
        assertEquals(Money(BigDecimal("2.30"), "USD"), draft.amount)
    }
    @Test fun malformedInputFailsAtParsingBoundary() {
        val form = CoreDebtForm(personId = "p", amount = "10", date = "bad", title = "借款")
        assertTrue(runCatching { form.toDraft() }.isFailure)
        assertTrue(runCatching { form.copy(date = "2026-10-10T12:30", dueDate = "2026-02-30").toDraft() }.isFailure)
        assertTrue(runCatching { form.copy(amount = "bad").toDraft() }.isFailure)
    }
    @Test fun noLocalBusinessValidationSubstitutesForCore() {
        val draft = CoreDebtForm(personId = "p", kind = DebtKind.REPAID_BY_ME, amount = "-5", date = "2026-10-10T12:30", dueDate = "2026-10-10").toDraft()
        // Invalid amount/kind/due-date is passed through for generated Core validation.
        assertEquals(BigDecimal("-5"), draft.amount)
        assertNotNull(draft.dueDate)
    }
}
