package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetHistoryViewModelTest : CoreViewModelTest() {
    @Test fun coreCursorContinuesPast24GuardsDuplicatePagesAndRetainsIncompleteAmounts() = runTest {
        val core = FakePlansCore(); core.budget = core.budget.copy(anchor = LocalDate(2026, 9u, 1u))
        core.onHistory = { date -> (0..23).map { offset -> date.javaBudgetDate().minusDays(offset.toLong()).coreBudgetDate().let {
            BudgetPeriodSummary(it, it, core.budget.amount.amount, core.progress.spent, false, false)
        } }.filter { it.start.javaBudgetDate() >= core.budget.anchor.javaBudgetDate() } }
        val vm = keep(BudgetHistoryViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.loadBudgetHistory(core.budget.id); awaitState { !vm.uiState.value.isLoading }
        assertEquals(24, vm.uiState.value.periods.size); val cursor = vm.uiState.value.cursor!!
        assertEquals(vm.uiState.value.periods.last().start.javaBudgetDate().minusDays(1).coreBudgetDate(), cursor)
        val gate = CompletableDeferred<Unit>(); val history = core.onHistory
        core.onHistory = { gate.await(); history(it) }; vm.loadMore(); vm.loadMore()
        awaitState { core.historyRequests.size == 2 }; assertEquals(cursor, core.historyRequests.last())
        gate.complete(Unit); awaitState { !vm.uiState.value.loadingMore }
        assertTrue(vm.uiState.value.periods.size > 24); assertNull(vm.uiState.value.cursor)
        assertFalse(vm.uiState.value.periods.last().complete)
        assertEquals(core.progress.spent, vm.uiState.value.periods.last().spent)
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun changesRefreshLatestBudgetAndQueryAndPagingErrorsCanRetry() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus()
        val vm = keep(BudgetHistoryViewModel(CoreSession(backgroundScope) { core }, bus))
        val gate = CompletableDeferred<Unit>(); var entered = false; val history = core.onHistory
        core.onHistory = { entered = true; withContext(NonCancellable) { gate.await() }; history(it) }
        vm.loadBudgetHistory("old"); awaitState { entered }; vm.loadBudgetHistory("new"); gate.complete(Unit)
        awaitState { vm.uiState.value.budget?.id == "new" }; assertEquals("new", vm.uiState.value.budget!!.id)
        core.onHistory = { error("offline") }; vm.loadMore(); awaitState { vm.uiState.value.error == "offline" }
        core.onHistory = history; vm.loadMore(); awaitState { !vm.uiState.value.loadingMore }
        val previous = core.historyRequests.size; bus.changed(Table.TRANSACTIONS)
        awaitState { core.historyRequests.size > previous && !vm.uiState.value.loadingMore }
        assertEquals("new", vm.uiState.value.budget!!.id)
    }
}
