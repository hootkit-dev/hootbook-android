package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAccountsViewModelTest : CoreViewModelTest() {
    private fun input() = NewAccount("Wallet", null, AccountKind.WALLET, "GBP", BigDecimal("123"), null,
        null, null, null, null, "icon", "#123456", listOf(NewCurrency("USD", BigDecimal("9"))))
    @Test fun loadsCoreRecordsRefreshesAndRecoversFromQueryFailure() = runTest {
        val core = FakeAccountsCore(); val changes = ChangeBus()
        val vm = keep(ManageAccountsViewModel(CoreSession(backgroundScope) { core }, changes))
        awaitState { !vm.uiState.value.isLoading }
        assertSame(core.overview, vm.uiState.value.overview)
        assertSame(core.institutions, vm.uiState.value.institutions)
        assertEquals("GBP", vm.uiState.value.defaultCurrency)
        core.onOverview = { error("offline") }; changes.changed(Table.ACCOUNTS)
        awaitState { vm.uiState.value.errorMessage == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        core.overview = core.overview.copy(netWorth = Money(BigDecimal("777"), "GBP"))
        core.onOverview = { core.overview }; changes.changed(Table.BALANCE_ANCHORS)
        awaitState { vm.uiState.value.overview?.netWorth?.amount == BigDecimal("777") }
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun unlinkedBrokerageSnapshotsAreVisibleAndRefreshOnBrokerageChanges() = runTest {
        val core = FakeAccountsCore(); val changes = ChangeBus()
        val snapshot = BrokerageAccount("U123", LocalDate(2026, 10u, 11u), emptyList(),
            listOf(CashBalance("USD", BigDecimal("10093"), null)), null,
            listOf(Money(BigDecimal("10093"), "USD")), Money(BigDecimal("77777"), "GBP"), true)
        val connection = BrokerageConnection("42", "ibkr_flex", "IBKR", core.now, null, true, listOf(snapshot))
        core.connections = listOf(connection)
        val vm = keep(ManageAccountsViewModel(CoreSession(backgroundScope) { core }, changes))
        awaitState { !vm.uiState.value.isLoading }
        assertSame(connection, vm.uiState.value.connections.single())
        assertSame(snapshot.total, vm.uiState.value.connections.single().accounts.single().total)
        assertNull(snapshot.linkedAccountId)
        core.connections = emptyList(); changes.changed(Table.BROKERAGE)
        awaitState { vm.uiState.value.connections.isEmpty() }
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun creationForwardsPocketInputsAndGuardsDoubleSubmitAndErrors() = runTest {
        val core = FakeAccountsCore(); val gate = CompletableDeferred<Unit>(); var saved = 0
        core.onCommand = { gate.await(); error("save error") }
        val vm = keep(ManageAccountsViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.addAccount(input()) { saved++ }; vm.addAccount(input()) { saved++ }
        awaitState { core.creates == 1 }; assertTrue(vm.uiState.value.isSavingAccount)
        gate.complete(Unit); awaitState { !vm.uiState.value.isSavingAccount }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.accountSaveError); assertEquals(0, saved)
        core.onCommand = {}; vm.addAccount(input()) { saved++ }
        awaitState { !vm.uiState.value.isSavingAccount }
        assertEquals(input(), core.newAccount); assertEquals(1, saved)
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun metadataEditDoesNotCalibrateAndDefaultAndDeleteKeepOpaqueIds() = runTest {
        val core = FakeAccountsCore()
        val vm = keep(ManageAccountsViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.editAccount(core.account, input()); awaitState { !vm.uiState.value.isSavingAccount }
        assertEquals(listOf(core.account.id to "USD"), core.addedPockets)
        assertEquals(core.account.id, core.patch?.first); assertEquals(0, core.calibrations)
        vm.setDefault(core.account.id); awaitState { !vm.uiState.value.isSavingAccount }
        assertEquals(core.account.id, core.defaultId)
        vm.deleteAccount(core.account.id); awaitState { !vm.uiState.value.isSavingAccount }
        assertEquals(core.account.id to false, core.deleted)
    }
}
