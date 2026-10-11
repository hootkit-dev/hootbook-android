@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision
import io.github.hootkit_dev.hootbook.presentation.core.common.recordKindLabel
import io.github.hootkit_dev.hootbook.presentation.ui.components.ConfirmDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.PasswordField
import io.github.hootkit_dev.hootbook.presentation.ui.components.RadioRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionTitle
import io.github.hootkit_dev.hootbook.utils.DateFormats

/**
 * Sync: the account, the passphrase when one is needed, on / off and Sync now, conflicts and
 * deletions from other devices, the advanced actions, and a collapsed section of status details.
 * Everything shown comes from the sync engine; nothing is decrypted or merged here.
 * [embedded] drops the app bar for onboarding's sync step.
 */
@Composable
fun CoreSyncScreen(onBack: () -> Unit, onBackup: () -> Unit, onRecord: (Table, String) -> Unit,
    viewModel: CoreSyncViewModel = hiltViewModel(), embedded: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val back = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = back)
    val snackbar = remember { SnackbarHostState() }
    val outcome = state.outcome
    val outcomeText = outcome?.let { if (it.ok) stringResource(R.string.sync_result, it.pushed.toInt(), it.pulled.toInt()) else stringResource(R.string.sync_result_failed) }
    LaunchedEffect(outcome) { outcomeText?.let { snackbar.showSnackbar(it) } }
    val open: (Table, String) -> Unit = { table, id -> if (!viewModel.back()) onRecord(table, id) }
    if (embedded) SyncContent(state, viewModel, PaddingValues(0.dp), onBackup, open)
    else SecondaryScreen(title = stringResource(R.string.sync_firebase_title), onBack = back, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        SyncContent(state, viewModel, padding, onBackup, open)
    }
}

