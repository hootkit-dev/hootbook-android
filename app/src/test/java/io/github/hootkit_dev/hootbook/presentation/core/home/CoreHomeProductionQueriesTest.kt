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
    @Test fun realThreeParameterConstructorQueriesRecentSubscriptionsHeatmapAndCoreDisplayConversion()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore()
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm)
            val state=vm.state.first{!it.loading&&!it.widgetsLoading&&!it.heatmapLoading&&it.displayNetWorth!=null}
            assertSame(core.transaction,state.recent.single());assertSame(core.subscription,state.upcoming.single())
            assertEquals(BigDecimal("1.25"),state.displayNetWorth!!.amount);assertEquals(Triple(BigDecimal("100"),"CNY","USD"),core.lastConversion)
            assertEquals("USD",core.displayCurrency);assertEquals(mapOf("PTS" to "★"),state.customSymbols)
            val first=JavaDate.now().minusWeeks(26).with(java.time.DayOfWeek.MONDAY)
            assertEquals(first.coreDate(),state.heatmap.first().date);assertEquals(JavaDate.now().coreDate(),state.heatmap.last().date);assertTrue(state.heatmap.all{it.transactions==3u})
            assertEquals(false,core.includeHidden);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.subscriptionReads>0)
            assertTrue(core.summaryFilters.any { it.from?.day == 1.toUByte() && it.from?.month == JavaDate.now().monthValue.toUByte() && it.to?.day == 1.toUByte() })
            assertTrue(core.summaryFilters.any { it.from?.month == JavaDate.now().minusMonths(1).monthValue.toUByte() })
            assertSame(core.summary, state.previousSummary)
            assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun preferencesHideAndUnhideDriveProductionQueriesWithoutInventingAmounts()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());val hidden=setOf(HomeWidget.RECENT_TRANSACTIONS,HomeWidget.UPCOMING_SUBSCRIPTIONS,HomeWidget.TRANSACTION_HEATMAP);prefs.update{it.copy(hiddenHomeWidgets=hidden)};val core=WidgetCore()
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm);vm.state.first{!it.loading&&!it.widgetsLoading}
            assertEquals(0,core.recentReads);assertEquals(0,core.dailyReads);assertEquals(0,core.subscriptionReads)
            prefs.update{it.copy(hiddenHomeWidgets=emptySet())};val visible=vm.state.first{!it.widgetsLoading&&it.heatmap.isNotEmpty()}
            assertSame(core.overview.netWorth,visible.overview!!.netWorth);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.subscriptionReads>0)
            prefs.update{it.copy(hiddenHomeWidgets=hidden)};vm.state.first{!it.widgetsLoading&&it.heatmap.isEmpty()};assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun cancelledOldProductionConversionNeverOverwritesNewPreference()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore();val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore();var delayed=false
            core.conversion={_,_,to->if(to=="USD"&&!delayed){delayed=true;entered.complete(Unit);withContext(NonCancellable){release.await()};BigDecimal("99")}else BigDecimal("3")}
            val vm=CoreHomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs);store.put("vm",vm);entered.await()
            prefs.update{it.copy(unifiedDisplayCurrency="EUR")};vm.state.first{!it.widgetsLoading&&!it.heatmapLoading&&it.displayNetWorth?.currency=="EUR"}
            release.complete(Unit);runCurrent();assertEquals("EUR",vm.state.value.displayNetWorth!!.currency);assertEquals(BigDecimal("3"),vm.state.value.displayNetWorth!!.amount)
            assertEquals(core.opened,core.destroyed)
        }finally{release.complete(Unit);store.clear();Dispatchers.resetMain()}
    }

    @Test fun refreshRetainsCoherentContentAndPublishesActivityBeforeHeatmap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val conversionEntered = CompletableDeferred<Unit>(); val releaseConversion = CompletableDeferred<Unit>()
        val heatmapEntered = CompletableDeferred<Unit>(); val releaseHeatmap = CompletableDeferred<Unit>()
        try {
            val prefs = CoreUiPreferencesStore(context())
            prefs.update { it.copy(unifiedDisplayEnabled = true, unifiedDisplayCurrency = "USD") }
            val core = WidgetCore()
            val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs)
            store.put("vm", vm)
            val original = vm.state.first { it.widgetsLoaded && !it.heatmapLoading }
            val reads = core.overviewReads
            core.overview = core.overview.copy(netWorth = Money(BigDecimal("200"), "CNY"))
            core.transaction = core.transaction.copy(merchant = "Updated merchant")
            core.conversion = { _, _, _ -> conversionEntered.complete(Unit); releaseConversion.await(); BigDecimal("2.50") }
            core.beforeDay = { heatmapEntered.complete(Unit); releaseHeatmap.await() }
            vm.refresh(); conversionEntered.await()
            vm.refresh(); vm.refresh(); runCurrent()
            assertEquals(reads + 1, core.overviewReads)
            vm.state.value.let { refreshing ->
                assertTrue(refreshing.refreshing)
                assertSame(original.overview, refreshing.overview)
                assertSame(original.displayNetWorth, refreshing.displayNetWorth)
                assertSame(original.recent, refreshing.recent)
                assertSame(original.upcoming, refreshing.upcoming)
                assertSame(original.heatmap, refreshing.heatmap)
                assertEquals(original.customSymbols, refreshing.customSymbols)
            }
            releaseConversion.complete(Unit); heatmapEntered.await()
            vm.state.value.let { activity ->
                assertFalse(activity.loading); assertFalse(activity.refreshing); assertFalse(activity.widgetsLoading)
                assertTrue(activity.heatmapLoading)
                assertSame(core.overview, activity.overview)
                assertEquals(BigDecimal("2.50"), activity.displayNetWorth!!.amount)
                assertEquals(core.overview.netWorth.amount, activity.displayNetWorth!!.originalAmount)
                assertSame(core.transaction, activity.recent.single())
                assertSame(original.heatmap, activity.heatmap)
            }
            releaseHeatmap.complete(Unit)
            vm.state.first { !it.heatmapLoading }
            assertEquals(core.opened, core.destroyed)
        } finally {
            releaseConversion.complete(Unit); releaseHeatmap.complete(Unit)
            store.clear(); Dispatchers.resetMain()
        }
    }

    @Test fun failedActivityRefreshKeepsLastSuccessfulAmountsAndRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(context())
            prefs.update { it.copy(unifiedDisplayEnabled = true, unifiedDisplayCurrency = "USD") }
            val core = WidgetCore()
            val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs)
            store.put("vm", vm)
            val original = vm.state.first { it.widgetsLoaded && !it.heatmapLoading }
            core.overview = core.overview.copy(netWorth = Money(BigDecimal("300"), "CNY"))
            core.beforeRecent = { error("Unavailable") }
            vm.refresh()
            val failed = vm.state.first { it.widgetError != null }
            assertSame(original.overview, failed.overview)
            assertSame(original.displayNetWorth, failed.displayNetWorth)
            assertSame(original.recent, failed.recent)
            assertSame(original.heatmap, failed.heatmap)
            assertTrue(failed.widgetsLoaded)
            assertFalse(failed.loading || failed.refreshing || failed.widgetsLoading || failed.heatmapLoading)
            assertEquals(core.opened, core.destroyed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedHeatmapDoesNotDiscardAlreadyRefreshedActivity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(context()); val core = WidgetCore()
            val vm = CoreHomeViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs)
            store.put("vm", vm)
            val original = vm.state.first { it.widgetsLoaded && !it.heatmapLoading }
            core.transaction = core.transaction.copy(merchant = "New merchant")
            core.beforeDay = { error("Unavailable") }
            vm.refresh()
            val failed = vm.state.first { it.widgetError != null }
            assertSame(core.transaction, failed.recent.single())
            assertSame(original.heatmap, failed.heatmap)
            assertTrue(failed.widgetsLoaded)
            assertFalse(failed.heatmapLoading)
            assertEquals(core.opened, core.destroyed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

/** Complete generated service overrides for the production widget path; no native calls in JVM. */
private class WidgetCore:HootKitCore(NoPointer){
    private val day=JavaDate.now();private val at=JavaTime.now().let{LocalDateTime(it.year,it.monthValue.toUByte(),it.dayOfMonth.toUByte(),it.hour.toUByte(),it.minute.toUByte(),0u,0u)}
    var transaction=Transaction("tx",TransactionKind.EXPENSE,BigDecimal("5"),"CNY",at,null,null,null,null,null,null,"Merchant",null,null,null,emptyList(),false,at,at)
    val subscription=Subscription(
        id="subscription",name="Subscription",amount=Money(BigDecimal.TEN,"CNY"),cycle=Cycle(1u,CycleUnit.MONTH),
        endDate=null,nextDue=day.plusDays(2).coreDate(),lastPaid=null,accountId=null,categoryId=null,subcategoryId=null,notes=null,
        hidden=false,monthlyEquivalent=BigDecimal.TEN,ended=false,overdue=false,daysUntil=2,
    )
    var overview=AccountsOverview(emptyList(),Money(BigDecimal("100"),"CNY"),true,null,null)
    val summary=LedgerSummary("CNY",BigDecimal("10"),BigDecimal("5"),BigDecimal("5"),true,emptyList(),emptyList(),emptyList(),emptyList())
    val summaryFilters = mutableListOf<TransactionFilter>()
    var opened=0;var destroyed=0;var recentReads=0;var dailyReads=0;var subscriptionReads=0
    var overviewReads = 0
    var beforeRecent: suspend () -> Unit = {}
    var beforeDay: suspend () -> Unit = {}
    var displayCurrency:String?=null;var includeHidden:Boolean?=null;var lastConversion:Triple<BigDecimal,String,String>?=null
    var conversion:suspend(BigDecimal,String,String)->BigDecimal?={_,_,_->BigDecimal("1.25")}
    override fun accounts():Accounts {opened++;return object:Accounts(NoPointer){override suspend fun overview(): AccountsOverview { overviewReads++; return this@WidgetCore.overview };private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun ledger():Ledger {opened++;return object:Ledger(NoPointer){
        override suspend fun summary(filter:TransactionFilter): LedgerSummary { summaryFilters += filter; return this@WidgetCore.summary }
        override suspend fun list(filter:TransactionFilter,page:PageRequest):TransactionPage=if(filter.from==null){recentReads++;beforeRecent();TransactionPage(listOf(transaction),1u)}else{dailyReads++;beforeDay();TransactionPage(emptyList(),3u)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun categories(): Categories { opened++; return object : Categories(NoPointer) {
        override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = emptyList<Category>()
        private var released = false; override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
    } }
    override fun budgets():Budgets {opened++;return object:Budgets(NoPointer){override suspend fun list(today:LocalDate,homeOnly:Boolean,displayCurrency:String?):List<Budget>{assertTrue(homeOnly);this@WidgetCore.displayCurrency=displayCurrency;return emptyList()};private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun plans():Plans {opened++;return object:Plans(NoPointer){
        override suspend fun subscriptionSummary(today:LocalDate)=SubscriptionSummary("CNY",BigDecimal.TEN,BigDecimal("120"),BigDecimal.TEN,1u,true,emptyList())
        override suspend fun debtSummary(today:LocalDate)=DebtSummary("CNY",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0u,0u,0u,0u,true)
        override suspend fun subscriptions(today:LocalDate,includeHidden:Boolean):List<Subscription>{subscriptionReads++;this@WidgetCore.includeHidden=includeHidden;return listOf(subscription)}
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
