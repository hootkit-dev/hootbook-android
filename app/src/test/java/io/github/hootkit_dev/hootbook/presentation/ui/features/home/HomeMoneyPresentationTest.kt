package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HomeMoneyPresentationTest {
    @Test fun homeUsesOneCurrencyLabelWithoutChangingPrecision() {
        val amount = BigDecimal("89857.260000001")
        val formatted = HomeMoneyPresentation.format(amount, "CNY", locale = Locale.US)
        assertTrue(formatted.endsWith("89,857.260000001"))
        assertFalse(formatted.contains(" · "))
        assertFalse(formatted.endsWith("CNY"))
    }

    @Test fun foreignCurrencyUsesCodeOnceEvenWhenSymbolsAreAmbiguous() {
        assertEquals("USD 12.30", HomeMoneyPresentation.format(BigDecimal("12.30"), "USD", locale = Locale.US, useCode = true))
        assertEquals("CAD 12.30", HomeMoneyPresentation.format(BigDecimal("12.30"), "CAD", locale = Locale.US, useCode = true))
    }

    @Test fun customCurrencyAndUnavailableAmountsRemainExplicit() {
        assertEquals("★ 7.125", HomeMoneyPresentation.format(BigDecimal("7.125"), "PTS", mapOf("PTS" to "★"), Locale.US))
        assertEquals("PTS 7.125", HomeMoneyPresentation.format(BigDecimal("7.125"), "PTS", mapOf("PTS" to ""), Locale.US))
        assertEquals("— CNY", HomeMoneyPresentation.format(null, "CNY", locale = Locale.CHINA))
        assertEquals("XYZ 7", HomeMoneyPresentation.format(BigDecimal("7"), "XYZ", locale = Locale.US))
    }

    @Test fun chineseFormattingRetainsZeroesAndNegativeAmounts() {
        val text = HomeMoneyPresentation.format(BigDecimal("-12.3400"), "CNY", locale = Locale.CHINA)
        assertTrue(text.endsWith("-12.3400"))
        assertFalse(text.contains(" · "))
    }
}
