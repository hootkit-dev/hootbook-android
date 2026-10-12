package io.github.hootkit_dev.hootbook.presentation.core.analytics

import dev.hootkit.core.*
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaDateTime

internal fun JavaDate.coreDate()=LocalDate(year,monthValue.toUByte(),dayOfMonth.toUByte())
internal fun LocalDate.javaDate()=JavaDate.of(year,month.toInt(),day.toInt())
internal fun JavaDateTime.coreTime()=LocalDateTime(year,monthValue.toUByte(),dayOfMonth.toUByte(),hour.toUByte(),minute.toUByte(),second.toUByte(),nano.toUInt())
/** Query-day boundaries are UI input windows, never budget-cycle or money calculations. */
internal fun dayFilter(date:JavaDate,base:TransactionFilter)=base.copy(from=date.atStartOfDay().coreTime(),to=date.plusDays(1).atStartOfDay().coreTime())
internal fun emptyCoreFilter()=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),null,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
data class CoreDayCount(val date:LocalDate,val transactions:UInt)
data class CoreDailySummary(val date:LocalDate,val summary:LedgerSummary,val transactions:UInt)
internal suspend fun queryDayCounts(ledger:LedgerInterface,from:JavaDate,through:JavaDate,base:TransactionFilter=emptyCoreFilter()):List<CoreDayCount>{
    require(from<=through)
    val rows=mutableListOf<CoreDayCount>();var day=from
    while(day<=through){rows+=CoreDayCount(day.coreDate(),ledger.list(dayFilter(day,base),PageRequest(0u,1u)).total);day=day.plusDays(1)}
    return rows
}
internal suspend fun queryDailySummaries(ledger:LedgerInterface,from:JavaDate,through:JavaDate,base:TransactionFilter):List<CoreDailySummary>{
    require(from<=through)
    val rows=mutableListOf<CoreDailySummary>();var day=from
    while(day<=through){val filter=dayFilter(day,base);rows+=CoreDailySummary(day.coreDate(),ledger.summary(filter),ledger.list(filter,PageRequest(0u,1u)).total);day=day.plusDays(1)}
    return rows
}

/** All-time endpoints come from the same exact Core filter, including valid future transactions. */
internal suspend fun queryAllTimeDayRange(ledger:LedgerInterface,filter:TransactionFilter,today:JavaDate):Pair<JavaDate,JavaDate>{
    fun Transaction.day()=JavaDate.of(dateTime.year,dateTime.month.toInt(),dateTime.day.toInt())
    val oldest=ledger.list(filter.copy(sort=SortOrder.OLDEST_FIRST),PageRequest(0u,1u)).items.firstOrNull()?.day()
    val latest=ledger.list(filter.copy(sort=SortOrder.NEWEST_FIRST),PageRequest(0u,1u)).items.firstOrNull()?.day()
    val start=oldest?:today;val through=maxOf(latest?:today,today)
    require(start<=through){"Core returned inconsistent all-time endpoints"}
    return start to through
}
