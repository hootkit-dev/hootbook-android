package io.github.hootkit_dev.hootbook.presentation.core.accounts

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreAccountFormsTest {
    @Test fun creationTrimsBoundaryTextAndKeepsAmountsExact() {
        val input = CoreAccountDraft(name = "  钱包  ", kind = AccountKind.WALLET, last4 = "1234",
            currency = " usd ", openingBalance = "-123456789012345.123456789").newAccount()
        assertEquals("钱包", input.name)
        assertNull(input.last4)
        assertEquals("USD", input.mainCurrency)
        assertEquals(BigDecimal("-123456789012345.123456789"), input.openingBalance)
        assertNull(input.openingAt)
    }
    @Test fun invalidAmountIsNotSilentlyConvertedToZero() {
        assertThrows(IllegalArgumentException::class.java) { CoreAccountDraft(openingBalance = "x").newAccount() }
        assertThrows(IllegalArgumentException::class.java) { requiredAmount(" ") }
        assertNull(optionalAmount(" "))
    }
    @Test fun validNegativeCalibrationIsPreserved() { assertEquals(BigDecimal("-12.75"), requiredAmount("-12.75")) }
    @Test fun datesAreStrictAndPreserveNanoseconds() {
        val time = optionalDateTime("2026-10-10T12:30:59.123456789")!!
        assertEquals(123456789u, time.nanosecond)
        assertEquals("2026-10-10T12:30:59.123456789", time.inputText())
        assertThrows(IllegalArgumentException::class.java) { optionalDateTime("2026-02-30T12:30:00") }
    }
    @Test fun billingDaysRejectWrappingAndOutOfRangeValues() {
        assertEquals(31.toUByte(), optionalDay("31"))
        listOf("0", "32", "257", "x", "-1").forEach { input -> assertThrows(IllegalArgumentException::class.java) { optionalDay(input) } }
    }
    @Test fun editExplicitlyClearsOptionalFieldsAndResetsBillingBeforeApplying() {
        val patch = CoreAccountDraft(id = "account-uuid", kind = AccountKind.CREDIT_CARD, statementDay = "", dueDay = "25").patch()
        assertTrue(patch.clearLast4)
        assertTrue(patch.clearInstitution)
        assertTrue(patch.clearCreditLimit)
        assertTrue(patch.clearBillingDays)
        assertNull(patch.statementDay)
        assertEquals(25.toUByte(), patch.dueDay)
    }
    @Test fun switchingAwayFromCreditCardOmitsCreditFields() {
        val input = CoreAccountDraft(kind = AccountKind.BANK, creditLimit = "garbage", dueDay = "bad").newAccount()
        assertNull(input.creditLimit)
        assertNull(input.dueDay)
        val walletPatch = CoreAccountDraft(kind = AccountKind.WALLET, last4 = "1234").patch()
        assertNull(walletPatch.last4)
        assertTrue(walletPatch.clearLast4)
    }
    @Test fun coreConflictExplanationPreservesDestructiveChoice() {
        val message = coreFailureText(CoreException.Conflict(ConflictReason.IN_USE))
        assertTrue(message.contains("回收站"))
        assertTrue(message.contains("归档"))
    }
}
