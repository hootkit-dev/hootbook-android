package io.github.hootkit_dev.hootbook.presentation.core.ai

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreAiDraftTest {
    @Test fun decimalDateAndOpaqueExistingIdsRoundTripWithoutLongMapping() {
        val original = CoreAiViewModelTest.draft(); val parsed = CoreAiDraftForm.from(original).toDraft()
        assertEquals(original, parsed); assertEquals(AiAccountRef.Existing("account-string"), parsed.account)
    }
    @Test fun newReferencesAndCrossCurrencyTransferRemainExplicitWithoutUiFx() {
        val parsed = CoreAiDraftForm.from(CoreAiViewModelTest.draft()).copy(kind = TransactionKind.TRANSFER, account = AiAccountRef.New("N9"),
            toAccount = AiAccountRef.Existing("destination-uuid"), toCurrency = "USD", toAmount = "15.42").toDraft()
        assertEquals(AiAccountRef.New("N9"), parsed.account); assertEquals("USD", parsed.toCurrency); assertEquals(BigDecimal("15.42"), parsed.toAmount)
    }
    @Test fun parseErrorsDoNotInventZeroOrCurrentDate() {
        assertThrows(NumberFormatException::class.java) { CoreAiDraftForm.from(CoreAiViewModelTest.draft()).copy(amount = "not-a-number").toDraft() }
        assertThrows(java.time.format.DateTimeParseException::class.java) { CoreAiDraftForm.from(CoreAiViewModelTest.draft()).copy(dateTime = "not-a-date").toDraft() }
    }
    @Test fun apiKeyDraftNeverAppearsInToString() { assertFalse(CoreAiConfigDraft(apiKey = "private_key").toString().contains("private_key")) }
    @Test fun platformAndUnknownErrorsNeverExposeExceptionBodyOrSecrets() {
        assertFalse(aiFailureText(CoreException.Platform("private_key")).contains("private_key"))
        assertFalse(aiFailureText(IllegalStateException("Bearer private_key")).contains("private_key"))
    }
    @Test fun knownAiFailuresAndRateLimitHaveActionableDescriptions() {
        AiFailure.entries.forEach { assertTrue(aiFailureText(CoreException.Ai(it)).isNotBlank()) }
        assertTrue(aiFailureText(CoreException.Network(429.toUShort())).contains("rate"))
        assertTrue(aiFailureText(CoreException.Cancelled()).contains("cancelled"))
    }
}
