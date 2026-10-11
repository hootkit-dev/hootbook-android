package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest : CoreViewModelTest() {
    @Test fun loadsCoreRecordsRefreshesAndLatestExactPeriodWins() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus()
        val vm = keep(BudgetViewModel(CoreSession(backgroundScope) { core }, bus))
        awaitState { !vm.uiState.value.isLoading }
        assertSame(core.budget, vm.uiState.value.budgets.single()); assertFalse(vm.uiState.value.budgets.single().current.complete)
        core.budget = core.budget.copy(name = "Updated"); bus.changed(Table.BUDGETS)
        awaitState { vm.uiState.value.budgets.single().name == "Updated" }
        val gate = CompletableDeferred<Unit>(); var entered = false
        core.onBudget = { id, date -> if (id == "old") { entered = true; withContext(NonCancellable) { gate.await() } }; core.budget.copy(id = id, current = core.progress.copy(start = date)) }
        vm.selectBudget("old", "2020-01-01"); awaitState { entered }
        vm.selectBudget("new", "2021-02-03"); gate.complete(Unit)
        awaitState { vm.uiState.value.selectedBudget?.id == "new" }
        assertEquals(LocalDate(2021, 2u, 3u), vm.uiState.value.selectedBudget!!.current.start)
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun createAndLimitCommandsKeepInputsGuardDoubleSubmitAndRecoverErrors() = runTest {
        val core = FakePlansCore(); val gate = CompletableDeferred<Unit>()
        core.onCommand = { gate.await(); error("invalid") }
        val vm = keep(BudgetViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        awaitState { !vm.uiState.value.isLoading }; vm.initNewBudget(); vm.updateBudgetAmount(BigDecimal("12")); vm.addCategoryLimit("cat/中文", BigDecimal("5"))
        vm.saveBudget(); vm.saveBudget(); awaitState { core.commandCalls == 1 }; gate.complete(Unit)
        awaitState { !vm.uiState.value.saving }; assertEquals("invalid", vm.uiState.value.error)
        assertEquals(BigDecimal("12"), vm.editBudgetState.value.amount)
        assertEquals("cat/中文", core.newBudget!!.limits.single().categoryId)
        core.onCommand = {}; var success = false; vm.saveBudget(onSuccess = { success = true })
        awaitState { !vm.uiState.value.saving }; assertTrue(success)
        vm.initEditBudget(core.budget); vm.updateBudgetName("Renamed"); vm.saveBudget()
        awaitState { !vm.uiState.value.saving }; assertNull(core.budgetPatch!!.start)
        assertEquals(core.opened.get(), core.closed.get())
    }
}
