package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private fun summary(value: String) =
        LedgerSummary(
            "CNY",
            BigDecimal.ZERO,
            BigDecimal(value),
            BigDecimal(value).negate(),
            false,
            listOf("USD"),
            listOf(CurrencyTotals("USD", BigDecimal.ZERO, BigDecimal("10"))),
            listOf(CategoryTotal("category/string", BigDecimal(value))),
            emptyList(),
        )

    private fun api(handler: (String, Array<out Any?>) -> Any?) =
        Proxy.newProxyInstance(
            LedgerInterface::class.java.classLoader,
            arrayOf(LedgerInterface::class.java, Disposable::class.java),
        ) { _, method, args ->
            if (method.name == "destroy") Unit
            else handler(method.name.substringBefore('-'), args ?: emptyArray())
        } as LedgerInterface

    private fun fixture(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            block(store)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun mixedSelectionsExposeBothExactCoreTotalsIncludingSelectedCurrency() = fixture { store ->
        val native = summary("45.50").copy(income = BigDecimal("6000"),
            perCurrency = listOf(CurrencyTotals("USD", BigDecimal("900"), BigDecimal("7"))))
        val vm = AnalyticsViewModel(ChangeBus(), ledger = {
            api { method, _ -> when (method) {
                "summary" -> native
                "list" -> TransactionPage(emptyList(), 2u)
                else -> error(method)
            } }
        })
        store.put("vm", vm)
        vm.toggleTransactionTypeFilter(io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter.ALL)
        runCurrent()
        val both = listOf(io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter.INCOME,
            io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter.EXPENSE)
        assertEquals(both, trendTypes(vm.uiState.value.types))
        val point = vm.uiState.value.trend.values.first()
        assertSame(native, point)
        assertEquals(BigDecimal("6000"), point.chartAmount("CNY", true))
        assertEquals(BigDecimal("45.50"), point.chartAmount("CNY", false))
        assertEquals(BigDecimal("900"), point.chartAmount("USD", true))
        assertEquals(BigDecimal("7"), point.chartAmount("USD", false))
        vm.toggleTransactionTypeFilter(both[0])
        vm.toggleTransactionTypeFilter(both[1])
        runCurrent()
        assertEquals(both, trendTypes(vm.uiState.value.types))
        assertSame(native, vm.uiState.value.trend.values.first())
    }

    @Test
    fun initialLoadKeepsExactCoreRecordsAndRefreshesRelevantChanges() = fixture { store ->
        val returned = summary("123.45")
        var calls = 0
        val bus = ChangeBus()
        val vm =
            AnalyticsViewModel(
                bus,
                ledger = {
                    api { method, _ ->
                        when (method) {
                            "summary" -> {
                                calls++
                                returned
                            }
                            "list" -> TransactionPage(emptyList(), 8u)
                            else -> error(method)
                        }
                    }
                },
            )
        store.put("vm", vm)
        runCurrent()
        assertSame(returned, vm.uiState.value.summary)
        assertEquals(8u, vm.uiState.value.transactionCount)
        assertFalse(vm.uiState.value.summary!!.complete)
        assertTrue(vm.uiState.value.trend.values.all { it === returned })
        val before = calls
        bus.onChange(ChangeSet(listOf(Table.PEOPLE), emptyList(), ChangeOrigin.LOCAL))
        runCurrent()
        assertEquals(before, calls)
        bus.onChange(ChangeSet(listOf(Table.TRANSACTIONS), emptyList(), ChangeOrigin.LOCAL))
        runCurrent()
        assertTrue(calls > before)
    }

    @Test
    fun lateSummaryCannotReplaceNewCurrencyAndExactCustomWindow() = fixture { store ->
        var old: Continuation<LedgerSummary>? = null
        val vm =
            AnalyticsViewModel(
                ChangeBus(),
                ledger = {
                    api { method, args ->
                        when (method) {
                            "summary" ->
                                if ((args[0] as TransactionFilter).currency == "USD") {
                                    @Suppress("UNCHECKED_CAST")
                                    val c = args.last() as Continuation<LedgerSummary>
                                    old = c
                                    COROUTINE_SUSPENDED
                                } else summary("2")
                            "list" -> TransactionPage(emptyList(), 1u)
                            else -> error(method)
                        }
                    }
                },
            )
        store.put("vm", vm)
        runCurrent()
        vm.setCustomDateRange(JavaDate.parse("2026-09-02"), JavaDate.parse("2026-09-02"))
        runCurrent()
        vm.selectCurrency("USD")
        runCurrent()
        vm.selectCurrency("JPY")
        runCurrent()
        old!!.resume(summary("99"))
        runCurrent()
        assertEquals("JPY", vm.uiState.value.filter.currency)
        assertEquals(BigDecimal("2"), vm.uiState.value.summary!!.expense)
        assertEquals(2.toUByte(), vm.uiState.value.filter.from!!.day)
        assertEquals(3.toUByte(), vm.uiState.value.filter.to!!.day)
        assertEquals(1, vm.uiState.value.trend.size)
    }

    @Test
    fun chartPeriodNavigationRetainsTheExactQueriedWindow() = fixture { store ->
        val vm =
            AnalyticsViewModel(
                ChangeBus(),
                ledger = {
                    api { method, _ ->
                        when (method) {
                            "summary" -> summary("1")
                            "list" -> TransactionPage(emptyList(), 1u)
                            else -> error(method)
                        }
                    }
                },
            )
        store.put("vm", vm)
        runCurrent()
        vm.selectPeriod(TimePeriod.CURRENT_FY)
        runCurrent()
        val first = vm.uiState.value.trendFilters.values.first()
        assertEquals(1.toUByte(), first.from!!.day)
        assertEquals(2.toUByte(), first.to!!.month)
        assertEquals(1.toUByte(), first.to!!.day)
        assertEquals(vm.uiState.value.filter.to, vm.uiState.value.trendFilters.values.last().to)
    }

    @Test
    fun failureIsRetryableOnChangeAndSavedCustomRangeSurvivesRecreation() = fixture { store ->
        var fail = true
        val bus = ChangeBus()
        val saved =
            SavedStateHandle(
                mapOf("period" to TimePeriod.CUSTOM.name, "customDateRange" to longArrayOf(1, 2))
            )
        val vm =
            AnalyticsViewModel(
                bus,
                saved,
                ledger = {
                    api { method, _ ->
                        when (method) {
                            "summary" ->
                                if (fail) throw CoreException.Network(null) else summary("7")
                            "list" -> TransactionPage(emptyList(), 0u)
                            else -> error(method)
                        }
                    }
                },
            )
        store.put("vm", vm)
        runCurrent()
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        fail = false
        bus.onChange(ChangeSet(listOf(Table.EXCHANGE_RATES), emptyList(), ChangeOrigin.LOCAL))
        runCurrent()
        assertNull(vm.uiState.value.error)
        assertEquals(TimePeriod.CUSTOM, vm.uiState.value.period)
        assertEquals(JavaDate.ofEpochDay(1), vm.uiState.value.customRange!!.first)
    }
}
