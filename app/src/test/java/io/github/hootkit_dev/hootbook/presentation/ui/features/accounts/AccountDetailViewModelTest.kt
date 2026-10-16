package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.lifecycle.SavedStateHandle
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest : CoreViewModelTest() {
    @Test fun detailUsesCoreSeriesAnchorsAndSummaryAndRefreshes() = runTest {
        val core = FakeAccountsCore(); val changes = ChangeBus()
        val vm = keep(AccountDetailViewModel(CoreSession(backgroundScope) { core }, changes, SavedStateHandle(mapOf("accountId" to core.account.id))))
        awaitState { !vm.uiState.value.isLoading }
        assertEquals(core.account.id, vm.uiState.value.account?.id)
        assertEquals(core.anchors, vm.uiState.value.anchors)
        assertSame(core.summary, vm.uiState.value.summary)
        assertEquals(BigDecimal("555"), vm.uiState.value.balanceSeries.single().balance)
        core.onGet = { error("offline") }; changes.changed(Table.ACCOUNTS)
        awaitState { vm.uiState.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        core.account = core.account.copy(name = "Updated"); core.onGet = { core.account }; changes.changed(Table.ACCOUNTS)
        awaitState { vm.uiState.value.account?.name == "Updated" && vm.uiState.value.error == null }
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun newestCurrencyAndDateRangeWinAgainstUncancellableQueries() = runTest {
        val core = FakeAccountsCore(); val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val vm = keep(AccountDetailViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), SavedStateHandle(mapOf("accountId" to core.account.id))))
        awaitState { !vm.uiState.value.isLoading }
        core.onSeries = { code ->
            if (code == "GBP") withContext(NonCancellable) { started.complete(Unit); gate.await() }
            listOf(BalanceDay(LocalDate(2026, 10u, 11u), if (code == "USD") BigDecimal("2") else BigDecimal("1")))
        }
        vm.selectDateRange(DateRange.LAST_7_DAYS); awaitState { started.isCompleted }
        vm.selectCurrency("USD"); vm.selectDateRange(DateRange.LAST_YEAR)
        awaitState { !vm.uiState.value.isLoading && vm.uiState.value.currency == "USD" }
        gate.complete(Unit); awaitState { core.opened.get() == core.closed.get() }
        assertEquals(DateRange.LAST_YEAR, vm.uiState.value.selectedDateRange)
        assertEquals(BigDecimal("2"), vm.uiState.value.balanceSeries.single().balance)
    }
    @Test fun calibrationAndAnchorCommandsAreGuardedAndKeepErrors() = runTest {
        val core = FakeAccountsCore(); val gate = CompletableDeferred<Unit>(); var completed = 0
        val vm = keep(AccountDetailViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), SavedStateHandle(mapOf("accountId" to core.account.id))))
        awaitState { !vm.uiState.value.isLoading }
        core.onCommand = { gate.await(); error("cannot save") }
        vm.calibrate(BigDecimal("25"), "USD") { completed++ }; vm.calibrate(BigDecimal("25"), "USD") { completed++ }
        awaitState { core.calibrations == 1 }; gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.error); assertEquals(0, completed)
        core.onCommand = {}; vm.calibrate(BigDecimal("25"), "USD") { completed++ }
        awaitState { !vm.uiState.value.saving }; assertEquals(1, completed)
        assertEquals(Triple(core.account.id, "USD", BigDecimal("25")), core.lastCalibration)
        vm.updateAnchor("anchor/中文", BigDecimal("70")); awaitState { !vm.uiState.value.saving }
        assertEquals("anchor/中文" to AnchorPatch(balance = BigDecimal("70")), core.lastAnchorPatch)
        vm.deleteAnchor("anchor/中文"); vm.deleteAnchor("anchor/中文")
        awaitState { !vm.uiState.value.saving }; assertEquals(1, core.anchorDeletions)
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun scrollPagingAndChangeRefreshDoNotDropAlreadyVisiblePages() = runTest {
        val core = FakeAccountsCore(); val changes = ChangeBus(); val requests = mutableListOf<PageRequest>()
        core.onList = { filter, page ->
            assertEquals(listOf(core.account.id), filter.accountIds); requests += page
            TransactionPage((page.offset.toInt() until minOf(65, page.offset.toInt() + page.limit.toInt())).map { core.transaction.copy(id = "tx/$it") }, 65u)
        }
        val vm = keep(AccountDetailViewModel(CoreSession(backgroundScope) { core }, changes, SavedStateHandle(mapOf("accountId" to core.account.id))))
        awaitState { !vm.uiState.value.isLoading }; assertEquals(50, vm.uiState.value.transactions.size)
        vm.loadMore(); vm.loadMore(); awaitState { !vm.uiState.value.loadingMore }; assertEquals(65, vm.uiState.value.transactions.size)
        changes.changed(Table.TRANSACTIONS); awaitState { requests.size >= 3 && vm.uiState.value.transactions.size == 65 }
        assertEquals(listOf(0u, 50u, 0u), requests.map { it.offset })
        assertEquals(65u, requests.last().limit)
    }
    @Test fun markStatementPaidAnchorsStatementEndToZeroWhilePreservingCurrentBalance() = runTest {
        val core = FakeAccountsCore()
        core.account = core.account.copy(
            kind = AccountKind.CREDIT_CARD,
            mainCurrency = "USD",
            pockets = listOf(Pocket("USD", BigDecimal("294.94"), null, null, null)),
            statementDay = 31u,
            dueDay = 15u,
        )
        core.cardStatus = CardStatus(
            accountId = core.account.id,
            currency = "USD",
            statementDate = LocalDate(2026, 9u, 30u),
            dueDate = LocalDate(2026, 10u, 15u),
            statementAmount = BigDecimal("489.09"),
            repaidSince = BigDecimal.ZERO,
            outstanding = BigDecimal("489.09"),
            overdue = true,
        )
        // Case 1: Anchor already exists after statement end (2026-10-11 > 2026-09-30T23:59:59)
        val vm = keep(AccountDetailViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), SavedStateHandle(mapOf("accountId" to core.account.id))))
        awaitState { !vm.uiState.value.isLoading }
        vm.markStatementPaid()
        awaitState { !vm.uiState.value.saving }
        assertEquals(
            listOf(BigDecimal.ZERO to LocalDateTime(2026, 9u, 30u, 23u, 59u, 59u, 0u)),
            core.recordedCalibrations,
        )
        // Case 2: No anchor after statement end -> preserves current balance first, then zeros statement end
        core.recordedCalibrations.clear()
        core.anchors = emptyList()
        vm.markStatementPaid()
        awaitState { !vm.uiState.value.saving }
        assertEquals(
            listOf(
                BigDecimal("294.94") to null,
                BigDecimal.ZERO to LocalDateTime(2026, 9u, 30u, 23u, 59u, 59u, 0u),
            ),
            core.recordedCalibrations,
        )
    }
}
