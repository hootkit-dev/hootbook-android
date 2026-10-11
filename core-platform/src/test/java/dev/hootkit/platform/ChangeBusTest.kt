package dev.hootkit.platform

import dev.hootkit.core.ChangeOrigin
import dev.hootkit.core.ChangeSet
import dev.hootkit.core.Table
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeBusTest {
    @Test fun commitsWithoutCollectorsRemainVisible() = runTest {
        val bus = ChangeBus()
        repeat(100) { bus.onChange(change(Table.TRANSACTIONS, Table.ACCOUNTS)) }
        val first = bus.observe(setOf(Table.TRANSACTIONS, Table.ACCOUNTS)).take(1).toList().single()
        assertEquals(mapOf(Table.TRANSACTIONS to 100L, Table.ACCOUNTS to 100L), first)
    }

    @Test fun unrelatedTablesDoNotRefreshAndDuplicateTablesIncrementOnce() = runTest {
        val bus = ChangeBus()
        val seen = mutableListOf<Map<Table, Long>>()
        val job = launch { bus.observe(setOf(Table.ACCOUNTS)).toList(seen) }
        runCurrent()
        bus.onChange(change(Table.TRANSACTIONS))
        runCurrent()
        bus.onChange(change(Table.ACCOUNTS, Table.ACCOUNTS))
        runCurrent()
        assertEquals(listOf(mapOf(Table.ACCOUNTS to 0L), mapOf(Table.ACCOUNTS to 1L)), seen)
        job.cancel()
    }

    @Test fun concurrentCallbacksDoNotLoseRevisions() {
        val bus = ChangeBus()
        val pool = Executors.newFixedThreadPool(8)
        try {
            pool.invokeAll((1..1000).map { java.util.concurrent.Callable { bus.onChange(change(Table.TRANSACTIONS)) } })
            assertEquals(1000L, bus.revisions.value.getValue(Table.TRANSACTIONS))
        } finally { pool.shutdownNow() }
    }

    private fun change(vararg tables: Table) = ChangeSet(tables.toList(), emptyList(), ChangeOrigin.LOCAL)
}
