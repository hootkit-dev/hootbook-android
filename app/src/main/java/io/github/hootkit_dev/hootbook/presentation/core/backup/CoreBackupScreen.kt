@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.backup.BackupPhase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The only source for legacy migration is a user-selected backup, never old Room storage. */
@Composable
fun CoreBackupScreen(onBack: () -> Unit, viewModel: CoreBackupViewModel = hiltViewModel()) {
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Secrets intentionally use remember, not rememberSaveable / SavedStateHandle.
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(true) }
    var ai by remember { mutableStateOf(false) }
    var brokerage by remember { mutableStateOf(false) }
    var exportSensitive by remember { mutableStateOf(false) }
    var importSensitive by remember { mutableStateOf(false) }
    var legacy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var replaceConfirmed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importSensitive = false; replaceConfirmed = false
        viewModel.select(uri, legacy, importPassword.takeIf { it.isNotEmpty() })
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::save)
    val savePreferences = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::savePreferences)
    var sourceOperationId by remember { mutableStateOf<String?>(null) }
    val saveSource = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        sourceOperationId?.let { viewModel.saveSource(uri, it) }; sourceOperationId = null
    }
    val back: () -> Unit = { if (viewModel.canBack()) onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(state.shareFile) {
        state.shareFile?.let { file ->
            val failed = runCatching {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri("backup", uri)
                context.startActivity(Intent.createChooser(send, context.getString(R.string.core_backup_share)))
            }.isFailure
            viewModel.shareHandled(failed)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.core_backup_title)) }, navigationIcon = {
        TextButton(onClick = back, enabled = !state.busy) { Text(stringResource(R.string.core_backup_back)) }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.core_backup_local_only))
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.core_backup_busy)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it) }
            Text(stringResource(R.string.core_backup_export), style = MaterialTheme.typography.titleLarge)
            BackupCheck(R.string.core_backup_attachments, attachments, !state.busy) { attachments = it }
            BackupCheck(R.string.core_backup_ai, ai, !state.busy) { ai = it; exportSensitive = false }
            BackupCheck(R.string.core_backup_brokerage, brokerage, !state.busy) { brokerage = it; exportSensitive = false }
            SecretInput(R.string.core_backup_password, exportPassword, !state.busy) { exportPassword = it; exportSensitive = false }
            if ((ai || brokerage) && exportPassword.isEmpty()) BackupCheck(R.string.core_backup_secret_confirm, exportSensitive, !state.busy) { exportSensitive = it }
            Button(onClick = { viewModel.export(attachments, ai, brokerage, exportPassword.takeIf(String::isNotEmpty), exportSensitive) }, enabled = !state.busy) {
                Text(stringResource(R.string.core_backup_export))
            }
            state.exportInfo?.let { BackupInfoText(it) }
            if (state.exportFile != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { save.launch("HootKit_Backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".hkbak") }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_save)) }
                OutlinedButton(onClick = viewModel::share, enabled = !state.busy) { Text(stringResource(R.string.core_backup_share)) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.core_backup_restore), style = MaterialTheme.typography.titleLarge)
            BackupCheck(R.string.core_backup_legacy, legacy, !state.busy) { legacy = it }
            SecretInput(R.string.core_backup_password, importPassword, !state.busy) {
                importPassword = it; viewModel.invalidateInspection(); importSensitive = false
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_select)) }
                OutlinedButton(onClick = { viewModel.inspect(importPassword.takeIf(String::isNotEmpty)) }, enabled = !state.busy && state.source != null) { Text(stringResource(R.string.core_backup_inspect)) }
            }
            state.source?.let { Text("${it.size} bytes · SHA-256 ${it.sha256}", style = MaterialTheme.typography.bodySmall) }
            state.info?.let { BackupInfoText(it) }
            state.legacyInfo?.let { Text(stringResource(R.string.core_ui_backup_legacy_info, it.accounts.toString(), it.transactions.toString(), it.encrypted.toString())) }
            if (state.info != null || state.legacyInfo != null) {
                if (!state.legacy) {
                    BackupCheck(R.string.core_backup_merge, mode == ImportMode.MERGE, !state.busy) { mode = ImportMode.MERGE; replaceConfirmed = false }
                    BackupCheck(R.string.core_backup_replace, mode == ImportMode.REPLACE_ALL, !state.busy) { mode = ImportMode.REPLACE_ALL; replaceConfirmed = false }
                    if (mode == ImportMode.REPLACE_ALL) BackupCheck(R.string.core_backup_replace_confirm, replaceConfirmed, !state.busy) { replaceConfirmed = it }
                }
                if (state.legacy || state.info?.includes?.ai == true || state.info?.includes?.brokerage == true)
                    BackupCheck(R.string.core_backup_sensitive_confirm, importSensitive, !state.busy) { importSensitive = it }
                Button(onClick = { viewModel.restore(mode, importPassword.takeIf(String::isNotEmpty), replaceConfirmed, importSensitive) },
                    enabled = !state.busy && state.operations.none { it.blocksImport }) { Text(stringResource(R.string.core_backup_import)) }
            }
            Text(stringResource(R.string.core_ui_restore_references_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.core_backup_platform_note), style = MaterialTheme.typography.bodySmall)
            if (state.operations.any { it.blocksImport }) {
                Text(stringResource(R.string.core_backup_pending), style = MaterialTheme.typography.titleLarge)
                state.operations.filter { it.blocksImport }.forEach { operation ->
                    Text("${operation.source.operationId} · ${operation.phase}")
                    if (operation.phase == BackupPhase.CORE_COMMITTED) TextButton(onClick = { viewModel.skipPlatform(operation) }, enabled = !state.busy) { Text(stringResource(R.string.core_shell_skip_platform)) }
                    if (operation.phase == BackupPhase.CORE_COMMITTED) OutlinedButton(onClick = { viewModel.finishPlatform(operation) }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_platform_retry)) }
                    else Text(stringResource(R.string.core_backup_uncertain), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { sourceOperationId = operation.source.operationId; saveSource.launch("Original_Backup_" + operation.source.operationId + ".backup") }, enabled = !state.busy) {
                        Text(stringResource(R.string.core_ui_backup_export_source))
                    }
                }
            }
            (state.result ?: state.operations.lastOrNull { it.phase == BackupPhase.PLATFORM_APPLIED })?.let { operation ->
                Text(stringResource(R.string.core_backup_result), style = MaterialTheme.typography.titleLarge)
                operation.importSummary?.let { summary ->
                    Text(stringResource(R.string.core_ui_backup_counts,summary.added.toString(),summary.updated.toString(),summary.skipped.toString()))
                    Text(stringResource(R.string.core_ui_backup_secrets,summary.aiRestored.toString(),summary.brokerageAdded.toString()))
                    summary.safetyBackup?.let { Text(stringResource(R.string.core_ui_backup_safety,it)) }
                }
                operation.legacySummary?.let { summary ->
                    Text(stringResource(R.string.core_ui_backup_imported,summary.imported.joinToString { "${it.item}: ${it.count}" }))
                    Text(stringResource(R.string.core_ui_backup_dropped,summary.dropped.joinToString { "${it.item}: ${it.count}" }))
                    if (summary.renamedCurrencies.isNotEmpty()) Text(stringResource(R.string.core_ui_backup_renamed,summary.renamedCurrencies.entries.joinToString { "${it.key} → ${it.value}" }))
                }
                Text(stringResource(R.string.core_ui_backup_assets,operation.files.size,(operation.preferences != null).toString()))
                if (operation.preferences != null) OutlinedButton(onClick = { savePreferences.launch("HootKit_Preferences_Review.json") }, enabled = !state.busy) {
                    Text(stringResource(R.string.core_backup_preferences_review))
                }
                Text(stringResource(R.string.core_ui_backup_directory,operation.source.file.parent.orEmpty()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun BackupCheck(label: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row { Checkbox(checked, onChange, enabled = enabled); Text(stringResource(label), Modifier.padding(top = 12.dp)) }
}
@Composable private fun SecretInput(label: Int, value: String, enabled: Boolean, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
@Composable private fun BackupInfoText(info: BackupInfo) {
    Text(stringResource(R.string.core_ui_backup_info,info.appVersion,info.schema.toString(),info.encrypted.toString()))
    Text(info.counts.joinToString { "${it.table}: ${it.count}" })
    Text(stringResource(R.string.core_ui_backup_includes,info.includes.attachments.toString(),info.includes.preferences.toString(),info.includes.ai.toString(),info.includes.brokerage.toString()))
}
