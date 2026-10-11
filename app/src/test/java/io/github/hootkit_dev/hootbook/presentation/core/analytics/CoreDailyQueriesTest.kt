package io.github.hootkit_dev.hootbook.presentation.core.analytics

import dev.hootkit.core.*
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreDailyQueriesTest {
    private val summary=LedgerSummary("CNY",BigDecimal("71.23"),BigDecimal("11.11"),BigDecimal("60.12"),false,listOf("USD"),emptyList(),emptyList(),emptyList())
    private fun api(filters:MutableList<TransactionFilter>,count:UInt=7u):LedgerInterface=Proxy.newProxyInstance(LedgerInterface::class.java.classLoader,arrayOf(LedgerInterface::class.java)){_,method,args->
        val name=method.name.substringBefore('-')
        when(name){"list"->{filters+=args[0] as TransactionFilter;TransactionPage(emptyList(),count)};"summary"->{filters+=args[0] as TransactionFilter;summary};else->error(name)}
    } as LedgerInterface
    @Test fun dailyWindowsUseCoreValuesAndExactExclusiveNextDay()=runTest {
        val filters=mutableListOf<TransactionFilter>();val rows=queryDailySummaries(api(filters),JavaDate.parse("2026-10-09"),JavaDate.parse("2026-10-10"),emptyCoreFilter().copy(currency="USD"))
        assertEquals(2,rows.size);assertSame(summary,rows.first().summary);assertEquals(7u,rows.last().transactions);assertFalse(rows.last().summary.complete)
        assertEquals(LocalDateTime(2026,10u,9u,0u,0u,0u,0u),filters.first().from);assertEquals(LocalDateTime(2026,10u,10u,0u,0u,0u,0u),filters.first().to);assertTrue(filters.all{it.currency=="USD"})
    }
    @Test fun heatmapUsesOnlyListTotalNotReturnedPageLength()=runTest {
        val filters=mutableListOf<TransactionFilter>();val rows=queryDayCounts(api(filters,42u),JavaDate.parse("2026-10-10"),JavaDate.parse("2026-10-10"))
        assertEquals(42u,rows.single().transactions);assertEquals(1,filters.size)
    }
    @Test fun dateQueryValidatesInputsWithoutChangingCoreFilterSemantics(){
        val filter=CoreAnalyticsQuery("2026-10-01","2026-10-10",kinds=setOf(TransactionKind.INCOME),currency="JPY",categoryId="cat").filter()
        assertEquals("JPY",filter.currency);assertEquals(listOf("cat"),filter.categoryIds);assertEquals(11u.toUByte(),filter.to!!.day)
        try{CoreAnalyticsQuery("2026-10-11","2026-10-10").filter();fail()}catch(_:IllegalArgumentException){}
    }
    private suspend fun allTimeRange(oldest:String?,latest:String?):Pair<JavaDate,JavaDate>{
        fun transaction(day:String):Transaction {val d=JavaDate.parse(day);val time=LocalDateTime(d.year,d.monthValue.toUByte(),d.dayOfMonth.toUByte(),12u,0u,0u,0u)
            return Transaction(day,TransactionKind.EXPENSE,BigDecimal.ONE,"CNY",time,null,null,null,null,null,null,"Merchant",null,null,null,emptyList(),false,time,time)}
        val ledger=Proxy.newProxyInstance(LedgerInterface::class.java.classLoader,arrayOf(LedgerInterface::class.java)){_,method,args->
            require(method.name.substringBefore('-')=="list");val filter=args[0] as TransactionFilter
            assertEquals("USD",filter.currency);assertEquals(listOf("category"),filter.categoryIds)
            val day=if(filter.sort==SortOrder.OLDEST_FIRST)oldest else latest
            TransactionPage(day?.let{listOf(transaction(it))}.orEmpty(),if(day==null)0u else 1u)
        } as LedgerInterface
        return queryAllTimeDayRange(ledger,emptyCoreFilter().copy(currency="USD",categoryIds=listOf("category")),JavaDate.parse("2026-10-11"))
    }
    @Test fun allFutureAllTimeRangeDoesNotClampOldestToToday()=runTest {
        assertEquals(JavaDate.parse("2027-01-05") to JavaDate.parse("2027-04-03"),allTimeRange("2027-01-05","2027-04-03"))
    }
    @Test fun mixedPastFutureAllTimeRangeIncludesFutureUpperBound()=runTest {
        assertEquals(JavaDate.parse("2020-01-01") to JavaDate.parse("2028-12-31"),allTimeRange("2020-01-01","2028-12-31"))
    }
    @Test fun emptyAllTimeRangeUsesOneCurrentDay()=runTest {
        assertEquals(JavaDate.parse("2026-10-11") to JavaDate.parse("2026-10-11"),allTimeRange(null,null))
    }

}
