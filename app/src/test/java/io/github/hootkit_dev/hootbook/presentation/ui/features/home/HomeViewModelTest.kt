package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.data.coreDate
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
class HomeViewModelTest {
    private fun context()=ApplicationProvider.getApplicationContext<Application>().also {File(it.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(it.filesDir,"datastore").deleteRecursively()}
    @Test fun initialLoadQueriesRecentDueHeatmapAndCoreDisplayConversion()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore()
            val connection = BrokerageConnection("42", "ibkr_flex", "IBKR", null, null, true, emptyList())
            core.connections = listOf(connection)
            val vm=HomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs,io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()));store.put("vm",vm)
            val state=vm.uiState.first{!it.loading&&it.displayNetWorth!=null}
            assertSame(connection, state.connections.single())
            assertSame(core.category,state.categories["category"]);assertSame(core.subcategory,state.subcategories["subcategory"]);
            assertSame(core.transaction,state.recent.single());assertSame(core.subscription,state.upcoming.single())
            assertEquals(BigDecimal("1.25"),state.displayNetWorth!!.amount);assertTrue(core.conversions.contains(Triple(BigDecimal("100"),"CNY","USD")))
            assertFalse(state.subscriptions!!.complete);assertFalse(state.debts!!.complete);
            assertEquals("USD",core.displayCurrency);assertEquals(mapOf("PTS" to "★"),state.customSymbols)
            val first=JavaDate.now().minusWeeks(26).with(java.time.DayOfWeek.MONDAY)
            assertEquals(first.coreDate(),state.heatmap.keys.first());assertEquals(JavaDate.now().coreDate(),state.heatmap.keys.last());assertTrue(state.heatmap.values.all{it==3u})
            assertEquals(JavaDate.now().plusDays(7).coreDate(),core.dueThrough);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.dueReads>0)
            assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun preferencesHideAndUnhideDriveProductionQueriesWithoutInventingAmounts()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try{val prefs=CoreUiPreferencesStore(context());val hidden=setOf(HomeWidget.RECENT_TRANSACTIONS,HomeWidget.UPCOMING_SUBSCRIPTIONS,HomeWidget.TRANSACTION_HEATMAP);prefs.update{it.copy(hiddenHomeWidgets=hidden)};val core=WidgetCore()
            val vm=HomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs,io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()));store.put("vm",vm);vm.uiState.first{!it.loading}
            assertEquals(0,core.recentReads);assertEquals(0,core.dailyReads);assertEquals(0,core.dueReads)
            prefs.update{it.copy(hiddenHomeWidgets=emptySet())};val visible=vm.uiState.first{!it.loading&&it.heatmap.isNotEmpty()}
            assertSame(core.overview.netWorth,visible.overview!!.netWorth);assertTrue(core.recentReads>0&&core.dailyReads>0&&core.dueReads>0)
            prefs.update{it.copy(hiddenHomeWidgets=hidden)};vm.uiState.first{!it.loading&&it.heatmap.isEmpty()};assertEquals(core.opened,core.destroyed)
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun cancelledOldProductionConversionNeverOverwritesNewPreference()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore();val release=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        try{val prefs=CoreUiPreferencesStore(context());prefs.update{it.copy(unifiedDisplayEnabled=true,unifiedDisplayCurrency="USD")};val core=WidgetCore();var delayed=false
            core.conversion={_,_,to->if(to=="USD"&&!delayed){delayed=true;entered.complete(Unit);withContext(NonCancellable){release.await()};BigDecimal("99")}else BigDecimal("3")}
            val vm=HomeViewModel(CoreSession(backgroundScope){core},ChangeBus(),prefs,io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()));store.put("vm",vm);entered.await()
            prefs.update{it.copy(unifiedDisplayCurrency="EUR")};runCurrent();release.complete(Unit);vm.uiState.first{!it.loading&&it.displayNetWorth?.currency=="EUR"}
            release.complete(Unit);runCurrent();assertEquals("EUR",vm.uiState.value.displayNetWorth!!.currency);assertEquals(BigDecimal("3"),vm.uiState.value.displayNetWorth!!.amount)
            assertEquals(core.opened,core.destroyed)
        }finally{release.complete(Unit);store.clear();Dispatchers.resetMain()}
    }
    @Test fun missingDisplayRateKeepsCoreTotalsAndExplicitIncompleteConversion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(context())
            prefs.update { it.copy(unifiedDisplayEnabled = true, unifiedDisplayCurrency = "USD") }
            val core = WidgetCore(); core.conversion = { _, _, _ -> null }
            val vm = HomeViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs,
                io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()))
            store.put("vm", vm)
            val state = vm.uiState.first { !it.loading }
            assertSame(core.overview, state.overview)
            assertSame(core.summary, state.summary)
            assertEquals("USD", state.displayCurrency)
            assertNull(state.displayNetWorth!!.amount)
            assertFalse(state.displayNetWorth.complete)
            assertTrue(state.displayTotals.values.all { !it.complete && it.amount == null })
            assertFalse(state.recentConversions["tx"]!!.complete)
            assertEquals(core.opened, core.destroyed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun changesRefreshAndQueryFailureCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(context()); val core = WidgetCore(); val changes = ChangeBus()
            val vm = HomeViewModel(CoreSession(backgroundScope) { core }, changes, prefs,
                io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()))
            store.put("vm", vm)
            vm.uiState.first { !it.loading }
            assertSame(core.summary, vm.uiState.value.summary)
            assertSame(core.summary, vm.uiState.value.lastSummary)
            val month = JavaDate.now().withDayOfMonth(1)
            assertEquals(month.coreDate().year, core.summaryFilters.first().from!!.year)
            assertEquals(month.monthValue.toUByte(), core.summaryFilters.first().from!!.month)
            assertEquals(1.toUByte(), core.summaryFilters.first().from!!.day)
            assertEquals(month.plusMonths(1).monthValue.toUByte(), core.summaryFilters.first().to!!.month)
            assertEquals(month.minusMonths(1).monthValue.toUByte(), core.summaryFilters[1].from!!.month)
            core.overviewError = true
            changes.onChange(ChangeSet(HOME_TABLES.toList(), emptyList(), ChangeOrigin.LOCAL))
            vm.uiState.first { it.error != null }
            core.overviewError = false; core.overview = core.overview.copy(netWorthComplete = false)
            vm.refresh()
            val recovered = vm.uiState.first { it.error == null && it.overview?.netWorthComplete == false }
            assertSame(core.overview, recovered.overview)
            assertEquals(core.opened, core.destroyed)
            assertTrue(HOME_TABLES.containsAll(setOf(Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS, Table.BROKERAGE,
                Table.SETTINGS, Table.EXCHANGE_RATES, Table.BUDGET_ACCOUNTS, Table.BUDGET_LIMITS, Table.CATEGORIES, Table.SUBCATEGORIES, Table.PEOPLE, Table.DEBTS)))
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun preferenceCommandsPersistRejectDoubleTapAndRecoverFromInvalidCurrency() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(context()); val core = WidgetCore()
            val vm = HomeViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs,
                io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(ApplicationProvider.getApplicationContext()))
            store.put("vm", vm); vm.uiState.first { !it.loading }
            vm.toggleBannerImage(); vm.toggleBannerImage()
            assertTrue(vm.uiState.value.submitting)
            vm.uiState.first { !it.submitting }
            assertTrue(prefs.ready().showBanner)
            vm.selectCurrency("invalid!")
            vm.uiState.first { it.error != null }
            vm.selectCurrency("USD"); vm.selectCurrency("EUR")
            val latest = vm.uiState.first { it.displayNetWorth?.currency == "EUR" }
            assertEquals(BigDecimal("1.25"), latest.displayNetWorth!!.amount)
            assertNull(latest.error)
            vm.toggleHomeWidgetVisibility(HomeWidget.LOANS, false)
            prefs.state.first { HomeWidget.LOANS in it.hiddenHomeWidgets }
            vm.updateWidgetsOrder(listOf(HomeWidget.LOANS, HomeWidget.RECENT_TRANSACTIONS))
            prefs.state.first { it.homeOrder[2] == HomeWidget.LOANS }
            vm.resetWidgetsLayout(); vm.uiState.first { !it.submitting }
            assertTrue(prefs.ready().hiddenHomeWidgets.isEmpty())
            assertEquals(HomeWidget.entries.sortedBy { it.defaultOrder }, prefs.ready().homeOrder)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun buildCreditCardRemindersCoversOverdueDueAndStatementSoon() {
        val now = LocalDateTime(2026, 10u, 16u, 9u, 0u, 0u, 0u)
        fun card(id: String, name: String, balance: String, statementDay: UByte?, dueDay: UByte?) = Account(
            id = id, name = name, last4 = "1008", kind = AccountKind.CREDIT_CARD, mainCurrency = "USD",
            pockets = listOf(Pocket("USD", BigDecimal(balance), null, null, null)),
            total = Money(BigDecimal(balance), "USD"), totalComplete = true, securities = null, broker = null,
            creditLimit = null, statementDay = statementDay, dueDay = dueDay, institutionKey = "amex",
            icon = "type_finance_bank", color = "#33B5E5", archived = false, isDefault = false, sortOrder = 0, createdAt = now,
        )
        val overdueCard = card("c1", "AMEX HILTON", "294.94", 31u, 15u)
        val dueSoonCard = card("c2", "Chase Sapphire", "120.00", 5u, 20u)
        val statementSoonCard = card("c3", "Citi Premier", "88.00", 18u, 5u)
        val settledFarCard = card("c4", "AMEX Settled", "294.94", 31u, 15u)
        val statuses = mapOf(
            "c1" to CardStatus("c1", "USD", LocalDate(2026, 9u, 30u), LocalDate(2026, 10u, 15u), BigDecimal("489.09"), BigDecimal.ZERO, BigDecimal("489.09"), true),
            "c2" to CardStatus("c2", "USD", LocalDate(2026, 10u, 5u), LocalDate(2026, 10u, 20u), BigDecimal("120.00"), BigDecimal.ZERO, BigDecimal("120.00"), false),
            "c3" to CardStatus("c3", "USD", LocalDate(2026, 10u, 18u), LocalDate(2026, 11u, 5u), null, null, null, false),
            "c4" to CardStatus("c4", "USD", LocalDate(2026, 10u, 31u), LocalDate(2026, 11u, 15u), null, null, null, false),
        )
        val reminders = buildCreditCardReminders(
            accounts = listOf(settledFarCard, statementSoonCard, dueSoonCard, overdueCard),
            cardStatuses = statuses,
            today = JavaDate.of(2026, 10, 16),
        )
        assertEquals(listOf("c1", "c2", "c3"), reminders.map { it.account.id })
        assertEquals(CreditCardReminderKind.OVERDUE, reminders[0].kind)
        assertEquals(BigDecimal("489.09"), reminders[0].amount)
        assertEquals(1, reminders[0].daysDiff)
        assertEquals(CreditCardReminderKind.PAYMENT_DUE, reminders[1].kind)
        assertEquals(4, reminders[1].daysDiff)
        assertEquals(CreditCardReminderKind.STATEMENT_SOON, reminders[2].kind)
        assertEquals(2, reminders[2].daysDiff)
    }

}

