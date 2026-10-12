package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
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
class CoreTransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    private fun TestScope.model(core: FakeLedgerCore, changes: ChangeBus = ChangeBus()): CoreTransactionsViewModel {
        val session = CoreSession(backgroundScope) { core }
        return CoreTransactionsViewModel(session, changes).also { vm ->
            stores += ViewModelStore().also { it.put("vm", vm) }
        }
    }

    @Test fun firstTemplateNavigationResolvesActualCoreDefaultsBeforeNullPrefill() = runTest {
        val core = FakeLedgerCore(); core.mainCurrency = "GBP"; core.defaultAccountId = "default-uuid"
        val vm = model(core)
        vm.initialize(null, null, templateId = "first-template"); runCurrent()
        assertEquals("GBP", vm.state.value.form!!.currency)
        assertEquals("default-uuid", vm.state.value.form!!.accountId)
        assertEquals("first-template", vm.state.value.form!!.merchant)
        core.assertHandlesReleased()
    }

    @Test fun delayedCoreDefaultsNeverOverrideUserModifiedFirstTemplateDraft() = runTest {
        val core = FakeLedgerCore(); val gate = CompletableDeferred<Unit>(); core.mainCurrency = "GBP"
        core.onSettings = { withContext(NonCancellable) { gate.await() } }
        val vm = model(core); vm.initialize(null, null, templateId = "first"); runCurrent()
        vm.updateForm(vm.state.value.form!!.copy(currency = "JPY", amount = "1500", merchant = "typed"))
        gate.complete(Unit); runCurrent()
        assertEquals("JPY", vm.state.value.form!!.currency); assertEquals("1500", vm.state.value.form!!.amount)
        assertEquals("typed", vm.state.value.form!!.merchant); assertFalse(vm.state.value.loadingSource)
        core.assertHandlesReleased()
    }

    @Test fun newerSourceStillResolvesDefaultsWhenFirstSourceIsPending() = runTest {
        val core = FakeLedgerCore(); val gate = CompletableDeferred<Unit>()
        core.mainCurrency = "GBP"; core.defaultAccountId = "real-default"
        core.onSettings = { withContext(NonCancellable) { gate.await() } }
        val vm = model(core); vm.initialize(null, null, templateId = "old"); runCurrent()
        core.onSettings = {}
        vm.applyTemplate("new"); runCurrent()
        assertEquals("new", vm.state.value.form!!.merchant)
        assertEquals("GBP", vm.state.value.form!!.currency)
        assertEquals("real-default", vm.state.value.form!!.accountId)
        gate.complete(Unit); runCurrent()
        assertEquals("new", vm.state.value.form!!.merchant)
        assertEquals("GBP", vm.state.value.form!!.currency); core.assertHandlesReleased()
    }

    @Test fun subscriptionPaymentPrefillsAndCommitsExactlyOneLedgerCommand() = runTest {
        val core = FakeLedgerCore()
        val vm = model(core); runCurrent()
        vm.initialize(null, null, subscriptionId = core.subscription.id); runCurrent()
        val form = vm.state.value.form!!
        assertEquals(TransactionKind.EXPENSE, form.kind)
        assertEquals("1500", form.amount); assertEquals("JPY", form.currency)
        assertNull("missing account is not guessed", form.accountId)
        assertNull("missing category is not guessed", form.categoryId)
        assertEquals("会员", form.merchant); assertEquals("订阅备注", form.notes)
        assertEquals(java.time.LocalDate.now(), java.time.LocalDateTime.parse(form.dateTime).toLocalDate())
        vm.updateForm(form.copy(accountId = "jpy-account", categoryId = "expense", dateTime = "2026-10-05T12:00"))
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, core.addCalls); assertEquals(0, core.paidCalls)
        assertEquals(core.subscription.id, core.lastDraft!!.subscriptionId)
        assertEquals(5u.toUByte(), core.lastDraft!!.dateTime.day)
        assertEquals(BigDecimal("1500"), core.lastDraft!!.amount)
        core.assertHandlesReleased()
    }

    @Test fun sourceQueryDoesNotOverwriteUserEditsEvenWhenNativeIgnoresCancellation() = runTest {
        val core = FakeLedgerCore(); val gate = CompletableDeferred<Unit>()
        core.onTemplatePrefill = { withContext(NonCancellable) { gate.await() }; TemplatePrefill(TransactionKind.INCOME, "old", null, null, null, null, null, null) }
        val vm = model(core); runCurrent(); vm.applyTemplate("old"); runCurrent()
        assertTrue(vm.state.value.loadingSource)
        vm.updateForm(vm.state.value.form!!.copy(merchant = "typed", amount = "42"))
        gate.complete(Unit); runCurrent()
        assertEquals("typed", vm.state.value.form!!.merchant); assertEquals("42", vm.state.value.form!!.amount)
        assertFalse(vm.state.value.loadingSource); core.assertHandlesReleased()
    }

    @Test fun newestTemplateSourceWinsAndBackDiscardsPendingPrefill() = runTest {
        val core = FakeLedgerCore(); val gate = CompletableDeferred<Unit>()
        core.onTemplatePrefill = { id -> if (id == "old") withContext(NonCancellable) { gate.await() }; TemplatePrefill(TransactionKind.EXPENSE, id, null, null, null, null, null, null) }
        val vm = model(core); runCurrent(); vm.applyTemplate("old"); runCurrent()
        vm.applyTemplate("new"); runCurrent(); assertEquals("new", vm.state.value.form!!.merchant)
        gate.complete(Unit); runCurrent(); assertEquals("new", vm.state.value.form!!.merchant)
        val second = CompletableDeferred<Unit>()
        core.onTemplatePrefill = { withContext(NonCancellable) { second.await() }; TemplatePrefill(TransactionKind.EXPENSE, "discard", null, null, null, null, null, null) }
        vm.applyTemplate("discard"); runCurrent(); assertTrue(vm.back())
        second.complete(Unit); runCurrent(); assertNull(vm.state.value.form); assertFalse(vm.state.value.loadingSource)
        assertEquals(0, core.addCalls); core.assertHandlesReleased()
    }

    @Test fun existingTransactionRejectsTemplateAndDetailNavigationDoesNotCreateALedgerEntry() = runTest {
        val core = FakeLedgerCore(); val vm = model(core); runCurrent()
        vm.initialize(null, "existing-string-id"); runCurrent(); vm.editTransaction()
        val original = vm.state.value.form
        vm.applyTemplate("ignored"); runCurrent()
        assertSame(original, vm.state.value.form); assertEquals(0, core.prefillCalls); assertEquals(0, core.addCalls)
        core.assertHandlesReleased()
    }

    @Test fun missingSubscriptionShowsErrorAndInitialSourceIsNotRepeated() = runTest {
        val core = FakeLedgerCore(); core.onSubscriptions = { emptyList() }
        val vm = model(core); runCurrent(); vm.initialize(null, null, subscriptionId = "missing"); runCurrent()
        assertNotNull(vm.state.value.error); assertFalse(vm.state.value.loadingSource)
        vm.updateForm(vm.state.value.form!!.copy(merchant = "keep"))
        vm.initialize(null, null, subscriptionId = core.subscription.id); runCurrent()
        assertEquals("keep", vm.state.value.form!!.merchant); assertEquals(0, core.addCalls)
        core.assertHandlesReleased()
    }

    @Test fun linkingExistingTransactionCreatesNoSecondLedgerWriteAndGuardsNavigation() = runTest {
        val core = FakeLedgerCore(); core.transaction = core.transaction.copy(kind = TransactionKind.LOAN_OUT)
        val gate = CompletableDeferred<Unit>(); core.onLink = { gate.await() }
        val vm = model(core); runCurrent(); vm.openDetail("tx"); runCurrent()
        vm.linkExistingTransaction("person-uuid", DebtKind.LENT); vm.linkExistingTransaction("other", null); runCurrent()
        assertEquals(1, core.linkCalls); assertEquals(0, core.addCalls)
        assertEquals(Triple("tx", "person-uuid", DebtKind.LENT), core.linked)
        assertTrue(vm.back()); vm.openDetail("other"); assertEquals("tx", vm.state.value.detailId)
        gate.complete(Unit); runCurrent(); assertFalse(vm.state.value.writing)
        assertEquals(0, core.addCalls); core.assertHandlesReleased()
    }

    @Test fun failedExistingLinkRetainsTargetAndCanRetry() = runTest {
        val core = FakeLedgerCore(); core.transaction = core.transaction.copy(kind = TransactionKind.LOAN_OUT)
        core.onLink = { throw IllegalArgumentException("link failure") }
        val vm = model(core); runCurrent(); vm.openDetail("tx"); runCurrent()
        vm.linkExistingTransaction("person", null); runCurrent()
        assertEquals("tx", vm.state.value.detailId); assertNotNull(vm.state.value.detail)
        assertNotNull(vm.state.value.error); assertEquals(0, core.addCalls)
        core.onLink = {}; vm.linkExistingTransaction("person", null); runCurrent()
        assertEquals(2, core.linkCalls); assertEquals(0, core.addCalls); core.assertHandlesReleased()
    }

    @Test fun latestFilterWinsEvenWhenOlderNativeQueryIgnoresCancellation() = runTest {
        val core = FakeLedgerCore()
        val oldQuery = CompletableDeferred<Unit>()
        core.onList = { filter, _ ->
            if (filter.text == "old") withContext(NonCancellable) { oldQuery.await() }
            TransactionPage(listOf(core.transaction.copy(merchant = filter.text ?: "all")), 1u)
        }
        val vm = model(core)
        runCurrent()
        vm.applyQuery(CoreTransactionQuery(text = "old"))
        runCurrent()
        vm.applyQuery(CoreTransactionQuery(text = "new"))
        runCurrent()
        assertEquals("new", vm.state.value.page!!.items.single().merchant)
        oldQuery.complete(Unit)
        runCurrent()
        assertEquals("new", vm.state.value.page!!.items.single().merchant)
        assertFalse(vm.state.value.loading)
        core.assertHandlesReleased()
    }

    @Test fun repeatedTapSubmitsOneWriteAndSeparateRefreshDoesNotCancelIt() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val commit = CompletableDeferred<Unit>()
        core.onAdd = { commit.await(); "tx" }
        val vm = model(core, changes)
        runCurrent()
        vm.newTransaction()
        vm.updateForm(vm.state.value.form!!.copy(amount = "35", categoryId = "category"))
        vm.save(); vm.save()
        runCurrent()
        assertTrue(vm.state.value.writing)
        assertEquals(1, core.addCalls)
        val draft = vm.state.value.form
        vm.openDetail("other"); vm.newTransaction(); vm.editTransaction()
        vm.applyQuery(CoreTransactionQuery(text = "must-not-change")); vm.nextPage(); vm.previousPage()
        assertTrue("system Back during commit must be consumed", vm.back())
        assertSame(draft, vm.state.value.form)
        assertNull(vm.state.value.detailId)
        assertEquals("", vm.state.value.query.text)
        changes.onChange(ChangeSet(listOf(Table.TRANSACTIONS), emptyList(), ChangeOrigin.LOCAL))
        runCurrent()
        assertNotNull(vm.state.value.form)
        commit.complete(Unit)
        runCurrent()
        assertFalse(vm.state.value.writing)
        assertNull(vm.state.value.form)
        assertEquals("tx", vm.state.value.detailId)
        core.assertHandlesReleased()
    }

    @Test fun failedWriteRetainsUserDraft() = runTest {
        val core = FakeLedgerCore()
        core.onAdd = { throw IllegalArgumentException("core validation") }
        val vm = model(core)
        runCurrent()
        vm.newTransaction()
        vm.updateForm(vm.state.value.form!!.copy(amount = "-9", merchant = "未丢失"))
        vm.save()
        runCurrent()
        assertEquals("-9", vm.state.value.form!!.amount)
        assertEquals("未丢失", vm.state.value.form!!.merchant)
        assertFalse(vm.state.value.writing)
        assertNotNull(vm.state.value.error)
        core.assertHandlesReleased()
    }

    @Test fun deletionTokenIsUsedForUndoWithoutLocalBalanceArithmetic() = runTest {
        val core = FakeLedgerCore()
        val vm = model(core)
        runCurrent()
        vm.openDetail("tx")
        runCurrent()
        vm.delete(); vm.delete()
        runCurrent()
        assertEquals(listOf("tx"), core.deletedIds)
        assertEquals("opaque-core-token", vm.state.value.undoToken)
        assertNull(vm.state.value.detailId)
        vm.undo()
        runCurrent()
        assertEquals("opaque-core-token", core.undoneToken)
        assertNull(vm.state.value.undoToken)
        core.assertHandlesReleased()
    }

    @Test fun backClosesEditorBeforeDetailAndOnlyIdleListDelegatesToHost() = runTest {
        val core = FakeLedgerCore()
        val vm = model(core)
        runCurrent()
        vm.openDetail("tx"); runCurrent()
        vm.editTransaction()
        assertNotNull(vm.state.value.form)
        assertTrue(vm.back())
        assertNull(vm.state.value.form)
        assertEquals("tx", vm.state.value.detailId)
        assertTrue(vm.back()); runCurrent()
        assertNull(vm.state.value.detailId)
        assertFalse(vm.back())
        core.assertHandlesReleased()
    }

    @Test fun undoInListConsumesBackAndKeepsCommandTargetUntilCompletion() = runTest {
        val core = FakeLedgerCore()
        val resumeUndo = CompletableDeferred<Unit>()
        core.onUndo = { resumeUndo.await() }
        val vm = model(core)
        runCurrent()
        vm.openDetail("tx"); runCurrent()
        vm.delete(); runCurrent()
        assertNull(vm.state.value.detailId)
        vm.undo(); runCurrent()
        assertTrue(vm.state.value.writing)
        assertTrue("Back on list during undo must not pop host route", vm.back())
        vm.openDetail("other"); vm.newTransaction()
        assertNull(vm.state.value.detailId)
        assertNull(vm.state.value.form)
        assertEquals("opaque-core-token", vm.state.value.undoToken)
        resumeUndo.complete(Unit); runCurrent()
        assertEquals("opaque-core-token", core.undoneToken)
        assertFalse(vm.state.value.writing)
        assertFalse("idle list Back delegates to host", vm.back())
        core.assertHandlesReleased()
    }

    @Test fun paginationUsesCoreOffsetAndChangingFilterResetsOffset() = runTest {
        val core = FakeLedgerCore()
        val offsets = mutableListOf<UInt>()
        core.onList = { _, page -> offsets += page.offset; TransactionPage(listOf(core.transaction), 103u) }
        val vm = model(core)
        runCurrent()
        vm.nextPage(); runCurrent()
        vm.nextPage(); runCurrent()
        assertEquals(100u, vm.state.value.offset)
        vm.applyQuery(CoreTransactionQuery(accountId = "string-account-id")); runCurrent()
        assertEquals(0u, vm.state.value.offset)
        assertEquals(listOf(0u, 50u, 100u, 0u), offsets)
        core.assertHandlesReleased()
    }

    @Test fun initialChangeSnapshotAndLaterInvalidationPreserveEditor() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val vm = model(core, changes)
        runCurrent()
        assertEquals(1, core.listCalls)
        vm.newTransaction()
        vm.updateForm(vm.state.value.form!!.copy(amount = "12.34", notes = "草稿"))
        runCurrent()
        val beforeInvalidation = core.listCalls
        changes.onChange(ChangeSet(listOf(Table.CATEGORIES, Table.TRANSACTIONS), emptyList(), ChangeOrigin.SYNC))
        runCurrent()
        assertEquals(beforeInvalidation + 1, core.listCalls)
        assertEquals("12.34", vm.state.value.form!!.amount)
        assertEquals("草稿", vm.state.value.form!!.notes)
        assertTrue(vm.back())
        assertNull(vm.state.value.form)
        assertFalse(vm.back())
    }
}

