package io.github.hootkit_dev.hootbook.presentation.core.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.use
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import kotlinx.coroutines.launch

@Composable fun CoreOnboardingScreen(session: CoreSession, prefs: CoreUiPreferencesStore, onRoute: (String) -> Unit, onFinished: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(prefs.state.value.userName) }; var currency by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(session) { try { currency = session.awaitCore().settings().use { it.mainCurrency() } } catch (_: Exception) { error = true } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.core_shell_welcome), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.core_shell_onboarding_note))
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.core_shell_name)) }, enabled = !busy)
        OutlinedTextField(currency, { currency = it }, label = { Text(stringResource(R.string.core_settings_main_currency)) }, enabled = !busy)
        listOf("accounts" to R.string.accounts, "profile" to R.string.core_shell_profile, "backup" to R.string.core_shell_backup,
            "sync" to R.string.core_shell_sync, "notifications" to R.string.core_shell_notifications).forEach { (route, label) ->
            OutlinedButton(onClick = { onRoute(route) }, enabled = !busy) { Text(stringResource(label)) }
        }
        if (error) Text(stringResource(R.string.core_shell_onboarding_failed))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(enabled = !busy && currency.isNotBlank(), onClick = {
            busy = true; error = false
            scope.launch { try { session.awaitCore().settings().use { it.setMainCurrency(currency.trim()) }
                prefs.update { it.copy(userName = name, onboardingFinished = true) }; onFinished()
            } catch (_: Exception) { error = true } finally { busy = false } }
        }) { Text(stringResource(R.string.core_shell_finish)) }
    }
}
