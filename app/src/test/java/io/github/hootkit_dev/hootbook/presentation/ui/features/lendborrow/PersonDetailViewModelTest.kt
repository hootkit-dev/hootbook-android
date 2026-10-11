package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import androidx.lifecycle.SavedStateHandle
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest : CoreViewModelTest() {
    @Test fun queriesCorePersonDebtsAndJoinsRefreshWithoutStaleSnapshotOrLosingSelection() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus()
        val vm = keep(PersonDetailViewModel(CoreSession(backgroundScope) { core }, bus, files, SavedStateHandle(mapOf("personId" to core.person.id))))
        awaitState { !vm.uiState.value.isLoading }; assertSame(core.person, vm.uiState.value.person)
        assertSame(core.debt, vm.uiState.value.transactions.single()); assertEquals("tx", vm.uiState.value.linkedTransactions["tx"]!!.id)
        val gate = CompletableDeferred<Unit>(); var entered = false; val old = core.person
        core.onPerson = { entered = true; withContext(NonCancellable) { gate.await() }; old }
        bus.changed(Table.PEOPLE); awaitState { entered }; vm.enterSelectionMode(core.debt.id)
        core.person = core.person.copy(name = "Latest"); core.onPerson = { core.person }; bus.changed(Table.DEBTS); gate.complete(Unit)
        awaitState { vm.uiState.value.person?.name == "Latest" }; assertEquals(setOf(core.debt.id), vm.uiState.value.selectedRecordIds)
        assertFalse(vm.uiState.value.person!!.totals.complete); assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun commandsPreserveStandaloneDebtAndFullSettlementSemanticsAndGuardRetry() = runTest {
        val core = FakePlansCore(); val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await(); error("invalid") }
        val vm = keep(PersonDetailViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), files, SavedStateHandle(mapOf("personId" to core.person.id))))
        awaitState { !vm.uiState.value.isLoading }; vm.showSettleSheet(true)
        val full = SettleDraft(null, core.account.id, "Settlement", core.now)
        vm.settle(full); vm.settle(full); awaitState { core.commandCalls == 1 }; gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertEquals("invalid", vm.uiState.value.error); assertTrue(vm.uiState.value.showSettleSheet)
        assertNull(core.settleDraft!!.amount); core.onCommand = {}; vm.settle(full)
        awaitState { !vm.uiState.value.saving }; assertFalse(vm.uiState.value.showSettleSheet)
        val draft = DebtDraft(core.person.id, DebtKind.BORROWED, core.debt.amount.amount, "EUR", core.now, null, "Standalone", null, emptyList())
        vm.updateTransaction(core.debt.id, draft); awaitState { !vm.uiState.value.saving }; assertSame(draft, core.debtDraft)
        assertNull(core.debtDraft!!.accountId); vm.deleteTransaction(core.debt.id); awaitState { !vm.uiState.value.saving }
        assertEquals(4, core.commandCalls); assertEquals(core.opened.get(), core.closed.get())
    }
}
