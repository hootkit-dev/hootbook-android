@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.system.notificationPermission

@Composable
fun CoreNotificationPreferencesScreen(onBack:()->Unit,viewModel:CoreNotificationPreferencesViewModel=hiltViewModel()){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    val p by viewModel.prefs.state.collectAsStateWithLifecycle();val ready by viewModel.prefs.loaded.collectAsStateWithLifecycle()
    val rows by viewModel.subscriptions.collectAsStateWithLifecycle();val error by viewModel.error.collectAsStateWithLifecycle();val busy by viewModel.busy.collectAsStateWithLifecycle()
    var platformRefresh by remember{mutableIntStateOf(0)}
    var platformError by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){platformRefresh++;viewModel.refreshPlatform()}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){platformRefresh++;viewModel.refreshPlatform()}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    val granted=remember(platformRefresh){notificationPermission(context)&&NotificationManagerCompat.from(context).areNotificationsEnabled()}
    val exact=remember(platformRefresh){Build.VERSION.SDK_INT<31||context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}
    var hour by rememberSaveable(p.reminderMinutes){mutableStateOf((p.reminderMinutes/60).toString())}
    var minute by rememberSaveable(p.reminderMinutes){mutableStateOf((p.reminderMinutes%60).toString().padStart(2,'0'))}
    val value=hour.toIntOrNull()?.takeIf{it in 0..23}?.let{h->minute.toIntOrNull()?.takeIf{it in 0..59}?.let{h*60+it}}
    fun openSettings(intent:Intent){try{context.startActivity(intent)}catch(_:Exception){platformError=true}}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_system_notifications))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.core_ui_back))}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter){
            LazyColumn(Modifier.widthIn(max=720.dp).fillMaxWidth().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(busy||!ready)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                error?.let{item{Text(stringResource(it),color=MaterialTheme.colorScheme.error);TextButton(onClick=viewModel::reload){Text(stringResource(R.string.core_system_retry))}}}
                if(platformError)item{Text(stringResource(R.string.core_system_settings_unavailable),color=MaterialTheme.colorScheme.error)}
                item{Text(stringResource(if(granted)R.string.core_system_permission_granted else R.string.core_system_permission_needed))
                    if(!granted)Button(onClick={if(Build.VERSION.SDK_INT>=33&&!notificationPermission(context))permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.core_system_allow_notifications))}
                }
                item{NotificationToggle(R.string.core_system_master,p.notificationsEnabled,ready&&!busy){v->viewModel.update{it.copy(notificationsEnabled=v)}}}
                item{NotificationToggle(R.string.core_system_daily_scan,p.scanReminderEnabled,ready&&!busy){v->viewModel.update{it.copy(scanReminderEnabled=v)}};Text(stringResource(R.string.core_system_daily_scan_help))}
                item{Text(stringResource(R.string.core_system_reminder_time),style=MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        OutlinedTextField(hour,{hour=it.take(2).filter(Char::isDigit)},Modifier.width(120.dp),label={Text(stringResource(R.string.core_system_hour))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=ready&&!busy)
                        OutlinedTextField(minute,{minute=it.take(2).filter(Char::isDigit)},Modifier.width(120.dp),label={Text(stringResource(R.string.core_system_minute))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=ready&&!busy)
                        Button(onClick={value?.let{v->viewModel.update{it.copy(reminderMinutes=v)}}},enabled=ready&&!busy&&value!=null,modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.core_ui_save))}
                    }
                }
                item{Text(stringResource(if(exact)R.string.core_system_exact_enabled else R.string.core_system_inexact_fallback))
                    if(!exact&&Build.VERSION.SDK_INT>=31)OutlinedButton(onClick={openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+context.packageName)))},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.core_system_exact_settings))}
                }
                item{HorizontalDivider();Text(stringResource(R.string.core_system_subscriptions),style=MaterialTheme.typography.titleLarge);Text(stringResource(R.string.core_system_core_dates))}
                if(rows.isEmpty())item{Text(stringResource(R.string.core_system_no_subscriptions))}
                items(rows,key={it.id}){row->NotificationToggleText(row.name,row.id !in p.mutedSubscriptionIds,ready&&!busy){v->viewModel.mute(row.id,!v)}}
            }
        }
    }
}
@Composable private fun NotificationToggle(label:Int,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit)=NotificationToggleText(stringResource(label),checked,enabled,onChange)
@Composable private fun NotificationToggleText(label:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,onChange,enabled=enabled)}
}
