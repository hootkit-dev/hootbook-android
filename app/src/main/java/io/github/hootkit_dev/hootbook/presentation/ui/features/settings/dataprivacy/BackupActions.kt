@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import dev.chrisbanes.haze.HazeState
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.backup.BackupPhase
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.icons.*
import io.github.hootkit_dev.hootbook.presentation.ui.theme.*

/** Original manual backup rows and export dialog, backed by Core's durable restore workflow. */
@Composable
internal fun BackupActions(blurEffects: Boolean, hazeState: HazeState, viewModel: DataPrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportDialog by remember { mutableStateOf(false) }
    var restoreDialog by remember { mutableStateOf(false) }
    var legacy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var replaceConfirmed by remember { mutableStateOf(false) }
    var sensitiveConfirmed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreDialog = true; viewModel.select(uri, legacy, password.takeIf(String::isNotEmpty)) }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::save)
    var operationId by remember { mutableStateOf<String?>(null) }
    val saveSource = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        operationId?.let { viewModel.saveSource(uri, it) }; operationId = null
    }
    BackHandler(state.busy) { }
    LaunchedEffect(state.exportFile) { state.exportFile?.let { save.launch("Hootbook_Backup.hkbak") } }
    LaunchedEffect(state.shareFile) { state.shareFile?.let { file ->
        val failed = runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("backup", uri)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_backup)))
        }.isFailure
        viewModel.shareHandled(failed)
    } }
    SectionHeader(title = stringResource(R.string.manual_actions), modifier = Modifier.padding(start = Spacing.md))
    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        ActionItem(stringResource(R.string.backup_export_to, stringResource(R.string.provider_local_file)),
            stringResource(R.string.backup_export_local_body), Iconax.DirectboxSend, yellow_light, yellow_dark,
            ListItemPosition.Top, if (state.busy) null else ({ exportDialog = true }))
        ActionItem(stringResource(R.string.backup_restore_from, stringResource(R.string.provider_local_file)),
            stringResource(R.string.backup_restore_local_body), Iconax.DirectboxReceive, green_light, green_dark,
            ListItemPosition.Bottom, if (state.busy) null else ({ legacy = false; restoreDialog = true }))
    }
    if (state.busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it) }
    if (state.exportFile != null) Row {
        TextButton(onClick = { save.launch("Hootbook_Backup.hkbak") }, enabled = !state.busy) { Text(stringResource(R.string.save)) }
        TextButton(onClick = viewModel::share, enabled = !state.busy) { Text(stringResource(R.string.share_backup)) }
    }
    state.operations.filter { it.blocksImport }.forEach { operation ->
        Text(stringResource(R.string.core_backup_pending), style = MaterialTheme.typography.titleMedium)
        if (operation.phase == BackupPhase.CORE_COMMITTED) {
            TextButton(onClick = { viewModel.finishPlatform(operation) }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_platform_retry)) }
            TextButton(onClick = { viewModel.skipPlatform(operation) }, enabled = !state.busy) { Text(stringResource(R.string.core_shell_skip_platform)) }
        } else Text(stringResource(R.string.core_backup_uncertain), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { operationId = operation.source.operationId; saveSource.launch("Original_Backup.backup") }, enabled = !state.busy) {
            Text(stringResource(R.string.core_ui_backup_export_source))
        }
    }
    state.result?.importSummary?.let { summary ->
        Text(stringResource(R.string.core_ui_backup_counts, summary.added.toString(), summary.updated.toString(), summary.skipped.toString()))
        summary.safetyBackup?.let { Text(stringResource(R.string.core_ui_backup_safety, it)) }
    }
    state.result?.legacySummary?.let { summary ->
        Text(stringResource(R.string.core_ui_backup_imported, summary.imported.joinToString { "${it.item}: ${it.count}" }))
        Text(stringResource(R.string.core_ui_backup_dropped, summary.dropped.joinToString { "${it.item}: ${it.count}" }))
    }
    if (exportDialog) ExportOptionsDialog(onDismiss = { if (!state.busy) exportDialog = false },
        onConfirm = { attachments, ai, brokerage, secret ->
            viewModel.export(attachments, ai, brokerage, secret, secret == null && (ai || brokerage)); exportDialog = false
        }, busy = state.busy)
    if (restoreDialog) {
        AlertDialog(onDismissRequest = { if (!state.busy) restoreDialog = false },
            containerColor = HootbookDialogDefaults.containerColor,
            title = { Text(stringResource(R.string.core_backup_restore)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ExportCheckbox(stringResource(R.string.core_backup_legacy), legacy, !state.busy) {
                    legacy = it; viewModel.invalidateInspection(); replaceConfirmed = false; sensitiveConfirmed = false
                }
                TextField(password, { password = it; viewModel.invalidateInspection(); sensitiveConfirmed = false },
                    label = { Text(stringResource(R.string.core_backup_password)) }, singleLine = true,
                    enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp), colors = hootbookTextFieldColors(),
                    modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_select)) }
                if (state.source != null) TextButton(onClick = { viewModel.inspect(password.takeIf(String::isNotEmpty), legacy) }, enabled = !state.busy) {
                    Text(stringResource(R.string.core_backup_inspect))
                }
                state.info?.let { info ->
                    Text(stringResource(R.string.core_ui_backup_info, info.appVersion, info.schema.toString(), info.encrypted.toString()))
                    Text(info.counts.joinToString { "${it.table}: ${it.count}" })
                }
                state.legacyInfo?.let { Text(stringResource(R.string.core_ui_backup_legacy_info, it.accounts.toString(), it.transactions.toString(), it.encrypted.toString())) }
                if (!legacy && state.info != null) {
                    Row {
                        RadioButton(mode == ImportMode.MERGE, onClick = { mode = ImportMode.MERGE; replaceConfirmed = false })
                        Text(stringResource(R.string.core_backup_merge), Modifier.padding(top = 12.dp))
                    }
                    Row {
                        RadioButton(mode == ImportMode.REPLACE_ALL, onClick = { mode = ImportMode.REPLACE_ALL })
                        Text(stringResource(R.string.core_backup_replace), Modifier.padding(top = 12.dp))
                    }
                    if (mode == ImportMode.REPLACE_ALL) ExportCheckbox(stringResource(R.string.core_backup_replace_confirm), replaceConfirmed, !state.busy) { replaceConfirmed = it }
                }
                if (legacy || state.info?.includes?.ai == true || state.info?.includes?.brokerage == true)
                    ExportCheckbox(stringResource(R.string.core_backup_sensitive_confirm), sensitiveConfirmed, !state.busy) { sensitiveConfirmed = it }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
            } },
            confirmButton = { DialogConfirmButton(stringResource(R.string.core_backup_restore), enabled = !state.busy &&
                state.operations.none { it.blocksImport } && (state.info != null || state.legacyInfo != null) &&
                (mode != ImportMode.REPLACE_ALL || replaceConfirmed) &&
                (!(legacy || state.info?.includes?.ai == true || state.info?.includes?.brokerage == true) || sensitiveConfirmed), onClick = {
                viewModel.restore(if (legacy) ImportMode.MERGE else mode, password.takeIf(String::isNotEmpty), replaceConfirmed, sensitiveConfirmed)
            }) }, dismissButton = { DialogDismissButton(stringResource(R.string.cancel), enabled = !state.busy, onClick = { restoreDialog = false }) })
    }
}

