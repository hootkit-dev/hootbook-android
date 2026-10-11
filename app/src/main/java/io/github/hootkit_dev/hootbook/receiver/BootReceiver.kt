package io.github.hootkit_dev.hootbook.receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hootkit_dev.hootbook.core.system.*
class BootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in ALLOWED)return
        launchBounded(context){it.scheduler().scheduleDailyReminder()}
    }
    companion object { val ALLOWED=setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED) }
}
