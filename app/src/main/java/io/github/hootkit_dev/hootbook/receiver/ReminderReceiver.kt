package io.github.hootkit_dev.hootbook.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.hootkit_dev.hootbook.MainActivity
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.NotificationChannels
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.manager.NotificationScheduler
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The daily reminder. Names the subscriptions due today or tomorrow (except those whose reminder
 * the user turned off), else reminds to record the day, then arms the next day's alarm: an exact
 * alarm fires once.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderReceiverEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
        fun subscriptionRepository(): SubscriptionRepository
        fun notificationScheduler(): NotificationScheduler
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderReceiverEntryPoint::class.java
        )
        val pending = goAsync()
        receiverScope.launch {
            try {
                val preferences = entryPoint.userPreferencesRepository()
                if (preferences.upcomingNotificationsEnabled.first()) {
                    val muted = preferences.disabledSubscriptionNotificationIds.first()
                    val tomorrow = LocalDate.now().plusDays(1)
                    val due = entryPoint.subscriptionRepository().getActiveSubscriptions().first()
                        .filter { it.id.toString() !in muted }
                        .filter { s -> s.nextPaymentDate?.let { !it.isAfter(tomorrow) } == true }
                    notify(context, due)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing reminder alarm", e)
            } finally {
                runCatching { entryPoint.notificationScheduler().scheduleDailyReminder() }
                    .onFailure { Log.e(TAG, "Could not arm the next reminder", it) }
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, due: List<SubscriptionEntity>) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NotificationChannels.REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, text) = if (due.isEmpty()) {
            context.getString(R.string.reminder_daily_title) to context.getString(R.string.reminder_daily_text)
        } else {
            context.getString(R.string.reminder_due_title, due.size) to
                due.joinToString(context.getString(R.string.list_separator)) { it.merchantName }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.hootbook)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val NOTIFICATION_ID = 100
    }
}
