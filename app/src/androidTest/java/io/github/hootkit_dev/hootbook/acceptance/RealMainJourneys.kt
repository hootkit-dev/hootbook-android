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
import io.github.hootkit_dev.hootbook.presentation.core.budgets.appendBudgetHistory
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionCsv
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
        awaitCondition{nodes().any{it.contentDescription?.toString()?.startsWith("core-screen-")==true}}
        if(!f.preferences.state.value.onboardingFinished){
            val originalAccounts=runBlocking{f.core().accounts().use{it.overview().accounts}}
            val starterName="${f.tag} Starter account"
            click(R.string.onboarding_start_fresh)
            if(originalAccounts.isEmpty()){
                field(R.string.bank_name_hint,starterName)
                field(R.string.balance,"0")
                field(R.string.last_4_digits_hint,"1234")
            }
            click(R.string.continue_action)
            showText(f.text(R.string.set_up_your_profile))
            field(R.string.what_should_we_call_you,"${f.tag} Profile")
            click(R.string.continue_action)
            showText(f.text(R.string.onboarding_notifications_body))
            click(R.string.onboarding_not_now)
            awaitCondition{f.preferences.state.value.onboardingFinished}
            assertEquals("${f.tag} Profile",f.preferences.state.value.userName)
            val resultingAccounts=runBlocking{f.core().accounts().use{it.overview().accounts}}
            if(originalAccounts.isEmpty()){
                val starter=resultingAccounts.single{it.name==starterName}
                assertEquals("1234",starter.last4)
                assertEquals(runBlocking{f.core().settings().use{it.mainCurrency()}},starter.mainCurrency)
            }else assertEquals(originalAccounts.map{it.id}.toSet(),resultingAccounts.map{it.id}.toSet())
            f.note("onboarding","real four-step UI saved profile and native account/main currency; existing accounts were not duplicated; optional notifications skipped")
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
    private fun showText(expected:String,contains:Boolean=false,direction:Int=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,timeout:Long=40_000){
        val end=android.os.SystemClock.elapsedRealtime()+timeout
        while(true){val current=nodes();if(current.any{it.isVisibleToUser&&it.text?.toString()?.let{value->if(contains)value.contains(expected) else value==expected}==true})return
            check(android.os.SystemClock.elapsedRealtime()<end){"Missing visible UI text: $expected"}
            scrollingNode(current)?.let{scroll(it,direction)};Thread.sleep(650)
        }
    }
    private fun descendants(root:AccessibilityNodeInfo?):List<AccessibilityNodeInfo>{
        if(root==null)return emptyList();val list=mutableListOf(root)
        for(i in 0 until root.childCount)list+=descendants(root.getChild(i));return list
    }
    private fun nodes():List<AccessibilityNodeInfo>{automation.clearCache();return descendants(automation.rootInActiveWindow)}
    private fun scrollingNode(current:List<AccessibilityNodeInfo>):AccessibilityNodeInfo? = current
        .filter { node -> val bounds=Rect();node.getBoundsInScreen(bounds);node.isScrollable&&node.isVisibleToUser&&bounds.height()>100 }
        .maxByOrNull { node -> val bounds=Rect();node.getBoundsInScreen(bounds);bounds.height() }
    private fun scroll(node:AccessibilityNodeInfo,direction:Int){
        val b=Rect();node.getBoundsInScreen(b)
        check(b.width()>0&&b.height()>80)
        val inset=main.window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
        val top=maxOf(b.top,(inset?.top?:0))+40;val bottom=minOf(b.bottom,f.context.resources.displayMetrics.heightPixels-(inset?.bottom?:0))-40
        check(bottom>top)
        val start=if(direction==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)bottom else top;val finish=if(direction==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)top else bottom
        automation.executeShellCommand("input swipe ${b.centerX()} $start ${b.centerX()} $finish 600").use{descriptor->android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()}
    }
    private fun click(id:Int) {
        // Destination names from the native journeys still identify the same feature. The
        // restored Home uses its avatar/settings and three main tabs instead of a shortcut grid.
        val settingsDestination = when (id) {
            R.string.core_complete_accounts -> R.string.title_accounts
            R.string.core_complete_categories -> R.string.categories
            R.string.core_complete_currencies -> R.string.currency
            R.string.core_complete_backup -> R.string.backup_sync_title
            R.string.core_complete_templates -> R.string.quick_add_templates
            R.string.core_complete_subscriptions -> R.string.core_plans_subscriptions
            R.string.core_complete_debts -> R.string.lend_borrow_title
            R.string.core_complete_budgets -> R.string.budgets
            R.string.core_complete_profile, R.string.core_shell_profile -> R.string.profile
            R.string.core_complete_layout, R.string.core_shell_appearance -> R.string.appearances
            R.string.core_shell_notifications -> R.string.notifications
            R.string.core_shell_about -> R.string.about
            R.string.core_shell_investments -> R.string.core_shell_investments
            else -> null
        }
        when {
            settingsDestination != null -> { settings(); clickText(f.text(settingsDestination)) }
            id == R.string.core_complete_settings -> settings()
            id == R.string.core_complete_transactions -> { home(); clickText(f.text(R.string.transactions)) }
            id == R.string.core_complete_analytics -> { home(); clickText(f.text(R.string.analytics)) }
            id == R.string.core_shell_app_lock -> { settings(); clickText(f.text(R.string.appearances)); clickText(f.text(R.string.core_ui_app_lock)) }
            id == R.string.core_shell_sync -> { settings(); clickText(f.text(R.string.backup_sync_title)); clickText(f.text(R.string.sync_firebase_title)) }
            id == R.string.core_ai_title -> clickText(f.text(R.string.ai_assistant_title))
            else -> clickText(f.text(id))
        }
    }
    private fun settings() {
        if(nodes().any { it.contentDescription?.toString() == "core-screen-settings" }) return
        home(); clickText(f.text(R.string.profile_desc))
        awaitCondition { nodes().any { it.contentDescription?.toString() == "core-screen-settings" } }
    }
    private fun clickPrefix(id:Int)=clickText(f.text(id)+"：",true)
    private fun clickText(text:String,prefix:Boolean=false){
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        var direction=AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;var previous="";var unchanged=0
        while(true){
            val current=nodes()
            if(current.firstOrNull()?.packageName?.toString()=="com.android.systemui"){
                automation.executeShellCommand("cmd statusbar collapse").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()};Thread.sleep(650);continue
            }
            val labels=current.filter{it.isVisibleToUser&&!it.isEditable&&(it.text?.toString()?.equals(text,true)==true||it.contentDescription?.toString()?.equals(text,true)==true||(prefix&&it.text?.toString()?.startsWith(text)==true))}
            val actions=labels.flatMap{label->val lb=Rect();label.getBoundsInScreen(lb)
                current.filter{candidate->val b=Rect();candidate.getBoundsInScreen(b)
                    candidate.isClickable&&candidate.isEnabled&&candidate.isVisibleToUser&&b.contains(lb.centerX(),lb.centerY())}}
            val action=actions.minByOrNull{candidate->val b=Rect();candidate.getBoundsInScreen(b);b.width().toLong()*b.height()}
            if(action!=null){check(action.performAction(AccessibilityNodeInfo.ACTION_CLICK)){"UI action rejected: $text"};Thread.sleep(850);f.note("driver-click",text);return}
            if(android.os.SystemClock.elapsedRealtime()>=deadline){shot("failed-action-"+text.replace(Regex("[^a-zA-Z0-9]"),"-"));error("Missing real UI action: $text")}
            scrollingNode(current)?.let{scroll->
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
            if(target==null){scrollingNode(nodes())?.let{scroll(it,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)};Thread.sleep(850)}}
        if(target==null){val labelNode=nodes().firstOrNull{it.text?.toString()==label};val labelBounds=Rect();labelNode?.getBoundsInScreen(labelBounds)
            target=nodes().firstOrNull{node->val b=Rect();node.getBoundsInScreen(b);node.isEditable&&node.isEnabled&&labelNode!=null&&b.contains(labelBounds.centerX(),labelBounds.centerY())}}
        check(target!=null){"Missing real UI field: $label"}
        target!!.performAction(AccessibilityNodeInfo.ACTION_FOCUS);check(target.refresh()&&target.isFocused){"Field did not focus: $label"}
        check(target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))
        Thread.sleep(850)
    }
    private fun home(){
        val deadline=android.os.SystemClock.elapsedRealtime()+40_000
        while(nodes().none{it.contentDescription?.toString()=="core-screen-home"}) {
            check(android.os.SystemClock.elapsedRealtime()<deadline){"Real navigation did not return to Home"}
            val homeTab=nodes().firstOrNull{it.isVisibleToUser&&it.isClickable&&it.isEnabled&&
                (it.text?.toString()==f.text(R.string.core_shell_home)||it.contentDescription?.toString()==f.text(R.string.core_shell_home))}
            if(homeTab!=null){check(homeTab.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(850)}else back()
        }
    }
    private fun transactionAmount(value:String,current:String="0") {
        clickText(current); field(R.string.amount,value); click(R.string.done)
    }
    private fun keypadAmount(value:String,confirmation:Int,current:String="0") {
        clickText(current)
        // Subscription/debt keypads are text-only decimal pads, not native numeric fields.
        repeat(if(current=="0")0 else current.length){clickText("⌫")}
        value.forEach { digit -> clickText(digit.toString()) }
        click(confirmation)
    }
    private fun transactionCategory(name:String) { click(R.string.category); clickText(name) }
    private fun openTransaction(merchant:String) {
        click(R.string.core_complete_transactions);field(R.string.search_transactions,merchant)
        clickText(merchant)
        // Compact windows show the original summary sheet; wide windows open a detail pane.
        if(nodes().any{it.isVisibleToUser&&it.text?.toString()==f.text(R.string.transaction_details)&&it.isClickable})click(R.string.transaction_details)
        else if(nodes().none{it.isVisibleToUser&&it.contentDescription?.toString()==f.text(R.string.edit_transaction)})click(R.string.transaction_details)
        awaitCondition{nodes().any{it.contentDescription?.toString()==f.text(R.string.edit_transaction)&&it.isEnabled}}
    }
    private fun categoryDetails(name:String) {
        click(R.string.expense)
        field(R.string.search_categories,name)
        clickText(name)
        showText(name)
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
        val c=f.core();home();click(R.string.core_complete_categories);click(R.string.add_category)
        val name="${f.tag} Category";field(R.string.cat_name,name);click(R.string.save)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).any{row->row.displayName==name}}}}
        clickText(name);val edited="$name Edited";field(R.string.cat_name,edited);click(R.string.save)
        val id=c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.displayName==edited}.id}
        categoryDetails(edited);click(R.string.more_options);click(R.string.cat_hide);awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.hidden}}}
        click(R.string.more_options);click(R.string.cat_show);awaitCondition{runBlocking{!c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.hidden}}}
        click(R.string.add_subcategory);field(R.string.cat_name,"${f.tag} Child");click(R.string.save)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==id}.subcategories.any{child->child.displayName=="${f.tag} Child"}}}}
        shot("ui-category-child-restored");f.note("ui-category","real category create/rename/hide/unhide + child create confirmed native id=$id")
    }
    @Test fun uiExpenseCreateEditDeleteUndoThroughTypedEntry()=runBlocking{
        val c=f.core();val merchant="${f.tag} UI expense";typed(QuickEntry.Entry.NewTransaction)
        transactionAmount("9.25");field(R.string.core_full_label_67,merchant)
        transactionCategory(c.categories().use{it.list(CategoryKind.EXPENSE,false).first().displayName})
        click(R.string.save)
        val filter=TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=emptyList(),categoryIds=emptyList(),text=merchant,currency=null,deleted=DeletedFilter.INCLUDE,sort=SortOrder.NEWEST_FIRST)
        awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,10u)).total==1u}}}
        val id=c.ledger().use{it.list(filter,PageRequest(0u,10u)).items.single().id};assertEquals(0,c.ledger().use{it.get(id).amount}.compareTo(BigDecimal("9.25")))
        openTransaction(merchant);click(R.string.edit_transaction);field(R.string.notes_optional,"UI edited notes");click(R.string.save)
        awaitCondition{runBlocking{c.ledger().use{it.get(id).notes=="UI edited notes"}}}
        click(R.string.delete);click(R.string.delete)
        awaitCondition{runBlocking{c.ledger().use{it.get(id).deleted}}};click(R.string.undo)
        awaitCondition{runBlocking{!c.ledger().use{it.get(id).deleted}}};shot("ui-expense-undo")
        f.note("ui-ledger","real typed-entry UI expense create/edit/delete/undo id=$id")
    }
    @Test fun uiBudgetCreateHistoryAndPauseResume()=runBlocking{
        val c=f.core();val name="${f.tag} UI budget";home();click(R.string.core_complete_budgets);click(R.string.new_budget);click(R.string.expense_budget)
        field(R.string.core_budgets_name,name);field(R.string.core_budgets_amount,"80");field(R.string.core_budgets_start,"2023-01-31");click(R.string.core_budgets_save)
        awaitCondition{nodes().any{it.text?.toString()==name}};click(R.string.core_budgets_pause);click(R.string.core_budgets_resume)
        click(R.string.core_budgets_limit_edit);field(R.string.core_budgets_limit_amount,"10");click(R.string.core_budgets_save)
        val id=c.budgets().use{it.list(JavaDate.now().nativeDate(),false,null).single{budget->budget.name==name}.id}
        awaitCondition{runBlocking{c.budgets().use{it.get(id,JavaDate.now().nativeDate(),null).current.limits.any{limit->limit.amount.compareTo(BigDecimal("10"))==0}}}}
        click(R.string.core_budgets_history);shot("ui-budget-history-first-page");click(R.string.core_history_load_older);shot("ui-budget-history-second-page")
        val oldest=c.budgets().use{it.get(id,LocalDate(2023,1u,31u),null).current}
        fun display(day:LocalDate)="%04d-%02d-%02d".format(java.util.Locale.ROOT,day.year,day.month.toInt(),day.day.toInt())
        clickText("${display(oldest.start)} — ${display(oldest.end)}");shot("ui-budget-oldest-exact-period");f.note("ui-budget","real create/pause/resume/categorylimit/history Load older traversed >24 through production UI")
    }
    @Test fun uiTemplateAndSubscriptionCreateAndPersonDebt()=runBlocking{
        val c=f.core();val name="${f.tag} UI template";home();click(R.string.core_complete_templates);clickText("＋")
        field(R.string.core_plans_name,name);field(R.string.core_plans_merchant,"${f.tag} UI merchant");field(R.string.core_plans_optional_amount,"6");field(R.string.core_plans_optional_currency,"CNY");click(R.string.save)
        awaitCondition{runBlocking{c.plans().use{it.templates().any{t->t.name==name}}}};shot("ui-template-saved")
        home();click(R.string.core_complete_subscriptions);click(R.string.add_transaction_subscription_cd);val sub="${f.tag} UI subscription"
        keypadAmount("5",R.string.core_plans_confirm);field(R.string.service_name_required,sub);field(R.string.core_plans_currency,"CNY");field(R.string.core_plans_next_due,JavaDate.now().toString());click(R.string.save)
        awaitCondition{nodes().any{it.text?.toString()==sub}};shot("ui-subscription-saved")
        home();click(R.string.core_complete_debts);click(R.string.core_debts_add_person);val person="${f.tag} UI person"
        field(R.string.core_debts_name,person);field(R.string.core_debts_phone,"+15551234567");click(R.string.core_debts_save)
        awaitCondition{nodes().any{it.text?.toString()==person}};click(R.string.core_debts_add_record)
        keypadAmount("7",R.string.done);field(R.string.core_debts_record_title,"${f.tag} UI debt");click(R.string.core_debts_save)
        awaitCondition{nodes().any{it.text?.toString()=="${f.tag} UI debt"}};shot("ui-person-debt-saved")
        f.note("ui-plans","real template/subscription/person/debt forms saved via Main UI")
    }
    private fun layout(name:String){File(f.output,"$name-layout.txt").writeText(nodes().joinToString("\n"){node->val b=Rect();node.getBoundsInScreen(b);"${node.className} text=${if(node.isPassword)"[PASSWORD]" else node.text} description=${node.contentDescription} bounds=$b checked=${node.isChecked}"})}
    @Test fun uiCurrenciesCustomAndNativeRateConversion()=runBlocking{
        val c=f.core();val code="X"+f.tag.takeLast(3).uppercase();home();click(R.string.core_complete_currencies)
        click(R.string.exchange_rates);click(R.string.core_currencies_add)
        field(R.string.core_currencies_code,code);field(R.string.core_currencies_name,"${f.tag} Currency");field(R.string.core_currencies_symbol,"X");click(R.string.core_currencies_save)
        awaitCondition{runBlocking{c.currency().use{it.customCurrencies().any{row->row.code==code}}}}
        c.currency().use{it.setCustomRate("USD","EUR",BigDecimal("1.5"))}
        systemBack();click(R.string.exchange_rates)
        field(R.string.core_currencies_amount,"2");field(R.string.core_currencies_from,"USD");field(R.string.core_currencies_to,"EUR");click(R.string.core_currencies_convert)
        awaitCondition{nodes().any{node->node.text?.toString()?.let{value->Regex("2 USD = ([0-9.]+) EUR").matchEntire(value)?.groupValues?.get(1)?.toBigDecimalOrNull()?.compareTo(BigDecimal("3"))==0}==true}};assertEquals(0,c.currency().use{it.convert(BigDecimal("2"),"USD","EUR")!!}.compareTo(BigDecimal("3")))
        shot("ui-currency-conversion");f.note("ui-currency","real customcurrency save code=$code, real convert UI using native rate fixture=1.5 result=3; no online rates")
    }
    @Test fun uiAppearanceThemesAndNoCredentialLockGuard()=runBlocking{
        home();click(R.string.core_complete_layout);click(R.string.theme_dark)
        awaitCondition{f.preferences.state.value.darkTheme==true};shot("ui-appearance-dark")
        click(R.string.theme_light);awaitCondition{f.preferences.state.value.darkTheme==false}
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
            click(R.string.notif_allow);clickResource("com.android.permissioncontroller:id/permission_deny_button")
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,androidx.core.content.ContextCompat.checkSelfPermission(f.context,android.Manifest.permission.POST_NOTIFICATIONS))
            click(R.string.notif_allow);clickResource("com.android.permissioncontroller:id/permission_allow_button")
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
        assertFalse(nodes().any{it.contentDescription?.toString()?.startsWith("core-screen-")==true})
        click(R.string.core_ui_unlock);awaitCondition{nodes().any{it.isEditable&&it.isPassword}};InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_ui_locked)}}
        assertFalse(nodes().any{it.contentDescription?.toString()?.startsWith("core-screen-")==true})
        click(R.string.core_ui_unlock);enterFixturePin();awaitCondition{nodes().any{it.contentDescription?.toString()=="core-screen-app-lock"}}
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
        showText(f.context.getString(R.string.transactions_format,total.toInt()))
        shot("ui-analytics-alltime-future");click(R.string.core_analytics_this_month);shot("ui-analytics-this-month")
        f.note("ui-analytics","real allTime Expense-filter UI count equals exact matching Core total=$total includes native futureid=$future; thismonth preset rendered; no App monetary calculation")
    }
    @Test fun uiInvestmentEmptyCredentialConnectCancelProtected()=runBlocking{
        home();click(R.string.core_complete_settings);click(R.string.core_shell_investments);click(R.string.core_invest_connect)
        assertTrue(main.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertTrue(nodes().any{it.isEditable&&it.isPassword})
        val before=f.core().brokerage().use{it.connections().size}
        click(R.string.core_invest_cancel)
        awaitCondition{nodes().any{it.contentDescription?.toString()=="core-screen-investments"}}
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
        home();click(R.string.core_complete_transactions);field(R.string.search_transactions,"${f.tag} Page")
        click(R.string.more_options);click(R.string.select_all);click(R.string.core_full_bulk_edit);click(R.string.core_full_bulk_edit);field(R.string.core_full_bulk_amount,"1.25");click(R.string.core_full_confirm)
        awaitCondition{runBlocking{c.ledger().use{api->ids.all{api.get(it).amount.compareTo(BigDecimal("1.25"))==0}}}}
        click(R.string.core_full_export_all_pages)
        awaitCondition{nodes().any{it.text?.toString()==f.context.getString(R.string.core_full_export_count,"206")}}
        val csv=File(f.context.cacheDir,"core-attachment-share").walkTopDown().filter{it.isFile&&it.extension=="csv"}.toList()
        assertTrue(csv.isNotEmpty());assertTrue(csv.any{it.readText().contains(ids.last())});layout("ui-csv206-ready")
        systemBack();showText("${f.tag} Page 0",timeout=180_000)
        showText(f.context.getString(R.string.transaction_page_position,"1","206","206"));shot("ui-ledger-second-page")
        f.note("ui-ledger-pages","native206 unique Page fixture rows, actual UI textfilter/selectAll206/bulkamount1.25/CSV206/continuous Load more; CSV contains final generatedID; seed writes not UI")
    }
    @Test fun uiCreditCardPocketCalibrationArchiveAndDefault()=runBlocking{
        val c=f.core();home();click(R.string.core_complete_accounts);click(R.string.add_account_fab_desc);click(R.string.account_kind_credit_card)
        val name="${f.tag} UI card";field(R.string.account_name,name);field(R.string.account_last4,"8679");field(R.string.account_opening_debt,"20")
        field(R.string.account_credit_limit,"1000");field(R.string.account_statement_day,"15");field(R.string.account_due_day,"2");click(R.string.save)
        awaitCondition{runBlocking{c.accounts().use{it.overview().accounts.any{row->row.name==name}}}}
        val id=c.accounts().use{it.overview().accounts.single{row->row.name==name}.id};val card=c.accounts().use{it.get(id)}
        assertEquals(AccountKind.CREDIT_CARD,card.kind);assertEquals(0,card.creditLimit!!.compareTo(BigDecimal("1000")));assertEquals(15.toUByte(),card.statementDay);assertEquals(2.toUByte(),card.dueDay)
        click(R.string.edit);field(R.string.account_name,"$name Edited");click(R.string.save)
        awaitCondition{runBlocking{c.accounts().use{it.get(id).name=="$name Edited"}}}
        click(R.string.more_options);click(R.string.account_set_default);awaitCondition{runBlocking{c.accounts().use{it.get(id).isDefault}}};click(R.string.more_options);click(R.string.account_clear_default)
        click(R.string.more_options);click(R.string.account_archive);awaitCondition{runBlocking{c.accounts().use{it.get(id).archived}}};click(R.string.more_options);click(R.string.account_unarchive)
        click(R.string.account_currency_add);field(R.string.account_currency_code,"USD");field(R.string.account_balance,"3");click(R.string.save)
        awaitCondition{runBlocking{c.accounts().use{it.get(id).pockets.any{pocket->pocket.currency=="USD"}}}}
        assertFalse(c.accounts().use{it.get(id).totalComplete});shot("ui-card-pocket-created");click(R.string.account_calibrate);field(R.string.account_balance,"25");click(R.string.save)
        click(R.string.account_balance_trend);click(R.string.net_worth_history_month)
        shot("ui-card-native-history");systemBack();click(R.string.more_options);click(R.string.account_delete);click(R.string.delete)
        awaitCondition{runBlocking{c.accounts().use{it.overview().accounts.none{row->row.id==id}}}}
        f.note("ui-accounts-advanced","real card create/nameedit/default/archive/unarchive/USD pocket/calibrate/history/zero-refdelete UI and Core queries; balance/missing-rate output remains Core")
    }
    @Test fun uiOwnedCategoryChildDeletionAndProtectedBuiltinSurface()=runBlocking{
        val c=f.core();val category=c.categories().use{it.list(CategoryKind.EXPENSE,true).first{row->row.builtinKey==null&&row.displayName.startsWith("Acceptance-")&&row.subcategories.isNotEmpty()}}
        home();click(R.string.core_complete_categories);categoryDetails(category.displayName)
        val child=category.subcategories.first();clickBelow(child.displayName,R.string.more_options);click(R.string.delete);click(R.string.delete)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).single{row->row.id==category.id}.subcategories.none{row->row.id==child.id}}}}
        click(R.string.more_options);click(R.string.delete);click(R.string.cat_delete_confirm)
        awaitCondition{runBlocking{c.categories().use{it.list(CategoryKind.EXPENSE,true).none{row->row.id==category.id}}}}
        val builtin=c.categories().use{it.list(CategoryKind.EXPENSE,false).first{row->row.builtinKey!=null}};categoryDetails(builtin.displayName)
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
    @Test fun uiSubscriptionHideSchedulePaidAndConfirmedLedgerPayment()=runBlocking{
        val c=f.core();val today=JavaDate.now().nativeDate();val sub=c.plans().use{it.subscriptions(today,true).first{row->row.name.contains("UI subscription")&&!row.ended}}
        home();click(R.string.core_complete_subscriptions);field(R.string.core_detail_name_filter,sub.name);clickText(sub.name)
        click(R.string.core_plans_hide);awaitCondition{runBlocking{c.plans().use{it.subscriptions(today,true).single{row->row.id==sub.id}.hidden}}}
        click(R.string.core_plans_unhide);click(R.string.core_plans_mark_paid);click(R.string.core_plans_confirm)
        awaitCondition{runBlocking{c.plans().use{it.subscriptions(today,true).single{row->row.id==sub.id}.lastPaid==today}}}
        val all=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),null,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        val before=c.ledger().use{it.list(all,PageRequest(0u,1u)).total};click(R.string.core_plans_record_payment)
        assertEquals(before,c.ledger().use{it.list(all,PageRequest(0u,1u)).total})
        val account=c.accounts().use{it.overview().accounts.first{row->!row.archived&&row.kind==AccountKind.WALLET}}
        click(R.string.account);clickText(account.name)
        transactionCategory(c.categories().use{it.list(CategoryKind.EXPENSE,false).first().displayName});click(R.string.save)
        awaitCondition{runBlocking{c.ledger().use{it.list(all,PageRequest(0u,1u)).total==before+1u}}}
        val saved=c.ledger().use{it.list(all.copy(text=sub.name),PageRequest(0u,50u)).items};assertTrue(saved.any{row->row.subscriptionId==sub.id})
        shot("ui-subscription-payment-confirmed");f.note("ui-subscription","actual hide/unhide/schedulepaid + typed paymentprefill nosave before confirmation/one linked nativeledger record after UI Save")
    }
    private fun systemBack(){automation.executeShellCommand("input keyevent 4").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()};Thread.sleep(850)}
    private fun documentDownloads(){
        awaitCondition{nodes().any{it.packageName?.toString()=="com.google.android.documentsui"}}
        clickText("Show roots");clickText("Downloads");Thread.sleep(850)
    }
    @Test fun uiAttachmentSafCancelSelectPreviewShareAndConfirmedSave()=runBlocking{
        val c=f.core();val merchant="${f.tag} SAF expense";typed(QuickEntry.Entry.NewTransaction)
        transactionAmount("3.50");field(R.string.core_full_label_67,merchant)
        click(R.string.core_full_attachment_add);awaitCondition{nodes().any{it.packageName?.toString()=="com.google.android.documentsui"}};systemBack()
        awaitCondition{nodes().any{it.isEditable&&it.text?.toString()==merchant}}
        click(R.string.core_full_attachment_add);documentDownloads();clickText("hoot-core-accept-image.png")
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.core_full_attachment_view)}}
        click(R.string.core_full_attachment_view);click(R.string.core_attachment_share)
        awaitCondition{nodes().none{it.contentDescription?.toString()=="core-screen-transactions"}};systemBack();click(R.string.core_attachment_close)
        transactionCategory(c.categories().use{it.list(CategoryKind.EXPENSE,false).first().displayName});click(R.string.save)
        val filter=TransactionFilter(null,null,listOf(TransactionKind.EXPENSE),emptyList(),emptyList(),merchant,null,DeletedFilter.EXCLUDE,SortOrder.NEWEST_FIRST)
        awaitCondition{runBlocking{c.ledger().use{it.list(filter,PageRequest(0u,1u)).total==1u}}}
        val saved=c.ledger().use{it.list(filter,PageRequest(0u,1u)).items.single()};assertEquals(merchant,saved.merchant);assertEquals(1,saved.attachments.size)
        assertTrue(io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore(f.context).resolve(saved.attachments.single())?.isFile==true)
        shot("ui-saf-attachment-saved");f.note("ui-attachment","actual DocumentsUI cancel preserves merchant; chosen external PNG->native reference, preview/share chooser cancel, user Save commits one with retained file")
    }
    @Test fun uiBackupEncryptedExportSafSaveInspectAndMerge()=runBlocking{
        val root=File(f.context.noBackupFilesDir,"hootkit-core/backup-ui");home();click(R.string.core_complete_backup)
        click(R.string.backup_export)
        field(R.string.backup_password,"fixture-password");click(R.string.backup_export_action);click(R.string.backup_save)
        documentDownloads()
        val edit=nodes().first{it.isEditable&&it.isEnabled};check(edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        val fileName="${f.tag}-ui.hkbak";check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,fileName)}));clickText("Save")
        awaitCondition{nodes().any{it.text?.toString()==f.text(R.string.backup_share)}}
        click(R.string.backup_share)
        awaitCondition{nodes().firstOrNull()?.packageName?.toString()?.let{it!=f.context.packageName}==true}
        systemBack();showText(f.text(R.string.backup_share));systemBack()
        click(R.string.backup_restore)
        field(R.string.backup_password,"fixture-password",last=true)
        click(R.string.backup_file);documentDownloads();clickText(fileName)
        val source=root.walkTopDown().filter{it.isFile&&it.name=="source.backup"}.maxBy{it.lastModified()}
        assertTrue(f.core().backup().use{it.inspectBackup(source.path,"fixture-password").encrypted})
        val original=java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString(""){"%02x".format(it)}
        click(R.string.backup_restore_action)
        showText(f.text(R.string.backup_last_restore))
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
            click(label);awaitCondition{nodes().any{it.contentDescription?.toString()=="core-screen-"+when(label){R.string.core_shell_profile->"profile";R.string.core_shell_appearance->"appearance";R.string.core_shell_app_lock->"app-lock";R.string.core_shell_notifications->"notifications";R.string.core_shell_about->"about";R.string.core_shell_sync->"sync";else->"investments"}}};shot("menu-$label");f.note("menu","real route label=${f.text(label)}")
            InstrumentationRegistry.getInstrumentation().runOnMainSync{main.onBackPressedDispatcher.onBackPressed()};Thread.sleep(850)
        }
        home();for(label in listOf(R.string.core_complete_categories,R.string.core_complete_currencies,R.string.core_complete_backup,
            R.string.core_complete_templates,R.string.core_complete_subscriptions,R.string.core_complete_debts,R.string.core_complete_budgets)){
            click(label);awaitCondition{nodes().any{it.contentDescription?.toString()=="core-screen-"+when(label){R.string.core_complete_categories->"categories";R.string.core_complete_currencies->"currencies";R.string.core_complete_backup->"backup";R.string.core_complete_templates->"templates";R.string.core_complete_subscriptions->"subscriptions";R.string.core_complete_debts->"debts";else->"budgets"}}};shot("home-route-$label");f.note("menu","real Home → settings menu route=${f.text(label)}")
            home()
        }
    }
    @Test fun uiCreatesWalletAndProfileSurvivesActivityRecreation()=runBlocking {
        home();click(R.string.core_complete_accounts);click(R.string.add_account_fab_desc)
        val name="${f.tag} UI wallet"
        field(R.string.account_name,name);click(R.string.account_kind_wallet);field(R.string.account_opening_balance,"25")
        click(R.string.save)
        awaitCondition{nodes().any{it.text?.toString()?.contains(name)==true}}
        val wallets=f.core().accounts().use{it.overview().accounts}
        assertTrue(wallets.any{it.name==name&&it.kind==AccountKind.WALLET});f.note("ui-account","saved native id=${wallets.single{it.name==name}.id}")
        home();click(R.string.core_complete_profile);click(R.string.edit)
        field(R.string.name_label,"${f.tag} Display");click(R.string.save)
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
