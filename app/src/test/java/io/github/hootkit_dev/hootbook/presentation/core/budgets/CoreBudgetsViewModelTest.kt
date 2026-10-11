package io.github.hootkit_dev.hootbook.presentation.core.budgets

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
class CoreBudgetsViewModelTest {
    private val date = budgetDate("2026-10-10")
    private fun row(id: String) = Budget(id, id, BudgetKind.EXPENSE, BudgetPeriod.MONTHLY, Money(BigDecimal("100"), "CNY"),
        date, null, null, true, "#4CAF50", BudgetProgress(date, date, 1u, 1u, BigDecimal("71.11"), BigDecimal("28.89"),
        false, BigDecimal("28.89"), BigDecimal.ZERO, false, listOf("USD"), emptyList(), emptyList()), null,
        LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u), LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u))
    private fun api(onDestroy: () -> Unit = {}, handler: (String, Array<out Any?>) -> Any?): BudgetsInterface = Proxy.newProxyInstance(
        BudgetsInterface::class.java.classLoader, arrayOf(BudgetsInterface::class.java, Disposable::class.java),
    ) { _, method, args ->
        if (method.name == "destroy") { onDestroy(); Unit }
        else handler(method.name.substringBefore('-'), args ?: emptyArray())
    } as BudgetsInterface
    private fun ordinary(method: String, args: Array<out Any?>, rows: List<Budget>): Any? = when (method) {
        "list" -> rows
        "get" -> rows.first { it.id == args[0] }
        "history" -> listOf(BudgetPeriodSummary(date, date, BigDecimal("100"), BigDecimal("71.11"), false, false))
        else -> error(method)
    }
    private fun runModel(test: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try { test(store) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun coreProgressAndHistoryStayGeneratedAndIncomplete() = runModel { store ->
        val row = row("budget-uuid"); var historyMax: UInt? = null
        val model = CoreBudgetsViewModel(ChangeBus(), today = { date }) { api { method, args ->
            if (method == "history") historyMax = (args[2] as Number).toInt().toUInt()
            ordinary(method, args, listOf(row))
        } }
        store.put("test", model); runCurrent(); model.select(row.id); runCurrent()
        assertSame(row, model.state.value.selected)
        assertFalse(model.state.value.selected!!.current.complete)
        assertEquals(listOf("USD"), model.state.value.selected!!.current.missingCurrencies)
        assertFalse(model.state.value.history.single().complete)
        assertEquals(24u, historyMax)
        assertNull(model.state.value.error)
    }
    @Test fun staleDetailCannotOverwriteNewStringIdSelection() = runModel { store ->
        val a = row("a"); val b = row("b"); var old: Continuation<Budget>? = null
        val model = CoreBudgetsViewModel(ChangeBus(), today = { date }) { api { method, args ->
            if (method == "get" && args[0] == "a") {
                @Suppress("UNCHECKED_CAST")
                old = args.last() as Continuation<Budget>; COROUTINE_SUSPENDED
            } else ordinary(method, args, listOf(a, b))
        } }
        store.put("test", model); runCurrent(); model.select("a"); runCurrent(); model.select("b"); runCurrent()
        assertSame(b, model.state.value.selected)
        old!!.resume(a); runCurrent()
        assertSame(b, model.state.value.selected)
        assertEquals("b", model.state.value.selectedId)
    }
    @Test fun savingPreventsDoubleCreateAndConsumesBackSelectionAndEditorChanges() = runModel { store ->
        var calls = 0; var write: Continuation<String>? = null; val rows = mutableListOf<Budget>()
        val model = CoreBudgetsViewModel(ChangeBus(), today = { date }) { api { method, args ->
            if (method == "create") {
                calls++
                @Suppress("UNCHECKED_CAST")
                write = args.last() as Continuation<String>; COROUTINE_SUSPENDED
            } else ordinary(method, args, rows)
        } }
        store.put("test", model); runCurrent(); model.edit(); model.draftChanged(model.state.value.draft!!.copy(amount = "100"))
        model.save("月度预算"); runCurrent(); model.save("月度预算")
        assertTrue(model.back()); model.select("other"); model.history(true); model.edit(); model.dismissEditor()
        assertTrue(model.state.value.navigationBlocked)
        assertNull(model.state.value.selectedId)
        assertNotNull(model.state.value.draft)
        assertEquals(1, calls)
        val created = row("created-uuid"); rows += created; write!!.resume(created.id); runCurrent()
        assertEquals(created.id, model.state.value.selectedId)
        assertNull(model.state.value.draft)
        assertFalse(model.state.value.saving)
    }
    @Test fun failedLimitCanRetryOnExistingBudgetWithoutCreatingAgain() = runModel { store ->
        val row = row("created-uuid"); var creates = 0; var limits = 0
        val model = CoreBudgetsViewModel(ChangeBus(), today = { date }) { api { method, args -> when (method) {
            "create" -> { creates++; row.id }
            "setLimit" -> { limits++; if (limits == 1) throw IllegalStateException("fixture failure"); Unit }
            else -> ordinary(method, args, listOf(row))
        } } }
        store.put("test", model); runCurrent(); model.select(row.id); runCurrent()
        model.editLimit(row, Category("category-uuid", CategoryKind.EXPENSE, null, "餐饮", "餐饮", "icon", "#112233", 0, false, emptyList()))
        model.limitAmountChanged("50")
        model.saveLimit(); runCurrent()
        assertEquals(row.id, model.state.value.selectedId)
        assertEquals(io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText(IllegalStateException("fixture failure")),model.state.value.error)
        assertFalse(model.state.value.error!!.contains("fixture failure"))
        assertEquals("50", model.state.value.limitDraft!!.amount)
        assertEquals(row.id, model.state.value.limitDraft!!.budgetId)
        val revision = model.state.value.commandRevision
        model.saveLimit(); runCurrent()
        assertNull(model.state.value.limitDraft)
        assertEquals(0, creates); assertEquals(2, limits)
        assertEquals(revision + 1, model.state.value.commandRevision)
        assertNull(model.state.value.error)
    }
    @Test fun failedBudgetSaveRetainsDraftAndEveryHandleIsDestroyed() = runModel { store ->
        var opened = 0; var destroyed = 0
        val model = CoreBudgetsViewModel(ChangeBus(), today = { date }) {
            opened++; api(onDestroy = { destroyed++ }) { method, args ->
                if (method == "create") throw IllegalArgumentException("invalid fixture")
                ordinary(method, args, emptyList())
            }
        }
        store.put("test", model); runCurrent(); model.edit(); model.draftChanged(model.state.value.draft!!.copy(amount = "100", name = "保留草稿"))
        model.save("预算"); runCurrent()
        assertEquals("保留草稿", model.state.value.draft!!.name)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.saving)
        assertTrue(opened >= 2)
        assertEquals(opened, destroyed)
        model.edit()
        assertNull(model.state.value.error) // A fresh dialog does not show the previous command error.
    }
    @Test fun budgetChildTableInvalidatesAndBackExitsEditorHistoryDetailThenHost() = runModel { store ->
        val row = row("budget-uuid"); var reads = 0; val changes = ChangeBus()
        val model = CoreBudgetsViewModel(changes, today = { date }) { api { method, args ->
            if (method == "list") reads++
            ordinary(method, args, listOf(row))
        } }
        store.put("test", model); runCurrent(); model.select(row.id); runCurrent()
        val before = reads
        changes.onChange(ChangeSet(listOf(Table.BUDGET_LIMITS), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        assertTrue(reads > before)
        model.history(true); model.edit(row)
        assertTrue(model.back()); assertNull(model.state.value.draft)
        assertTrue(model.back()); assertFalse(model.state.value.showHistory)
        assertTrue(model.back()); runCurrent(); assertNull(model.state.value.selectedId)
        assertFalse(model.back())
    }
}
