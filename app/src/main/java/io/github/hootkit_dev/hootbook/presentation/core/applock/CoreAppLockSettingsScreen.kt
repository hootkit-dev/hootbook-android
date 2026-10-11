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
fun CoreAppLockSettingsScreen(onBack:()->Unit,viewModel:CoreAppLockSettingsViewModel=hiltViewModel()){
    val activity=coreFragmentActivity(androidx.compose.ui.platform.LocalContext.current)
    val p by viewModel.preferences.state.collectAsStateWithLifecycle();val busy by viewModel.busy.collectAsStateWithLifecycle();val error by viewModel.error.collectAsStateWithLifecycle()
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_ui_app_lock))},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text(stringResource(R.string.core_ui_back))}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text(stringResource(R.string.core_ui_lock_subtitle));error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
            UiSwitch(R.string.core_ui_app_lock,p.appLockEnabled,!busy){viewModel.enabled(it,activity)}
            Text(stringResource(R.string.core_ui_lock_timeout));var expanded by remember{mutableStateOf(false)}
            OutlinedButton(onClick={expanded=true},enabled=!busy){Text(if(p.appLockTimeoutMinutes==0)stringResource(R.string.core_ui_immediately)else stringResource(R.string.core_ui_minutes,p.appLockTimeoutMinutes))}
            DropdownMenu(expanded,{expanded=false}){listOf(0,1,5,15,30,60).forEach{minutes->DropdownMenuItem(text={Text(if(minutes==0)stringResource(R.string.core_ui_immediately)else stringResource(R.string.core_ui_minutes,minutes))},onClick={expanded=false;viewModel.timeout(minutes)})}}
            OutlinedButton(onClick=viewModel.controller::lockNow,enabled=p.appLockEnabled&&!busy){Text(stringResource(R.string.core_ui_lock_now))}
        }
    }
}
