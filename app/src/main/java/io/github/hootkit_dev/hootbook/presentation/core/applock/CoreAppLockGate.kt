package io.github.hootkit_dev.hootbook.presentation.core.applock

import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.Icons
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreAppLockController

/** Wrap the NavHost, not just a screen. Locked/background state does not compose private content. */
@Composable
fun CoreAppLockGate(controller:CoreAppLockController,content: @Composable () -> Unit) {
    // Keep saveable NavController/back-stack and ordinary UI drafts outside the visibility branch.
    // Secret inputs intentionally use remember (not rememberSaveable) and are discarded when locked.
    val contentState = rememberSaveableStateHolder()
    val state by controller.state.collectAsStateWithLifecycle()
    val activity=coreFragmentActivity(LocalContext.current)
    LifecycleEventEffect(Lifecycle.Event.ON_START){controller.onForeground()}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){controller.onBackground()}
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow(enabled = state.enabled || !state.loaded)
    if(state.allowContent){contentState.SaveableStateProvider("private_core_content"){content()};return}
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally) {
            Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Lock,null,Modifier.padding(24.dp).size(40.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(R.string.core_ui_locked),style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
            Text(stringResource(R.string.lock_body),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
            state.errorResource?.let {Text(stringResource(it),color=MaterialTheme.colorScheme.error,textAlign=TextAlign.Center)}
            if(!state.loaded){LoadingCircle(Modifier.size(48.dp));TextButton(onClick=controller::retryLoading){Text(stringResource(R.string.core_ui_retry))}}
            else if(state.foreground)Button(onClick={activity?.let(controller::authenticate)},enabled=activity!=null&&!state.authenticating){Text(stringResource(R.string.core_ui_unlock))}
        }
    }
}
internal fun coreFragmentActivity(context:Context):FragmentActivity? = when(context){is FragmentActivity->context;is ContextWrapper->coreFragmentActivity(context.baseContext);else->null}
