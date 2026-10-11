package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreBudgetHistoricalDetailTest {
    private fun date(s:String)=budgetDate(s)
    private val today=date("2026-10-10")
    private val historical=BudgetPeriodSummary(date("2026-09-01"),date("2026-09-30"),BigDecimal("100"),BigDecimal("9"),false,false)
    private fun row(start:LocalDate)=Budget("b","Budget",BudgetKind.EXPENSE,BudgetPeriod.MONTHLY,Money(BigDecimal("100"),"CNY"),date("2026-01-01"),null,null,true,"#00AA00",
        BudgetProgress(start,if(start==historical.start)historical.end else date("2026-10-31"),30u,1u,BigDecimal("9"),BigDecimal("91"),false,BigDecimal("3"),BigDecimal("0"),false,listOf("USD"),emptyList(),listOf(CategorySpend("category",BigDecimal("9")))),null,
        LocalDateTime(2026,1u,1u,0u,0u,0u,0u),LocalDateTime(2026,10u,1u,0u,0u,0u,0u))
    @Test fun historicalDetailRequestsSelectedCoreDateAndPreservesCategoryAndMissingFlags()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore();val getDates=mutableListOf<LocalDate>();var destroyed=0
        val vm=CoreBudgetsViewModel(ChangeBus(),today={today},context={BudgetQueryContext(emptyList(),emptyList(),"USD")}){
            Proxy.newProxyInstance(BudgetsInterface::class.java.classLoader,arrayOf(BudgetsInterface::class.java,Disposable::class.java)){_,method,args->when(method.name.substringBefore('-')){
                "list"->listOf(row(date("2026-10-01")))
                "get"->{val selected=args[1] as LocalDate;getDates+=selected;row(if(selected==historical.start)selected else date("2026-10-01"))}
                "history"->listOf(historical)
                "destroy"->{destroyed++;Unit}
                else->error(method.name)
            }} as BudgetsInterface
        }
        try{store.put("vm",vm);runCurrent();vm.select("b");runCurrent();vm.history(true);vm.historyPeriod(historical);runCurrent()
            assertEquals(historical.start,getDates.last());val detail=vm.state.value.historyDetail!!;assertEquals(BigDecimal("9"),detail.current.byCategory.single().spent);assertFalse(detail.current.complete);assertEquals(listOf("USD"),detail.current.missingCurrencies);assertTrue(destroyed>=3)
            assertTrue(vm.back());assertNull(vm.state.value.historyDetail);assertTrue(vm.state.value.showHistory)
        }finally{store.clear();Dispatchers.resetMain()}
    }
}
