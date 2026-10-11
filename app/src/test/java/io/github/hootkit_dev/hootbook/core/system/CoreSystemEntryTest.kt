package io.github.hootkit_dev.hootbook.core.system

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.receiver.BootReceiver
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import io.github.hootkit_dev.hootbook.widget.QuickEntryPublisher
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate as JavaDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreSystemEntryTest {
    private fun context()=ApplicationProvider.getApplicationContext<Application>().also{
        File(it.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(it.filesDir,"datastore").deleteRecursively()
        it.getSharedPreferences("core-reminder-delivery",0).edit().clear().commit()
        it.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun typedCoreIdsRoundTripAndDataSeparatesHashCollidingStrings(){
        val c=context()
        for(entry in listOf(QuickEntry.Entry.NewTransaction,QuickEntry.Entry.NewTransfer,QuickEntry.Entry.NewSubscription,QuickEntry.Entry.Home,
            QuickEntry.Entry.Template("id/with spaces?encoded"),QuickEntry.Entry.Subscription("uuid-string"))){
            val i=QuickEntry.intent(c,entry);assertEquals(entry,QuickEntry.parse(i));assertEquals(c.packageName,i.component!!.packageName)
        }
        assertEquals("Aa".hashCode(),"BB".hashCode())
        assertNotEquals(QuickEntry.templateIntent(c,"Aa").data,QuickEntry.templateIntent(c,"BB").data)
    }
    @Test fun rejectsForeignNestedMalformedAndMismatchedNavigation(){
        val c=context();val valid=QuickEntry.intent(c,QuickEntry.Entry.Template("real"))
        for(uri in listOf("https://core-entry/template/real","hootbook://other/template/real","hootbook://core-entry/template/real?x=y",
            "hootbook://core-entry/template/real#x","hootbook://u@core-entry/template/real","hootbook://core-entry:3/template/real","hootbook://core-entry/template/a/b")){
            assertNull(uri,QuickEntry.parse(Intent(valid).setData(Uri.parse(uri))))
        }
        assertNull(QuickEntry.parse(Intent(valid).setAction(QuickEntry.ACTION_TRANSFER)))
        assertNull(QuickEntry.parse(Intent().putExtra("nested",valid)))
        assertThrows(IllegalArgumentException::class.java){QuickEntry.templateIntent(c,"\n")}
    }
    @Test fun legacyLongShortcutDegradesToUserConfirmedNewEntry(){
        assertEquals(QuickEntry.Entry.NewTransaction,QuickEntry.parse(Intent(QuickEntry.ACTION_TRANSACTION).setData(Uri.parse("hootbook://template/24")).putExtra("template_id",24L)))
        assertEquals(QuickEntry.Entry.NewTransaction,QuickEntry.parse(Intent(QuickEntry.ACTION_TRANSACTION)))
        assertFalse(QuickEntry.isTemplateShortcut("template_24"));assertTrue(QuickEntry.isTemplateShortcut(QuickEntry.shortcutId("uuid")))
    }
    @Test fun protectedBootActionsOnly(){
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.ALLOWED);assertTrue(Intent.ACTION_TIMEZONE_CHANGED in BootReceiver.ALLOWED)
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in BootReceiver.ALLOWED);assertFalse(Intent.ACTION_LOCKED_BOOT_COMPLETED in BootReceiver.ALLOWED)
        assertFalse("random" in BootReceiver.ALLOWED)
    }
    @Test fun wallClockNextDayMarginAndDstArePlatformOnly(){
        val utc=ZoneId.of("UTC")
        assertEquals(Instant.parse("2026-01-01T20:00:00Z"),nextReminder(Instant.parse("2026-01-01T19:59:00Z"),utc,1200))
        assertEquals(Instant.parse("2026-01-02T20:00:00Z"),nextReminder(Instant.parse("2026-01-01T19:59:55Z"),utc,1200))
        val ny=ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"),nextReminder(Instant.parse("2026-03-08T05:00:00Z"),ny,150))
        assertThrows(IllegalArgumentException::class.java){nextReminder(Instant.now(),utc,1440)}
    }
    @Test fun launcherPublishesCurrentStringTemplatesAndHidesNamesUnderLock()=runTest{
        val c=context();val prefs=CoreUiPreferencesStore(c);val core=ReminderCore()
        val publisher=QuickEntryPublisher(c,CoreSession(backgroundScope){core},ChangeBus(),prefs,backgroundScope)
        val template=QuickTemplate("string-id","SECRET MERCHANT",TransactionKind.EXPENSE,"",null,null,BigDecimal.TEN,true,null,"USD",null,0)
        publisher.publish(listOf(template),false)
        val shown=ShortcutManagerCompat.getDynamicShortcuts(c)
        assertTrue(shown.any{it.id==QuickEntry.shortcutId("string-id")});assertTrue(shown.any{it.id=="core_transfer"});assertTrue(shown.any{it.id=="core_subscription"})
        val typed=shown.single{it.id==QuickEntry.shortcutId("string-id")};assertEquals(QuickEntry.Entry.Template("string-id"),QuickEntry.parse(typed.intent))
        publisher.publish(listOf(template),true)
        assertFalse(ShortcutManagerCompat.getDynamicShortcuts(c).any{it.shortLabel.toString().contains("SECRET")})
        assertEquals(0,core.reads)
    }
    @Test fun deliveryQueriesCoreDatesMutesStringIdAndClosesTemporaryHandle()=runTest{
        val c=context();val prefs=CoreUiPreferencesStore(c);prefs.update{it.copy(mutedSubscriptionIds=setOf("muted"))}
        val core=ReminderCore();val session=CoreSession(backgroundScope){core};val dispatcher=CoreReminderDispatcher(c,session,prefs);val today=JavaDate.of(2026,1,1)
        assertTrue(dispatcher.deliver(today));assertEquals(today.coreDate() to today.plusDays(1).coreDate(),core.range)
        val n=Shadows.shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Current Core name",n.extras.getString("android.text"));assertEquals(core.opened,core.closed)
        assertFalse(dispatcher.deliver(today));assertEquals(1,core.reads)
    }
    @Test fun appLockNotificationPublicAndPrivateAreGeneric()=runTest{
        val c=context();val prefs=CoreUiPreferencesStore(c);prefs.update{it.copy(appLockEnabled=true)}
        val core=ReminderCore();val dispatcher=CoreReminderDispatcher(c,CoreSession(backgroundScope){core},prefs)
        assertTrue(dispatcher.deliver(JavaDate.of(2026,1,2)))
        val n=Shadows.shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE,n.visibility)
        assertFalse(n.extras.getString("android.text")!!.contains("Current Core"));assertFalse(n.publicVersion.extras.getString("android.text")!!.contains("Current Core"))
    }
    @Test fun disabledRemindersNeverOpenCoreAndAllMutedNoScanIsQuiet()=runTest{
        val c=context();val prefs=CoreUiPreferencesStore(c);prefs.update{it.copy(notificationsEnabled=false)}
        val core=ReminderCore();val dispatcher=CoreReminderDispatcher(c,CoreSession(backgroundScope){core},prefs)
        assertFalse(dispatcher.deliver());assertEquals(0,core.opened)
        prefs.update{it.copy(notificationsEnabled=true,scanReminderEnabled=false,mutedSubscriptionIds=setOf("current","muted"))}
        assertFalse(dispatcher.deliver());assertEquals(0,Shadows.shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.size)
        prefs.update{it.copy(scanReminderEnabled=true)};assertTrue(dispatcher.deliver());assertEquals(core.opened,core.closed)
    }
    @Test fun missingCoreResponseLeavesDayRetryableAndDoesNotCloseAppSession()=runTest{
        val c=context();val prefs=CoreUiPreferencesStore(c);val core=ReminderCore();core.fail=true
        val dispatcher=CoreReminderDispatcher(c,CoreSession(backgroundScope){core},prefs)
        try{dispatcher.deliver();fail("failure must propagate")}catch(_:IllegalStateException){}
        assertEquals(core.opened,core.closed);assertNull(c.getSharedPreferences("core-reminder-delivery",0).getString("deliveredDay",null))
        core.fail=false;assertTrue(dispatcher.deliver());assertEquals(0,core.shutdowns)
    }
}
private class ReminderCore:HootKitCore(NoPointer){
    var reads=0;var opened=0;var closed=0;var shutdowns=0;var fail=false
    var range:Pair<LocalDate,LocalDate>?=null
    override fun plans():Plans{opened++;return object:Plans(NoPointer){
        override suspend fun dueSubscriptions(today:LocalDate,through:LocalDate):List<Subscription>{
            reads++;range=today to through;if(fail)throw IllegalStateException("fixture")
            return listOf(row("current","Current Core name"),row("muted","Muted Core name"))
        }
        private var released=false;override fun destroy(){if(!released){released=true;closed++};super.destroy()}
    }}
    private fun row(id:String,name:String)=Subscription(id=id,name=name,amount=Money(BigDecimal.ONE,"USD"),cycle=Cycle(1u,CycleUnit.MONTH),endDate=null,
        nextDue=null,lastPaid=null,accountId=null,categoryId=null,subcategoryId=null,notes=null,hidden=false,monthlyEquivalent=null,ended=false,overdue=false,daysUntil=null)
    override suspend fun shutdown(){shutdowns++}
}
