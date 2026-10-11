package io.github.hootkit_dev.hootbook.receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hootkit_dev.hootbook.core.system.*
class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=CoreReminderScheduler.ACTION_REMINDER||intent.dataString!="hootbook://core-reminder/daily")return
        launchBounded(context){entry->
            try{entry.dispatcher().deliver()}finally{entry.scheduler().scheduleDailyReminder()}
        }
    }
}
