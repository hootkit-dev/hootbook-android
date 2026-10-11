package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.ChangeOrigin
import dev.hootkit.core.ChangeSet
import dev.hootkit.core.Table
import dev.hootkit.platform.ChangeBus
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreNetWorthHistoryViewModelTest {
    private val date = LocalDate.of(2026, 10, 11)
    private fun result(value: String = "100", currency: String = "CNY") =
        CoreNetWorthHistoryResult(currency, listOf(CoreNetWorthHistoryPoint(date, BigDecimal(value), true)), true)
    private fun change(bus: ChangeBus, table: Table) = bus.onChange(ChangeSet(listOf(table), emptyList(), ChangeOrigin.LOCAL))
    private suspend fun TestScope.settle() { runCurrent(); advanceTimeBy(76); runCurrent() }
    private fun withModel(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(store) } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun startupAndBurstsCoalesceAndUnrelatedTablesOrRepeatedResumeDoNotReload() = withModel { store ->
        val bus = ChangeBus(); var calls = 0
        val vm = CoreNetWorthHistoryViewModel(bus, today = { date }) { _, _, _ -> calls++; result() }
        store.put("history", vm); vm.onResume(); settle()
        assertEquals(1, calls)
        repeat(10) { vm.onResume(); change(bus, Table.PEOPLE) }; settle()
        assertEquals(1, calls)
        repeat(10) { change(bus, Table.TRANSACTIONS); change(bus, Table.EXCHANGE_RATES) }; settle()
        assertEquals(2, calls)
    }

    @Test fun refreshAndFailureRetainLastGraphAndExplicitRetryCanReplaceIt() = withModel { store ->
        val bus = ChangeBus(); val reply = CompletableDeferred<CoreNetWorthHistoryResult>(); var calls = 0
        val vm = CoreNetWorthHistoryViewModel(bus, today = { date }) { _, _, _ ->
            when (++calls) { 1 -> result(); 2 -> reply.await(); else -> result("200") }
        }
        store.put("history", vm); settle()
        val previous = vm.state.value.data
        change(bus, Table.BALANCE_ANCHORS); settle()
        assertTrue(vm.state.value.refreshing); assertSame(previous, vm.state.value.data)
        reply.completeExceptionally(java.io.IOException("Synthetic failure")); runCurrent()
        assertTrue(vm.state.value.failed); assertFalse(vm.state.value.refreshing)
        assertSame(previous, vm.state.value.data)
        vm.retry(); settle()
        assertFalse(vm.state.value.failed); assertEquals(BigDecimal("200"), vm.state.value.data!!.points.single().amount)
    }

    @Test fun resultFromChangedRevisionIsDiscardedEvenBeforeDebouncedReplacementStarts() = withModel { store ->
        val bus = ChangeBus(); val reply = CompletableDeferred<CoreNetWorthHistoryResult>(); var calls = 0
        val vm = CoreNetWorthHistoryViewModel(bus, today = { date }) { _, _, _ -> if (++calls == 1) reply.await() else result("300") }
        store.put("history", vm); settle()
        change(bus, Table.SETTINGS)
        reply.complete(result("999"))
        runCurrent()
        assertNull(vm.state.value.data)
        settle()
        assertEquals(BigDecimal("300"), vm.state.value.data!!.points.single().amount)
    }

    @Test fun displayCurrencyRangeAndNewDayInvalidateWholeBatch() = withModel { store ->
        val display = MutableStateFlow<String?>(null); var currentDate = date
        val requested = mutableListOf<Triple<LocalDate, CoreHistoryRange, String?>>()
        val vm = CoreNetWorthHistoryViewModel(ChangeBus(), display, { currentDate }) { day, range, currency ->
            requested += Triple(day, range, currency); result(currency = currency ?: "CNY")
        }
        store.put("history", vm); settle()
        display.value = "USD"; settle(); assertEquals("USD", vm.state.value.data!!.currency)
        vm.chooseRange(CoreHistoryRange.ALL); settle(); assertEquals(CoreHistoryRange.ALL, requested.last().second)
        currentDate = date.plusDays(1); vm.onResume(); settle(); assertEquals(currentDate, requested.last().first)
        assertEquals(4, requested.size)
    }
}