@Composable
private fun SyncContent(state: CoreSyncUiState, viewModel: CoreSyncViewModel, padding: PaddingValues, onBackup: () -> Unit, onRecord: (Table, String) -> Unit) {
    val context = LocalContext.current
    val status = state.status
    val phase = status?.phase
    val accepted = state.control?.identityDecision == CoreIdentityDecision.ACCEPTED
    val canWrite = !state.busy && !state.loading && state.control?.initialized == true && accepted
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by remember { mutableStateOf<SyncConflict?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Text(stringResource(R.string.sync_intro), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.navigationBlocked) NoticeCard(stringResource(R.string.sync_wait))
        (state.error ?: state.control?.error)?.let { ErrorBanner(it, viewModel::clearError) }
        if (phase == SyncPhase.UNAVAILABLE) {
            NoticeCard(stringResource(R.string.sync_unavailable_body), title = stringResource(R.string.sync_unavailable_title))
            return@Column
        }
        if (phase == SyncPhase.SIGNED_OUT || !accepted)
            Button(onClick = { viewModel.signIn(context) }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sync_sign_in))
            }
        if (status?.accountUid != null || state.control?.identityDecision == CoreIdentityDecision.DIFFERENT_UID_BLOCKED)
            ListSection(title = stringResource(R.string.sync_account)) {
                item(leading = { Icon(Icons.Rounded.AccountCircle, null) },
                    headline = { Text(status?.accountEmail ?: status?.accountUid ?: stringResource(R.string.sync_signed_in_as)) },
                    supporting = { Text(stringResource(R.string.sync_one_account)) },
                    trailing = { TextButton(onClick = { confirm = "sign-out" }, enabled = !state.busy) { Text(stringResource(R.string.sync_sign_out)) } })
            }
        if (phase == SyncPhase.NEEDS_PASSPHRASE) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(stringResource(R.string.sync_passphrase_enter_title))
            Text(stringResource(R.string.sync_passphrase_enter_body), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            NoticeCard(stringResource(R.string.sync_passphrase_warning), error = true)
            PasswordField(passphrase, stringResource(R.string.sync_passphrase), { passphrase = it }, enabled = canWrite,
                supporting = stringResource(R.string.sync_passphrase_note))
            Button(onClick = { val input = passphrase; passphrase = ""; viewModel.passphrase(input) }, enabled = canWrite && passphrase.isNotEmpty(),
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text(stringResource(R.string.sync_passphrase_unlock)) }
        }
        if (phase == SyncPhase.NEEDS_ACCOUNT_CHOICE) NoticeCard(stringResource(R.string.sync_account_choice), error = true) {
            TextButton(onClick = onBackup) { Text(stringResource(R.string.sync_open_backup)) }
        }
        if (phase == SyncPhase.UPGRADE_REQUIRED) NoticeCard(stringResource(R.string.sync_upgrade), error = true)
        if (phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED, SyncPhase.SYNCING)) ListSection(title = stringResource(R.string.sync_status)) {
            val on = phase != SyncPhase.PAUSED
            item(leading = { Icon(if (on) Icons.Rounded.Sync else Icons.Rounded.SyncDisabled, null) },
                headline = { Text(stringResource(R.string.sync_enabled)) },
                supporting = { Text(stringResource(if (on) R.string.sync_enabled_body else R.string.sync_paused_body)) },
                trailing = { HootbookSwitch(on, null, enabled = canWrite) },
                toggled = on, onToggle = if (canWrite) { { viewModel.paused(!it) } } else null)
            item(leading = { Icon(Icons.Rounded.CloudSync, null) },
                headline = { Text(stringResource(if (phase == SyncPhase.SYNCING) R.string.sync_syncing else R.string.sync_now)) },
                supporting = { Text(status?.lastSuccess?.let { stringResource(R.string.sync_last_at, DateFormats.dayTime(it.toJava())) } ?: stringResource(R.string.sync_never)) },
                onClick = if (canWrite && (phase == SyncPhase.IDLE || phase == SyncPhase.FAILED)) viewModel::syncNow else null)
            if (phase == SyncPhase.FAILED) item(leading = { Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
                headline = { Text(stringResource(R.string.sync_problem_title)) }, supporting = { Text(problemText(status?.lastError)) })
        }
        status?.corrupt?.let { corrupt ->
            NoticeCard(stringResource(if (corrupt.writtenHere) R.string.sync_corrupt_here else R.string.sync_corrupt_elsewhere), error = true) {
                TextButton(onClick = { onRecord(corrupt.table, corrupt.recordId) }, enabled = !state.busy) { Text(stringResource(R.string.sync_open_record)) }
            }
        }
        if (state.conflicts.isNotEmpty()) ListSection(title = stringResource(R.string.sync_conflicts)) {
            state.conflicts.forEach { conflict ->
                item(leading = { Icon(if (conflict.pending) Icons.Rounded.Warning else Icons.Rounded.CheckCircle, null,
                        tint = if (conflict.pending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                    headline = { Text(recordKindLabel(conflict.table) + " · " + conflictKindLabel(conflict.kind)) },
                    supporting = { Text(stringResource(if (conflict.pending) R.string.sync_conflict_pending else R.string.sync_conflict_resolved)) },
                    trailing = if (!conflict.pending) ({
                        TextButton(onClick = { viewModel.dismissConflicts(listOf(conflict.id)) }, enabled = canWrite) { Text(stringResource(R.string.sync_dismiss)) }
                    }) else null,
                    onClick = if (canWrite) { { reviewing = conflict } } else null)
            }
        }
        if (state.notices.isNotEmpty()) ListSection(title = stringResource(R.string.sync_notices)) {
            state.notices.forEach { notice ->
                row { shape ->
                    Surface(Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
                            Text(listOfNotNull(notice.name, notice.last4, notice.currency).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.sync_notice_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                notice.references.forEach { refs -> refs.ids.forEach { id ->
                                    TextButton(onClick = { onRecord(refs.table, id) }, enabled = !state.busy) { Text(recordKindLabel(refs.table)) }
                                } }
                                TextButton(onClick = { viewModel.dismissNotices(listOf(notice.id)) }, enabled = canWrite) { Text(stringResource(R.string.sync_dismiss)) }
                            }
                        }
                    }
                }
            }
        }
        if (phase != null && phase != SyncPhase.SIGNED_OUT) ListSection(title = stringResource(R.string.sync_advanced)) {
            state.policy?.let { item(leading = { Icon(Icons.Rounded.Rule, null) }, headline = { Text(stringResource(R.string.sync_policy)) },
                supporting = { Text(stringResource(R.string.sync_policy_help)) }, chevron = true, onClick = if (canWrite) { { sheet = "policy" } } else null) }
            if (phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED))
                item(leading = { Icon(Icons.Rounded.CloudDownload, null) }, headline = { Text(stringResource(R.string.sync_replace)) },
                    supporting = { Text(stringResource(R.string.sync_replace_help)) }, onClick = if (canWrite) { { confirm = "replace" } } else null)
            if (phase != SyncPhase.UPGRADE_REQUIRED)
                item(leading = { Icon(Icons.Rounded.LockReset, null) }, headline = { Text(stringResource(R.string.sync_reset)) },
                    supporting = { Text(stringResource(R.string.sync_reset_help)) }, chevron = true, onClick = if (canWrite) { { sheet = "reset" } } else null)
            item(leading = { Icon(Icons.Rounded.History, null) }, headline = { Text(stringResource(R.string.sync_v1)) },
                supporting = { Text(stringResource(when (state.hasV1) { true -> R.string.sync_v1_present; false -> R.string.sync_v1_absent; null -> R.string.sync_v1_unknown })) },
                trailing = {
                    if (state.hasV1 == true) TextButton(onClick = { confirm = "v1" }, enabled = canWrite) { Text(stringResource(R.string.sync_delete)) }
                    else TextButton(onClick = viewModel::checkV1, enabled = canWrite) { Text(stringResource(R.string.sync_check)) }
                })
        }
        ListSection {
            item(leading = { Icon(Icons.Rounded.BugReport, null) }, headline = { Text(stringResource(R.string.sync_debug_title)) },
                trailing = { Icon(if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    stringResource(if (showDetails) R.string.sync_debug_collapse else R.string.sync_debug_expand)) },
                onClick = { showDetails = !showDetails })
            if (showDetails) {
                status?.let { info -> debugRows(info) }
                item(leading = { Icon(Icons.Rounded.Refresh, null) }, headline = { Text(stringResource(R.string.sync_debug_refresh)) },
                    onClick = if (!state.busy) viewModel::refresh else null)
            }
        }
    }
    when (confirm) {
        "sign-out" -> ConfirmDialog(stringResource(R.string.sync_sign_out_confirm_title), stringResource(R.string.sync_sign_out_confirm_body),
            stringResource(R.string.sync_sign_out), { confirm = null; viewModel.signOut() }, { confirm = null }, enabled = !state.busy)
        "replace" -> ConfirmDialog(stringResource(R.string.sync_replace), stringResource(R.string.sync_replace_confirm),
            stringResource(R.string.sync_replace_action), { confirm = null; viewModel.replaceWithCloud(true) }, { confirm = null }, destructive = true, enabled = !state.busy)
        "v1" -> ConfirmDialog(stringResource(R.string.sync_v1_delete_title), stringResource(R.string.sync_v1_confirm),
            stringResource(R.string.sync_delete), { confirm = null; viewModel.deleteV1(true) }, { confirm = null }, destructive = true, enabled = !state.busy)
    }
    when (sheet) {
        "policy" -> state.policy?.let { policy -> PolicySheet(policy, canWrite, onDismiss = { sheet = null }) { viewModel.policy(it); sheet = null } }
        "reset" -> ResetSheet(canWrite, onDismiss = { sheet = null }) { newPassphrase -> sheet = null; viewModel.resetCloud(newPassphrase, true) }
    }
    reviewing?.let { selected ->
        val current = state.conflicts.find { it.id == selected.id }
        if (current == null) LaunchedEffect(selected.id) { reviewing = null }
        else ConflictDialog(current, !state.busy, onDismiss = { reviewing = null }, onOpen = { reviewing = null; onRecord(current.table, current.recordId) }) { choice ->
            viewModel.resolve(current.id, choice); reviewing = null
        }
    }
}

