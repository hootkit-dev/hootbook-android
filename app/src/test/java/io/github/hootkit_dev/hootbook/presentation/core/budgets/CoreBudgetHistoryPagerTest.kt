package io.github.hootkit_dev.hootbook.presentation.core.budgets

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import org.junit.Assert.*
import org.junit.Test

class CoreBudgetHistoryPagerTest {
    private fun date(s:String)=budgetDate(s)
    private fun period(start:JavaDate,end:JavaDate)=BudgetPeriodSummary(date(start.toString()),date(end.toString()),BigDecimal("100"),BigDecimal("34.56"),false,false)
    @Test fun loadsMoreThan24CoreSuppliedPeriodsWithoutCalculatingBudgetRules(){
        val anchor=date("2020-01-31");val starts=(0..60).map{JavaDate.of(2020,1,31).plusMonths(it.toLong())}.reversed()
        val all=starts.map{period(it,it.plusDays(20))}
        val first=appendBudgetHistory(emptyList(),all.take(24),anchor,date("2025-02-01"))
        assertEquals(24,first.rows.size);assertEquals(all[23].start.historyJavaDate().minusDays(1),first.nextCursor!!.historyJavaDate())
        val second=appendBudgetHistory(first.rows,all.drop(24).take(24),anchor,first.nextCursor!!)
        val third=appendBudgetHistory(second.rows,all.drop(48),anchor,second.nextCursor!!)
        assertEquals(61,third.rows.size);assertNull(third.nextCursor);assertEquals(BigDecimal("34.56"),third.rows.last().spent);assertFalse(third.rows.last().complete)
    }
    @Test fun leapMonthEndAndWeeklyBoundariesUseReturnedStarts(){
        val month=appendBudgetHistory(emptyList(),listOf(period(JavaDate.parse("2024-03-31"),JavaDate.parse("2024-04-29")),period(JavaDate.parse("2024-02-29"),JavaDate.parse("2024-03-30"))),date("2024-01-31"),date("2024-04-10"))
        assertEquals(date("2024-02-28"),month.nextCursor)
        val week=appendBudgetHistory(emptyList(),listOf(period(JavaDate.parse("2026-10-08"),JavaDate.parse("2026-10-14"))),date("2026-10-01"),date("2026-10-10"))
        assertEquals(date("2026-10-07"),week.nextCursor)
    }
    @Test fun anchorAndFutureAnchorStopWithoutRepeatedFirstPeriod(){
        val first=listOf(period(JavaDate.parse("2026-10-12"),JavaDate.parse("2026-11-11")))
        val window=appendBudgetHistory(emptyList(),first,date("2026-10-12"),date("2026-10-10"));assertNull(window.nextCursor)
        assertNull(appendBudgetHistory(emptyList(),first,date("2026-10-12"),date("2026-10-12")).nextCursor)
    }
    @Test fun stalledDuplicateOrAscendingBackendIsRejected(){
        val row=period(JavaDate.parse("2026-09-01"),JavaDate.parse("2026-09-30"))
        try{appendBudgetHistory(listOf(row),listOf(row),date("2026-01-01"),date("2026-08-31"));fail()}catch(_:IllegalArgumentException){}
        try{appendBudgetHistory(emptyList(),listOf(row,row),date("2026-01-01"),date("2026-10-10"));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun emptyPageStopsAndCoreIncompleteAmountsStayUnchanged(){
        val rows=listOf(period(JavaDate.parse("2026-01-01"),JavaDate.parse("2026-01-31")))
        assertEquals(rows,appendBudgetHistory(rows,emptyList(),date("2026-01-01"),date("2025-12-31")).rows)
        assertNull(appendBudgetHistory(rows,emptyList(),date("2026-01-01"),date("2025-12-31")).nextCursor)
    }
}
