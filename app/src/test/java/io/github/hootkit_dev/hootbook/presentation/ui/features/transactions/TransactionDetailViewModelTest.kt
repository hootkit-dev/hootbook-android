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
class TransactionDetailViewModelTest : CoreViewModelTest() {
    @Test
    fun detailRefreshLatestIdentityAndQueryRecovery() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val vm = keep(TransactionDetailViewModel(session(core), changes, files, preferences))
        vm.loadTransaction("first")
        awaitState { vm.uiState.value.transaction != null }
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        core.onGet = { id ->
            if (id == "slow")
                withContext(NonCancellable) {
                    started.complete(Unit)
                    gate.await()
                }
            core.transaction.copy(id = id)
        }
        vm.loadTransaction("slow")
        awaitState { started.isCompleted }
        vm.loadTransaction("latest")
        awaitState { vm.uiState.value.transaction?.id == "latest" }
        gate.complete(Unit)
        awaitState { core.openedHandles == core.closedHandles }
        assertEquals("latest", vm.uiState.value.transaction!!.id)
        core.onGet = { error("read failed") }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.errorMessage == "read failed" }
        core.onGet = { core.transaction.copy(id = it, merchant = "changed") }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.transaction?.merchant == "changed" }
    }

    @Test
    fun detailCommandsDoNotDoubleSubmitAndOrdinaryExpenseCannotBeMarkedAsLoan() = runTest {
        val core = FakeLedgerCore()
        val vm = keep(TransactionDetailViewModel(session(core), ChangeBus(), files, preferences))
        vm.loadTransaction("tx")
        awaitState { vm.uiState.value.transaction != null }
        vm.markAsLoan("person", DebtKind.LENT)
        runCurrent()
        assertEquals(0, core.linkCalls)
        vm.enterEditMode()
        vm.updateAmount("99")
        val gate = CompletableDeferred<Unit>()
        core.onEdit = { _, _ -> gate.await() }
        vm.saveChanges()
        vm.saveChanges()
        awaitState { core.editCalls == 1 }
        gate.complete(Unit)
        awaitState { vm.uiState.value.saveSuccess }
        assertEquals(BigDecimal("99"), core.lastDraft!!.amount)
        assertEquals(1, core.editCalls)
        core.onAdd = { error("cannot add") }
        vm.duplicateTransaction()
        awaitState { !vm.uiState.value.isSaving }
        assertEquals("cannot add", vm.uiState.value.errorMessage)
        core.onAdd = { "copy" }
        vm.duplicateTransaction()
        vm.duplicateTransaction()
        awaitState { vm.uiState.value.duplicateSuccess }
        assertEquals(2, core.addCalls)
        assertNull(core.lastDraft!!.subscriptionId)
    }

    @Test
    fun recurringCreationUsesCoreAndCycleNeverRecalculatesDatesInPresentation() = runTest {
        val core = FakeLedgerCore()
        val vm = keep(TransactionDetailViewModel(session(core), ChangeBus(), files, preferences))
        vm.loadTransaction("tx")
        awaitState { vm.uiState.value.transaction != null }
        vm.enterEditMode()
        vm.updateRecurringStatus(true)
        vm.updateBillingCycle("Quarterly")
        vm.saveChanges()
        awaitState { vm.uiState.value.saveSuccess }
        assertEquals(Triple("tx", Cycle(3u, CycleUnit.MONTH), null), core.createdSubscriptionFrom)
    }

    @Test
    fun loanKindsLinkWithoutChangingKindOrCategoryAndSurfaceErrors() = runTest {
        val core = FakeLedgerCore()
        core.transaction = core.transaction.copy(kind = TransactionKind.LOAN_OUT, categoryId = null)
        val vm = keep(TransactionDetailViewModel(session(core), ChangeBus(), files, preferences))
        vm.loadTransaction("tx")
        awaitState { vm.uiState.value.transaction != null }
        val gate = CompletableDeferred<Unit>()
        core.onLink = { gate.await() }
        vm.markAsLoan("person", DebtKind.LENT)
        vm.markAsLoan("person", DebtKind.LENT)
        awaitState { core.linkCalls == 1 }
        gate.complete(Unit)
        awaitState { vm.uiState.value.markAsLoanSuccess }
        assertEquals(0, core.editCalls)
        assertEquals(Triple("tx", "person", DebtKind.LENT), core.linked)
    }

    @Test
    fun unmarkOnlyUnlinksExistingCoreLoanAndNeverEditsLedgerKind() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val person =
            Person(
                "person",
                "Pat",
                null,
                null,
                "#123456",
                null,
                null,
                true,
                PersonTotals(
                    "CNY",
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    false,
                    true,
                    emptyList(),
                ),
                core.transaction.dateTime,
            )
        core.onPeople = { listOf(person) }
        core.onDebts = {
            listOf(
                DebtEntry(
                    "debt",
                    "person",
                    DebtKind.LENT,
                    Money(BigDecimal.ONE, "CNY"),
                    core.transaction.dateTime,
                    null,
                    "loan",
                    "tx",
                    null,
                    emptyList(),
                )
            )
        }
        core.transaction = core.transaction.copy(debtId = "debt")
        val vm = keep(TransactionDetailViewModel(session(core), changes, files, preferences))
        vm.loadTransaction("tx")
        awaitState { vm.uiState.value.linkedLendBorrow != null }
        vm.unmarkAsLoan()
        runCurrent()
        assertNull(core.unlinkedDebt)
        core.transaction = core.transaction.copy(kind = TransactionKind.LOAN_OUT, categoryId = null)
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.transaction?.kind == TransactionKind.LOAN_OUT }
        vm.unmarkAsLoan()
        vm.unmarkAsLoan()
        awaitState { core.unlinkedDebt == "debt" }
        assertEquals(0, core.editCalls)
    }
}
