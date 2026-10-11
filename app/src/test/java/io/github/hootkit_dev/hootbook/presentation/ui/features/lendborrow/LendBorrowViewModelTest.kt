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
class LendBorrowViewModelTest : CoreViewModelTest() {
    @Test fun coreTotalsRefreshWithoutReplacingLatestSavedSearchAndFilters() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus(); val saved = SavedStateHandle(mapOf("filter" to "YOU_OWE"))
        val vm = keep(LendBorrowViewModel(CoreSession(backgroundScope) { core }, bus, files, saved))
        awaitState { !vm.uiState.value.isLoading }; assertSame(core.debtSummary, vm.uiState.value.summary)
        assertEquals("bob", vm.uiState.value.filteredPersons.single().id)
        val gate = CompletableDeferred<Unit>(); var entered = false
        core.onPeople = { entered = true; withContext(NonCancellable) { gate.await() }; core.people }
        bus.changed(Table.DEBTS); awaitState { entered }; vm.onFilterSelected(LendBorrowFilter.ALL)
        vm.onSearchQueryChanged("Alice"); vm.onCategorySelected(PersonRelation.FRIEND); gate.complete(Unit)
        awaitState { core.opened.get() == core.closed.get() }; assertEquals(core.person.id, vm.uiState.value.filteredPersons.single().id)
        assertEquals("Alice", saved.get<String>("searchQuery")); assertFalse(vm.uiState.value.summary!!.complete)
        core.onPeople = { error("offline") }; bus.changed(Table.PEOPLE); awaitState { vm.uiState.value.error == "offline" }
        core.onPeople = { core.people }; bus.changed(Table.PEOPLE); awaitState { vm.uiState.value.error == null }
    }
    @Test fun personAndDebtCommandsAreGuardedAndFailuresKeepSheetsForRetry() = runTest {
        val core = FakePlansCore(); val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await(); error("save error") }
        val vm = keep(LendBorrowViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), files, SavedStateHandle()))
        awaitState { !vm.uiState.value.isLoading }; vm.showAddPersonSheet(true)
        vm.addPerson("Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND); vm.addPerson("Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND)
        awaitState { core.commandCalls == 1 }; gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertTrue(vm.uiState.value.showAddPersonSheet); assertEquals("save error", vm.uiState.value.error)
        core.onCommand = {}; vm.addPerson("Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND)
        awaitState { !vm.uiState.value.saving }; assertFalse(vm.uiState.value.showAddPersonSheet)
        val draft = DebtDraft(core.person.id, DebtKind.LENT, core.debt.amount.amount, "EUR", core.now, null, "Debt", null, emptyList())
        vm.showAddTransactionSheet(true); vm.addTransaction(draft); vm.addTransaction(draft)
        awaitState { !vm.uiState.value.saving }; assertSame(draft, core.debtDraft); assertEquals(3, core.commandCalls)
        assertFalse(vm.uiState.value.showAddTransactionSheet); assertEquals(core.opened.get(), core.closed.get())
    }
}
