package io.github.hootkit_dev.hootbook.presentation.core.home

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.transactions.FakeLedgerCore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    @Test fun homeUsesCoreOverviewAndSummaryIncludingIncompleteFlags() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, changes)
        stores += ViewModelStore().also { it.put("vm", vm) }
        runCurrent()
        assertSame(core.overview, vm.state.value.overview)
        assertSame(core.summary, vm.state.value.summary)
        assertSame(core.subscriptionSummary, vm.state.value.subscriptions)
        assertSame(core.debtSummary, vm.state.value.debts)
        assertFalse(vm.state.value.subscriptions!!.complete)
        assertFalse(vm.state.value.debts!!.complete)
        assertEquals(1, core.budgetCalls)
        assertFalse(vm.state.value.overview!!.netWorthComplete)
        assertFalse(vm.state.value.summary!!.complete)
        assertEquals(listOf("USD"), vm.state.value.summary!!.missingCurrencies)
        assertFalse(vm.state.value.loading)
        core.assertHandlesReleased()
    }

    @Test fun businessChangesReloadCoreSummariesWithoutLocalArithmetic() = runTest {
        val core = FakeLedgerCore(); val changes = ChangeBus()
        val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, changes)
        stores += ViewModelStore().also { it.put("vm", vm) }; runCurrent()
        changes.onChange(ChangeSet(listOf(Table.BUDGET_LIMITS, Table.SUBSCRIPTIONS, Table.DEBTS), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        assertEquals(2, core.planSummaryCalls); assertEquals(2, core.budgetCalls)
        assertEquals(core.overview.netWorth, vm.state.value.overview!!.netWorth)
        assertSame(core.debtSummary, vm.state.value.debts)
        core.assertHandlesReleased()
    }

    @Test fun staleHomeSummaryNeverReplacesMoreRecentData() = runTest {
        val core = FakeLedgerCore(); val changes = ChangeBus(); val gate = CompletableDeferred<Unit>(); var first = true
        core.onSubscriptionSummary = {
            if (first) { first = false; withContext(NonCancellable) { gate.await() }; core.subscriptionSummary.copy(count = 99u) }
            else core.subscriptionSummary.copy(count = 2u)
        }
        val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, changes)
        stores += ViewModelStore().also { it.put("vm", vm) }; runCurrent()
        changes.onChange(ChangeSet(listOf(Table.SUBSCRIPTIONS), emptyList(), ChangeOrigin.LOCAL))
        runCurrent(); assertEquals(2u, vm.state.value.subscriptions!!.count)
        gate.complete(Unit); runCurrent(); assertEquals(2u, vm.state.value.subscriptions!!.count)
        assertFalse(vm.state.value.loading); core.assertHandlesReleased()
    }

    @Test fun homeDependencySetIncludesBalancesSettingsAndBrokerage() {
        assertTrue(HOME_TABLES.containsAll(setOf(Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
            Table.TRANSACTIONS, Table.SETTINGS, Table.BROKERAGE, Table.EXCHANGE_RATES,
            Table.BUDGETS, Table.BUDGET_LIMITS, Table.BUDGET_ACCOUNTS, Table.SUBSCRIPTIONS,
            Table.PEOPLE, Table.DEBTS, Table.CATEGORIES, Table.SUBCATEGORIES)))
    }
}
