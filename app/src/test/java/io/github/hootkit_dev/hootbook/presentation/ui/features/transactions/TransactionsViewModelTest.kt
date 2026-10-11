package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import android.app.Application
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TransactionsViewModelTest : CoreViewModelTest() {
    @Test
    fun listLoadsCoreSummaryPagesAndRecoversOnChangeAfterQueryError() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val requested = java.util.Collections.synchronizedList(mutableListOf<PageRequest>())
        core.onList = { _, page ->
            requested += page
            TransactionPage(
                (page.offset.toInt() until minOf(page.offset.toInt() + page.limit.toInt(), 65))
                    .map { core.transaction.copy(id = "id/$it") },
                65u,
            )
        }
        val vm = keep(TransactionsViewModel(session(core), changes, preferences))
        awaitState { !vm.uiState.value.isLoading }
        assertEquals(
            io.github.hootkit_dev.hootbook.presentation.common.TimePeriod.THIS_MONTH,
            vm.uiState.value.period,
        )
        assertEquals(1u.toUByte(), vm.uiState.value.filter.from!!.day)
        assertSame(core.summary, vm.uiState.value.summary)
        assertEquals(50, vm.uiState.value.transactions.size)
        vm.loadMore()
        vm.loadMore()
        awaitState { !vm.uiState.value.loadingMore }
        assertEquals(65, vm.uiState.value.transactions.size)
        assertEquals(listOf(0u, 50u), requested.map { it.offset })
        core.onList = { _, _ -> error("offline") }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.error == "offline" }
        core.onList = { _, _ -> TransactionPage(listOf(core.transaction.copy(id = "updated")), 1u) }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.transactions.singleOrNull()?.id == "updated" }
    }

    @Test
    fun latestFilterWinsEvenIfOldNativeQueryIgnoresCancellation() = runTest {
        val core = FakeLedgerCore()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        core.onList = { filter, _ ->
            if (filter.text == "old")
                withContext(NonCancellable) {
                    started.complete(Unit)
                    gate.await()
                }
            TransactionPage(listOf(core.transaction.copy(id = filter.text ?: "initial")), 1u)
        }
        val vm = keep(TransactionsViewModel(session(core), ChangeBus(), preferences))
        awaitState { !vm.uiState.value.isLoading }
        vm.updateSearchQuery("old")
        awaitState { started.isCompleted }
        vm.updateSearchQuery("new")
        awaitState { vm.uiState.value.transactions.singleOrNull()?.id == "new" }
        gate.complete(Unit)
        awaitState { core.openedHandles == core.closedHandles }
        assertEquals("new", vm.uiState.value.transactions.single().id)
        core.assertHandlesReleased()
    }

    @Test
    fun bulkDeleteIsGuardedAndUndoAndPurgeUseCoreCommands() = runTest {
        val core = FakeLedgerCore()
        val gate = CompletableDeferred<Unit>()
        val vm = keep(TransactionsViewModel(session(core), ChangeBus(), preferences))
        awaitState { !vm.uiState.value.isLoading }
        core.onDelete = { gate.await() }
        vm.selectTransactionSet(setOf("one", "two"))
        vm.deleteSelectedTransactions()
        vm.deleteSelectedTransactions()
        assertTrue(vm.uiState.value.writing)
        gate.complete(Unit)
        awaitState { !vm.uiState.value.writing }
        assertEquals(setOf("one", "two"), core.deletedIds.toSet())
        assertEquals(1, core.deleteCalls)
        assertEquals("opaque-core-token", vm.uiState.value.undoToken)
        core.onUndo = { error("expired") }
        vm.undoDelete("opaque-core-token")
        awaitState { !vm.uiState.value.writing }
        assertEquals("expired", vm.uiState.value.error)
        core.onUndo = {}
        vm.undoDelete("opaque-core-token")
        awaitState { !vm.uiState.value.writing }
        assertEquals("opaque-core-token", core.undoneToken)
        assertNull(vm.uiState.value.undoToken)
        vm.setDeletedFilter(DeletedFilter.ONLY)
        vm.selectTransactionSet(setOf("gone"))
        vm.deleteSelectedTransactions()
        awaitState { !vm.uiState.value.writing }
        assertEquals(listOf("gone"), core.purgedIds)
        vm.batchUpdateTransactions(setOf("x"), null, null, "cat", null, BigDecimal("77"), "")
        awaitState { !vm.uiState.value.writing }
        assertEquals(BigDecimal("77"), core.lastBulk!!.second.amount)
        assertEquals("", core.lastBulk!!.second.notes)
    }

    @Test
    fun dateSectionsKeepCoreOrderWhenLargestRowsCrossDateGroups() {
        val core = FakeLedgerCore()
        val today = java.time.LocalDate.now().atStartOfDay().coreTime()
        val old = java.time.LocalDate.now().minusMonths(1).atStartOfDay().coreTime()
        val rows =
            listOf(
                core.transaction.copy(id = "largest", dateTime = old),
                core.transaction.copy(id = "middle", dateTime = today),
                core.transaction.copy(id = "smallest", dateTime = old),
            )
        val sections = groupTransactionsByDate(rows)
        assertEquals(rows, sections.flatMap { it.second })
        assertEquals(
            listOf(DateGroup.EARLIER, DateGroup.TODAY, DateGroup.EARLIER),
            sections.map { it.first },
        )
    }

    @Test
    fun selectAllUsesEveryCorePageAndKeepsTheExactFilter() = runTest {
        val core = FakeLedgerCore()
        val filters = java.util.Collections.synchronizedList(mutableListOf<TransactionFilter>())
        core.onList = { filter, page ->
            filters += filter
            TransactionPage(
                (page.offset.toInt() until minOf(451, page.offset.toInt() + page.limit.toInt()))
                    .map { core.transaction.copy(id = "row/$it") },
                451u,
            )
        }
        val vm = keep(TransactionsViewModel(session(core), ChangeBus(), preferences))
        val filter =
            emptyTransactionFilter().copy(text = "Coffee", accountIds = listOf("opaque/account"))
        vm.applyFilter(filter)
        awaitState { !vm.uiState.value.isLoading }
        filters.clear()
        vm.selectAllTransactions()
        awaitState { !vm.uiState.value.writing }
        assertEquals(451, vm.uiState.value.selectedIds.size)
        assertTrue(filters.all { it == filter })
    }
}
