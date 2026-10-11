package io.github.hootkit_dev.hootbook.presentation.core.budgets

import dev.hootkit.core.*
import java.time.LocalDate as JavaDate

internal fun LocalDate.historyJavaDate()=JavaDate.of(year,month.toInt(),day.toInt())
internal data class BudgetHistoryWindow(val rows:List<BudgetPeriodSummary>,val nextCursor:LocalDate?)
/** Core supplies all cycle boundaries/amounts; Android only advances an exclusive date cursor. */
internal fun appendBudgetHistory(existing:List<BudgetPeriodSummary>,page:List<BudgetPeriodSummary>,anchor:LocalDate,cursor:LocalDate):BudgetHistoryWindow {
    val start=anchor.historyJavaDate();val request=cursor.historyJavaDate()
    var previous:JavaDate?=null
    for(row in page){val date=row.start.historyJavaDate();require(date>=start && row.end.historyJavaDate()>=date)
        require(date<=request || request<start && date==start)
        previous?.let{require(date<it){"History page does not descend"}};previous=date
    }
    val merged=(existing+page).distinctBy{it.start}.sortedByDescending{it.start.historyJavaDate()}
    if(page.isEmpty())return BudgetHistoryWindow(merged,null)
    val earliest=page.last().start.historyJavaDate()
    val oldEarliest=existing.lastOrNull()?.start?.historyJavaDate()
    require(oldEarliest==null || earliest<oldEarliest){"History cursor made no progress"}
    val next=earliest.minusDays(1).takeIf{earliest>start}
    require(next==null || next<request){"History cursor did not decrease"}
    return BudgetHistoryWindow(merged,next?.let{LocalDate(it.year,it.monthValue.toUByte(),it.dayOfMonth.toUByte())})
}
