@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.system.notificationPermission
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.TimeDialog
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Notification settings: the time reminders go out, subscription due reminders (with a switch per
 * subscription) and the daily recording reminder. Android's permission and exact-alarm state are
 * shown only when they are missing.
 */
@Composable
fun CoreNotificationPreferencesScreen(onBack: () -> Unit, viewModel: CoreNotificationPreferencesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val preferences by viewModel.prefs.state.collectAsStateWithLifecycle()
    val ready by viewModel.prefs.loaded.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var platformRefresh by remember { mutableIntStateOf(0) }
    var platformError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        platformRefresh++; viewModel.refreshPlatform()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                platformRefresh++; viewModel.refreshPlatform(); viewModel.reload()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val granted = remember(platformRefresh) { notificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(platformRefresh) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    fun openSettings(intent: Intent) { try { context.startActivity(intent) } catch (_: Exception) { platformError = true } }
    val enabled = ready && !busy
    SecondaryScreen(title = stringResource(R.string.notifications), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            error?.let { ErrorBanner(stringResource(it), viewModel::clearError, actionLabel = stringResource(R.string.core_system_retry), onAction = viewModel::reload) }
            if (platformError) ErrorBanner(stringResource(R.string.notif_settings_unavailable), { platformError = false })
            if (!granted) PermissionCard {
                if (Build.VERSION.SDK_INT >= 33 && !notificationPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListSection {
                    item(leading = { Icon(Icons.Rounded.Schedule, null) },
                        headline = { Text(stringResource(R.string.notif_time)) },
                        supporting = { Text(stringResource(R.string.notif_time_help)) },
                        trailing = { Text(LocalTime.of(preferences.reminderMinutes / 60, preferences.reminderMinutes % 60)
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), color = MaterialTheme.colorScheme.primary) },
                        onClick = if (enabled) { { showTimePicker = true } } else null)
                }
                if (!exact && Build.VERSION.SDK_INT >= 31) Row(Modifier.padding(start = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.notif_inexact), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) }) {
                        Text(stringResource(R.string.notif_exact_settings))
                    }
                }
            }
            ListSection(title = stringResource(R.string.notif_subscriptions)) {
                switchRow(Icons.Rounded.EventRepeat, R.string.notif_due, R.string.notif_due_help, preferences.notificationsEnabled, enabled) { value ->
                    viewModel.update { it.copy(notificationsEnabled = value) }
                }
                if (preferences.notificationsEnabled) subscriptions.forEach { subscription ->
                    val muted = subscription.id in preferences.mutedSubscriptionIds
                    item(headline = { Text(subscription.name) },
                        supporting = { Text(subscription.nextDue?.let { stringResource(R.string.notif_next_due,
                            DateFormats.shortDate(java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()))) } ?: stringResource(R.string.no_date)) },
                        trailing = { HootbookSwitch(!muted, null, enabled = enabled) },
                        toggled = !muted, onToggle = if (enabled) { on -> viewModel.mute(subscription.id, !on) } else null)
                }
            }
            ListSection(title = stringResource(R.string.notif_daily)) {
                switchRow(Icons.Rounded.EditCalendar, R.string.notif_daily_record, R.string.notif_daily_record_help, preferences.scanReminderEnabled, enabled) { value ->
                    viewModel.update { it.copy(scanReminderEnabled = value) }
                }
            }
        }
    }
    if (showTimePicker) TimeDialog(LocalTime.of(preferences.reminderMinutes / 60, preferences.reminderMinutes % 60), { showTimePicker = false },
        android.text.format.DateFormat.is24HourFormat(context)) { time -> viewModel.update { it.copy(reminderMinutes = time.hour * 60 + time.minute) } }
}

/** Notifications are off at the system level: say so and offer to turn them on. */
@Composable
private fun PermissionCard(onAllow: () -> Unit) =
    NoticeCard(stringResource(R.string.notif_permission_text), title = stringResource(R.string.notif_permission_title)) {
        TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_allow)) }
    }

private fun io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope.switchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, help: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
) = item(leading = { Icon(icon, null) }, headline = { Text(stringResource(title)) }, supporting = { Text(stringResource(help)) },
    trailing = { HootbookSwitch(checked, null, enabled = enabled) }, toggled = checked, onToggle = if (enabled) onChange else null)
