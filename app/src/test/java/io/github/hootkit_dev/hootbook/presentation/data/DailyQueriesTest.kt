package io.github.hootkit_dev.hootbook.presentation.data

import dev.hootkit.core.*
import java.lang.reflect.Proxy
import java.time.LocalDate as JavaDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DailyQueriesTest {
    private fun api(filters:MutableList<TransactionFilter>,count:UInt=7u):LedgerInterface=Proxy.newProxyInstance(LedgerInterface::class.java.classLoader,arrayOf(LedgerInterface::class.java)){_,method,args->
        val name=method.name.substringBefore('-')
        when(name){"list"->{filters+=args[0] as TransactionFilter;TransactionPage(emptyList(),count)};else->error(name)}
    } as LedgerInterface
    @Test fun heatmapUsesOnlyListTotalNotReturnedPageLength()=runTest {
        val filters=mutableListOf<TransactionFilter>();val rows=queryDayCounts(api(filters,42u),JavaDate.parse("2026-10-10"),JavaDate.parse("2026-10-10"))
        assertEquals(42u,rows.values.single());assertEquals(1,filters.size)
    }
}
