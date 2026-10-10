package io.github.hootkit_dev.hootbook.data.currency

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRateParsersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `open er-api keeps its update times and adds nothing for the base`() {
        val body = """{"result":"success","time_last_update_unix":1790985752,"time_next_update_unix":1791073102,
            "base_code":"USD","rates":{"USD":1,"CNY":6.7046,"HKD":7.8471}}"""
        val parsed = parseOpenErApi(json, body, "USD")!!
        assertEquals(1790985752L, parsed.lastUpdateTimeUnix)
        assertEquals(1791073102L, parsed.nextUpdateTimeUnix)
        assertEquals(0, BigDecimal("6.7046").compareTo(parsed.rates["CNY"]))
        assertEquals(0, BigDecimal.ONE.compareTo(parsed.rates["USD"]))
        assertEquals(PROVIDER_OPEN_ER_API, parsed.provider)
    }

    @Test fun `open er-api errors are not rates`() {
        assertNull(parseOpenErApi(json, """{"result":"error","error-type":"unsupported-code"}""", "XYZ"))
    }

    @Test fun `frankfurter adds the base and stays current after a lagging date`() {
        val body = """{"amount":1.0,"base":"USD","date":"2026-10-02","rates":{"CNY":6.7046,"JPY":157.67}}"""
        val parsed = parseFrankfurter(json, body, "USD")!!
        assertEquals(0, BigDecimal.ONE.compareTo(parsed.rates["USD"]))
        assertEquals(0, BigDecimal("157.67").compareTo(parsed.rates["JPY"]))
        assertTrue(parsed.nextUpdateTimeUnix >= parsed.lastUpdateTimeUnix + 24 * 3600L)
        assertTrue(parsed.nextUpdateTimeUnix > System.currentTimeMillis() / 1000)
    }

    @Test fun `currency-api codes are upper-cased and bad values skipped`() {
        val body = """{"date":"2026-10-02","usd":{"cny":6.7046,"btc":"x","zero":0}}"""
        val parsed = parseFawaz(json, body, "USD")!!
        assertEquals(setOf("USD", "CNY"), parsed.rates.keys)
    }
}
