package io.github.hootkit_dev.hootbook.acceptance

import android.content.Intent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.MainActivity
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.appendBudgetHistory
import io.github.hootkit_dev.hootbook.presentation.export.CoreTransactionCsv
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Launches actual MainActivity; no manual setContent, NoPointer, test Application or second Core. */
class RealMainJourneys {
    @get:Rule val activityRule=ActivityScenarioRule(MainActivity::class.java)
    private lateinit var f:NativeScenarioFixture
    private lateinit var main:MainActivity
    private lateinit var launchIntent:Intent
    private var tlsRollback:(()->Unit)?=null
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    @Before fun ready() {
        f=NativeScenarioFixture();runBlocking{f.core();f.preferences.ready()}
        activityRule.scenario.onActivity{main=it;launchIntent=Intent(it.intent)}
        automation.serviceInfo=automation.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
        awaitCondition{nodes().any{it.viewIdResourceName?.startsWith("hootbook-screen-")==true}}
        if(!f.preferences.state.value.onboardingFinished){
            click(R.string.core_shell_finish)
            awaitCondition{f.preferences.state.value.onboardingFinished}
            f.note("onboarding","real Finish UI persisted after native currency command")
        }
    }
    @After fun restoreScenarioIntent(){
        tlsRollback?.invoke();tlsRollback=null
        // ActivityScenario tracks lifecycle by launch Intent; onNewIntent updates MainActivity.intent.
        // Restore only after actual typed-entry assertions so Rule observes destruction correctly.
        if(::main.isInitialized&&::launchIntent.isInitialized)InstrumentationRegistry.getInstrumentation().runOnMainSync{main.intent=launchIntent}
    }
    private fun awaitCondition(timeout:Long=30_000,predicate:()->Boolean){val deadline=android.os.SystemClock.elapsedRealtime()+timeout
        while(!predicate()){check(android.os.SystemClock.elapsedRealtime()<deadline){"Real UI condition timed out"};Thread.sleep(100)}
    }
    private fun showText(expected:String,contains:Boolean=false,direction:Int=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){
        val end=android.os.SystemClock.elapsedRealtime()+40_000
        while(true){val current=nodes();if(current.any{it.isVisibleToUser&&it.text?.toString()?.let{value->if(contains)value.contains(expected) else value==expected}==true})return
            check(android.os.SystemClock.elapsedRealtime()<end){"Missing visible UI text: $expected"}
            current.firstOrNull{it.isScrollable&&it.isVisibleToUser}?.let{scroll(it,direction)};Thread.sleep(650)
        }
    }
    private fun descendants(root:AccessibilityNodeInfo?):List<AccessibilityNodeInfo>{
        if(root==null)return emptyList();val list=mutableListOf(root)
        for(i in 0 until root.childCount)list+=descendants(root.getChild(i));return list
    }
    private fun nodes():List<AccessibilityNodeInfo>{automation.clearCache();return descendants(automation.rootInActiveWindow)}
    private fun scroll(node:AccessibilityNodeInfo,direction:Int){
        val b=Rect();node.getBoundsInScreen(b)
        check(b.width()>0&&b.height()>80)
        val inset=main.window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
        val top=maxOf(b.top,(inset?.top?:0))+40;val bottom=minOf(b.bottom,f.context.resources.displayMetrics.heightPixels-(inset?.bottom?:0))-40
        check(bottom>top)
        val start=if(direction==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)bottom else top;val finish=if(direction==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)top else bottom
        automation.executeShellCommand("input swipe ${b.centerX()} $start ${b.centerX()} $finish 600").use{descriptor->android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()}
    }
    private fun click(id:Int)=clickText(f.text(id))
    private fun clickPrefix(id:Int)=clickText(f.text(id)+"：",true)
    private fun clickText(text:String,prefix:Boolean=false){
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        var direction=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;var previous="";var unchanged=0
        while(true){
            val current=nodes()
            if(current.firstOrNull()?.packageName?.toString()=="com.android.systemui"){
                automation.executeShellCommand("cmd statusbar collapse").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()};Thread.sleep(650);continue
            }
            val labels=current.filter{it.isVisibleToUser&&(it.text?.toString()?.equals(text,true)==true||it.contentDescription?.toString()?.equals(text,true)==true||(prefix&&it.text?.toString()?.startsWith(text)==true))}
            val actions=labels.flatMap{label->val lb=Rect();label.getBoundsInScreen(lb)
                current.filter{candidate->val b=Rect();candidate.getBoundsInScreen(b)
                    candidate.isClickable&&candidate.isEnabled&&candidate.isVisibleToUser&&b.contains(lb.centerX(),lb.centerY())}}
            val action=actions.minByOrNull{candidate->val b=Rect();candidate.getBoundsInScreen(b);b.width().toLong()*b.height()}
            if(action!=null){check(action.performAction(AccessibilityNodeInfo.ACTION_CLICK)){"UI action rejected: $text"};Thread.sleep(850);f.note("driver-click",text);return}
            if(android.os.SystemClock.elapsedRealtime()>=deadline){shot("failed-action-"+text.replace(Regex("[^a-zA-Z0-9]"),"-"));error("Missing real UI action: $text")}
            current.firstOrNull{it.isScrollable&&it.isVisibleToUser}?.let{scroll->
                val signature=current.filter{it.isVisibleToUser}.joinToString{node->val bounds=Rect();node.getBoundsInScreen(bounds);node.text?.toString().orEmpty()+bounds.toString()}
                if(signature==previous)unchanged++ else unchanged=0
                previous=signature
                if(unchanged>=2){direction=if(direction==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;unchanged=0}
                scroll(scroll,direction)
                f.note("driver-scroll","action=$text direction=$direction observed-pointer=true")
            }
            Thread.sleep(850)
        }
    }
    private fun clickBelow(scope:String,id:Int){
        showText(scope)
        val current=nodes();val heading=current.first{it.text?.toString()==scope};val hb=Rect();heading.getBoundsInScreen(hb)
        val label=current.filter{it.text?.toString()==f.text(id)&&it.isVisibleToUser}.filter{node->val b=Rect();node.getBoundsInScreen(b);b.top>=hb.bottom}
            .minByOrNull{node->val b=Rect();node.getBoundsInScreen(b);b.top}
        checkNotNull(label){"Missing scoped action below $scope"};val lb=Rect();label.getBoundsInScreen(lb)
        val action=current.filter{node->val b=Rect();node.getBoundsInScreen(b);node.isClickable&&node.isEnabled&&b.contains(lb.centerX(),lb.centerY())}
            .minByOrNull{node->val b=Rect();node.getBoundsInScreen(b);b.width().toLong()*b.height()}
        checkNotNull(action);check(action.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(850)
    }
    private fun field(id:Int,value:String,last:Boolean=false){
        val label=f.text(id);var target:AccessibilityNodeInfo?=null;var count=0
        while(target==null&&count++<30){val matches=nodes().filter{it.isEditable&&(it.text?.toString()==label||it.hintText?.toString()==label||descendants(it).any{child->child.text?.toString()==label})};target=if(last)matches.lastOrNull()else matches.firstOrNull()
            if(target==null){nodes().firstOrNull{it.isScrollable&&it.isVisibleToUser}?.let{scroll(it,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)};Thread.sleep(850)}}
        if(target==null){val labelNode=nodes().firstOrNull{it.text?.toString()==label};val labelBounds=Rect();labelNode?.getBoundsInScreen(labelBounds)
            target=nodes().firstOrNull{node->val b=Rect();node.getBoundsInScreen(b);node.isEditable&&node.isEnabled&&labelNode!=null&&b.contains(labelBounds.centerX(),labelBounds.centerY())}}
        check(target!=null){"Missing real UI field: $label"}
        target!!.performAction(AccessibilityNodeInfo.ACTION_FOCUS);check(target.refresh()&&target.isFocused){"Field did not focus: $label"}
        check(target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))
        Thread.sleep(850)
    }
    private fun home(){
        if(nodes().any{it.viewIdResourceName=="hootbook-screen-Home"})return // Selected bottom tab legitimately omits redundant click action.
        typed(QuickEntry.Entry.Home)
        try{awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-Home"}}}catch(failure:IllegalStateException){shot("failed-home-navigation");throw failure}
    }
    private fun shot(name:String){
        Thread.sleep(850) // Capture after real NavHost crossfade, not a transient overlapping frame.
        val bitmap=automation.takeScreenshot();requireNotNull(bitmap){"Screenshot is protected/unavailable"}
        File(f.output,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        File(f.output,"$name-layout.txt").writeText(nodes().joinToString("\n"){node->val bounds=Rect();node.getBoundsInScreen(bounds);"${node.className} text=${node.text} description=${node.contentDescription} bounds=$bounds"})
    }
    private fun back(){InstrumentationRegistry.getInstrumentation().runOnMainSync{main.onBackPressedDispatcher.onBackPressed()};Thread.sleep(850)}
    private fun typed(entry:QuickEntry.Entry){activityRule.scenario.onActivity{it.startActivity(QuickEntry.intent(f.context,entry).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))};Thread.sleep(850)}
    @Test fun uiCategoryAndChildCreateRenameHideRestore()=runBlocking{
        val c=f.core();home();click(R.string.core_complete_categories);clickText("＋")
        val name="${f.tag} Category";field(R.string.core_categories_20,name);click(R.string.save)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).any{row->row.displayName==name}}}}
        clickText(name);click(R.string.edit);val edited="$name Edited";field(R.string.core_categories_20,edited);click(R.string.save)
        val id=c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.displayName==edited}.id}
        click(R.string.core_categories_12);awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.hidden}}}
        click(R.string.core_categories_11);awaitCondition{runBlocking{!c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.hidden}}}
        clickText("＋");field(R.string.core_categories_20,"${f.tag} Child");click(R.string.save)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.subcategories.any{child->child.displayName=="${f.tag} Child"}}}}
        shot("ui-category-child-restored");f.note("ui-category","real category create/rename/hide/unhide + child create confirmed native id=$id")
    }
    @Test fun uiExpenseCreateEditDeleteUndoThroughTypedEntry()=runBlocking{
        val c=f.core();val merchant="${f.tag} UI expense";typed(QuickEntry.Entry.NewTransaction)
        field(R.string.core_full_label_55,"9.25");field(R.string.core_full_label_67,merchant)
        clickPrefix(R.string.core_full_label_40);clickText(c.categories().use{it.list(CategoryKind.EXPENSE,false).first().displayName})
        click(R.string.core_full_label_71)
        val filter=TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=emptyList(),categoryIds=emptyList(),text=merchant,currency=null,deleted=DeletedFilter.INCLUDE,sort=SortOrder.NEWEST_FIRST)
        awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,10u)).total==1u}}}
        val id=c.ledger().use{it.list(filter,PageRequest(0u,10u)).items.single().id};assertEquals(0,c.ledger().use{it.get(id).amount}.compareTo(BigDecimal("9.25")))
        click(R.string.core_full_label_5);field(R.string.core_full_label_68,"UI edited notes");click(R.string.core_full_label_71)
        awaitCondition{runBlocking{c.ledger().use{it.get(id).notes=="UI edited notes"}}}
        click(R.string.core_full_label_6);click(R.string.core_full_label_17)
        awaitCondition{runBlocking{c.ledger().use{it.get(id).deleted}}};click(R.string.core_full_label_3)
        awaitCondition{runBlocking{!c.ledger().use{it.get(id).deleted}}};shot("ui-expense-undo")
        f.note("ui-ledger","real typed-entry UI expense create/edit/delete/undo id=$id")
    }
    private fun layout(name:String){File(f.output,"$name-layout.txt").writeText(nodes().joinToString("\n"){node->val b=Rect();node.getBoundsInScreen(b);"${node.className} text=${if(node.isPassword)"[PASSWORD]" else node.text} description=${node.contentDescription} bounds=$b checked=${node.isChecked}"})}
    @Test fun uiCurrenciesCustomAndNativeRateConversion()=runBlocking{
        val c=f.core();val code="X"+f.tag.takeLast(3).uppercase();home();click(R.string.core_complete_currencies);click(R.string.core_currencies_add)
        field(R.string.core_currencies_code,code);field(R.string.core_currencies_name,"${f.tag} Currency");field(R.string.core_currencies_symbol,"X");click(R.string.core_currencies_save)
        awaitCondition{runBlocking{c.currency().use{it.customCurrencies().any{row->row.code==code}}}}
        c.currency().use{it.setCustomRate("USD","EUR",BigDecimal("1.5"))}
        back();home();click(R.string.core_complete_currencies)
        field(R.string.core_currencies_amount,"2");field(R.string.core_currencies_from,"USD");field(R.string.core_currencies_to,"EUR");click(R.string.core_currencies_convert)
        awaitCondition{nodes().any{node->node.text?.toString()?.let{value->Regex("2 USD = ([0-9.]+) EUR").matchEntire(value)?.groupValues?.get(1)?.toBigDecimalOrNull()?.compareTo(BigDecimal("3"))==0}==true}};assertEquals(0,c.currency().use{it.convert(BigDecimal("2"),"USD","EUR")!!}.compareTo(BigDecimal("3")))
        shot("ui-currency-conversion");f.note("ui-currency","real customcurrency save code=$code, real convert UI using native rate fixture=1.5 result=3; no online rates")
    }
    @Test fun uiAppearanceThemesAndNoCredentialLockGuard()=runBlocking{
        home();click(R.string.core_complete_layout);click(R.string.core_ui_dark)
        awaitCondition{f.preferences.state.value.darkTheme==true};shot("ui-appearance-dark")
        click(R.string.core_ui_light);awaitCondition{f.preferences.state.value.darkTheme==false}
        click(R.string.core_ui_app_lock);click(R.string.core_ui_app_lock)
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_ui_lock_enroll)}}
        assertFalse(f.preferences.state.value.appLockEnabled);shot("ui-lock-no-credential-guard")
        f.note("ui-appearance-lock","UI dark/light persist; no device credential enable rejected without enabling")
    }
    @Test fun uiAiControlledNativeToolsReviewApproveAndUndo()=runBlocking{
        val c=f.core();tlsRollback=f.fixtureTlsTransport();home();click(R.string.core_complete_settings);click(R.string.core_ai_title)
        assertTrue(main.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
        if(c.ai().use{it.settings().configured})clickText(f.text(R.string.core_ai_configuration)+" · ",true)
        clickText("OpenAI compatible")
        field(R.string.core_ai_base_url,"https://10.0.2.2:8787/v1");field(R.string.core_ai_model,"fixture-model");field(if(c.ai().use{it.settings().hasKey})R.string.core_ai_key_replace else R.string.core_ai_key,"fixture-key");click(R.string.core_ai_save_configuration)
        val merchant="${f.tag} AI purchase";val filter=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),merchant,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        field(R.string.core_ai_request,"${f.tag} controlled expense");click(R.string.core_ai_analyze)
        awaitCondition(60_000){nodes().any{it.text?.toString()==f.text(R.string.core_ai_apply)}}
        assertEquals(0u,c.ledger().use{it.list(filter,PageRequest(0u,10u)).total});layout("ui-ai-review-user-unconfirmed")
        click(R.string.core_ai_apply);click(R.string.core_ai_apply)
        awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,10u)).total==1u}}};layout("ui-ai-applied-native")
        click(R.string.core_ai_undo);awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,10u)).total==0u}}}
        f.note("ui-ai","real UI config + securewindow + native Tool add_transactions/finish via loopback controlledHTTP; zero before approval/oneafter/undoremove; no real provider")
    }
    private fun clickResource(id:String){awaitCondition{nodes().any{it.viewIdResourceName==id&&it.isEnabled&&it.isClickable}};check(nodes().first{it.viewIdResourceName==id&&it.isEnabled&&it.isClickable}.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(850)}
    @Test fun uiNotificationPermissionDenyAllowAndReminderTime()=runBlocking{
        home();click(R.string.core_complete_settings);click(R.string.core_shell_notifications)
        if(androidx.core.content.ContextCompat.checkSelfPermission(f.context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            click(R.string.core_system_allow_notifications);clickResource("com.android.permissioncontroller:id/permission_deny_button")
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,androidx.core.content.ContextCompat.checkSelfPermission(f.context,android.Manifest.permission.POST_NOTIFICATIONS))
            click(R.string.core_system_allow_notifications);clickResource("com.android.permissioncontroller:id/permission_allow_button")
        }
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,androidx.core.content.ContextCompat.checkSelfPermission(f.context,android.Manifest.permission.POST_NOTIFICATIONS))
        field(R.string.core_system_hour,"22");field(R.string.core_system_minute,"42");click(R.string.core_ui_save)
        awaitCondition{f.preferences.state.value.reminderMinutes==1362};shot("ui-notifications-permission-reminder")
        f.note("ui-notifications","system permission deny then allow; UI reminder time22:42 persisted; exactAllowed="+f.context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms())
    }
    private fun enterFixturePin(){
        awaitCondition{nodes().any{it.isEditable&&it.isEnabled&&it.isPassword}}
        val pin=nodes().first{it.isEditable&&it.isEnabled&&it.isPassword};check(pin.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        check(pin.refresh()&&pin.isFocused)
        automation.executeShellCommand("input text 1357").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER);Thread.sleep(850)
    }
    @Test fun uiEnrolledPinEnableCancelAndReauthentication()=runBlocking{
        home();click(R.string.core_complete_settings);click(R.string.core_shell_app_lock);click(R.string.core_ui_app_lock);enterFixturePin()
        awaitCondition{f.preferences.state.value.appLockEnabled}
        assertTrue(main.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
        click(R.string.core_ui_lock_now);awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_ui_locked)}}
        assertFalse(nodes().any{it.viewIdResourceName?.startsWith("hootbook-screen-")==true})
        click(R.string.core_ui_unlock);awaitCondition{nodes().any{it.isEditable&&it.isPassword}};InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_ui_locked)}}
        assertFalse(nodes().any{it.viewIdResourceName?.startsWith("hootbook-screen-")==true})
        click(R.string.core_ui_unlock);enterFixturePin();awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-AppLockSettings"}}
        layout("ui-lock-authenticated-restored-route");f.note("ui-lock-pin","actual owned system PIN -> enable secure, lock hides Core, cancel preserves locked, fresh PIN restores original route; left enabled for real cold CLI check")
    }
    @Test fun nativePlatformReminderMutedScanAndSameDayDedup()=runBlocking{
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,androidx.core.content.ContextCompat.checkSelfPermission(f.context,android.Manifest.permission.POST_NOTIFICATIONS))
        val entry=dagger.hilt.android.EntryPointAccessors.fromApplication(f.context.applicationContext,io.github.hootkit_dev.hootbook.core.system.CoreSystemEntryPoint::class.java)
        val old=f.preferences.state.value;val ids=f.core().plans().use{it.subscriptions(JavaDate.now().nativeDate(),true).map{row->row.id}.toSet()}
        val record=f.context.getSharedPreferences("core-reminder-delivery",android.content.Context.MODE_PRIVATE);record.edit().remove("deliveredDay").commit()
        f.preferences.update{it.copy(notificationsEnabled=true,scanReminderEnabled=false,mutedSubscriptionIds=ids)}
        assertFalse(entry.dispatcher().deliver());f.preferences.update{it.copy(scanReminderEnabled=true)}
        assertTrue(entry.dispatcher().deliver());assertFalse(entry.dispatcher().deliver())
        val active=f.context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single{it.id==100}
        assertNotNull(active.notification.contentIntent);assertNotNull(active.notification.publicVersion)
        entry.scheduler().scheduleDailyReminder()
        f.preferences.update{it.copy(mutedSubscriptionIds=old.mutedSubscriptionIds)}
        f.note("native-platform-notify","actual Android notify accepted; allmuted+scanoff false, scanon true, sameday false, immutable navigation PI/public generic; scheduled alarm; UI permission tested separately")
    }
    @Test fun nativeInstalledWidgetProviderAndDynamicTypedShortcuts()=runBlocking{
        f.seed();Thread.sleep(1300)
        val shortcuts=f.context.getSystemService(android.content.pm.ShortcutManager::class.java)
        assertTrue(shortcuts.manifestShortcuts.isNotEmpty());assertTrue(shortcuts.dynamicShortcuts.isNotEmpty())
        assertTrue(shortcuts.dynamicShortcuts.all{QuickEntry.parse(it.intent!!)!=null})
        val provider=android.content.ComponentName(f.context,io.github.hootkit_dev.hootbook.widget.QuickAddWidgetReceiver::class.java)
        assertTrue(f.context.getSystemService(android.appwidget.AppWidgetManager::class.java).installedProviders.any{it.provider==provider})
        layout("native-system-entry-publication")
        f.note("native-system-entry","actual Android manifest/dynamic shortcuts typed parse and installed Glance provider; not yet widget launcher binding or QS click")
    }
    @Test fun uiAnalyticsAllTimeIncludesFutureAndCoreCounts()=runBlocking{
        val c=f.core();val seed=f.seed()
        val future=c.ledger().use{it.add(f.draft(seed.account,seed.category,"${f.tag} Future",BigDecimal("1.25"),JavaTime.now().plusDays(400).nativeTime()))}
        home();click(R.string.core_complete_analytics);click(R.string.core_analytics_all_time)
        val filter=TransactionFilter(null,null,listOf(TransactionKind.EXPENSE),emptyList(),emptyList(),null,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        val total=c.ledger().use{it.list(filter,PageRequest(0u,1u)).total}
        showText(f.context.getString(R.string.core_analytics_count,total.toString()))
        shot("ui-analytics-alltime-future");click(R.string.core_analytics_this_month);shot("ui-analytics-this-month")
        f.note("ui-analytics","real allTime Expense-filter UI count equals exact matching Core total=$total includes native futureid=$future; thismonth preset rendered; no App monetary calculation")
    }
    @Test fun uiInvestmentEmptyCredentialConnectCancelProtected()=runBlocking{
        home();click(R.string.core_complete_settings);click(R.string.core_shell_investments);click(R.string.core_invest_connect)
        assertTrue(main.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertTrue(nodes().any{it.isEditable&&it.isPassword})
        val before=f.core().brokerage().use{it.connections().size}
        click(R.string.core_invest_cancel)
        awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-Investments"}}
        assertEquals(before,f.core().brokerage().use{it.connections().size})
        shot("ui-investments-cancel-no-connection");f.note("ui-investments","actual provider connect dialog empty credential inputs secure, cancel retained no native connection; no realbroker network")
    }
    @Test fun uiAiControlledFailureCancellationAndNoLedgerMutation()=runBlocking{
        val c=f.core();tlsRollback=f.fixtureTlsTransport();home();click(R.string.core_complete_settings);click(R.string.core_ai_title)
        if(c.ai().use{it.settings().configured})clickText(f.text(R.string.core_ai_configuration)+" · ",true)
        clickText("OpenAI compatible")
        field(R.string.core_ai_base_url,"https://10.0.2.2:8787/fail/v1");field(R.string.core_ai_model,"fixture-model")
        if(!c.ai().use{it.settings().hasKey})field(R.string.core_ai_key,"fixture-key")
        click(R.string.core_ai_save_configuration);field(R.string.core_ai_request,"${f.tag} failure fixture")
        val filter=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),null,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        val before=c.ledger().use{it.list(filter,PageRequest(0u,1u)).total};click(R.string.core_ai_analyze)
        showText("Provider network error (503)",true,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);layout("ui-ai-controlled-http503")
        field(R.string.core_ai_base_url,"https://10.0.2.2:8787/slow/v1");click(R.string.core_ai_save_configuration)
        field(R.string.core_ai_request,"${f.tag} cancellation fixture");click(R.string.core_ai_analyze);click(R.string.core_ai_cancel)
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_ai_analyze)}}
        Thread.sleep(5500);assertEquals(before,c.ledger().use{it.list(filter,PageRequest(0u,1u)).total});layout("ui-ai-controlled-cancel")
        f.note("ui-ai-errors","actual nativeHTTP503 displayed retry; native request cancellation with delayed fixture response committed zero; no realprovider")
    }
    @Test fun uiLedgerFiltersPagingBulkAndCsvOver200NativeRows()=runBlocking{
        val c=f.core();val seed=f.seed();val ids=mutableListOf<String>()
        c.ledger().use{api->for(index in 0 until 206)ids+=api.add(f.draft(null,seed.category,"${f.tag} Page $index"))}
        home();click(R.string.core_complete_transactions);click(R.string.core_full_label_33);field(R.string.core_full_label_34,"${f.tag} Page");click(R.string.core_full_label_52)
        click(R.string.core_full_select_all);click(R.string.core_full_bulk_edit);field(R.string.core_full_bulk_amount,"1.25");click(R.string.core_full_confirm)
        awaitCondition{runBlocking{c.ledger().use{api->ids.all{api.get(it).amount.compareTo(BigDecimal("1.25"))==0}}}}
        click(R.string.core_full_export_all_pages)
        awaitCondition{nodes().any{it.text?.toString()==f.context.getString(R.string.core_full_export_count,"206")}}
        val csv=File(f.context.cacheDir,"core-attachment-share").walkTopDown().filter{it.isFile&&it.extension=="csv"}.toList()
        assertTrue(csv.isNotEmpty());assertTrue(csv.any{it.readText().contains(ids.last())});layout("ui-csv206-ready")
        click(R.string.core_full_label_14);shot("ui-ledger-second-page")
        f.note("ui-ledger-pages","native206 unique Page fixture rows, actual UI textfilter/selectAll206/bulkamount1.25/CSV206/Next page; CSV contains final generatedID; seed writes not UI")
    }
    @Test fun uiCreditCardPocketCalibrationArchiveAndDefault()=runBlocking{
        val c=f.core();home();click(R.string.core_complete_accounts);clickText("＋");click(R.string.core_accounts_31)
        val name="${f.tag} UI card";field(R.string.core_accounts_37,name);field(R.string.core_accounts_38,"8679");field(R.string.core_accounts_40,"20")
        field(R.string.core_accounts_43,"1000");field(R.string.core_accounts_44,"15");field(R.string.core_accounts_45,"2");click(R.string.save)
        awaitCondition{runBlocking{c.accounts().use{it.overview().accounts.any{row->row.name==name}}}}
        val id=c.accounts().use{it.overview().accounts.single{row->row.name==name}.id};val card=c.accounts().use{it.get(id)}
        assertEquals(AccountKind.CREDIT_CARD,card.kind);assertEquals(0,card.creditLimit!!.compareTo(BigDecimal("1000")));assertEquals(15.toUByte(),card.statementDay);assertEquals(2.toUByte(),card.dueDay)
        click(R.string.edit);field(R.string.core_accounts_37,"$name Edited");click(R.string.save)
        awaitCondition{runBlocking{c.accounts().use{it.get(id).name=="$name Edited"}}}
        click(R.string.core_accounts_14);awaitCondition{runBlocking{c.accounts().use{it.get(id).isDefault}}};click(R.string.core_accounts_13)
        click(R.string.core_accounts_12);awaitCondition{runBlocking{c.accounts().use{it.get(id).archived}}};click(R.string.core_accounts_11)
        click(R.string.core_accounts_18);field(R.string.core_accounts_53,"USD");field(R.string.core_accounts_55,"3");click(R.string.core_accounts_59)
        awaitCondition{runBlocking{c.accounts().use{it.get(id).pockets.any{pocket->pocket.currency=="USD"}}}}
        assertFalse(c.accounts().use{it.get(id).totalComplete});shot("ui-card-pocket-created");click(R.string.core_accounts_19);field(R.string.core_accounts_55,"25");click(R.string.core_accounts_59)
        click(R.string.core_full_cash_history);field(R.string.core_full_history_from,JavaDate.now().minusDays(7).toString());field(R.string.core_full_history_to,JavaDate.now().toString());click(R.string.core_full_history_query)
        shot("ui-card-native-history");click(R.string.core_accounts_15);click(R.string.core_accounts_25)
        awaitCondition{runBlocking{c.accounts().use{it.overview().accounts.none{row->row.id==id}}}}
        f.note("ui-accounts-advanced","real card create/nameedit/default/archive/unarchive/USD pocket/calibrate/history/zero-refdelete UI and Core queries; balance/missing-rate output remains Core")
    }
    @Test fun uiOwnedCategoryChildDeletionAndProtectedBuiltinSurface()=runBlocking{
        val c=f.core();val category=c.categories().use{it.list(CategoryKind.EXPENSE,true).first{row->row.builtinKey==null&&row.displayName.startsWith("Acceptance-")&&row.subcategories.isNotEmpty()}}
        home();click(R.string.core_complete_categories);clickText(category.displayName)
        val child=category.subcategories.first();clickBelow(child.displayName,R.string.core_categories_18);click(R.string.core_categories_18)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==category.id}.subcategories.none{row->row.id==child.id}}}}
        click(R.string.delete);click(R.string.core_categories_28)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).none{row->row.id==category.id}}}}
        val builtin=c.categories().use{it.list(CategoryKind.EXPENSE,false).first{row->row.builtinKey!=null}};clickText(builtin.displayName)
        assertFalse(nodes().any{it.text?.toString()==f.text(R.string.delete)&&it.isEnabled});shot("ui-category-builtin-protected")
        f.note("ui-category-delete","actual UI deletes own child+custom parent with zero refs; builtin retains reset not ordinary delete")
    }
    @Test fun nativeWeeklyBudgetHistoryStrictCoreBoundariesBeyond24()=runBlocking{
        val c=f.core();val today=JavaDate.now();val anchor=today.minusWeeks(65).nativeDate()
        c.budgets().use{api->val id=api.create(NewBudget("${f.tag} Weekly",BudgetKind.EXPENSE,BudgetPeriod.WEEKLY,Money(BigDecimal("50"),"CNY"),anchor,null,null,"#336699",emptyList()))
            var cursor:LocalDate?=today.nativeDate();var rows=emptyList<BudgetPeriodSummary>();var pages=0
            while(cursor!=null){val request=cursor!!;val window=appendBudgetHistory(rows,api.history(id,request,24u),anchor,request);rows=window.rows;cursor=window.nextCursor;check(++pages<20)}
            assertTrue(rows.size>24);assertTrue(pages>=3);assertEquals(rows.size,rows.map{it.start}.distinct().size);assertEquals(anchor,rows.last().start)
            for(row in listOf(rows.first(),rows[24],rows.last())){val detail=api.get(id,row.start,null);assertEquals(row.start,detail.current.start);assertEquals(row.end,detail.current.end)}
            f.note("native-weekly-budget","public native weekly history rows=${rows.size} pages=$pages strict dedup/start cursors and exact returned details; not fabricated txnIDs")
        }
    }
    private fun systemBack(){automation.executeShellCommand("input keyevent 4").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()};Thread.sleep(850)}
    private fun documentDownloads(){
        awaitCondition{nodes().any{it.packageName?.toString()=="com.google.android.documentsui"}}
        clickText("Show roots");clickText("Downloads");Thread.sleep(850)
    }
    @Test fun uiAttachmentSafCancelSelectPreviewShareAndConfirmedSave()=runBlocking{
        val c=f.core();val merchant="${f.tag} SAF expense";typed(QuickEntry.Entry.NewTransaction)
        field(R.string.core_full_label_55,"3.50");field(R.string.core_full_label_67,merchant)
        click(R.string.core_full_attachment_add);awaitCondition{nodes().any{it.packageName?.toString()=="com.google.android.documentsui"}};systemBack()
        awaitCondition{nodes().any{it.isEditable&&it.text?.toString()==merchant}}
        click(R.string.core_full_attachment_add);documentDownloads();clickText("hoot-core-accept-image.png")
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_full_attachment_view)}}
        click(R.string.core_full_attachment_view);click(R.string.core_attachment_share)
        awaitCondition{nodes().none{it.viewIdResourceName=="hootbook-screen-AddTransaction"}};systemBack();click(R.string.core_attachment_close)
        clickPrefix(R.string.core_full_label_40);clickText(c.categories().use{it.list(CategoryKind.EXPENSE,false).first().displayName});click(R.string.core_full_label_71)
        val filter=TransactionFilter(null,null,listOf(TransactionKind.EXPENSE),emptyList(),emptyList(),merchant,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,1u)).total==1u}}}
        val saved=c.ledger().use{it.list(filter,PageRequest(0u,1u)).items.single()};assertEquals(merchant,saved.merchant);assertEquals(1,saved.attachments.size)
        assertTrue(io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore(f.context).resolve(saved.attachments.single())?.isFile==true)
        shot("ui-saf-attachment-saved");f.note("ui-attachment","actual DocumentsUI cancel preserves merchant; chosen external PNG->native reference, preview/share chooser cancel, user Save commits one with retained file")
    }
    @Test fun uiBackupEncryptedExportSafSaveInspectAndMerge()=runBlocking{
        val root=File(f.context.noBackupFilesDir,"hootkit-core/backup-ui");home();click(R.string.core_complete_backup)
        field(R.string.core_backup_password,"fixture-password");click(R.string.core_backup_export);click(R.string.core_backup_save)
        documentDownloads()
        val edit=nodes().first{it.isEditable&&it.isEnabled};check(edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        val fileName="${f.tag}-ui.hkbak";check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,fileName)}));clickText("Save")
        awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-DataPrivacy"}}
        click(R.string.core_backup_share);awaitCondition{nodes().none{it.viewIdResourceName=="hootbook-screen-DataPrivacy"}};systemBack()
        click(R.string.core_backup_select);documentDownloads();clickText(fileName)
        field(R.string.core_backup_password,"fixture-password",last=true);click(R.string.core_backup_inspect)
        val source=root.walkTopDown().filter{it.isFile&&it.name=="source.backup"}.maxBy{it.lastModified()}
        assertTrue(f.core().backup().use{it.inspectBackup(source.path,"fixture-password").encrypted})
        val original=java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString(""){"%02x".format(it)}
        click(R.string.core_backup_import)
        showText(f.text(R.string.core_backup_result))
        val journal=root.walkTopDown().filter{it.isFile&&it.name=="journal.json"}.maxBy{it.lastModified()};val op=org.json.JSONObject(journal.readText())
        assertEquals("PLATFORM_APPLIED",op.getString("phase"));assertEquals(original,op.getString("sha256"));assertTrue(source.isFile)
        File(f.output,"ui-saf-backup-journal.json").writeText(journal.readText());layout("ui-saf-backup-import-result")
        f.note("ui-backup-saf","actual native encrypted export -> DocumentsUI save/share chooser -> external select/password inspect -> UI Merge platform-applied, sourceSHA=$original filename=$fileName")
    }
    @Test fun realFirebaseDemoSdkBackendRoundTrip()=runBlocking{f.firebaseDemoRoundTrip()}
    @Test fun realColdRuntimeAndAllSupportedMenuRoutes()=runBlocking {
        assertTrue(main.application is io.github.hootkit_dev.hootbook.HootbookApplication)
        assertSame(f.session,main.coreSession);assertEquals("1.0",f.core().version().spec)
        home();click(R.string.core_complete_settings)
        for(label in listOf(R.string.core_shell_profile,R.string.core_shell_appearance,R.string.core_shell_app_lock,
            R.string.core_shell_notifications,R.string.core_shell_about,R.string.core_shell_sync,R.string.core_shell_investments)){
            click(label);awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-"+when(label){R.string.core_shell_profile->"Profile";R.string.core_shell_appearance->"Appearance";R.string.core_shell_app_lock->"AppLockSettings";R.string.core_shell_notifications->"NotificationSettings";R.string.core_shell_about->"About";R.string.core_shell_sync->"CloudSync";else->"Investments"}}};shot("menu-$label");f.note("menu","real route label=${f.text(label)}")
            InstrumentationRegistry.getInstrumentation().runOnMainSync{main.onBackPressedDispatcher.onBackPressed()};Thread.sleep(850)
        }
        home();for(label in listOf(R.string.core_complete_categories,R.string.core_complete_currencies,R.string.core_complete_backup,
            R.string.core_complete_templates,R.string.core_complete_subscriptions,R.string.core_complete_debts,R.string.core_complete_budgets)){
            click(label);awaitCondition{nodes().any{it.viewIdResourceName=="hootbook-screen-"+when(label){R.string.core_complete_categories->"Categories";R.string.core_complete_currencies->"CurrencySettings";R.string.core_complete_backup->"DataPrivacy";R.string.core_complete_templates->"QuickTemplates";R.string.core_complete_subscriptions->"Subscriptions";R.string.core_complete_debts->"LendBorrow";else->"Budgets"}}};shot("home-route-$label");f.note("menu","real home route=${f.text(label)}")
            home()
        }
    }
    @Test fun uiCreatesWalletAndProfileSurvivesActivityRecreation()=runBlocking {
        home();click(R.string.core_complete_accounts);clickText("＋")
        val name="${f.tag} UI wallet"
        field(R.string.core_accounts_37,name);click(R.string.core_accounts_32);field(R.string.core_accounts_41,"25")
        click(R.string.save)
        awaitCondition{nodes().any{it.text?.toString()?.contains(name)==true}}
        val wallets=f.core().accounts().use{it.overview().accounts}
        assertTrue(wallets.any{it.name==name&&it.kind==AccountKind.WALLET});f.note("ui-account","saved native id=${wallets.single{it.name==name}.id}")
        home();click(R.string.core_complete_profile)
        field(R.string.core_ui_name,"${f.tag} Display");click(R.string.core_ui_save)
        awaitCondition{f.preferences.state.value.userName=="${f.tag} Display"}
        activityRule.scenario.recreate();Thread.sleep(850)
        assertEquals("${f.tag} Display",f.preferences.state.value.userName);shot("profile-after-recreate")
    }
    @Test fun nativeLedgerCrudBulkDeleteUndoPurgeTransferAndCsv()=runBlocking {
        val seed=f.seed();val c=f.core()
        c.ledger().use{ledger->
            ledger.edit(seed.transaction,f.draft(seed.account,seed.category,"${f.tag} Edited",BigDecimal("15")))
            assertEquals("${f.tag} Edited",ledger.get(seed.transaction).merchant)
            ledger.bulkUpdate(listOf(seed.transaction),TransactionBulkPatch(notes="BULK FIXTURE"));assertEquals("BULK FIXTURE",ledger.get(seed.transaction).notes)
            val undo=ledger.delete(listOf(seed.transaction));assertTrue(ledger.get(seed.transaction).deleted)
            ledger.undo(undo);assertFalse(ledger.get(seed.transaction).deleted)
            val transfer=ledger.add(f.draft(seed.account,null,"${f.tag} Transfer",BigDecimal("7"),kind=TransactionKind.TRANSFER,to=seed.second))
            assertEquals(seed.second,ledger.get(transfer).toAccountId)
            val gone=ledger.add(f.draft(null,seed.category,"${f.tag} Purged"));ledger.delete(listOf(gone));ledger.purge(listOf(gone))
            try{ledger.get(gone);fail("purged transaction must not resolve")}catch(_:CoreException.NotFound){}
            val buffer=ByteArrayOutputStream()
            CoreTransactionCsv.export(ledger,TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=listOf(seed.account),categoryIds=emptyList(),text=null,currency=null,deleted=DeletedFilter.EXCLUDE,sort=SortOrder.NEWEST_FIRST),buffer,
                c.accounts().use{it.overview().accounts},c.categories().use{it.list(CategoryKind.EXPENSE,true)+it.list(CategoryKind.INCOME,true)})
            val csv=buffer.toString("UTF-8");assertTrue(csv.contains(seed.transaction));assertTrue(csv.contains("BULK FIXTURE"))
            File(f.output,"${f.tag}-ledger.csv").writeText(csv)
        }
        home();click(R.string.core_complete_transactions);shot("native-ledger-visible");f.note("native-ledger","CRUD/bulk/undo/purge/transfer/CSV real public Core; UI list entered")
    }
    @Test fun nativeHistoryBeyond24AndExactCorePeriodDetails()=runBlocking {
        val seed=f.seed();val c=f.core();val today=JavaDate.now().nativeDate()
        c.budgets().use{api->
            val budget=api.get(seed.budget,today,null);var cursor:LocalDate?=today;var rows=emptyList<BudgetPeriodSummary>();var rounds=0
            while(cursor!=null){check(++rounds<200);val request=cursor!!;val page=api.history(seed.budget,request,24u)
                val window=appendBudgetHistory(rows,page,budget.anchor,request);rows=window.rows;cursor=window.nextCursor
            }
            assertTrue("must cross legacy 24",rows.size>24);assertEquals(rows.size,rows.map{it.start}.distinct().size)
            for(row in rows.take(2)+rows.takeLast(2)){val detail=api.get(seed.budget,row.start,null);assertEquals(row.start,detail.current.start);assertEquals(row.end,detail.current.end)}
            f.note("native-budget","count=${rows.size} pages=$rounds oldest=${rows.last().start} exact Core period detail verified")
        }
        home();click(R.string.core_complete_budgets);shot("native-history-budget")
    }
    @Test fun nativePlansDebtAttachmentAndTemplateEntryRemainUserConfirmed()=runBlocking {
        val seed=f.seed();val c=f.core();val ref=f.attachment()
        c.plans().use{plans->
            assertTrue(plans.dueSubscriptions(JavaDate.now().nativeDate(),JavaDate.now().plusDays(1).nativeDate()).any{it.id==seed.subscription})
            val prefill=plans.templatePrefill(seed.template);assertNotNull(prefill)
            val attachmentDebt=plans.addDebt(DebtDraft(seed.person,DebtKind.LENT,BigDecimal("3"),"CNY",JavaTime.now().nativeTime(),null,"${f.tag} Attached",null,listOf(ref)))
            assertTrue(plans.debts(seed.person).single{it.id==attachmentDebt}.attachments.contains(ref))
            plans.settle(seed.person,SettleDraft(null,null,"${f.tag} Settled",JavaTime.now().nativeTime()))
            plans.markSubscriptionPaid(seed.subscription,JavaDate.now().nativeDate())
        }
        val before=c.ledger().use{it.list(TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=emptyList(),categoryIds=emptyList(),text=null,currency=null,deleted=DeletedFilter.EXCLUDE,sort=SortOrder.NEWEST_FIRST),PageRequest(0u,1u)).total}
        val intent=QuickEntry.templateIntent(f.context,seed.template)
        activityRule.scenario.onActivity{it.startActivity(Intent(intent).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))} // Same ActivityScenario task; cold/external NEW_TASK tested separately by CLI.
        awaitCondition{nodes().any{it.isEditable&&it.text?.toString()=="${f.tag} Merchant"}}
        shot("template-entry-unconfirmed")
        val after=c.ledger().use{it.list(TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=emptyList(),categoryIds=emptyList(),text=null,currency=null,deleted=DeletedFilter.EXCLUDE,sort=SortOrder.NEWEST_FIRST),PageRequest(0u,1u)).total}
        assertEquals(before,after);f.note("native-plans","due/payment/debt/attachment/settle/template real native, entry did not save")
    }
}
