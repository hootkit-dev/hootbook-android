package io.github.hootkit_dev.hootbook.presentation.core.home

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class CoreHistoryRange { MONTH, QUARTER, HALF_YEAR, YEAR, ALL }

/** Display-only observations. The last point is native current net worth, not today's closing balance. */
data class CoreNetWorthHistoryPoint(val date: JavaDate, val amount: BigDecimal, val current: Boolean = false)
data class CoreNetWorthHistoryResult(
    val currency: String,
    val points: List<CoreNetWorthHistoryPoint>,
    val complete: Boolean,
    val sampled: Boolean = false,
)

/**
 * User-approved read-only presentation aggregation: Core still owns every pocket balance and FX
 * conversion. Current native net worth is the base; only historical cash deltas are added to it.
 * Thus linked securities and unlinked brokerage remain at their native current contribution,
 * without duplicating valuation/linking rules or replaying transactions in Android.
 */
class CoreNetWorthHistoryQuery internal constructor(
    private val overview: suspend () -> AccountsOverview,
    private val earliestDate: suspend (List<Account>) -> JavaDate?,
    private val series: suspend (String, String, JavaDate, JavaDate) -> List<BalanceDay>,
    private val convert: suspend (BigDecimal, String, String) -> BigDecimal?,
) {
    constructor(core: HootKitCoreInterface) : this(
        { core.accounts().use { it.overview() } },
        { accounts -> firstCoreHistoryDate(core, accounts) },
        { account, currency, from, to -> core.accounts().use {
            it.balanceSeries(account, currency, from.coreDate(), to.coreDate())
        } },
        { amount, from, to -> core.currency().use { it.convert(amount, from, to) } },
    )

    suspend fun load(today: JavaDate, range: CoreHistoryRange, displayCurrency: String? = null): CoreNetWorthHistoryResult {
        val native = overview()
        val main = native.netWorth.currency
        val display = displayCurrency?.takeIf { it.isNotBlank() } ?: main
        fun incomplete() = CoreNetWorthHistoryResult(display, emptyList(), false)
        if (!native.netWorthComplete) return incomplete()
        val converted = mutableMapOf<Triple<BigDecimal, String, String>, BigDecimal?>()
        suspend fun money(amount: BigDecimal, from: String, to: String): BigDecimal? {
            currentCoroutineContext().ensureActive()
            if (from == to) return amount // Identity display; no conversion or rounding.
            val key = Triple(amount, from, to)
            if (key !in converted) converted[key] = convert(amount, from, to)
            return converted[key]
        }
        val current = money(native.netWorth.amount, main, display) ?: return incomplete()
        val currentPoint = CoreNetWorthHistoryPoint(today, current, current = true)
        if (native.accounts.isEmpty()) return CoreNetWorthHistoryResult(display, listOf(currentPoint), true)
        val knownSince = earliestDate(native.accounts) ?: today
        val selectedStart = when (range) {
            CoreHistoryRange.MONTH -> today.minusMonths(1)
            CoreHistoryRange.QUARTER -> today.minusMonths(3)
            CoreHistoryRange.HALF_YEAR -> today.minusMonths(6)
            CoreHistoryRange.YEAR -> today.minusYears(1)
            CoreHistoryRange.ALL -> knownSince
        }
        val from = maxOf(selectedStart, knownSince)
        val to = today.minusDays(1)
        if (from > to) return CoreNetWorthHistoryResult(display, listOf(currentPoint), true)
        val dates = historySampleDates(from, to)
        val totals = dates.associateWith { native.netWorth.amount }.toMutableMap()
        // All native accounts count, including archived ones. Do not zero a pocket before its
        // created_at: Core may legitimately reconstruct an earlier balance from later anchors.
        for (account in native.accounts) {
            for (pocket in account.pockets) {
                val present = money(pocket.balance, pocket.currency, main) ?: return incomplete()
                for (chunk in historyDateChunks(dates)) {
                    currentCoroutineContext().ensureActive()
                    val rows = series(account.id, pocket.currency, chunk.first(), chunk.last())
                    val byDate = rows.associateBy { it.date.javaHistoryDate() }
                    check(byDate.size == rows.size) { "Core history returned duplicate dates" }
                    for (date in chunk) {
                        val balance = checkNotNull(byDate[date]) { "Core history omitted a requested date" }.balance
                        val historical = money(balance, pocket.currency, main) ?: return incomplete()
                        val delta = if (account.kind == AccountKind.CREDIT_CARD) present - historical else historical - present
                        totals[date] = totals.getValue(date) + delta
                    }
                }
            }
        }
        val points = ArrayList<CoreNetWorthHistoryPoint>(dates.size + 1)
        for (date in dates) {
            // Convert each combined main-currency total exactly as Home converts native net worth.
            // Converting pockets directly to a second display currency would change rounding.
            val amount = money(totals.getValue(date), main, display) ?: return incomplete()
            points += CoreNetWorthHistoryPoint(date, amount)
        }
        points += currentPoint
        return CoreNetWorthHistoryResult(display, points, true,
            sampled = dates.size.toLong() < ChronoUnit.DAYS.between(from, to) + 1)
    }
}

/** Select real day-end observations across the whole range; never average financial values. */
internal fun historySampleDates(from: JavaDate, to: JavaDate, limit: Int = 180): List<JavaDate> {
    require(limit >= 2)
    if (from > to) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to)
    val count = minOf(days + 1, limit.toLong()).toInt()
    if (count == 1) return listOf(from)
    return (0 until count).map { from.plusDays(days * it / (count - 1)) }
}

/** Core permits at most 3660 inclusive days per call; distant samples never read huge empty spans. */
internal fun historyDateChunks(dates: List<JavaDate>): List<List<JavaDate>> {
    val chunks = mutableListOf<MutableList<JavaDate>>()
    for (date in dates) {
        val last = chunks.lastOrNull()
        if (last == null || ChronoUnit.DAYS.between(last.first(), date) >= 3660) chunks += mutableListOf(date)
        else last += date
    }
    return chunks
}

private suspend fun firstCoreHistoryDate(core: HootKitCoreInterface, accounts: List<Account>): JavaDate? {
    if (accounts.isEmpty()) return null
    val evidence = accounts.map { it.createdAt.dateForHistory() }.toMutableList()
    core.accounts().use { api ->
        for (account in accounts) for (pocket in account.pockets) {
            currentCoroutineContext().ensureActive()
            api.anchors(account.id, pocket.currency).forEach { evidence += it.at.dateForHistory() }
        }
    }
    // Earliest valid transaction is only date-range evidence, never replayed to calculate balances.
    core.ledger().use { api ->
        val filter = TransactionFilter(null, null, emptyList(), accounts.map { it.id }, emptyList(),
            null, null, DeletedFilter.EXCLUDE, SortOrder.OLDEST_FIRST)
        api.list(filter, PageRequest(0u, 1u)).items.firstOrNull()?.dateTime?.let { evidence += it.dateForHistory() }
    }
    return evidence.minOrNull()
}

private fun JavaDate.coreDate() = LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())
private fun LocalDate.javaHistoryDate() = JavaDate.of(year, month.toInt(), day.toInt())
private fun LocalDateTime.dateForHistory() = JavaDate.of(year, month.toInt(), day.toInt())
