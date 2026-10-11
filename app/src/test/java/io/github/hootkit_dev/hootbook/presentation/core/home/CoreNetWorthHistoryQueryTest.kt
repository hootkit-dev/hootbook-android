package io.github.hootkit_dev.hootbook.presentation.core.home

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreNetWorthHistoryQueryTest {
    private val today = JavaDate.of(2026, 10, 11)
    private fun decimal(text: String) = BigDecimal(text)
    private fun account(id: String, balance: String, kind: AccountKind = AccountKind.BANK,
        currency: String = "CNY", archived: Boolean = false) = Account(
        id, id, null, kind, currency, listOf(Pocket(currency, decimal(balance), null, null)),
        Money(decimal(balance), "CNY"), true, null, null, null, null, null, null, "", "#336699",
        archived, false, 0, LocalDateTime(2026, 10u, 11u, 0u, 0u, 0u, 0u))
    private fun overview(rows: List<Account>, value: String, complete: Boolean = true) =
        AccountsOverview(rows, Money(decimal(value), "CNY"), complete, null, null)
    private fun days(from: JavaDate, to: JavaDate, amount: (JavaDate) -> BigDecimal) =
        (0..ChronoUnit.DAYS.between(from, to)).map { offset ->
            val date = from.plusDays(offset)
            BalanceDay(LocalDate(date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte()), amount(date))
        }
    private fun query(native: AccountsOverview, values: Map<String, String>, since: JavaDate = today.minusDays(2),
        convert: suspend (BigDecimal, String, String) -> BigDecimal? = { _, _, _ -> error("Unexpected FX") }) =
        CoreNetWorthHistoryQuery({ native }, { since }, { id, _, from, to -> days(from, to) { decimal(values.getValue(id)) } }, convert)
    private fun assertMoney(expected: String, actual: BigDecimal) = assertEquals(0, decimal(expected).compareTo(actual))

    @Test fun nativeNetWorthBaseKeepsBrokerageConstantAndIncludesArchivedAccounts() = runTest {
        val native = overview(listOf(account("bank", "1000"), account("archived", "200", archived = true),
            account("card", "50", AccountKind.CREDIT_CARD)), "1650") // Native brokerage remainder = 500.
        val result = query(native, mapOf("bank" to "900", "archived" to "100", "card" to "40")).load(today, CoreHistoryRange.ALL)
        assertTrue(result.complete)
        assertMoney("1460", result.points.first().amount)
        assertSame(native.netWorth.amount, result.points.last().amount)
        assertTrue(result.points.last().current)
    }

    @Test fun cardOverpaymentIsAnAssetAndNativePreCreationHistoryIsNotZeroed() = runTest {
        val native = overview(listOf(account("card", "-20", AccountKind.CREDIT_CARD)), "20")
        val result = query(native, mapOf("card" to "-50")).load(today, CoreHistoryRange.ALL)
        // Account.createdAt is today; earlier Core-returned history must remain unchanged.
        assertMoney("50", result.points.first().amount)
        assertMoney("20", result.points.last().amount)
        assertEquals(today.minusDays(2), result.points.first().date)
    }

    @Test fun sameCurrencyTransferAndCardRepaymentPreserveTotal() = runTest {
        val transfer = query(overview(listOf(account("a", "900"), account("b", "600")), "1500"),
            mapOf("a" to "1000", "b" to "500")).load(today, CoreHistoryRange.ALL)
        assertTrue(transfer.points.all { it.amount.compareTo(decimal("1500")) == 0 })
        val repayment = query(overview(listOf(account("a", "900"), account("card", "100", AccountKind.CREDIT_CARD)), "800"),
            mapOf("a" to "1000", "card" to "200")).load(today, CoreHistoryRange.ALL)
        assertTrue(repayment.points.all { it.amount.compareTo(decimal("800")) == 0 })
    }

    @Test fun crossCurrencyTransferUsesNativeAmountsAndDoesNotForceNetZero() = runTest {
        val native = overview(listOf(account("cny", "900"), account("usd", "14", currency = "USD")), "998")
        val result = query(native, mapOf("cny" to "1000", "usd" to "0")) { amount, from, to ->
            assertEquals("USD", from); assertEquals("CNY", to)
            when (amount.toPlainString()) { "14" -> decimal("98"); "0" -> BigDecimal.ZERO; else -> error("Unexpected amount") }
        }.load(today, CoreHistoryRange.ALL)
        assertMoney("1000", result.points.first().amount)
        assertMoney("998", result.points.last().amount)
    }

    @Test fun convertEachNativeBalanceBeforeDeltaAndConvertCombinedDisplayTotalLast() = runTest {
        val calls = mutableListOf<Triple<String, String, String>>()
        val native = overview(listOf(account("usd", "1.005", currency = "USD"), account("cny", "1")), "8.04")
        val result = query(native, mapOf("usd" to "1.004", "cny" to "1")) { amount, from, to ->
            calls += Triple(amount.toPlainString(), from, to)
            when {
                from == "USD" && to == "CNY" && amount == decimal("1.005") -> decimal("7.04")
                from == "USD" && to == "CNY" && amount == decimal("1.004") -> decimal("7.03")
                from == "CNY" && to == "EUR" && amount.compareTo(decimal("8.04")) == 0 -> decimal("1.01")
                from == "CNY" && to == "EUR" && amount.compareTo(decimal("8.03")) == 0 -> decimal("1.00")
                else -> error("Conversion of a delta or wrong target: $amount $from $to")
            }
        }.load(today, CoreHistoryRange.ALL, "EUR")
        assertEquals("EUR", result.currency)
        assertMoney("1.00", result.points.first().amount)
        assertMoney("1.01", result.points.last().amount)
        assertFalse(calls.any { it.second == "USD" && it.third == "EUR" })
    }

    @Test fun exactHighPrecisionValuesNeverBecomeFloatingPointFinancialAmounts() = runTest {
        val current = "999999999999999999.123456789"
        val historical = "999999999999999998.987654321"
        val result = query(overview(listOf(account("a", current)), current), mapOf("a" to historical))
            .load(today, CoreHistoryRange.ALL)
        assertMoney(historical, result.points.first().amount)
        assertMoney(current, result.points.last().amount)
    }

    @Test fun incompleteNativeValueOrMissingConversionNeverProducesAPlausibleZeroTrend() = runTest {
        val missingNative = CoreNetWorthHistoryQuery({ overview(emptyList(), "0", false) },
            { error("Incomplete native overview cannot produce history") }, { _, _, _, _ -> error("Unexpected history") },
            { _, _, _ -> error("Unexpected FX") }).load(today, CoreHistoryRange.ALL)
        assertFalse(missingNative.complete); assertTrue(missingNative.points.isEmpty())
        val missingFx = query(overview(listOf(account("a", "0", currency = "XYZ")), "0"), mapOf("a" to "0")) { _, _, _ -> null }
            .load(today, CoreHistoryRange.ALL)
        assertFalse(missingFx.complete); assertTrue(missingFx.points.isEmpty())
    }

    @Test fun currentEndpointIsSeparateAndNeverPretendsToBeTodayClosingBalance() = runTest {
        val askedUntil = mutableListOf<JavaDate>()
        val native = overview(listOf(account("future", "200")), "200")
        val result = CoreNetWorthHistoryQuery({ native }, { today.minusDays(2) }, { _, _, from, to ->
            askedUntil += to; days(from, to) { decimal("100") }
        }, { _, _, _ -> error("Unexpected FX") }).load(today, CoreHistoryRange.ALL)
        assertTrue(askedUntil.all { it == today.minusDays(1) })
        assertMoney("100", result.points[result.points.lastIndex - 1].amount)
        assertEquals(today.minusDays(1), result.points[result.points.lastIndex - 1].date)
        assertMoney("200", result.points.last().amount)
        assertEquals(today, result.points.last().date)
        assertTrue(result.points.last().current)
        assertTrue(result.points.dropLast(1).none { it.current })
    }

    @Test fun allRangeCoversOldImportedDataWithExactSamplingAndBoundedCoreCalls() = runTest {
        val since = JavaDate.of(2000, 1, 1)
        val requests = mutableListOf<Pair<JavaDate, JavaDate>>()
        val native = overview(listOf(account("a", "0")), "0")
        val result = CoreNetWorthHistoryQuery({ native }, { since }, { _, _, from, to ->
            requests += from to to; days(from, to) { BigDecimal(it.toEpochDay()) }
        }, { _, _, _ -> error("Unexpected FX") }).load(today, CoreHistoryRange.ALL)
        assertEquals(since, result.points.first().date)
        assertEquals(today.minusDays(1), result.points[result.points.lastIndex - 1].date)
        assertEquals(181, result.points.size)
        assertTrue(result.sampled)
        assertTrue(requests.all { ChronoUnit.DAYS.between(it.first, it.second) + 1 <= 3660 })
        assertTrue(result.points.dropLast(1).all { it.amount.compareTo(BigDecimal(it.date.toEpochDay())) == 0 })
        val month = historySampleDates(today.minusMonths(1), today.minusDays(1))
        assertEquals(30, month.size)
    }

    @Test fun missingOrDuplicateNativeDatesFailInsteadOfBeingFilledOrCarriedForward() = runTest {
        for (rows in listOf(emptyList(), days(today.minusDays(2), today.minusDays(2)) { BigDecimal.ONE }.let { it + it })) {
            val helper = CoreNetWorthHistoryQuery({ overview(listOf(account("a", "1")), "1") },
                { today.minusDays(2) }, { _, _, _, _ -> rows }, { _, _, _ -> error("Unexpected FX") })
            assertTrue(runCatching { helper.load(today, CoreHistoryRange.ALL) }.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun noHistoricalAccountsMeansCurrentBrokeragePointOnlyAndCancellationPropagates() = runTest {
        val currentOnly = query(overview(emptyList(), "500"), emptyMap()).load(today, CoreHistoryRange.ALL)
        assertEquals(1, currentOnly.points.size); assertTrue(currentOnly.points.single().current)
        val cancelled = CoreNetWorthHistoryQuery({ throw CancellationException("Synthetic cancel") },
            { null }, { _, _, _, _ -> emptyList() }, { _, _, _ -> null })
        assertTrue(runCatching { cancelled.load(today, CoreHistoryRange.ALL) }.exceptionOrNull() is CancellationException)
    }
}
