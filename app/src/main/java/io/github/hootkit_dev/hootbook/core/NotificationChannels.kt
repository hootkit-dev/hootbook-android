package io.github.hootkit_dev.hootbook.core

/**
 * Notification channel identity for the app's local reminders.
 *
 * The id is unchanged from when these constants lived on the removed SMS receiver, so
 * existing installs keep the channel the user has already configured.
 */
object NotificationChannels {
    const val REMINDER_CHANNEL_ID = "transaction_notifications"
}
