package io.github.hootkit_dev.hootbook.presentation.core.investments

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.hootkit.core.BrokerageProviderInfo
import io.github.hootkit_dev.hootbook.R

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
    Dialog(
        onDismissRequest = { fields.clear(); if (state.busy) onCancel() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.95f), shape = MaterialTheme.shapes.large) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
                TopAppBar(title = { Text(stringResource(R.string.core_invest_connect_title, providerName)) })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (provider.id == "ibkr_flex") {
                        Text(stringResource(R.string.core_invest_ibkr_setup))
                        TextButton(onClick = {
                            // Link navigation is a platform action; no token or account data is appended.
                            runCatching { uriHandler.openUri("https://www.interactivebrokers.com/docs/web-api/flex-web-service/client-portal-configuration") }
                        }) { Text(stringResource(R.string.core_invest_provider_help)) }
                    }
                    Text(stringResource(R.string.core_invest_credentials_privacy), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(label, onValueChange = { label = it.take(60) },
                        label = { Text(stringResource(R.string.core_invest_connection_label)) },
                        enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    provider.fields.forEach { field ->
                        OutlinedTextField(
                            value = fields[field.key].orEmpty(),
                            onValueChange = { fields[field.key] = it },
                            label = { Text(when (field.key) {
                                "token" -> "Flex Token"
                                "queryId" -> "Query ID"
                                else -> field.key
                            }) },
                            visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
                                autoCorrectEnabled = false),
                            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.error?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
                    if (state.busy) {
                        Text(stringResource(if (state.cancelling) R.string.core_invest_cancelling else R.string.core_invest_working))
                    }
                    TextButton(enabled = !state.cancelling, onClick = {
                        fields.clear()
                        if (state.busy) onCancel() else onDismiss()
                    }) { Text(stringResource(if (state.busy) R.string.core_invest_cancel_request else R.string.core_invest_cancel)) }
                    Button(enabled = !state.busy && provider.fields.all { fields[it.key]?.isNotBlank() == true },
                        onClick = {
                            onSubmit(label, provider.fields.associate { it.key to fields[it.key].orEmpty() })
                            fields.clear()
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_invest_connect_and_refresh)) }
                }
            }
        }
    }
}
