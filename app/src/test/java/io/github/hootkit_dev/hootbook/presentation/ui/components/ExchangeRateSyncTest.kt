package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.data.currency.RateFailure
import io.github.hootkit_dev.hootbook.data.currency.model.CurrencyConversion
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRateSyncTest {
    // Relative to CNY: 1 CNY = 0.14 USD = 1.1 HKD
    private val rates = mapOf("CNY" to BigDecimal.ONE, "USD" to BigDecimal("0.14"), "HKD" to BigDecimal("1.1"))

    @Test fun `any pair converts through the base`() {
        assertEquals(0, BigDecimal("0.14").compareTo(convertRate(rates, "CNY", "USD")))
        assertEquals(0, BigDecimal("7.85714").compareTo(convertRate(rates, "USD", "HKD")!!.round(java.math.MathContext(6))))
        assertNull(convertRate(rates, "USD", "EUR"))
    }

    @Test fun `account currencies first, crypto apart`() {
        val conversions = listOf("USD", "HKD", "EUR", "BTC", "1INCH").map { CurrencyConversion(it, 1.0) }
        val groups = groupRates(conversions, accountCurrencies = setOf("USD", "HKD"))
        assertEquals(listOf("USD", "HKD"), groups.accounts.map { it.currencyCode })
        assertEquals(listOf("EUR"), groups.fiat.map { it.currencyCode })
        assertEquals(listOf("BTC", "1INCH"), groups.other.map { it.currencyCode })
        assertTrue(isFiat("cny"))
    }

    @Test fun `network errors read as timeouts or unreachable`() {
        assertEquals(RateFailure.Timeout, RateFailure.of(SocketTimeoutException("read timed out")))
        assertEquals(RateFailure.Unreachable, RateFailure.of(RuntimeException(UnknownHostException("open.er-api.com"))))
        assertTrue(RateFailure.of(IllegalStateException("boom")) is RateFailure.Other)
    }
}
