package io.github.hootkit_dev.hootbook.core.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.receiver.ReminderReceiver
import java.time.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Android wall-clock scheduling only; subscription dates and amounts remain Core-owned. */
@Singleton
class CoreReminderScheduler @Inject constructor(@ApplicationContext private val context:Context,private val prefs:CoreUiPreferencesStore) {
    suspend fun scheduleDailyReminder(){
        cancelLegacyAlarm()
        prefs.ready()
        val p=prefs.state.value
        if(!p.notificationsEnabled){cancelDailyReminder();return}
        val next=nextReminder(Instant.now(),ZoneId.systemDefault(),p.reminderMinutes)
        val alarm=context.getSystemService(AlarmManager::class.java)
        val pending=alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        if(Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms()) {
            try{alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toEpochMilli(),pending)}
            catch(_:SecurityException){alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toEpochMilli(),pending)}
        }else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toEpochMilli(),pending)
        // A platform-only fallback: delivery is de-duplicated by local day even if both wake up.
        val delay=Duration.between(Instant.now(),next).toMillis().coerceAtLeast(1000)
        val request=PeriodicWorkRequestBuilder<CoreReminderWorker>(24,TimeUnit.HOURS,30,TimeUnit.MINUTES)
            .setInitialDelay(delay,TimeUnit.MILLISECONDS).addTag(WORK_NAME).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun cancelDailyReminder(){
        alarmIntent(PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let{
            context.getSystemService(AlarmManager::class.java).cancel(it);it.cancel()
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    private fun cancelLegacyAlarm(){
        PendingIntent.getBroadcast(context,200,Intent(context,ReminderReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let{context.getSystemService(AlarmManager::class.java).cancel(it);it.cancel()}
    }
    private fun alarmIntent(flags:Int):PendingIntent?=PendingIntent.getBroadcast(context,200,
        Intent(context,ReminderReceiver::class.java).setAction(ACTION_REMINDER).setData(Uri.parse("hootbook://core-reminder/daily")),flags)
    companion object {
        const val ACTION_REMINDER="io.github.hootkit_dev.hootbook.action.CORE_DAILY_REMINDER"
        const val WORK_NAME="hootkit-core-daily-reminder"
    }
}
internal fun nextReminder(now:Instant,zone:ZoneId,minutes:Int):Instant {
    require(minutes in 0..1439)
    val local=now.atZone(zone)
    val time=LocalTime.of(minutes/60,minutes%60)
    var target=local.toLocalDate().atTime(time).atZone(zone)
    if(!target.toInstant().isAfter(now.plusSeconds(10)))target=local.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    return target.toInstant()
}