private fun ListSectionScope.debugRows(info: SyncStatus) {
    debugRow(R.string.sync_status) { phaseLabel(info.phase) }
    debugRow(R.string.sync_debug_account) { listOfNotNull(info.accountEmail, info.accountUid).joinToString("\n").ifEmpty { stringResource(R.string.sync_debug_none) } }
    debugRow(R.string.sync_debug_device) { info.deviceId }
    debugRow(R.string.sync_last_synced) { info.lastSuccess?.let { DateFormats.dayTime(it.toJava()) } ?: stringResource(R.string.sync_never) }
    debugRow(R.string.sync_pending) { info.outboxCount.toString() }
    debugRow(R.string.sync_debug_conflicts) { "${info.conflictCount} · ${info.pendingConflictCount}" }
    debugRow(R.string.sync_debug_error) { info.lastError?.name ?: stringResource(R.string.sync_debug_none) }
    info.retryAfterMs?.let { ms -> debugRow(R.string.sync_debug_retry) { stringResource(R.string.sync_debug_seconds, (ms.toLong() + 999) / 1000) } }
    debugRow(R.string.sync_debug_protocol) { info.protocolVersion.toString() }
    debugRow(R.string.sync_debug_cursor) { info.cursor?.let { "${it.time.seconds}.${it.time.nanos} · ${it.docId}" } ?: stringResource(R.string.sync_debug_none) }
}

