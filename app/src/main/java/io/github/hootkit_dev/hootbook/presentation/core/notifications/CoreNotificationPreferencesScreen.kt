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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.theme.purple_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.purple_light
import io.github.hootkit_dev.hootbook.utils.DateFormats
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** The original grouped notification rows; due dates and identities come directly from Core. */
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haze = remember { HazeState() }
    val enabled = ready && !busy
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(R.string.notifications), scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
            scrollBehaviorLarge = scroll, hazeState = haze, hasBackButton = true, navigationContent = { NavigationContent(onBack) })
    }) { padding ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState()).padding(padding)
                .consumeWindowInsets(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.core_system_retry)) } }
                if (platformError) Text(stringResource(R.string.core_system_settings_unavailable), color = MaterialTheme.colorScheme.error)
                if (!granted) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.core_system_permission_needed))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !notificationPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { Text(stringResource(R.string.core_system_allow_notifications)) }
                    }
                }
                SectionHeader(title = stringResource(R.string.upcoming_transactions_section), modifier = Modifier.padding(start = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    NotificationPreference(stringResource(R.string.upcoming_transactions_section), stringResource(R.string.upcoming_transactions_desc),
                        preferences.notificationsEnabled, enabled, Icons.Rounded.Upcoming,
                        if (!preferences.notificationsEnabled || subscriptions.isEmpty()) ListItemPosition.Single else ListItemPosition.Top) {
                        value -> viewModel.update { it.copy(notificationsEnabled = value) }
                    }
                    AnimatedVisibility(preferences.notificationsEnabled && subscriptions.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            subscriptions.forEachIndexed { index, subscription ->
                                val date = subscription.nextDue?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()).format(DateFormats.monthDayFormatter()) }
                                NotificationPreference(subscription.name, date ?: stringResource(R.string.no_date),
                                    subscription.id !in preferences.mutedSubscriptionIds, enabled, Icons.Rounded.Notifications,
                                    if (index == subscriptions.lastIndex) ListItemPosition.Bottom else ListItemPosition.Middle) {
                                    viewModel.mute(subscription.id, !it)
                                }
                            }
                        }
                    }
                }
                SectionHeader(title = stringResource(R.string.core_system_reminder_time), modifier = Modifier.padding(start = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    NotificationPreference(stringResource(R.string.core_system_daily_scan), stringResource(R.string.core_system_daily_scan_help),
                        preferences.scanReminderEnabled, enabled, Icons.Rounded.Schedule, ListItemPosition.Top) { value ->
                        viewModel.update { it.copy(scanReminderEnabled = value) }
                    }
                    ListItem(headline = { Text(stringResource(R.string.core_system_reminder_time)) },
                        supporting = { Text(java.time.LocalTime.of(preferences.reminderMinutes / 60, preferences.reminderMinutes % 60)
                            .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))) },
                        onClick = if (enabled) { { showTimePicker = true } } else null,
                        shape = ListItemPosition.Bottom.toShape(), padding = PaddingValues(0.dp))
                }
                Text(stringResource(if (exact) R.string.core_system_exact_enabled else R.string.core_system_inexact_fallback),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                if (!exact && Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                    openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                }) { Text(stringResource(R.string.core_system_exact_settings)) }
                Text(stringResource(R.string.core_system_core_dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showTimePicker) {
        val time = rememberTimePickerState(preferences.reminderMinutes / 60, preferences.reminderMinutes % 60, android.text.format.DateFormat.is24HourFormat(context))
        io.github.hootkit_dev.hootbook.presentation.ui.components.TimePicker(onDismiss = { showTimePicker = false }, onConfirm = {
            viewModel.update { it.copy(reminderMinutes = time.hour * 60 + time.minute) }; showTimePicker = false
        }, timePickerState = time)
    }
}

@Composable
private fun NotificationPreference(title: String, subtitle: String, checked: Boolean, enabled: Boolean, icon: ImageVector,
    position: ListItemPosition, onChange: (Boolean) -> Unit) {
    ListItem(headline = { Text(title) }, supporting = { Text(subtitle) },
        leading = { Box(Modifier.size(48.dp).background(purple_light, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = purple_dark) } },
        trailing = { HootbookSwitch(checked, null, enabled = enabled) }, toggled = checked, onToggle = if (enabled) onChange else null,
        shape = position.toShape(), padding = PaddingValues(0.dp))
}
