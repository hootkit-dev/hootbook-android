package io.github.hootkit_dev.hootbook.presentation.core.home

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.core.analytics.coreDate
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreHomeProductionQueriesTest {
    private fun context()=ApplicationProvider.getApplicationContext<Application>().also {File(it.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(it.filesDir,"datastore").deleteRecursively()}
    @Test fun realThreeParameterConstructorQueriesRecentDueHeatmapAndCoreDisplayConversion()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore()
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm)
            val state=vm.state.first{!it.loading&&!it.widgetsLoading&&it.displayNetWorth!=null}
            assertSame(core.transaction,state.recent.single());assertSame(core.subscription,state.upcoming.single())
            assertEquals(BigDecimal("1.25"),state.displayNetWorth!!.amount);assertEquals(Triple(BigDecimal("100"),"CNY","USD"),core.lastConversion)
            assertEquals("USD",core.displayCurrency);assertEquals(mapOf("PTS" to "★"),state.customSymbols)
            val first=JavaDate.now().minusWeeks(26).with(java.time.DayOfWeek.MONDAY)
            assertEquals(first.coreDate(),state.heatmap.first().date);assertEquals(JavaDate.now().coreDate(),state.heatmap.last().date);assertTrue(state.heatmap.all{it.transactions==3u})
            assertEquals(JavaDate.now().plusDays(7).coreDate(),core.dueThrough);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.dueReads>0)
            assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun preferencesHideAndUnhideDriveProductionQueriesWithoutInventingAmounts()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());val hidden=setOf(HomeWidget.RECENT_TRANSACTIONS,HomeWidget.UPCOMING_SUBSCRIPTIONS,HomeWidget.TRANSACTION_HEATMAP);prefs.update{it.copy(hiddenHomeWidgets=hidden)};val core=WidgetCore()
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm);vm.state.first{!it.loading&&!it.widgetsLoading}
            assertEquals(0,core.recentReads);assertEquals(0,core.dailyReads);assertEquals(0,core.dueReads)
            prefs.update{it.copy(hiddenHomeWidgets=emptySet())};val visible=vm.state.first{!it.widgetsLoading&&it.heatmap.isNotEmpty()}
            assertSame(core.overview.netWorth,visible.overview!!.netWorth);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.dueReads>0)
            prefs.update{it.copy(hiddenHomeWidgets=hidden)};vm.state.first{!it.widgetsLoading&&it.heatmap.isEmpty()};assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun cancelledOldProductionConversionNeverOverwritesNewPreference()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore();val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore();var delayed=false
            core.conversion={_,_,to->if(to=="USD"&&!delayed){delayed=true;entered.complete(Unit);withContext(NonCancellable){release.await()};BigDecimal("99")}else BigDecimal("3")}
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm);entered.await()
            prefs.update{it.copy(unifiedDisplayCurrency="EUR")};vm.state.first{!it.widgetsLoading&&it.displayNetWorth?.currency=="EUR"}
            release.complete(Unit);runCurrent();assertEquals("EUR",vm.state.value.displayNetWorth!!.currency);assertEquals(BigDecimal("3"),vm.state.value.displayNetWorth!!.amount)
            assertEquals(core.opened,core.destroyed)
        }finally{release.complete(Unit);store.clear();Dispatchers.resetMain()}
    }
}

/** Complete generated service overrides for the production widget path; no native calls in JVM. */
private class WidgetCore:HootKitCore(NoPointer){
    private val day=JavaDate.now();private val at=JavaTime.now().let{LocalDateTime(it.year,it.monthValue.toUByte(),it.dayOfMonth.toUByte(),it.hour.toUByte(),it.minute.toUByte(),0u,0u)}
    val transaction=Transaction("tx",TransactionKind.EXPENSE,BigDecimal("5"),"CNY",at,null,null,null,null,null,null,"Merchant",null,null,null,emptyList(),false,at,at)
    val subscription=Subscription(
        id="subscription",name="Subscription",amount=Money(BigDecimal.TEN,"CNY"),cycle=Cycle(1u,CycleUnit.MONTH),
        endDate=null,nextDue=day.plusDays(2).coreDate(),lastPaid=null,accountId=null,categoryId=null,subcategoryId=null,notes=null,
        hidden=false,monthlyEquivalent=BigDecimal.TEN,ended=false,overdue=false,daysUntil=2,
    )
    val overview=AccountsOverview(emptyList(),Money(BigDecimal("100"),"CNY"),true,null,null)
    private val summary=LedgerSummary("CNY",BigDecimal("10"),BigDecimal("5"),BigDecimal("5"),true,emptyList(),emptyList(),emptyList(),emptyList())
    var opened=0;var destroyed=0;var recentReads=0;var dailyReads=0;var dueReads=0
    var displayCurrency:String?=null;var dueThrough:LocalDate?=null;var lastConversion:Triple<BigDecimal,String,String>?=null
    var conversion:suspend(BigDecimal,String,String)->BigDecimal?={_,_,_->BigDecimal("1.25")}
    override fun accounts():Accounts {opened++;return object:Accounts(NoPointer){override suspend fun overview()=this@WidgetCore.overview;private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun ledger():Ledger {opened++;return object:Ledger(NoPointer){
        override suspend fun summary(filter:TransactionFilter)=this@WidgetCore.summary
        override suspend fun list(filter:TransactionFilter,page:PageRequest):TransactionPage=if(filter.from==null){recentReads++;TransactionPage(listOf(transaction),1u)}else{dailyReads++;TransactionPage(emptyList(),3u)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun budgets():Budgets {opened++;return object:Budgets(NoPointer){override suspend fun list(today:LocalDate,homeOnly:Boolean,displayCurrency:String?):List<Budget>{assertTrue(homeOnly);this@WidgetCore.displayCurrency=displayCurrency;return emptyList()};private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun plans():Plans {opened++;return object:Plans(NoPointer){
        override suspend fun subscriptionSummary(today:LocalDate)=SubscriptionSummary("CNY",BigDecimal.TEN,BigDecimal("120"),BigDecimal.TEN,1u,true,emptyList())
        override suspend fun debtSummary(today:LocalDate)=DebtSummary("CNY",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0u,0u,0u,0u,true)
        override suspend fun dueSubscriptions(today:LocalDate,through:LocalDate):List<Subscription>{dueReads++;dueThrough=through;return listOf(subscription)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun currency():Currency {opened++;return object:Currency(NoPointer){
        override suspend fun customCurrencies()=listOf(CustomCurrency("PTS","Points","★"))
        override suspend fun convert(amount:BigDecimal,from:String,to:String):BigDecimal?{lastConversion=Triple(amount,from,to);return conversion(amount,from,to)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun destroy(){super.destroy()}
    override suspend fun shutdown()=Unit
}