/** Generated NoPointer constructors deliberately prevent JNI calls; this tests UI orchestration only. */
internal class FakeLedgerCore : HootKitCore(NoPointer) {
    private val now = LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u)
    var transaction = Transaction("tx", TransactionKind.EXPENSE, BigDecimal("35"), "CNY", now,
        "account", null, null, null, "category", null, "商户", null, null, null, emptyList(), false, now, now)
    var mainCurrency = "CNY"
    var onGet: suspend (String) -> Transaction = { transaction.copy(id = it) }
    var defaultAccountId: String? = null
    var onSettings: suspend () -> Unit = {}
    var onList: suspend (TransactionFilter, PageRequest) -> TransactionPage = { _, _ -> TransactionPage(listOf(transaction), 1u) }
    var onAdd: suspend (TransactionDraft) -> String = { "tx" }
    val subscription = Subscription("sub/?中文&=", "会员", Money(BigDecimal("1500"), "JPY"),
        Cycle(1u, CycleUnit.MONTH), null, null, null, null, null, null, "订阅备注", false, null, false, false, null)
    var onSubscriptions: suspend () -> List<Subscription> = { listOf(subscription) }
    var onTemplatePrefill: suspend (String) -> TemplatePrefill = { TemplatePrefill(TransactionKind.EXPENSE, it, null, null, null, null, null, null) }
    val subscriptionSummary = SubscriptionSummary("CNY", BigDecimal("10"), BigDecimal("120"), BigDecimal("20"), 1u, false, listOf("JPY"))
    val debtSummary = DebtSummary("CNY", BigDecimal("8"), BigDecimal("3"), BigDecimal("5"), 2u, 1u, 1u, 1u, false)
    var onSubscriptionSummary: suspend () -> SubscriptionSummary = { subscriptionSummary }
    var planSummaryCalls = 0
    var budgetCalls = 0
    var paidCalls = 0
    var linkCalls = 0
    var linked: Triple<String, String, DebtKind?>? = null
    var onLink: suspend () -> Unit = {}
    var prefillCalls = 0
    var lastDraft: TransactionDraft? = null
    var onUndo: suspend (String) -> Unit = {}
    var lastBulk: Pair<List<String>, TransactionBulkPatch>? = null
    var purgedIds = emptyList<String>()
    var duplicateIds = emptyList<String>()
    var fromTransactionCalls = 0
    var createdSubscriptionFrom: Triple<String, Cycle, LocalDate?>? = null
    var symbols = emptyList<CustomCurrency>()
    var onConvert: suspend (BigDecimal, String, String) -> BigDecimal? = { amount, _, _ -> amount }
    var addCalls = 0
    var listCalls = 0
    var deletedIds = emptyList<String>()
    var undoneToken: String? = null
    val overview = AccountsOverview(emptyList(), Money(BigDecimal("999.99"), "CNY"), false, null, null)
    val summary = LedgerSummary("CNY", BigDecimal("123"), BigDecimal("45"), BigDecimal("78"), false, listOf("USD"), emptyList(), emptyList(), emptyList())
    var openedHandles = 0
    var closedHandles = 0
    var coreClosed = false
    fun assertHandlesReleased() {
        assertEquals("every temporary service handle must close", openedHandles, closedHandles)
        assertFalse("the application-owned core must remain open", coreClosed)
    }
    override fun accounts(): Accounts {
        openedHandles++
        return object : Accounts(NoPointer) {
            override suspend fun overview() = this@FakeLedgerCore.overview
            private var released = false
            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) { released = true; closedHandles++ }
                super.destroy()
            }
        }
    }
    override fun categories(): Categories {
        openedHandles++
        return object : Categories(NoPointer) {
            override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = emptyList<Category>()
            private var released = false
            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) { released = true; closedHandles++ }
                super.destroy()
            }
        }
    }
    override fun settings(): Settings {
        openedHandles++
        return object : Settings(NoPointer) {
            override suspend fun mainCurrency(): String { onSettings(); return this@FakeLedgerCore.mainCurrency }
            override suspend fun defaultAccount(): String? = defaultAccountId
            private var released = false
            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) { released = true; closedHandles++ }
                super.destroy()
            }
        }
    }
    override fun ledger(): Ledger {
        openedHandles++
        return object : Ledger(NoPointer) {
            override suspend fun list(filter: TransactionFilter, page: PageRequest): TransactionPage { listCalls++; return onList(filter, page) }
            override suspend fun get(transactionId: String) = onGet(transactionId)
            override suspend fun summary(filter: TransactionFilter) = this@FakeLedgerCore.summary
            override suspend fun add(draft: TransactionDraft): String { addCalls++; lastDraft = draft; return onAdd(draft) }
            override suspend fun edit(transactionId: String, draft: TransactionDraft) = Unit
            override suspend fun delete(transactionIds: List<String>): String { deletedIds = transactionIds; return "opaque-core-token" }
            override suspend fun bulkUpdate(transactionIds: List<String>, patch: TransactionBulkPatch) { lastBulk = transactionIds to patch }
            override suspend fun purge(transactionIds: List<String>) { purgedIds = transactionIds }
            override suspend fun findDuplicates(draft: TransactionDraft) = duplicateIds
            override suspend fun merchants(prefix: String, limit: UInt) = emptyList<String>()
            override suspend fun undo(undoToken: String) { onUndo(undoToken); undoneToken = undoToken }
            private var released = false
            // dev.hootkit.core.Disposable.use calls destroy(), not AutoCloseable.close().
            override fun destroy() {
                if (!released) { released = true; closedHandles++ }
                super.destroy()
            }
        }
    }
    override fun currency(): dev.hootkit.core.Currency {
        openedHandles++
        return object : dev.hootkit.core.Currency(NoPointer) {
            override suspend fun customCurrencies() = symbols
            override suspend fun convert(amount: BigDecimal, from: String, to: String) = onConvert(amount, from, to)
            private var released = false
            override fun destroy() { if (!released) { released = true; closedHandles++ }; super.destroy() }
        }
    }
    override fun budgets(): Budgets {
        openedHandles++
        return object : Budgets(NoPointer) {
            override suspend fun list(today: LocalDate, homeOnly: Boolean, displayCurrency: String?): List<Budget> { budgetCalls++; assertTrue(homeOnly); return emptyList() }
            private var released = false
            override fun destroy() { if (!released) { released = true; closedHandles++ }; super.destroy() }
        }
    }
    override fun plans(): Plans {
        openedHandles++
        return object : Plans(NoPointer) {
            override suspend fun people(includeArchived: Boolean, today: LocalDate) = emptyList<Person>()
            override suspend fun linkTransaction(transactionId: String, personId: String, kind: DebtKind?): String {
                linkCalls++; linked = Triple(transactionId, personId, kind); onLink(); return "debt-link"
            }
            override suspend fun subscriptions(today: LocalDate, includeHidden: Boolean): List<Subscription> { assertTrue(includeHidden); return onSubscriptions() }
            override suspend fun templatePrefill(templateId: String): TemplatePrefill { prefillCalls++; return onTemplatePrefill(templateId) }
            override suspend fun subscriptionSummary(today: LocalDate): SubscriptionSummary { planSummaryCalls++; return onSubscriptionSummary() }
            override suspend fun debtSummary(today: LocalDate) = this@FakeLedgerCore.debtSummary
            override suspend fun subscriptionFromTransaction(transactionId: String, cycle: Cycle, endDate: LocalDate?): String {
                fromTransactionCalls++; createdSubscriptionFrom = Triple(transactionId, cycle, endDate); return "subscription-created"
            }
            override suspend fun markSubscriptionPaid(subscriptionId: String, today: LocalDate) { paidCalls++ }
            private var released = false
            override fun destroy() { if (!released) { released = true; closedHandles++ }; super.destroy() }
        }
    }
    override fun destroy() { coreClosed = true; super.destroy() }
    override suspend fun shutdown() = Unit
}
