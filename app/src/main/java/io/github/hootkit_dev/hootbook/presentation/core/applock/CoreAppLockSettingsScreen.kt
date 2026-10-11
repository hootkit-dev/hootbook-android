@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem as SettingsListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreAppLockController
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.core.appearance.UiSwitch

@HiltViewModel
class CoreAppLockSettingsViewModel @Inject constructor(val controller:CoreAppLockController,val preferences:CoreUiPreferencesStore):ViewModel(){
    private val mutable=MutableStateFlow(false);val busy=mutable.asStateFlow();private val failure=MutableStateFlow<Int?>(null);val error=failure.asStateFlow()
    fun enabled(value:Boolean,activity:androidx.fragment.app.FragmentActivity?)=command{if(value)controller.enableWithAuthentication(checkNotNull(activity))else controller.setEnabled(false)}
    fun timeout(minutes:Int)=command{controller.setTimeout(minutes)}
    private fun command(block:suspend()->Unit){if(mutable.value)return;mutable.value=true;failure.value=null;viewModelScope.launch{
        try{block()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failure.value=R.string.core_ui_lock_enroll}finally{mutable.value=false}
    }}
}
@Composable
fun CoreAppLockSettingsScreen(onBack:()->Unit,viewModel:CoreAppLockSettingsViewModel=hiltViewModel(), dataPrivacy: Boolean = false){
    val activity=coreFragmentActivity(androidx.compose.ui.platform.LocalContext.current)
    val p by viewModel.preferences.state.collectAsStateWithLifecycle();val busy by viewModel.busy.collectAsStateWithLifecycle();val error by viewModel.error.collectAsStateWithLifecycle()
    var showTimeout by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(if (dataPrivacy) R.string.data_privacy_title else R.string.app_lock),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent { if (!busy) onBack() } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.security_section), Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium)
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                PreferenceSwitch(title = stringResource(R.string.app_lock), subtitle = stringResource(R.string.core_ui_lock_subtitle),
                    checked = p.appLockEnabled, onCheckedChange = { if (!busy) viewModel.enabled(it, activity) },
                    leadingIcon = { Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    } }, padding = PaddingValues(0.dp), isFirst = true, isSingle = !p.appLockEnabled)
                AnimatedVisibility(p.appLockEnabled) {
                    SettingsListItem(headline = { Text(stringResource(R.string.lock_timeout)) }, supporting = {
                        Text(if (p.appLockTimeoutMinutes == 0) stringResource(R.string.lock_timeout_immediate) else stringResource(R.string.lock_timeout_minutes, p.appLockTimeoutMinutes))
                    }, trailing = { Icon(Icons.Rounded.ChevronRight, null) }, onClick = if (!busy) ({ showTimeout = true }) else null,
                        shape = ListItemPosition.Bottom.toShape(), padding = PaddingValues(0.dp))
                }
            }
            OutlinedButton(onClick = viewModel.controller::lockNow, enabled = p.appLockEnabled && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_ui_lock_now)) }
        }
    }
    if (showTimeout) AlertDialog(onDismissRequest = { showTimeout = false }, title = { Text(stringResource(R.string.lock_timeout)) }, text = {
        Column {
            listOf(0, 1, 5, 15, 30, 60).forEach { minutes ->
                Row(Modifier.fillMaxWidth().selectable(selected = p.appLockTimeoutMinutes == minutes, enabled = !busy,
                    onClick = { viewModel.timeout(minutes); showTimeout = false }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(p.appLockTimeoutMinutes == minutes, null)
                    Text(if (minutes == 0) stringResource(R.string.immediately) else stringResource(R.string.minutes_format, minutes), Modifier.padding(start = 8.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showTimeout = false }) { Text(stringResource(R.string.close)) } })
}
