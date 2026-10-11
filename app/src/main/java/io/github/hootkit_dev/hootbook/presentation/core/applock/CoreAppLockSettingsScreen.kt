package io.github.hootkit_dev.hootbook.presentation.core.applock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreAppLockController
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoreAppLockSettingsViewModel @Inject constructor(val controller:CoreAppLockController,val preferences:CoreUiPreferencesStore):ViewModel(){
    private val mutable=MutableStateFlow(false);val busy=mutable.asStateFlow();private val failure=MutableStateFlow<Int?>(null);val error=failure.asStateFlow()
    fun enabled(value:Boolean,activity:androidx.fragment.app.FragmentActivity?)=command{if(value)controller.enableWithAuthentication(checkNotNull(activity))else controller.setEnabled(false)}
    fun timeout(minutes:Int)=command{controller.setTimeout(minutes)}
    fun clearError(){failure.value=null}
    private fun command(block:suspend()->Unit){if(mutable.value)return;mutable.value=true;failure.value=null;viewModelScope.launch{
        try{block()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failure.value=R.string.core_ui_lock_enroll}finally{mutable.value=false}
    }}
}
/** App lock: the switch, then, while it is on, how soon the app locks in the background and a lock-now action. */
@Composable
fun CoreAppLockSettingsScreen(onBack: () -> Unit, viewModel: CoreAppLockSettingsViewModel = hiltViewModel(), dataPrivacy: Boolean = false) {
    val activity = coreFragmentActivity(androidx.compose.ui.platform.LocalContext.current)
    val p by viewModel.preferences.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showTimeout by remember { mutableStateOf(false) }
    SecondaryScreen(title = stringResource(if (dataPrivacy) R.string.data_privacy_title else R.string.app_lock), onBack = { if (!busy) onBack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { ErrorBanner(stringResource(it), viewModel::clearError) }
            ListSection(title = stringResource(R.string.security_section)) {
                item(leading = { Icon(Icons.Rounded.Lock, null) },
                    headline = { Text(stringResource(R.string.app_lock)) },
                    supporting = { Text(stringResource(R.string.core_ui_lock_subtitle)) },
                    trailing = { HootbookSwitch(p.appLockEnabled, null, enabled = !busy) },
                    toggled = p.appLockEnabled, onToggle = { if (!busy) viewModel.enabled(it, activity) })
                if (p.appLockEnabled) {
                    item(leading = { Icon(Icons.Rounded.Timer, null) },
                        headline = { Text(stringResource(R.string.privacy_lock_after)) },
                        supporting = { Text(timeoutLabel(p.appLockTimeoutMinutes)) },
                        chevron = true, onClick = if (!busy) ({ showTimeout = true }) else null)
                    item(leading = { Icon(Icons.Rounded.LockClock, null) },
                        headline = { Text(stringResource(R.string.core_ui_lock_now)) },
                        onClick = if (!busy) viewModel.controller::lockNow else null)
                }
            }
        }
    }
    if (showTimeout) AlertDialog(onDismissRequest = { showTimeout = false }, title = { Text(stringResource(R.string.privacy_lock_after)) }, text = {
        Column {
            listOf(0, 1, 5, 15, 30, 60).forEach { minutes ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = p.appLockTimeoutMinutes == minutes, enabled = !busy,
                    onClick = { viewModel.timeout(minutes); showTimeout = false }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(p.appLockTimeoutMinutes == minutes, null)
                    Text(timeoutLabel(minutes), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showTimeout = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun timeoutLabel(minutes: Int) =
    if (minutes == 0) stringResource(R.string.privacy_lock_immediately) else stringResource(R.string.privacy_lock_minutes, minutes)
