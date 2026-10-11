package io.github.hootkit_dev.hootbook.presentation.format

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class RestoredMoneyAndClockTest {
    @Test
    fun coreUnsignedClockFieldsDoNotReachJavaFormatterBoxed() {
        assertEquals(String.format("%02d", 7), coreClockPart(7u))
        assertEquals(String.format("%02d", 45), coreClockPart(45u))
    }

    @Test
    fun restoredCardsUseCompactCurrencyLabels() {
        val text = CoreMoneyPresentation.formatCurrency(BigDecimal("25"), "CNY")
        assertTrue(text, text.startsWith("¥"))
        assertFalse(text, text.contains("CNY"))
        assertFalse(text, text.contains("·"))
    }
}
