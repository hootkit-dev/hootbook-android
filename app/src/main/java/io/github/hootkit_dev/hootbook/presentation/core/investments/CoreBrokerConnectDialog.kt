package io.github.hootkit_dev.hootbook.presentation.core.investments

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.hootkit.core.BrokerageProviderInfo
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormTextField
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.PasswordField
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton

private fun Context.activityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activityWindow()
    else -> null
}

/** Change only our secure bit; retain an existing application lock / screenshot policy. */
internal fun protectInvestmentWindow(window: Window): () -> Unit {
    return io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindows.acquire(window)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoreBrokerConnectDialog(
    provider: BrokerageProviderInfo,
    providerName: String,
    state: CoreInvestmentsUiState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: (String, Map<String, String>) -> Unit,
) {
    // Credentials are deliberately transient; no rememberSaveable, SavedStateHandle or UI-state copy.
    var label by remember(provider.id) { mutableStateOf(providerName) }
    val fields = remember(provider.id) { mutableStateMapOf<String, String>() }
    val activityWindow = LocalContext.current.activityWindow()
    val uriHandler = LocalUriHandler.current
    DisposableEffect(activityWindow, provider.id) {
        val release = activityWindow?.let(::protectInvestmentWindow)
        onDispose { fields.clear(); release?.invoke() }
    }
    val close = { fields.clear(); if (state.busy) onCancel() else onDismiss() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
                TopAppBar(title = { Text(stringResource(R.string.core_invest_connect_title, providerName)) },
                    navigationIcon = { TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.close), close, enabled = !state.cancelling) })
            }, bottomBar = {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (state.busy) TextButton(onClick = close, enabled = !state.cancelling) { Text(stringResource(R.string.core_invest_cancel_request)) }
                    Button(enabled = !state.busy && provider.fields.all { fields[it.key]?.isNotBlank() == true }, onClick = {
                        onSubmit(label, provider.fields.associate { it.key to fields[it.key].orEmpty() })
                        fields.clear()
                    }) { Text(stringResource(R.string.core_invest_connect_and_refresh)) }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (provider.id == "ibkr_flex") {
                        Text(stringResource(R.string.core_invest_ibkr_setup), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            // Link navigation is a platform action; no token or account data is appended.
                            runCatching { uriHandler.openUri("https://www.interactivebrokers.com/docs/web-api/flex-web-service/client-portal-configuration") }
                        }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.core_invest_provider_help)) }
                    }
                    FormTextField(label, stringResource(R.string.core_invest_connection_label), { label = it.take(60) }, enabled = !state.busy)
                    provider.fields.forEach { field ->
                        val name = when (field.key) { "token" -> "Flex Token"; "queryId" -> "Query ID"; else -> field.key }
                        if (field.secret) PasswordField(fields[field.key].orEmpty(), name, { fields[field.key] = it }, enabled = !state.busy)
                        else FormTextField(fields[field.key].orEmpty(), name, { fields[field.key] = it }, enabled = !state.busy)
                    }
                    Text(stringResource(R.string.core_invest_credentials_privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { NoticeCard(investmentFailureText(it), error = true) }
                    if (state.busy) NoticeCard(stringResource(if (state.cancelling) R.string.core_invest_cancelling else R.string.core_invest_working))
                }
            }
        }
    }
}
