package io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest : CoreViewModelTest() {
    @Test fun usesCoreSummaryAndScheduleAndLatestSelectionWhileRefreshing() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus()
        val vm = keep(SubscriptionsViewModel(CoreSession(backgroundScope) { core }, bus))
        vm.selectId(core.subscription.id); awaitState { !vm.uiState.value.isLoading }
        assertSame(core.subscriptionSummary, vm.uiState.value.summary); assertFalse(vm.uiState.value.summary!!.complete)
        assertSame(core.subscription, vm.uiState.value.selectedSubscription)
        assertEquals(-8, vm.uiState.value.selectedSubscription!!.daysUntil)
        val gate = CompletableDeferred<Unit>(); var entered = false
        core.onSubscriptions = { entered = true; withContext(NonCancellable) { gate.await() }; listOf(core.subscription) }
        bus.changed(Table.SUBSCRIPTIONS); awaitState { entered }; vm.selectId(null); gate.complete(Unit)
        awaitState { core.opened.get() == core.closed.get() }; assertNull(vm.uiState.value.selectedSubscription)
        core.subscriptionSummary = core.subscriptionSummary.copy(monthly = java.math.BigDecimal("876")); bus.changed(Table.EXCHANGE_RATES)
        awaitState { vm.uiState.value.summary?.monthly == java.math.BigDecimal("876") }
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun hiddenUndoAndMarkPaidUseCoreCommandsOnceAndFailureIsRetryable() = runTest {
        val core = FakePlansCore(); val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await(); error("save error") }
        val vm = keep(SubscriptionsViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        awaitState { !vm.uiState.value.isLoading }; vm.hideSubscription(core.subscription.id); vm.hideSubscription(core.subscription.id)
        awaitState { core.commandCalls == 1 }; gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.error); assertNull(vm.uiState.value.lastHiddenSubscription)
        core.onCommand = {}; vm.hideSubscription(core.subscription.id); awaitState { !vm.uiState.value.saving }
        assertEquals(core.subscription.id, vm.uiState.value.lastHiddenSubscription!!.id)
        vm.undoHide(); awaitState { !vm.uiState.value.saving }; assertNull(vm.uiState.value.lastHiddenSubscription)
        assertEquals(core.subscription.id to false, core.hiddenWrites.last())
        vm.markAsPaid(core.subscription); awaitState { !vm.uiState.value.saving }; assertEquals(4, core.commandCalls)
        assertEquals(core.opened.get(), core.closed.get())
    }
}