@Composable
private fun ExportOptionsDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Boolean, Boolean, String?) -> Unit, busy: Boolean) {
    var ai by remember { mutableStateOf(false) }
    var brokerage by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = HootbookDialogDefaults.containerColor,
        title = { Text(stringResource(R.string.export_data)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.select_data_to_backup), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportCheckbox(stringResource(R.string.backup_brokerage_credentials), brokerage, !busy) { brokerage = it }
            if (brokerage) Text(stringResource(R.string.backup_brokerage_credentials_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            ExportCheckbox(stringResource(R.string.backup_ai_key), ai, !busy) { ai = it }
            if (ai) Text(stringResource(R.string.backup_ai_key_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextField(password, { password = it }, singleLine = true, label = { Text(stringResource(R.string.core_backup_password)) },
                visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp),
                colors = hootbookTextFieldColors(), modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { DialogConfirmButton(stringResource(R.string.export), enabled = !busy, onClick = {
            onConfirm(true, ai, brokerage, password.takeIf(String::isNotEmpty))
        }) },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss) })
}

@Composable
private fun ExportCheckbox(text: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).toggleable(checked, enabled = enabled, onValueChange = onCheckedChange), verticalAlignment = Alignment.CenterVertically) {
        HootbookCheckbox(checked, onCheckedChange = null, enabled = enabled)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable
private fun ActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    tint: Color,
    position: ListItemPosition,
    onClick: (() -> Unit)?
) {
    val enabled = onClick != null
    ListItem(
        headline = {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        supporting = { Text(subtitle) },
        leading = {
            Box(
                modifier = Modifier.size(48.dp).background(container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
        },
        trailing = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}