private fun ListSectionScope.debugRow(label: Int, value: @Composable () -> String) = item(
    headline = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
    supporting = { SelectionContainer { Text(value(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) } },
)

/** How future conflicts are settled: one choice per kind of conflict. */
@Composable
private fun PolicySheet(policy: ConflictPolicy, enabled: Boolean, onDismiss: () -> Unit, onSave: (ConflictPolicy) -> Unit) {
    var both by remember { mutableStateOf(policy.bothEdited) }
    var delete by remember { mutableStateOf(policy.deleteVsEdit) }
    FormSheet(title = stringResource(R.string.sync_policy), onDismiss = onDismiss, confirmEnabled = enabled,
        onConfirm = { onSave(policy.copy(bothEdited = both, deleteVsEdit = delete)) }) {
        Column {
            SectionTitle(stringResource(R.string.sync_policy_both), indent = 0.dp)
            BothEditedPolicy.entries.forEach { option ->
                RadioRow(stringResource(when (option) {
                    BothEditedPolicy.MINE -> R.string.sync_policy_mine; BothEditedPolicy.THEIRS -> R.string.sync_policy_theirs; BothEditedPolicy.ASK -> R.string.sync_policy_ask
                }), both == option, { both = option }, enabled = enabled)
            }
        }
        Column {
            SectionTitle(stringResource(R.string.sync_policy_delete), indent = 0.dp)
            DeletePolicy.entries.forEach { option ->
                RadioRow(stringResource(when (option) { DeletePolicy.DELETE -> R.string.sync_policy_delete_wins; DeletePolicy.ASK -> R.string.sync_policy_ask }),
                    delete == option, { delete = option }, enabled = enabled)
            }
        }
    }
}

/** A forgotten passphrase: delete the cloud copy and start again with a new one. */
@Composable
private fun ResetSheet(enabled: Boolean, onDismiss: () -> Unit, onReset: (String) -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    FormSheet(title = stringResource(R.string.sync_reset), onDismiss = onDismiss, confirmLabel = stringResource(R.string.sync_reset_action),
        confirmEnabled = enabled && passphrase.isNotEmpty(), onConfirm = { confirming = true }) {
        Text(stringResource(R.string.sync_reset_note), style = MaterialTheme.typography.bodyMedium)
        PasswordField(passphrase, stringResource(R.string.sync_new_passphrase), { passphrase = it }, enabled = enabled)
    }
    if (confirming) ConfirmDialog(stringResource(R.string.sync_reset), stringResource(R.string.sync_reset_confirm), stringResource(R.string.sync_reset_action),
        { confirming = false; val input = passphrase; passphrase = ""; onReset(input) }, { confirming = false }, destructive = true, enabled = enabled)
}

@Composable
private fun ConflictDialog(conflict: SyncConflict, enabled: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit, onResolve: (ConflictChoice) -> Unit) {
    var sides by remember(conflict) { mutableStateOf(conflict.fields.associate { it.column to ConflictSide.MINE }) }
    val deleted = stringResource(R.string.sync_conflict_deleted)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(recordKindLabel(conflict.table) + " · " + conflictKindLabel(conflict.kind)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (conflict.kind == ConflictKind.BOTH_EDITED) conflictGroups(conflict).forEach { fields ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fields.forEach { field ->
                            Text(field.column, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.sync_conflict_values, field.mine ?: "∅", field.theirs ?: "∅"), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConflictSide.entries.forEach { side ->
                                FilterChip(sides[fields.first().column] == side, onClick = { sides = sides + fields.associate { it.column to side } }, enabled = enabled,
                                    label = { Text(stringResource(if (side == ConflictSide.MINE) R.string.sync_conflict_mine else R.string.sync_conflict_theirs)) })
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.sync_conflict_mine), style = MaterialTheme.typography.titleSmall)
                    Text(conflict.mine ?: deleted, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.sync_conflict_theirs), style = MaterialTheme.typography.titleSmall)
                    Text(conflict.theirs ?: deleted, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.sync_open_record)) }
            }
        },
        confirmButton = {
            if (conflict.kind == ConflictKind.BOTH_EDITED) TextButton(onClick = { onResolve(ConflictChoice.PerField(sides)) }, enabled = enabled) { Text(stringResource(R.string.sync_conflict_apply)) }
            else Row {
                TextButton(onClick = { onResolve(ConflictChoice.Theirs) }, enabled = enabled) { Text(stringResource(R.string.sync_conflict_theirs)) }
                TextButton(onClick = { onResolve(ConflictChoice.Mine) }, enabled = enabled) { Text(stringResource(R.string.sync_conflict_mine)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun conflictKindLabel(kind: ConflictKind) = stringResource(when (kind) {
    ConflictKind.BOTH_EDITED -> R.string.sync_conflict_both
    ConflictKind.EDITED_DELETED, ConflictKind.DELETED_EDITED -> R.string.sync_conflict_edit_delete
})

@Composable private fun problemText(error: SyncErrorKind?) = stringResource(when (error) {
    SyncErrorKind.OFFLINE, SyncErrorKind.REMOTE -> R.string.sync_problem_network
    SyncErrorKind.NOT_SIGNED_IN -> R.string.sync_problem_permission
    SyncErrorKind.WRONG_PASSPHRASE, SyncErrorKind.CORRUPT -> R.string.sync_problem_unreadable
    SyncErrorKind.UPGRADE_REQUIRED -> R.string.sync_problem_newer
    else -> R.string.sync_problem_other
})

@Composable private fun phaseLabel(phase: SyncPhase) = stringResource(when (phase) {
    SyncPhase.UNAVAILABLE -> R.string.sync_phase_unavailable; SyncPhase.SIGNED_OUT -> R.string.sync_phase_signed_out
    SyncPhase.NEEDS_PASSPHRASE -> R.string.sync_phase_passphrase; SyncPhase.PAUSED -> R.string.sync_phase_paused
    SyncPhase.IDLE -> R.string.sync_phase_idle; SyncPhase.SYNCING -> R.string.sync_syncing
    SyncPhase.FAILED -> R.string.sync_phase_failed; SyncPhase.UPGRADE_REQUIRED -> R.string.sync_phase_upgrade
    SyncPhase.NEEDS_ACCOUNT_CHOICE -> R.string.sync_phase_account_choice
})

private fun dev.hootkit.core.LocalDateTime.toJava(): java.time.LocalDateTime =
    java.time.LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
