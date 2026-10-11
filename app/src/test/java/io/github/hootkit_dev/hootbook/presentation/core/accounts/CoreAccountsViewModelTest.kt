package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreAccountsViewModelTest {
    private fun api(handler: (String, Array<out Any?>) -> Any?): AccountsInterface = Proxy.newProxyInstance(
        AccountsInterface::class.java.classLoader, arrayOf(AccountsInterface::class.java),
    ) { _, method, args -> handler(method.name.substringBefore('-'), args ?: emptyArray()) } as AccountsInterface
    private fun overview(amount: String = "0") = AccountsOverview(emptyList(), Money(BigDecimal(amount), "CNY"), true, null, null)
    private fun change(table: Table) = ChangeSet(listOf(table), emptyList(), ChangeOrigin.LOCAL)

    @Test fun initialQueryAndOnlyRelevantInvalidationsRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var queries = 0
            val changes = ChangeBus()
            val model = CoreAccountsViewModel(changes, { api { method, _ ->
                check(method == "overview"); queries++; overview()
            } })
            store.put("test", model)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(1, queries)
            changes.onChange(change(Table.PEOPLE)); runCurrent(); assertEquals(1, queries)
            listOf(Table.EXCHANGE_RATES, Table.BROKERAGE, Table.SETTINGS, Table.TRANSACTIONS, Table.BALANCE_ANCHORS).forEach {
                changes.onChange(change(it)); runCurrent()
            }
            assertEquals(6, queries)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancelledStaleQueryNeverReplacesLatestSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var first: Continuation<AccountsOverview>? = null
            var calls = 0
            val model = CoreAccountsViewModel(ChangeBus(), { api { method, arguments ->
                check(method == "overview")
                if (++calls == 1) {
                    @Suppress("UNCHECKED_CAST")
                    first = arguments.last() as Continuation<AccountsOverview>
                    COROUTINE_SUSPENDED
                } else overview("200")
            } })
            store.put("test", model)
            runCurrent()
            model.refresh(); runCurrent()
            assertEquals(BigDecimal("200"), model.state.value.overview!!.netWorth.amount)
            first!!.resume(overview("100")); runCurrent()
            assertEquals(BigDecimal("200"), model.state.value.overview!!.netWorth.amount)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun doubleSubmitAndQueryRefreshDoNotDuplicateOrCancelWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var writes = 0
            var write: Continuation<String>? = null
            val model = CoreAccountsViewModel(ChangeBus(), { api { method, arguments -> when (method) {
                "overview" -> overview()
                "create" -> {
                    writes++
                    @Suppress("UNCHECKED_CAST")
                    write = arguments.last() as Continuation<String>
                    COROUTINE_SUSPENDED
                }
                else -> error(method)
            } } })
            store.put("test", model)
            runCurrent()
            model.edit(); model.draftChanged(CoreAccountDraft(name = "新账户"))
            model.saveAccount(); model.saveAccount(); runCurrent()
            assertEquals(1, writes)
            assertTrue(model.state.value.saving)
            model.refresh(); runCurrent()
            assertTrue(model.state.value.saving)
            write!!.resume("uuid-new"); runCurrent()
            assertFalse(model.state.value.saving)
            assertNull(model.state.value.draft)
            assertEquals(1L, model.state.value.commandRevision)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun commandFailurePreservesDraftAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val model = CoreAccountsViewModel(ChangeBus(), { api { method, _ -> when (method) {
                "overview" -> overview()
                "create" -> throw CoreException.Conflict(ConflictReason.ACCOUNT_EXISTS)
                else -> error(method)
            } } })
            store.put("test", model); runCurrent()
            model.edit(); model.draftChanged(CoreAccountDraft(name = "保留草稿")); model.saveAccount(); runCurrent()
            assertEquals("保留草稿", model.state.value.draft!!.name)
            assertFalse(model.state.value.saving)
            assertNotNull(model.state.value.error)
            assertEquals(0L, model.state.value.commandRevision)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    private fun account(id: String = "account-uuid") = Account(
        id = id, name = "账户", last4 = null, kind = AccountKind.BANK, mainCurrency = "CNY",
        pockets = listOf(Pocket("CNY", BigDecimal.ZERO, null, null)), total = Money(BigDecimal.ZERO, "CNY"),
        totalComplete = true, securities = null, broker = null, creditLimit = null, statementDay = null,
        dueDay = null, institutionKey = null, icon = "icon", color = "#001122", archived = false,
        isDefault = false, sortOrder = 0, createdAt = LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u),
    )
    @Test fun backClosesEditorThenDetailsBeforeHostPop() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val row = account()
            val model = CoreAccountsViewModel(ChangeBus(), { api { method, _ -> when (method) {
                "overview" -> overview().copy(accounts = listOf(row))
                "anchors" -> emptyList<BalanceAnchor>()
                "transactionCount" -> 0u
                else -> error(method)
            } } })
            store.put("test", model); runCurrent()
            model.select(row.id); runCurrent()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            assertSame(row, model.state.value.account)
            assertEquals("CNY", model.state.value.currency)
            model.edit(row)
            assertTrue(model.back()); assertNull(model.state.value.draft)
            assertEquals(row.id, model.state.value.selectedId)
            assertTrue(model.back()); runCurrent(); assertNull(model.state.value.selectedId)
            assertFalse(model.back())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun savingBackAndSelectionsKeepCapturedAccountAndPocket() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val row = account()
            var target: String? = null
            var write: Continuation<Unit>? = null
            val model = CoreAccountsViewModel(ChangeBus(), { api { method, args -> when (method) {
                "overview" -> overview().copy(accounts = listOf(row, account("other-uuid")))
                "anchors" -> emptyList<BalanceAnchor>()
                "transactionCount" -> 0u
                "update" -> {
                    target = args[0] as String
                    @Suppress("UNCHECKED_CAST")
                    write = args.last() as Continuation<Unit>
                    COROUTINE_SUSPENDED
                }
                else -> error(method)
            } } })
            store.put("test", model); runCurrent()
            model.select(row.id); runCurrent()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            assertSame(row, model.state.value.account)
            assertEquals("CNY", model.state.value.currency)
            model.edit(row); model.saveAccount(); runCurrent()
            assertTrue(model.back())
            model.select("other-uuid"); model.selectCurrency("USD")
            assertNull(model.transactionNavigationTarget())
            assertEquals(row.id, model.state.value.selectedId)
            assertEquals("CNY", model.state.value.currency)
            assertNotNull(model.state.value.draft)
            assertTrue(model.state.value.navigationBlocked)
            assertEquals(row.id, target)
            write!!.resume(Unit); runCurrent()
            assertFalse(model.state.value.saving)
            assertFalse(model.state.value.navigationBlocked)
            assertEquals(row.id, model.transactionNavigationTarget())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
