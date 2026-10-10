package io.github.hootkit_dev.hootbook.data.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.receiver.ReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles scheduling of notification-related tasks using AlarmManager for precision.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val ALARM_REQUEST_CODE = 200
    }

    /**
     * Schedules or updates the daily reminder notification using AlarmManager.
     */
    suspend fun scheduleDailyReminder() {
        val upcomingEnabled = userPreferencesRepository.upcomingNotificationsEnabled.first()
        val alertTimeMinutes = userPreferencesRepository.scanNewTransactionsAlertTime.first()

        if (!upcomingEnabled) {
            Log.d(TAG, "Notifications disabled, cancelling alarm")
            cancelDailyReminder()
            return
        }

        val currentTime = LocalDateTime.now()
        val alertTime = LocalTime.of((alertTimeMinutes / 60).toInt(), (alertTimeMinutes % 60).toInt())
        var scheduledTime = LocalDateTime.of(currentTime.toLocalDate(), alertTime)

        // If the time has already passed today, schedule for tomorrow
        if (currentTime.isAfter(scheduledTime)) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        // Add 10 seconds buffer if the scheduled time is basically "now" to avoid immediate trigger
        // if the calculation happened just as the minute changed.
        if (scheduledTime.isBefore(currentTime.plusSeconds(10))) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        val triggerAtMillis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        Log.d(TAG, "Scheduling daily reminder at $scheduledTime ($triggerAtMillis ms)")

        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for precision even in battery saving modes
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: SCHEDULE_EXACT_ALARM permission not granted, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancels the scheduled daily reminder.
     */
    fun cancelDailyReminder() {
        Log.d(TAG, "Cancelling daily reminder alarm")
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
