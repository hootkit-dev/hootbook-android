package io.github.hootkit_dev.hootbook.core.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.widget.QuickEntryPublisher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreSystemCoordinator @Inject constructor(@ApplicationContext private val context:Context,
    private val scheduler:CoreReminderScheduler,private val publisher:QuickEntryPublisher,
    private val prefs:CoreUiPreferencesStore,@ApplicationScope private val scope:CoroutineScope) {
    private var job:Job?=null
    @Synchronized fun start(){
        if(job?.isActive==true||CoreProcess.isCrashProcess(context))return
        publisher.start()
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("cashiro_sync")
        job=scope.launch {
            prefs.ready()
            prefs.state.map{Triple(it.notificationsEnabled,it.reminderMinutes,it.appLockEnabled)}.distinctUntilChanged().conflate().collect{
                if(!it.first||it.third)context.getSystemService(android.app.NotificationManager::class.java).cancel(100)
                try{scheduler.scheduleDailyReminder()}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){android.util.Log.w("CoreSystem","Reminder scheduling awaits retry")}
            }
        }
    }
    /** Hook on foreground/permission grant/exact permission changes; no database shutdown. */
    fun refresh(){if(CoreProcess.isCrashProcess(context))return;scope.launch{try{scheduler.scheduleDailyReminder()}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){android.util.Log.w("CoreSystem","Reminder refresh deferred")}}}
}
