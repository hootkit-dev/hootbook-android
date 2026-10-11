package io.github.hootkit_dev.hootbook.core.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.Subscription
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.core.NotificationChannels
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CoreReminderDispatcher @Inject constructor(@ApplicationContext private val context:Context,private val session:CoreSession,private val prefs:CoreUiPreferencesStore) {
    private val mutex=Mutex()
    private val delivery=context.getSharedPreferences("core-reminder-delivery",Context.MODE_PRIVATE)
    suspend fun deliver(now:LocalDate=LocalDate.now(),requireScheduledTime:Boolean=false):Boolean=mutex.withLock {
        if(CoreProcess.isCrashProcess(context))return@withLock false
        prefs.ready();val p=prefs.state.value
        if(!p.notificationsEnabled||!notificationPermission(context)||!NotificationManagerCompat.from(context).areNotificationsEnabled())return@withLock false
        if(requireScheduledTime&&java.time.LocalTime.now().let{it.hour*60+it.minute}<p.reminderMinutes)return@withLock false
        if(delivery.getString("deliveredDay",null)==now.toString())return@withLock false
        // Core alone decides which subscriptions are due and their dates. No local cycle filters.
        val due=session.awaitCore().plans().use{it.dueSubscriptions(now.coreDate(),now.plusDays(1).coreDate())}
            .filter{it.id !in p.mutedSubscriptionIds}
        if(due.isEmpty()&&!p.scanReminderEnabled)return@withLock false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(NotificationChannels.REMINDER_CHANNEL_ID,context.getString(R.string.reminder_channel_name),NotificationManager.IMPORTANCE_DEFAULT))
        val (title,text)=reminderText(context,due,p.appLockEnabled)
        val entry=if(due.size==1)QuickEntry.Entry.Subscription(due.single().id) else QuickEntry.Entry.Home
        val content=PendingIntent.getActivity(context,100,QuickEntry.intent(context,entry),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val generic=NotificationCompat.Builder(context,NotificationChannels.REMINDER_CHANNEL_ID).setSmallIcon(R.drawable.hootbook)
            .setContentTitle(context.getString(R.string.reminder_daily_title)).setContentText(context.getString(R.string.core_system_private_reminder)).build()
        val n=NotificationCompat.Builder(context,NotificationChannels.REMINDER_CHANNEL_ID).setSmallIcon(R.drawable.hootbook)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(generic).setContentIntent(content)
            .setOnlyAlertOnce(true).setAutoCancel(true).build()
        try{manager.cancel(100);manager.notify(100,n)}catch(_:SecurityException){return@withLock false}
        // Do not mark delivered until Android accepted notify. Missing permission remains retryable.
        delivery.edit().putString("deliveredDay",now.toString()).commit()
        true
    }
}
internal fun LocalDate.coreDate()=dev.hootkit.core.LocalDate(year,monthValue.toUByte(),dayOfMonth.toUByte())
fun notificationPermission(context:Context)=Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
internal fun reminderText(context:Context,due:List<Subscription>,locked:Boolean):Pair<String,String> = when {
    locked->context.getString(R.string.reminder_daily_title) to context.getString(R.string.core_system_private_reminder)
    due.isEmpty()->context.getString(R.string.reminder_daily_title) to context.getString(R.string.reminder_daily_text)
    else->context.getString(R.string.reminder_due_title,due.size) to due.joinToString(context.getString(R.string.list_separator)){it.name}
}
