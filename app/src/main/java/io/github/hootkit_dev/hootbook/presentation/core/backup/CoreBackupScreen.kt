@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.BackupInfo
import dev.hootkit.core.ImportMode
import dev.hootkit.core.LegacyInfo
import dev.hootkit.core.SyncPhase
import dev.hootkit.core.Table
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.backup.BackupOperation
import io.github.hootkit_dev.hootbook.core.backup.BackupPhase
import io.github.hootkit_dev.hootbook.presentation.core.common.legacyItemLabel
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.components.CheckboxRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.PasswordField
import io.github.hootkit_dev.hootbook.presentation.ui.components.RadioRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionTitle
import io.github.hootkit_dev.hootbook.presentation.ui.components.detailRow
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Backup & sync: the sync entry, export and restore (each in its own sheet), an unfinished
 * restore when there is one, and the last restore's result.
 */
@Composable
fun CoreBackupScreen(onBack: () -> Unit, viewModel: CoreBackupViewModel = hiltViewModel(), onSync: () -> Unit = {}) {
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val syncViewModel: CoreSyncViewModel = hiltViewModel()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::save)
    val savePreferences = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::savePreferences)
    var sourceOperationId by remember { mutableStateOf<String?>(null) }
    val saveSource = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        sourceOperationId?.let { viewModel.saveSource(uri, it) }; sourceOperationId = null
    }
    val back: () -> Unit = { if (viewModel.canBack()) { if (sheet != null) sheet = null else onBack() } }
    BackHandler(onBack = back)
    val shareTitle = stringResource(R.string.backup_share)
    LaunchedEffect(state.shareFile) {
        state.shareFile?.let { file ->
            val failed = runCatching {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri("backup", uri)
                context.startActivity(Intent.createChooser(send, shareTitle))
            }.isFailure
            viewModel.shareHandled(failed)
        }
    }
    LaunchedEffect(state.result?.source?.operationId, state.busy) {
        if (state.result != null && !state.busy && sheet == "restore") sheet = null
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, sheet) {
        val message = state.message
        if (message != null && sheet == null) { viewModel.clearMessage(); snackbar.showSnackbar(message) }
    }
    SecondaryScreen(title = stringResource(R.string.backup_sync_title), onBack = back, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            if (sheet == null) state.error?.let { ErrorBanner(it, viewModel::clearError) }
            ListSection(title = stringResource(R.string.backup_sync_section)) {
                item(leading = { Icon(Icons.Rounded.CloudSync, null) },
                    headline = { Text(stringResource(R.string.sync_firebase_title)) },
                    supporting = { Text(syncSummary(sync.status?.phase, sync.status?.accountEmail.orEmpty())) },
                    chevron = true, onClick = if (!state.busy) onSync else null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListSection(title = stringResource(R.string.backup_full)) {
                    item(leading = { Icon(Icons.Rounded.Upload, null) },
                        headline = { Text(stringResource(R.string.backup_export)) },
                        supporting = { Text(stringResource(R.string.backup_export_help)) },
                        chevron = true, onClick = if (!state.busy) { { viewModel.clearExport(); sheet = "export" } } else null)
                    item(leading = { Icon(Icons.Rounded.Restore, null) },
                        headline = { Text(stringResource(R.string.backup_restore)) },
                        supporting = { Text(stringResource(R.string.backup_restore_help)) },
                        chevron = true, onClick = if (!state.busy) { { sheet = "restore" } } else null)
                }
                Text(stringResource(R.string.backup_footer), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.operations.filter { it.blocksImport }.forEach { operation ->
                PendingRestore(operation, !state.busy, onApply = { viewModel.finishPlatform(operation) }, onSkip = { viewModel.skipPlatform(operation) },
                    onSaveSource = { sourceOperationId = operation.source.operationId; saveSource.launch("Original_Backup_" + operation.source.operationId + ".backup") })
            }
            (state.result ?: state.operations.lastOrNull { it.phase == BackupPhase.PLATFORM_APPLIED })?.let { operation ->
                LastRestore(operation, !state.busy) { savePreferences.launch("Hootbook_Preferences.json") }
            }
        }
    }
    when (sheet) {
        "export" -> ExportSheet(state, onDismiss = { if (viewModel.canBack()) sheet = null }, viewModel = viewModel,
            onSave = { save.launch("Hootbook_Backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".hkbak") })
        "restore" -> RestoreSheet(state, onDismiss = { if (viewModel.canBack()) sheet = null }, viewModel = viewModel)
    }
}

@Composable
private fun syncSummary(phase: SyncPhase?, email: String) = when (phase) {
    SyncPhase.UNAVAILABLE -> stringResource(R.string.sync_unavailable_title)
    SyncPhase.SIGNED_OUT -> stringResource(R.string.sync_entry_signed_out)
    SyncPhase.PAUSED -> stringResource(R.string.sync_entry_paused, email)
    SyncPhase.IDLE, SyncPhase.SYNCING -> stringResource(R.string.sync_entry_on, email)
    SyncPhase.NEEDS_PASSPHRASE, SyncPhase.NEEDS_ACCOUNT_CHOICE -> stringResource(R.string.sync_entry_setup, email)
    SyncPhase.FAILED -> stringResource(R.string.sync_result_failed)
    SyncPhase.UPGRADE_REQUIRED -> stringResource(R.string.sync_phase_upgrade)
    null -> stringResource(R.string.sync_checking)
}

/** Export options, then, once the file is made, save and share. */
@Composable
private fun ExportSheet(state: CoreBackupUiState, onDismiss: () -> Unit, viewModel: CoreBackupViewModel, onSave: () -> Unit) {
    // Secrets stay in remember, never in saved state.
    var password by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(true) }
    var ai by remember { mutableStateOf(false) }
    var brokerage by remember { mutableStateOf(false) }
    var plainSecrets by remember { mutableStateOf(false) }
    val secrets = ai || brokerage
    val exported = state.exportFile != null
    FormSheet(title = stringResource(R.string.backup_export), onDismiss = onDismiss, busy = state.busy, error = state.error,
        confirmLabel = stringResource(if (exported) R.string.backup_save else R.string.backup_export_action),
        confirmEnabled = exported || !secrets || password.isNotEmpty() || plainSecrets,
        onConfirm = { if (exported) onSave() else viewModel.export(attachments, ai, brokerage, password.takeIf(String::isNotEmpty), plainSecrets) },
        extraAction = if (exported) ({ OutlinedButton(onClick = viewModel::share, enabled = !state.busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.backup_share)) } }) else null,
    ) {
        SheetStatus(state)
        if (exported) {
            state.exportInfo?.let { BackupContents(it) }
        } else {
            Column {
                SectionTitle(stringResource(R.string.backup_include), indent = 0.dp)
                CheckboxRow(stringResource(R.string.backup_attachments), attachments, { attachments = it }, enabled = !state.busy)
                CheckboxRow(stringResource(R.string.backup_ai), ai, { ai = it; plainSecrets = false }, enabled = !state.busy)
                CheckboxRow(stringResource(R.string.backup_brokerage), brokerage, { brokerage = it; plainSecrets = false }, enabled = !state.busy)
            }
            PasswordField(password, stringResource(R.string.backup_password), { password = it; plainSecrets = false },
                enabled = !state.busy, supporting = stringResource(R.string.backup_password_export_help))
            if (secrets && password.isEmpty())
                CheckboxRow(stringResource(R.string.backup_plain_secrets), plainSecrets, { plainSecrets = it }, enabled = !state.busy)
        }
    }
}

/** Pick a file (checked at once), see what it holds, choose merge or replace, restore. */
@Composable
private fun RestoreSheet(state: CoreBackupUiState, onDismiss: () -> Unit, viewModel: CoreBackupViewModel) {
    var password by remember { mutableStateOf("") }
    var legacy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var replaceConfirmed by remember { mutableStateOf(false) }
    var sensitiveConfirmed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        sensitiveConfirmed = false; replaceConfirmed = false
        viewModel.select(uri, legacy, password.takeIf { it.isNotEmpty() })
    }
    val inspected = state.info != null || state.legacyInfo != null
    val sensitive = state.legacy || state.info?.includes?.ai == true || state.info?.includes?.brokerage == true
    val blocked = state.operations.any { it.blocksImport }
    FormSheet(title = stringResource(R.string.backup_restore), onDismiss = onDismiss, busy = state.busy, error = state.error,
        confirmLabel = stringResource(R.string.backup_restore_action),
        confirmEnabled = inspected && !blocked && (state.legacy || mode != ImportMode.REPLACE_ALL || replaceConfirmed) && (!sensitive || sensitiveConfirmed),
        onConfirm = { viewModel.restore(mode, password.takeIf(String::isNotEmpty), replaceConfirmed, sensitiveConfirmed) },
    ) {
        SheetStatus(state)
        if (blocked) Text(stringResource(R.string.backup_pending_uncertain), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        CheckboxRow(stringResource(R.string.backup_legacy), legacy, { legacy = it }, enabled = !state.busy)
        PasswordField(password, stringResource(R.string.backup_password), { password = it; viewModel.invalidateInspection(); sensitiveConfirmed = false },
            enabled = !state.busy, supporting = stringResource(R.string.backup_password_restore_help))
        ListSection {
            item(leading = { Icon(Icons.Rounded.Description, null) },
                headline = { Text(stringResource(R.string.backup_file)) },
                supporting = { Text(state.source?.let { android.text.format.Formatter.formatShortFileSize(LocalContext.current, it.size) }
                    ?: stringResource(R.string.backup_file_none)) },
                trailing = if (state.source != null && !inspected) ({
                    TextButton(onClick = { viewModel.inspect(password.takeIf(String::isNotEmpty)) }, enabled = !state.busy) { Text(stringResource(R.string.backup_check)) }
                }) else null,
                chevron = state.source == null || inspected, onClick = if (!state.busy) { { pick.launch(arrayOf("*/*")) } } else null)
        }
        state.info?.let { BackupContents(it) }
        state.legacyInfo?.let { LegacyContents(it) }
        if (inspected) {
            if (!state.legacy) Column {
                SectionTitle(stringResource(R.string.backup_mode), indent = 0.dp)
                RadioRow(stringResource(R.string.backup_merge), mode == ImportMode.MERGE, { mode = ImportMode.MERGE; replaceConfirmed = false },
                    enabled = !state.busy, supporting = stringResource(R.string.backup_merge_help))
                RadioRow(stringResource(R.string.backup_replace), mode == ImportMode.REPLACE_ALL, { mode = ImportMode.REPLACE_ALL; replaceConfirmed = false },
                    enabled = !state.busy, supporting = stringResource(R.string.backup_replace_help))
                if (mode == ImportMode.REPLACE_ALL)
                    CheckboxRow(stringResource(R.string.backup_replace_confirm), replaceConfirmed, { replaceConfirmed = it }, enabled = !state.busy)
            }
            if (sensitive) CheckboxRow(stringResource(R.string.backup_sensitive_confirm), sensitiveConfirmed, { sensitiveConfirmed = it }, enabled = !state.busy)
        }
        Text(stringResource(R.string.backup_restore_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What the sheet is doing, or the last thing it did. */
@Composable
private fun SheetStatus(state: CoreBackupUiState) {
    val text = if (state.busy) stringResource(R.string.backup_busy) else state.message
    if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BackupContents(info: BackupInfo) {
    val yes = stringResource(R.string.ui_yes); val no = stringResource(R.string.ui_no)
    fun count(table: Table) = info.counts.find { it.table == table }?.count ?: 0u
    val extras = listOfNotNull(
        stringResource(R.string.backup_inc_attachments).takeIf { info.includes.attachments },
        stringResource(R.string.backup_inc_preferences).takeIf { info.includes.preferences },
        stringResource(R.string.backup_inc_ai).takeIf { info.includes.ai },
        stringResource(R.string.backup_inc_brokerage).takeIf { info.includes.brokerage },
    )
    ListSection(title = stringResource(R.string.backup_contents)) {
        detailRow(stringResource(R.string.backup_created_at), DateFormats.dayTime(info.createdAt.toJava()))
        detailRow(stringResource(R.string.backup_accounts), count(Table.ACCOUNTS).toString())
        detailRow(stringResource(R.string.backup_transactions), count(Table.TRANSACTIONS).toString())
        detailRow(stringResource(R.string.backup_encrypted), if (info.encrypted) yes else no)
        detailRow(stringResource(R.string.backup_also_includes), extras.joinToString(" · ").ifEmpty { stringResource(R.string.backup_includes_none) })
    }
}

@Composable
private fun LegacyContents(info: LegacyInfo) {
    ListSection(title = stringResource(R.string.backup_contents)) {
        info.createdAt?.let { detailRow(stringResource(R.string.backup_created_at), DateFormats.dayTime(it.toJava())) }
        detailRow(stringResource(R.string.backup_accounts), info.accounts.toString())
        detailRow(stringResource(R.string.backup_transactions), info.transactions.toString())
        detailRow(stringResource(R.string.backup_encrypted), stringResource(if (info.encrypted) R.string.ui_yes else R.string.ui_no))
    }
}

/** A restore that stopped part-way: what happened and what can be done about it. */
@Composable
private fun PendingRestore(operation: BackupOperation, enabled: Boolean, onApply: () -> Unit, onSkip: () -> Unit, onSaveSource: () -> Unit) {
    val committed = operation.phase == BackupPhase.CORE_COMMITTED
    Column {
        SectionTitle(stringResource(R.string.backup_pending))
        NoticeCard(stringResource(if (committed) R.string.backup_pending_committed else R.string.backup_pending_uncertain), error = !committed) {
            TextButton(onClick = onSaveSource, enabled = enabled) { Text(stringResource(R.string.backup_save_source)) }
            if (committed) {
                TextButton(onClick = onSkip, enabled = enabled) { Text(stringResource(R.string.backup_skip_rest)) }
                TextButton(onClick = onApply, enabled = enabled) { Text(stringResource(R.string.backup_apply_rest)) }
            }
        }
    }
}

@Composable
private fun LastRestore(operation: BackupOperation, enabled: Boolean, onSavePreferences: () -> Unit) {
    ListSection(title = stringResource(R.string.backup_last_restore)) {
        operation.importSummary?.let { summary ->
            detailRow(stringResource(R.string.backup_added), summary.added.toString())
            detailRow(stringResource(R.string.backup_updated), summary.updated.toString())
            detailRow(stringResource(R.string.backup_skipped), summary.skipped.toString())
            if (summary.aiRestored) detailRow(stringResource(R.string.backup_ai_restored), stringResource(R.string.ui_yes))
            if (summary.brokerageAdded > 0u) detailRow(stringResource(R.string.backup_brokers_added), summary.brokerageAdded.toString())
            summary.safetyBackup?.let { detailRow(stringResource(R.string.backup_safety), it.substringAfterLast('/')) }
        }
        operation.legacySummary?.let { summary ->
            summaryRows(R.string.backup_imported) { summary.imported.map { "${legacyItemLabel(it.item)} ${it.count}" }.joinToString(" · ") }
            if (summary.dropped.isNotEmpty()) summaryRows(R.string.backup_dropped) { summary.dropped.map { "${legacyItemLabel(it.item)} ${it.count}" }.joinToString(" · ") }
            if (summary.renamedCurrencies.isNotEmpty())
                summaryRows(R.string.backup_renamed) { summary.renamedCurrencies.entries.joinToString { "${it.key} → ${it.value}" } }
        }
        if (operation.preferences != null) item(headline = { Text(stringResource(R.string.backup_preferences_review)) },
            supporting = { Text(stringResource(R.string.backup_preferences_review_help)) },
            chevron = true, onClick = if (enabled) onSavePreferences else null)
    }
}

private fun ListSectionScope.summaryRows(label: Int, value: @Composable () -> String) =
    item(headline = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant) }, supporting = { Text(value()) })

private fun dev.hootkit.core.LocalDateTime.toJava(): java.time.LocalDateTime =
    java.time.LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
