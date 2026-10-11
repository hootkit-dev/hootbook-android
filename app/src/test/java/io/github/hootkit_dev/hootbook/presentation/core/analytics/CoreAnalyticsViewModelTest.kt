package io.github.hootkit_dev.hootbook.presentation.core.analytics

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
class CoreAnalyticsViewModelTest {
    private fun summary(value:String)=LedgerSummary("CNY",BigDecimal(value),BigDecimal.ZERO,BigDecimal(value),false,listOf("USD"),emptyList(),emptyList(),emptyList())
    private fun api(handler:(String,Array<out Any?>)->Any?)=Proxy.newProxyInstance(LedgerInterface::class.java.classLoader,arrayOf(LedgerInterface::class.java,Disposable::class.java)){_,method,args->if(method.name=="destroy")Unit else handler(method.name.substringBefore('-'),args?:emptyArray())} as LedgerInterface
    private fun model(block:suspend TestScope.(ViewModelStore)->Unit)=runTest{Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore();try{block(store)}finally{store.clear();Dispatchers.resetMain()}}
    @Test fun fullRangeSummaryAndDailyPagesUseCoreAndKeepIncompleteFlags()=model{store->
        val returned=summary("123.45");val vm=CoreAnalyticsViewModel(ChangeBus(),{api{method,_->when(method){"summary"->returned;"list"->TransactionPage(emptyList(),8u);else->error(method)}}})
        store.put("vm",vm);runCurrent();vm.apply(CoreAnalyticsQuery("2026-07-01","2026-10-10"));runCurrent()
        assertSame(returned,vm.state.value.summary);assertEquals(8u,vm.state.value.total);assertEquals(31,vm.state.value.days.size);assertNotNull(vm.state.value.olderThrough);assertFalse(vm.state.value.summary!!.complete)
        vm.loadOlderDays();runCurrent();assertEquals(62,vm.state.value.days.size);assertTrue(vm.state.value.days.all{it.summary===returned});assertEquals(8u,vm.state.value.total)
    }
    @Test fun staleDailyQueriesNeverReplaceNewFilterResult()=model{store->
        var old:Continuation<LedgerSummary>?=null;var delay=false
        val vm=CoreAnalyticsViewModel(ChangeBus(),{api{method,args->when(method){
            "summary"->if(delay&&(args[0] as TransactionFilter).currency=="USD"){@Suppress("UNCHECKED_CAST") val c=args.last() as Continuation<LedgerSummary>;old=c;COROUTINE_SUSPENDED}else summary("2")
            "list"->TransactionPage(emptyList(),1u);else->error(method)
        }}})
        store.put("vm",vm);runCurrent();delay=true;vm.apply(CoreAnalyticsQuery("2026-10-01","2026-10-01",currency="USD"));runCurrent();vm.apply(CoreAnalyticsQuery("2026-10-02","2026-10-02",currency="JPY"));runCurrent()
        old!!.resume(summary("99"));runCurrent();assertEquals("JPY",vm.state.value.query.currency);assertEquals(BigDecimal("2"),vm.state.value.summary!!.income)
    }
    @Test fun invalidDateRangeIsRetryableAndDoesNotRequestCore()=model{store->
        var calls=0;val vm=CoreAnalyticsViewModel(ChangeBus(),{api{method,_->calls++;if(method=="summary")summary("1")else TransactionPage(emptyList(),0u)}})
        store.put("vm",vm);runCurrent();val before=calls;vm.apply(CoreAnalyticsQuery("bad","2026-10-10"));runCurrent();assertEquals(before,calls);assertNotNull(vm.state.value.error);assertFalse(vm.state.value.loading)
    }
}
