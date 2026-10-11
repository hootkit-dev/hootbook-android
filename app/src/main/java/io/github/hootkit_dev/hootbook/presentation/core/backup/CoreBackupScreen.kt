@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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

/** Legacy imports use user-selected backups. In-place old databases are guarded by the recovery-only bridge. */
@Composable
fun CoreBackupScreen(onBack: () -> Unit, viewModel: CoreBackupViewModel = hiltViewModel(), onSync: () -> Unit = {}) {
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val syncViewModel: CoreSyncViewModel = hiltViewModel()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haze = remember { HazeState() }
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
    val back: () -> Unit = { if (viewModel.canBack()) { if (sheet != null) sheet = null else onBack() } }
    BackHandler(onBack = back)
    val shareTitle = stringResource(R.string.core_backup_share)
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
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(R.string.backup_sync_title), scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
            scrollBehaviorLarge = scroll, hazeState = haze, hasBackButton = true, navigationContent = { NavigationContent(back) })
    }) { padding ->
        Column(Modifier.fillMaxSize().hazeSource(haze).padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.core_backup_busy)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it) }
            SectionHeader(title = stringResource(R.string.seamless_sync_section), modifier = Modifier.padding(start = 16.dp))
            val syncSubtitle = when (sync.status?.phase) {
                SyncPhase.UNAVAILABLE -> stringResource(R.string.sync_unavailable_title)
                SyncPhase.SIGNED_OUT -> stringResource(R.string.sync_entry_signed_out)
                SyncPhase.PAUSED -> stringResource(R.string.sync_entry_paused, sync.status?.accountEmail.orEmpty())
                SyncPhase.IDLE, SyncPhase.SYNCING -> stringResource(R.string.sync_entry_on, sync.status?.accountEmail.orEmpty())
                SyncPhase.NEEDS_PASSPHRASE, SyncPhase.NEEDS_ACCOUNT_CHOICE -> stringResource(R.string.sync_entry_setup, sync.status?.accountEmail.orEmpty())
                SyncPhase.FAILED -> stringResource(R.string.core_sync_phase_failed)
                SyncPhase.UPGRADE_REQUIRED -> stringResource(R.string.core_sync_phase_upgrade)
                null -> stringResource(R.string.core_sync_loading)
            }
            BackupActionRow(stringResource(R.string.sync_firebase_title), syncSubtitle, Icons.Rounded.CloudSync,
                ListItemPosition.Single, if (!state.busy) onSync else null)
            SectionHeader(title = stringResource(R.string.full_backup_section), modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            Text(stringResource(R.string.platform_backup_local_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                BackupProviderRow(stringResource(R.string.provider_local_file), stringResource(R.string.platform_backup_local_provider), Icons.Rounded.PhoneAndroid, ListItemPosition.Top, selected = true)
                BackupProviderRow(stringResource(R.string.platform_provider_google_drive), stringResource(R.string.platform_backup_cloud_unavailable), Icons.Rounded.Cloud, ListItemPosition.Middle)
                BackupProviderRow(stringResource(R.string.platform_provider_webdav), stringResource(R.string.platform_backup_cloud_unavailable), Icons.Rounded.Storage, ListItemPosition.Bottom)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                BackupActionRow(stringResource(R.string.export_data), stringResource(R.string.select_data_to_backup), Icons.Rounded.Upload,
                    ListItemPosition.Top, if (!state.busy) { { sheet = "export" } } else null)
                BackupActionRow(stringResource(R.string.core_backup_restore), stringResource(R.string.platform_backup_restore_body), Icons.Rounded.Restore,
                    ListItemPosition.Middle, if (!state.busy) { { sheet = "restore" } } else null)
                BackupActionRow(stringResource(R.string.import_cashew_backup), stringResource(R.string.platform_backup_cashew_unavailable), Icons.Rounded.FolderOpen,
                    ListItemPosition.Bottom, null)
            }
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
            Spacer(Modifier.height(16.dp))
        }
    }
    sheet?.let { page ->
        HootbookModalBottomSheet(onDismissRequest = { if (viewModel.canBack()) sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).imePadding().verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.core_backup_busy)) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
                if (page == "export") {
                    Text(stringResource(R.string.core_backup_export), style = MaterialTheme.typography.titleLarge)
                    BackupCheck(R.string.core_backup_attachments, attachments, !state.busy) { attachments = it }
                    BackupCheck(R.string.core_backup_ai, ai, !state.busy) { ai = it; exportSensitive = false }
                    BackupCheck(R.string.core_backup_brokerage, brokerage, !state.busy) { brokerage = it; exportSensitive = false }
                    SecretInput(R.string.core_backup_password, exportPassword, !state.busy) { exportPassword = it; exportSensitive = false }
                    if ((ai || brokerage) && exportPassword.isEmpty()) BackupCheck(R.string.core_backup_secret_confirm, exportSensitive, !state.busy) { exportSensitive = it }
                    Button(onClick = { viewModel.export(attachments, ai, brokerage, exportPassword.takeIf(String::isNotEmpty), exportSensitive) },
                        enabled = !state.busy && (!(ai || brokerage) || exportPassword.isNotEmpty() || exportSensitive)) {
                        Text(stringResource(R.string.core_backup_export))
                    }
                    state.exportInfo?.let { BackupInfoText(it) }
                    if (state.exportFile != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { save.launch("HootKit_Backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".hkbak") }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_save)) }
                        OutlinedButton(onClick = viewModel::share, enabled = !state.busy) { Text(stringResource(R.string.core_backup_share)) }
                    }

                } else {
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
                            enabled = !state.busy && state.operations.none { it.blocksImport } &&
                                (state.legacy || mode != ImportMode.REPLACE_ALL || replaceConfirmed) &&
                                (!(state.legacy || state.info?.includes?.ai == true || state.info?.includes?.brokerage == true) || importSensitive)) { Text(stringResource(R.string.core_backup_import)) }
                    }
                    if (state.operations.any { it.blocksImport }) {
                        Text(stringResource(R.string.core_backup_uncertain), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { sheet = null }, enabled = !state.busy) { Text(stringResource(R.string.core_backup_pending)) }
                    }
                    Text(stringResource(R.string.core_ui_restore_references_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.core_backup_platform_note), style = MaterialTheme.typography.bodySmall)
                }
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


@Composable
private fun BackupActionRow(title: String, subtitle: String, icon: ImageVector, position: ListItemPosition, onClick: (() -> Unit)?) {
    ListItem(headline = { Text(title) }, supporting = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leading = { BackupRowIcon(icon) }, trailing = {
            if (onClick != null) Icon(Icons.Rounded.ChevronRight, null)
            else if (position == ListItemPosition.Bottom) Icon(Icons.Rounded.Block, stringResource(R.string.platform_unavailable))
        }, onClick = onClick, shape = position.toShape(), padding = PaddingValues(0.dp))
}

@Composable
private fun BackupProviderRow(title: String, subtitle: String, icon: ImageVector, position: ListItemPosition, selected: Boolean = false) {
    ListItem(headline = { Text(title) }, supporting = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leading = { BackupRowIcon(icon) }, trailing = { RadioButton(selected, onClick = null, enabled = selected) },
        selected = selected, shape = position.toShape(), padding = PaddingValues(0.dp))
}

@Composable
private fun BackupRowIcon(icon: ImageVector) {
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
