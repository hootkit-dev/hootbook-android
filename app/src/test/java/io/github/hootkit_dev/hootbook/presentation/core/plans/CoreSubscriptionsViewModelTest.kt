package io.github.hootkit_dev.hootbook.presentation.core.plans

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
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

internal val PLAN_TODAY = LocalDate(2026, 10u, 10u)
internal fun planSubscription(id: String = "subscription-uuid") = Subscription(id, "会员", Money(BigDecimal("30"), "CNY"),
    Cycle(1u, CycleUnit.MONTH), null, LocalDate(2026, 10u, 15u), null, "account-uuid", "cat-uuid", null, null,
    false, BigDecimal("30"), false, false, 5)
internal fun planTemplate(id: String = "template-uuid") = QuickTemplate(id, "早餐", TransactionKind.EXPENSE, "商户", "cat-uuid", null,
    BigDecimal("21.5"), true, "account-uuid", "CNY", null, 0)
internal fun planSuggestion() = TemplateSuggestion("key", "早餐", TransactionKind.EXPENSE, "商户", "cat-uuid", null,
    BigDecimal("21.5"), "account-uuid", "CNY", 3u, LocalDateTime(2026, 10u, 1u, 12u, 0u, 0u, 0u))
internal fun planSummary() = SubscriptionSummary("USD", BigDecimal("1.25"), BigDecimal("15"), BigDecimal("10"), 2u, false, listOf("EUR"))
internal fun plansProxy(handler: (String, Array<out Any?>) -> Any?): PlansInterface = Proxy.newProxyInstance(
    PlansInterface::class.java.classLoader, arrayOf(PlansInterface::class.java),
) { _, method, args -> handler(method.name.substringBefore('-'), args ?: emptyArray()) } as PlansInterface

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreSubscriptionsViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }
    private fun keep(vm: CoreSubscriptionsViewModel) = vm.also { stores += ViewModelStore().also { it.put("vm", vm) } }
    private fun model(bus: ChangeBus = ChangeBus(), handler: (String, Array<out Any?>) -> Any?) = keep(
        CoreSubscriptionsViewModel(bus, plans = { plansProxy { method, args ->
            if (method == "subscriptionSummary") planSummary() else handler(method, args)
        } }, today = { PLAN_TODAY }))

    @Test fun latestHiddenFilterWinsEvenWhenCancelledQueryLaterReturns() = runTest {
        var old: Continuation<List<Subscription>>? = null
        val visible = planSubscription("visible")
        val hidden = planSubscription("hidden").copy(hidden = true)
        val vm = model { method, args -> check(method == "subscriptions")
            if (args[1] == true) listOf(visible, hidden) else {
                @Suppress("UNCHECKED_CAST")
                old = args.last() as Continuation<List<Subscription>>; COROUTINE_SUSPENDED
            }
        }
        runCurrent(); vm.includeHidden(true); runCurrent()
        assertSame(hidden, vm.state.value.subscriptions.last())
        old!!.resume(listOf(visible)); runCurrent()
        assertEquals(2, vm.state.value.subscriptions.size); assertTrue(vm.state.value.includeHidden)
    }
    @Test fun savesOnceAndBlocksBackSelectionFilterAndOutboundNavigationUntilCommit() = runTest {
        var pending: Continuation<String>? = null
        var calls = 0
        var target: SubscriptionDraft? = null
        val vm = model { method, args -> when (method) {
            "subscriptions" -> emptyList<Subscription>()
            "createSubscription" -> { calls++; target = args[0] as SubscriptionDraft
                @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<String>; COROUTINE_SUSPENDED }
            else -> error(method)
        } }
        runCurrent(); vm.create(); vm.updateForm(vm.state.value.form!!.copy(name = "会员", amount = "30"))
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, calls); assertTrue(vm.back()); assertFalse(vm.canNavigate())
        vm.select("other"); vm.includeHidden(true); vm.create()
        assertNull(vm.state.value.selectedId); assertFalse(vm.state.value.includeHidden); assertEquals("会员", vm.state.value.form!!.name)
        assertEquals(PLAN_TODAY, target!!.nextDue)
        pending!!.resume("created-uuid"); runCurrent()
        assertFalse(vm.state.value.saving); assertNull(vm.state.value.form); assertEquals("created-uuid", vm.state.value.selectedId)
    }
    @Test fun failedWriteRetainsEveryEditableFieldAndRetryCreatesOnlyAfterSuccess() = runTest {
        var calls = 0
        val vm = model { method, _ -> when (method) {
            "subscriptions" -> emptyList<Subscription>()
            "createSubscription" -> { calls++; if (calls == 1) throw IllegalArgumentException("Core validation"); "created" }
            else -> error(method)
        } }
        runCurrent(); vm.create(); val draft = vm.state.value.form!!.copy(name = "季度", amount = "30", every = "3", notes = "保留")
        vm.updateForm(draft); vm.save(); runCurrent()
        assertEquals(draft, vm.state.value.form); assertNotNull(vm.state.value.error); assertFalse(vm.state.value.saving)
        vm.save(); runCurrent(); assertEquals(2, calls); assertNull(vm.state.value.form)
    }
    @Test fun editingUpdatesTheSameStringIdInsteadOfCreating() = runTest {
        var updated: Pair<String, SubscriptionDraft>? = null
        val row = planSubscription()
        val vm = model { method, args -> when (method) {
            "subscriptions" -> listOf(row)
            "updateSubscription" -> { updated = args[0] as String to args[1] as SubscriptionDraft; Unit }
            else -> error(method)
        } }
        runCurrent(); vm.select(row.id); runCurrent(); vm.edit(); vm.updateForm(vm.state.value.form!!.copy(every = "3"))
        vm.save(); runCurrent()
        assertEquals(row.id, updated!!.first); assertEquals(3u, updated!!.second.cycle.every)
    }
    @Test fun hiddenSubscriptionUsesCoreUndoWithoutLedgerCommand() = runTest {
        var row = planSubscription()
        val writes = mutableListOf<Pair<String, Boolean>>()
        val vm = model { method, args -> when (method) {
            "subscriptions" -> listOf(row)
            "setSubscriptionHidden" -> { val pair = args[0] as String to args[1] as Boolean; writes += pair; row = row.copy(hidden = pair.second); Unit }
            else -> error("unexpected ledger or plans command $method")
        } }
        runCurrent(); vm.select(row.id); runCurrent(); vm.toggleHidden(); runCurrent()
        assertEquals(row.id, vm.state.value.undoHiddenId); assertTrue(vm.state.value.selected!!.hidden)
        vm.undoHidden(); runCurrent(); assertNull(vm.state.value.undoHiddenId)
        assertEquals(listOf(row.id to true, row.id to false), writes)
    }
    @Test fun markPaidOnlyDelegatesScheduleAndNeverCreatesTransaction() = runTest {
        val row = planSubscription()
        var marked: Pair<String, LocalDate>? = null
        val vm = model { method, args -> when (method) {
            "subscriptions" -> listOf(row)
            "markSubscriptionPaid" -> { marked = args[0] as String to args[1] as LocalDate; Unit }
            else -> error(method)
        } }
        runCurrent(); vm.select(row.id); runCurrent(); vm.markPaid(); runCurrent()
        assertEquals(row.id to PLAN_TODAY, marked)
    }
    @Test fun permanentDeleteClearsSelectionAndUndoNotTransactionsLocally() = runTest {
        var rows = listOf(planSubscription())
        var deleted: String? = null
        val vm = model { method, args -> when (method) {
            "subscriptions" -> rows
            "deleteSubscription" -> { deleted = args[0] as String; rows = emptyList(); Unit }
            else -> error(method)
        } }
        runCurrent(); vm.select(rows.single().id); runCurrent(); vm.delete(); runCurrent()
        assertEquals("subscription-uuid", deleted); assertNull(vm.state.value.selectedId)
    }
    @Test fun initialSummaryUsesGeneratedAmountsAndIncompleteFlagUnchanged() = runTest {
        val vm = model { method, _ -> check(method == "subscriptions"); emptyList<Subscription>() }
        runCurrent(); val summary = vm.state.value.summary!!
        assertEquals(BigDecimal("1.25"), summary.monthly); assertFalse(summary.complete); assertEquals(listOf("EUR"), summary.missingCurrencies)
    }
    @Test fun changesRefreshSubscriptionsWithoutLosingAnOpenDraft() = runTest {
        var reads = 0
        val bus = ChangeBus()
        val vm = model(bus) { method, _ -> check(method == "subscriptions"); reads++; emptyList<Subscription>() }
        runCurrent(); vm.create(); val draft = vm.state.value.form!!; val before = reads
        bus.onChange(ChangeSet(listOf(Table.SUBSCRIPTIONS), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        assertTrue(reads > before); assertSame(draft, vm.state.value.form)
    }
    @Test fun backClosesEditorThenSelectedRowBeforeHostNavigation() = runTest {
        val row = planSubscription()
        val vm = model { method, _ -> check(method == "subscriptions"); listOf(row) }
        runCurrent(); vm.select(row.id); runCurrent(); vm.edit()
        assertTrue(vm.back()); assertNull(vm.state.value.form); assertEquals(row.id, vm.state.value.selectedId)
        assertTrue(vm.back()); runCurrent(); assertNull(vm.state.value.selectedId); assertFalse(vm.back())
    }
    @Test fun productionServiceHandlesAreDestroyedOnQueryAndFailedCommandWithoutClosingRoot() = runTest {
        val root = FakePlansCore()
        val vm = keep(CoreSubscriptionsViewModel(CoreSession(backgroundScope) { root }, ChangeBus()))
        runCurrent(); root.assertReleased()
        vm.create(); vm.updateForm(vm.state.value.form!!.copy(amount = "10", name = "会员")); root.failWrite = true
        vm.save(); runCurrent(); assertNotNull(vm.state.value.error); assertNotNull(vm.state.value.form)
        root.assertReleased()
    }
    @Test fun cancelledRefreshDestroysServiceHandleAndKeepsRootAlive() = runTest {
        val root = FakePlansCore()
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        root.onSubscriptions = { pending.await(); emptyList() }
        val vm = keep(CoreSubscriptionsViewModel(CoreSession(backgroundScope) { root }, ChangeBus()))
        runCurrent(); root.onSubscriptions = { emptyList() }; vm.refresh(); runCurrent()
        root.assertReleased(); assertFalse(vm.state.value.loading)
    }
}

/** SDK 28 NoPointer UI orchestration fixture: does not load a native library. */
internal class FakePlansCore : HootKitCore(NoPointer) {
    var opened = 0; var destroyed = 0; var rootDestroyed = false; var failWrite = false
    var onSubscriptions: suspend () -> List<Subscription> = { emptyList() }
    fun assertReleased() { assertEquals(opened, destroyed); assertFalse(rootDestroyed) }
    override fun plans(): Plans {
        opened++
        return object : Plans(NoPointer) {
            override suspend fun subscriptions(today: LocalDate, includeHidden: Boolean) = onSubscriptions()
            override suspend fun subscriptionSummary(today: LocalDate) = planSummary()
            override suspend fun templates() = emptyList<QuickTemplate>()
            override suspend fun templateSuggestions(today: LocalDate) = emptyList<TemplateSuggestion>()
            override suspend fun createSubscription(draft: SubscriptionDraft): String { if (failWrite) error("failed"); return "new" }
            override suspend fun createTemplate(draft: QuickTemplateDraft): String { if (failWrite) error("failed"); return "new" }
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun accounts(): Accounts {
        opened++
        return object : Accounts(NoPointer) {
            override suspend fun overview() = AccountsOverview(emptyList(), Money(BigDecimal.ZERO, "CNY"), true, null, null)
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun categories(): Categories {
        opened++
        return object : Categories(NoPointer) {
            override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = emptyList<Category>()
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun settings(): Settings {
        opened++
        return object : Settings(NoPointer) {
            override suspend fun mainCurrency() = "CNY"
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun destroy() { rootDestroyed = true; super.destroy() }
}
