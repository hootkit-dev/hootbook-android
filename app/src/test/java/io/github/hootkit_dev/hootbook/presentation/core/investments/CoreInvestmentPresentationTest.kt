package io.github.hootkit_dev.hootbook.presentation.core.investments

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreInvestmentPresentationTest {
    @Test fun exactDecimalDisplayPreservesFractionsShortsAndMissingFields() {
        assertEquals("-0.1250", investmentDecimal(BigDecimal("-0.1250")))
        assertEquals("12345678901234567890.001", investmentDecimal(BigDecimal("12345678901234567890.001")))
        assertEquals("—", investmentDecimal(null))
    }
    @Test fun snapshotDatesAndSuccessTimeAreDifferentValues() {
        assertEquals("2026-10-01", investmentDate(LocalDate(2026, 10u, 1u)))
        assertEquals("2026-10-11 09:05:03", investmentTime(LocalDateTime(2026, 10u, 11u, 9u, 5u, 3u, 0u)))
        assertEquals("—", investmentTime(null))
    }
    @Test fun sameInstrumentInDifferentModelsOrCurrenciesHasDistinctSelectionIdentity() {
        val holding = Holding("42", "ABC", "Example", "USD", BigDecimal.ONE, null, null, null, "STK", "A")
        assertNotEquals(CoreHoldingKey.of(holding), CoreHoldingKey.of(holding.copy(model = "B")))
        assertNotEquals(CoreHoldingKey.of(holding), CoreHoldingKey.of(holding.copy(currency = "HKD")))
    }
    @Test fun brokerAccountNumbersAreScopedToTheirConnections() {
        val first = investmentConnection(1, "100")
        val second = investmentConnection(2, "200")
        assertEquals(BigDecimal("200"), linkedAccount(BrokerAccountRef(2, "U1"), listOf(first, second))!!.total!!.amount)
        assertNull(linkedAccount(BrokerAccountRef(3, "U1"), listOf(first, second)))
    }
    @Test fun nullCoreTotalRemainsNullAndIncompleteDoesNotBecomeZero() {
        val account = investmentConnection().accounts.single().copy(total = null, complete = false)
        val state = CoreInvestmentsUiState(connections = listOf(investmentConnection().copy(accounts = listOf(account))), selected = BrokerAccountRef(1, "U1"))
        assertNull(state.account!!.total)
        assertFalse(state.account!!.complete)
        assertEquals(BigDecimal("125.50"), state.account!!.valueByCurrency.single().amount)
    }
    @Test fun failureMappingNeverIncludesCredentialUrlOrRawStorageDetail() {
        val secret = "fixture-secret-token"
        listOf(CoreException.Platform(secret), CoreException.Storage(StorageErrorKind.IO, secret),
            CoreException.Invalid(secret, InvalidReason.UNKNOWN_VALUE), IllegalStateException("https://host/?token=$secret")).forEach {
            val failure = investmentFailure(it)
            assertFalse(failure.toString().contains(secret))
            assertFalse(failure.toString().contains("https://"))
        }
    }
    @Test fun everyTypedBrokerFailureKeepsOnlyTheEnumKind() {
        BrokerageErrorKind.entries.forEach { kind ->
            assertEquals(CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, kind), investmentFailure(CoreException.Brokerage(kind)))
        }
    }
    @Test fun credentialSubmissionIsRedactedAndReleasesOnlyItsPrivateCopy() {
        val original = mutableMapOf("token" to "123456789", "queryId" to "1")
        val submission = InvestmentCredentialSubmission(original)
        val privateReference = submission.fields()
        assertFalse(submission.toString().contains("123456789"))
        submission.close(); submission.close()
        assertTrue(privateReference.isEmpty())
        assertEquals("123456789", original["token"])
    }
}

internal fun investmentConnection(id: Long = 1, total: String = "125.50") = BrokerageConnection(
    id, "ibkr_flex", "Fixture", LocalDateTime(2026, 10u, 11u, 9u, 0u, 0u, 0u), null,
    listOf(BrokerageAccount("U1", LocalDate(2026, 10u, 10u),
        listOf(Holding("42", "ABC", "Example", "USD", BigDecimal("0.125"), BigDecimal(total), null, null, "STK", "")),
        listOf(CashBalance("USD", BigDecimal("-5"), null)), null,
        listOf(Money(BigDecimal("125.50"), "USD")), Money(BigDecimal(total), "USD"), true)),
)
