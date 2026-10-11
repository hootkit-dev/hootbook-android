@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R

/** No route leads to the old engine, old settings, or removed cloud-backup providers. */
@Composable
fun CoreSettingsScreen(onBack: () -> Unit, onCurrencies: () -> Unit, onBackup: () -> Unit,
    viewModel: CoreSettingsViewModel = hiltViewModel(), onRoute: (String) -> Unit = {}, coreSchemaVersion: UInt? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { viewModel.refresh() }
    val enabled = state.loaded && !state.saving
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.core_settings_title)) },
        navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_settings_back)) } },
        actions = { TextButton(onClick = { viewModel.clearError(); viewModel.refresh() }, enabled = !state.saving) { Text(stringResource(R.string.core_settings_retry)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.navigationBlocked) item { Text(stringResource(R.string.core_settings_saving)) }
            state.localeFailure?.let{failure->item{Surface(color=MaterialTheme.colorScheme.errorContainer){Column(Modifier.padding(12.dp)){
                Text(stringResource(when(failure){CoreLocaleFailure.CORE_COMMAND_FAILED->R.string.core_locale_command_failed;CoreLocaleFailure.UI_ROLLED_BACK->R.string.core_locale_rolled_back;CoreLocaleFailure.DIVERGED->R.string.core_locale_diverged}))
                Button(onClick={viewModel.language(state.localeSelection)},enabled=!state.saving){Text(stringResource(R.string.core_locale_retry_sync))}
            }}}}
            state.error?.let { error -> item { Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(12.dp)) { Text(error); TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_settings_dismiss)) } }
            } } }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_settings_main_currency), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.core_settings_main_currency_note))
                OutlinedTextField(value = state.currencyDraft, onValueChange = viewModel::currencyChanged, enabled = enabled,
                    singleLine = true, label = { Text(stringResource(R.string.core_settings_currency_code)) })
                FlowRow { Button(onClick = viewModel::saveCurrency, enabled = enabled && state.currencyDirty) { Text(stringResource(R.string.core_settings_save)) }
                    TextButton(onClick = viewModel::discardCurrencyDraft, enabled = enabled && state.currencyDirty) { Text(stringResource(R.string.core_settings_reset)) } }
            } } }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_settings_default_account), style = MaterialTheme.typography.titleMedium)
                Text(state.accounts.find { it.id == state.defaultAccountId }?.name
                    ?: state.defaultAccountId ?: stringResource(R.string.core_settings_no_default))
                Text(stringResource(R.string.core_settings_default_account_note))
                TextButton(onClick = { viewModel.defaultAccount(null) }, enabled = enabled) { Text(stringResource(R.string.core_settings_no_default)) }
                state.accounts.filter { !it.archived }.forEach { account ->
                    TextButton(onClick = { viewModel.defaultAccount(account.id) }, enabled = enabled) {
                        Text((if (account.id == state.defaultAccountId) "✓ " else "") + account.name + " · " + account.mainCurrency)
                    }
                }
                if (state.accounts.isEmpty()) Text(stringResource(R.string.core_settings_no_accounts))
            } } }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_settings_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.core_settings_language_note))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("" to stringResource(R.string.core_settings_system_language)) + io.github.hootkit_dev.hootbook.core.ui.CoreUiLanguages.supported(androidx.compose.ui.platform.LocalContext.current)).forEach { (tag, name) ->
                        FilterChip(selected = state.localeSelection == tag, onClick = { viewModel.language(tag) }, enabled = enabled, label = { Text(name) })
                    }
                }
                if (state.localeSelection.isNotEmpty()) Text(state.localeSelection)
            } } }
            item { Text("App ${io.github.hootkit_dev.hootbook.BuildConfig.VERSION_NAME} · Core schema ${coreSchemaVersion ?: "—"}") }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("accounts" to R.string.accounts,"categories" to R.string.categories,"ai" to R.string.core_ai_title,
                    "investments" to R.string.core_shell_investments,"sync" to R.string.core_shell_sync,"appearance" to R.string.core_shell_appearance,
                    "profile" to R.string.core_shell_profile,"app-lock" to R.string.core_shell_app_lock,"notifications" to R.string.core_shell_notifications,"about" to R.string.core_shell_about).forEach { (route,label) ->
                    OutlinedButton(onClick={if(!viewModel.back())onRoute(route)},enabled=!state.saving){Text(stringResource(label))}
                }
            } }
            item { Button(onClick = { if (!viewModel.back()) onCurrencies() }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.core_settings_currencies))
            } }
            item { OutlinedButton(onClick = { if (!viewModel.back()) onBackup() }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.core_settings_backup))
            } }
        }
    }
}