/** Complete generated service overrides for the production widget path; no native calls in JVM. */
private class WidgetCore:HootKitCore(NoPointer){
    private val day=JavaDate.now();private val at=JavaTime.now().let{LocalDateTime(it.year,it.monthValue.toUByte(),it.dayOfMonth.toUByte(),it.hour.toUByte(),it.minute.toUByte(),0u,0u)}
    val transaction=Transaction("tx",TransactionKind.EXPENSE,BigDecimal("5"),"CNY",at,null,null,null,null,null,null,"Merchant",null,null,null,null,emptyList(),null,emptyList(),false,at,at)
    val subscription=Subscription(
        id="subscription",name="Subscription",amount=Money(BigDecimal.TEN,"CNY"),cycle=Cycle(1u,CycleUnit.MONTH),
        endDate=null,nextDue=day.plusDays(2).coreDate(),lastPaid=null,accountId=null,categoryId=null,subcategoryId=null,notes=null,
        hidden=false,monthlyEquivalent=BigDecimal.TEN,ended=false,overdue=false,daysUntil=2,
    )
    var overviewError = false
    var overview=AccountsOverview(emptyList(),Money(BigDecimal("100"),"CNY"),true,null,null)
    val summary=LedgerSummary("CNY",BigDecimal("10"),BigDecimal("5"),BigDecimal("5"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,true,emptyList(),emptyList(),emptyList(),emptyList())
    val summaryFilters = mutableListOf<TransactionFilter>()
    var opened=0;var destroyed=0;var recentReads=0;var dailyReads=0;var dueReads=0
    var displayCurrency:String?=null;var dueThrough:LocalDate?=null;val conversions=mutableListOf<Triple<BigDecimal,String,String>>()
    var conversion:suspend(BigDecimal,String,String)->BigDecimal?={_,_,_->BigDecimal("1.25")}
    val subcategory = Subcategory("subcategory", "category", null, "Detail", "Detail", "", "", 0, false)
    val category = Category("category", CategoryKind.EXPENSE, null, "Category", "Category", "", "", 0, false, listOf(subcategory))
    var connections = emptyList<BrokerageConnection>()
    override fun brokerage(): Brokerage {opened++;return object:Brokerage(NoPointer){override suspend fun connections()=this@WidgetCore.connections;private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun categories(): Categories {opened++;return object:Categories(NoPointer){override suspend fun list(kind:CategoryKind?,includeHidden:Boolean)=listOf(category);private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun accounts():Accounts {opened++;return object:Accounts(NoPointer){override suspend fun overview():AccountsOverview { if (overviewError) error("Overview failed"); return this@WidgetCore.overview };override suspend fun cardStatus(accountId:String,today:LocalDate):CardStatus?=null;private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun ledger():Ledger {opened++;return object:Ledger(NoPointer){
        override suspend fun summary(filter:TransactionFilter):LedgerSummary {summaryFilters += filter;return this@WidgetCore.summary}
        override suspend fun list(filter:TransactionFilter,page:PageRequest):TransactionPage=if(filter.from==null){recentReads++;TransactionPage(listOf(transaction),1u)}else{dailyReads++;TransactionPage(emptyList(),3u)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun budgets():Budgets {opened++;return object:Budgets(NoPointer){override suspend fun list(today:LocalDate,homeOnly:Boolean,displayCurrency:String?):List<Budget>{assertTrue(homeOnly);this@WidgetCore.displayCurrency=displayCurrency;return emptyList()};private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}}}
    override fun plans():Plans {opened++;return object:Plans(NoPointer){
        override suspend fun subscriptionSummary(today:LocalDate)=SubscriptionSummary("CNY",BigDecimal.TEN,BigDecimal("120"),BigDecimal.TEN,1u,false,emptyList())
        override suspend fun debtSummary(today:LocalDate)=DebtSummary("CNY",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0u,0u,0u,0u,false)
        override suspend fun dueSubscriptions(today:LocalDate,through:LocalDate):List<Subscription>{dueReads++;dueThrough=through;return listOf(subscription)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun currency():Currency {opened++;return object:Currency(NoPointer){
        override suspend fun customCurrencies()=listOf(CustomCurrency("PTS","Points","★"))
        override suspend fun convert(amount:BigDecimal,from:String,to:String):BigDecimal?{conversions+=Triple(amount,from,to);return conversion(amount,from,to)}
        private var released=false;override fun destroy(){if(!released){released=true;destroyed++};super.destroy()}
    }}
    override fun destroy(){super.destroy()}
    override suspend fun shutdown()=Unit
}
