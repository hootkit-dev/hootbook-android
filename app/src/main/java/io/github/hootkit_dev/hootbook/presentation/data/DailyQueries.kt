package io.github.hootkit_dev.hootbook.presentation.data

import dev.hootkit.core.*
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaDateTime

internal fun JavaDate.coreDate() = LocalDate(year, monthValue.toUByte(), dayOfMonth.toUByte())
internal fun JavaDateTime.coreTime() = LocalDateTime(
    year, monthValue.toUByte(), dayOfMonth.toUByte(), hour.toUByte(), minute.toUByte(),
    second.toUByte(), nano.toUInt(),
)

/** Query-day boundaries are UI input windows, never budget-cycle or money calculations. */
internal fun dayFilter(date: JavaDate, base: TransactionFilter) = base.copy(
    from = date.atStartOfDay().coreTime(),
    to = date.plusDays(1).atStartOfDay().coreTime(),
)

internal fun emptyCoreFilter() = TransactionFilter(
    null, null, emptyList(), emptyList(), emptyList(), null, null,
    DeletedFilter.EXCLUDE, SortOrder.NEWEST_FIRST,
)

/** The heatmap uses Core's full page count, independent of the returned page length. */
internal suspend fun queryDayCounts(
    ledger: LedgerInterface,
    from: JavaDate,
    through: JavaDate,
    base: TransactionFilter = emptyCoreFilter(),
): Map<LocalDate, UInt> {
    require(from <= through)
    val rows = linkedMapOf<LocalDate, UInt>()
    var day = from
    while (day <= through) {
        rows[day.coreDate()] = ledger.list(dayFilter(day, base), PageRequest(0u, 1u)).total
        day = day.plusDays(1)
    }
    return rows
}
