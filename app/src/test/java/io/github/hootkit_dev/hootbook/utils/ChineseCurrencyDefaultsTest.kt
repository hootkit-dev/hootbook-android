package io.github.hootkit_dev.hootbook.utils

import io.github.hootkit_dev.hootbook.data.model.Currency
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferences
import io.github.hootkit_dev.hootbook.presentation.common.CurrencyGroupedTotals
import io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding.OnBoardingUiState
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ChineseCurrencyDefaultsTest {
    @Test
    fun `fresh preferences and onboarding start in yuan`() {
        assertEquals("CNY", UserPreferences().baseCurrency)
        assertEquals("CNY", OnBoardingUiState().selectedCurrency)
        assertEquals("USD", UserPreferences(baseCurrency = "USD").baseCurrency)
    }

    @Test
    fun `existing implicit rupee preference survives upgrade`() {
        assertEquals("INR", io.github.hootkit_dev.hootbook.data.preferences.resolveInitialBaseCurrency(null, true))
        assertEquals("CNY", io.github.hootkit_dev.hootbook.data.preferences.resolveInitialBaseCurrency(null, false))
        assertEquals("USD", io.github.hootkit_dev.hootbook.data.preferences.resolveInitialBaseCurrency("USD", true))
        assertEquals("HKD", io.github.hootkit_dev.hootbook.data.preferences.resolveInitialBaseCurrency("HKD", false))
    }

    @Test
    fun `yuan is prioritized without hiding existing currencies or changing a preference`() {
        assertEquals(listOf("CNY", "CNY", "EUR", "INR", "USD"),
            CurrencyUtils.sortCurrencies(listOf("USD", "CNY", "INR", "EUR", "CNY")))
        val totals = CurrencyGroupedTotals(availableCurrencies = listOf("USD", "CNY", "INR"))
        assertEquals("CNY", totals.getPrimaryCurrency())
        assertEquals("INR", totals.getPrimaryCurrency("INR"))
        assertEquals("USD", CurrencyGroupedTotals(availableCurrencies = listOf("USD")).getPrimaryCurrency())
        assertEquals("CNY", CurrencyGroupedTotals().getPrimaryCurrency())
    }

    @Test
    fun `formatting defaults to yuan and preserves explicit foreign currencies`() {
        val amount = BigDecimal("1234.50")
        assertEquals(CurrencyFormatter.formatCurrency(amount, "CNY"), CurrencyFormatter.formatCurrency(amount))
        assertTrue(CurrencyFormatter.formatCurrency(amount).startsWith("¥"))
        assertTrue(CurrencyFormatter.formatCurrency(amount, "USD").startsWith("$"))
        assertTrue(CurrencyFormatter.formatCurrency(amount, "INR").contains("₹"))
        assertTrue(CurrencyFormatter.formatCurrency(amount, "HKD").startsWith("HK$"))
        assertTrue(CurrencyFormatter.formatCurrency(amount, "SGD").startsWith("S$"))
        assertEquals("1,234.50", CurrencyFormatter.formatAmount(amount))
        assertEquals("0.50", CurrencyFormatter.formatAmount(BigDecimal("0.5")))
        assertEquals("0.00", CurrencyFormatter.formatAmount(BigDecimal.ZERO))
    }

    @Test
    fun `institutions give their region's currency`() {
        assertEquals("USD", CurrencyFormatter.getBankBaseCurrency("AMEX US"))
        assertEquals("HKD", CurrencyFormatter.getBankBaseCurrency("HSBC HK"))
        assertEquals("SGD", CurrencyFormatter.getBankBaseCurrency("DBS Singapore"))
        assertEquals("CNY", CurrencyFormatter.getBankBaseCurrency("中国建设银行"))
    }

    @Test
    fun `regional currencies are available offline and have Chinese display names`() {
        listOf("CNY", "HKD", "USD", "SGD", "TWD", "MOP").forEach { code ->
            assertNotNull(Currency.getByCode(code))
            assertTrue(Currency.POPULAR_CURRENCY_CODES.contains(code))
        }
        assertTrue(Currency.getByCode("CNY")!!.localizedName(Locale.CHINA).contains("人民币"))
        assertEquals("My points", Currency("POINTS", "My points", "P").localizedName(Locale.CHINA))
    }
}
