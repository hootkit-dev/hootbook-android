package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.CoreLocaleGateState
import io.github.hootkit_dev.hootbook.core.CoreLocaleReconciler
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController

/** Keep the navigation composition/VMs alive while hiding and blocking stale-language content. */
@Composable
internal fun CoreLocaleGate(session: CoreSession, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preference = remember(context) { CoreLocaleController(context) }
    val configurationKey = LocalConfiguration.current.locales.toLanguageTags()
    val reconciler = remember(session) { CoreLocaleReconciler { tag ->
        session.awaitCore().settings().use { it.setLocale(tag) }
    } }
    val state by reconciler.state.collectAsStateWithLifecycle()
    var retry by remember { mutableIntStateOf(0) }
    var appliedConfiguration by remember { mutableStateOf<String?>(null) }
    var appliedRetry by remember { mutableIntStateOf(-1) }
    LaunchedEffect(configurationKey, retry, reconciler) {
        reconciler.reconcile(preference::resolveStartupTag)
        if (reconciler.state.value is CoreLocaleGateState.Ready) {
            appliedConfiguration = configurationKey
            appliedRetry = retry
        }
    }
    val blocked = state !is CoreLocaleGateState.Ready ||
        appliedConfiguration != configurationKey || appliedRetry != retry
    BackHandler(enabled = blocked) { /* The gate must not navigate to stale-language content. */ }
    val focus = remember { FocusRequester() }
    LaunchedEffect(blocked) { if (blocked) focus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        // Not conditional composition: NavController, destination ViewModels and unsaved drafts survive.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (blocked) 0f else 1f }
            .then(if (blocked) Modifier.clearAndSetSemantics { } else Modifier)) { content() }
        if (blocked) Surface(Modifier.fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .focusRequester(focus).focusable()) {
            Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state == CoreLocaleGateState.Failed) {
                    Text(stringResource(R.string.core_locale_failed))
                    Button(onClick = { retry++ }) { Text(stringResource(R.string.core_host_retry)) }
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.core_locale_syncing))
                }
            }
        }
    }
}
