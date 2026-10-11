@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

/** All displayed ledger/sync state is generated Core data. No platform decryption/merge. */
@Composable
fun CoreSyncScreen(onBack: () -> Unit, onBackup: () -> Unit, onRecord: (Table, String) -> Unit,
    viewModel: CoreSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val context = LocalContext.current
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val back = { if (!viewModel.back()) onBack() }; BackHandler(onBack = back)
    var passphrase by remember { mutableStateOf("") }
    var resetPassphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf<SyncConflict?>(null) }
    val status = state.status; val phase = status?.phase
    val canWrite = !state.busy && !state.loading && state.control?.initialized == true && state.control?.identityDecision == io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.ACCEPTED
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text(stringResource(R.string.core_sync_title)) },
        navigationIcon = { TextButton(onClick = back) { Text(stringResource(R.string.core_sync_back)) } },
        actions = { TextButton(onClick = viewModel::refresh, enabled = !state.busy) { Text(stringResource(R.string.core_sync_refresh)) } }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 880.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading || state.busy || phase == SyncPhase.SYNCING || state.control?.authChanging == true) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.busy || state.navigationBlocked) item { Text(stringResource(R.string.core_sync_wait)) }
                (state.error ?: state.control?.error)?.let { error -> item { Surface(color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(12.dp)) {
                    Text(error); TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_sync_dismiss)) }
                } } } }
                item { Text(phase?.let { phaseLabel(it) } ?: stringResource(R.string.core_sync_loading), style = MaterialTheme.typography.titleLarge) }
                if (phase == SyncPhase.UNAVAILABLE) item { Text(stringResource(R.string.core_sync_offline)) }
                else {
                    item { Text(stringResource(R.string.core_sync_limitations)) }
                    item { Text(stringResource(R.string.core_sync_single_account)) }
                    if (phase == SyncPhase.SIGNED_OUT || state.control?.identityDecision != io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.ACCEPTED) item {
                        Button(onClick = { viewModel.signIn(context) }, enabled = !state.busy && !state.loading) { Text(stringResource(R.string.core_sync_sign_in)) }
                    }
                    if (status?.accountUid != null || state.control?.identityDecision == io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.DIFFERENT_UID_BLOCKED) item {
                        OutlinedButton(onClick = viewModel::signOut, enabled = !state.busy) { Text(stringResource(R.string.core_sync_sign_out)) }
                    }
                    status?.let { info -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(info.accountEmail ?: info.accountUid ?: "—")
                        Text(stringResource(R.string.core_sync_last_success, info.lastSuccess?.toString() ?: "—"))
                        Text(stringResource(R.string.core_sync_counts, info.outboxCount.toInt(), info.conflictCount.toInt(), info.pendingConflictCount.toInt()))
                        Text("protocol v${info.protocolVersion} · device ${info.deviceId}")
                        info.lastError?.let { Text(stringResource(R.string.core_sync_last_error, it.name)) }
                        info.retryAfterMs?.let { Text(stringResource(R.string.core_sync_retry, it.toLong())) }
                        info.cursor?.let { Text("cursor ${it.time.seconds}.${it.time.nanos} · ${it.docId}") }
                    } } } }
                    if (phase == SyncPhase.NEEDS_PASSPHRASE) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.core_sync_passphrase_note))
                        OutlinedTextField(passphrase, { passphrase = it }, Modifier.fillMaxWidth(), enabled = canWrite, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text(stringResource(R.string.core_sync_passphrase)) })
                        Button(onClick = { val input = passphrase; passphrase = ""; viewModel.passphrase(input) }, enabled = canWrite && passphrase.isNotEmpty()) { Text(stringResource(R.string.core_sync_unlock)) }
                    } } }
                    if (phase == SyncPhase.NEEDS_ACCOUNT_CHOICE) item { Column {
                        Text(stringResource(R.string.core_sync_account_choice_blocked))
                        OutlinedButton(onClick = onBackup) { Text(stringResource(R.string.core_sync_backup)) }
                    } }
                    if (phase == SyncPhase.UPGRADE_REQUIRED) item { Text(stringResource(R.string.core_sync_upgrade)) }
                    if (phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED, SyncPhase.SYNCING)) item { Column {
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = phase != SyncPhase.PAUSED, onCheckedChange = { viewModel.paused(!it) }, enabled = canWrite)
                            Text(stringResource(R.string.core_sync_enabled), Modifier.weight(1f)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::syncNow, enabled = canWrite && phase != SyncPhase.PAUSED) { Text(stringResource(R.string.core_sync_now)) }
                            OutlinedButton(onClick = { confirmation = "replace" }, enabled = canWrite && phase != SyncPhase.SYNCING) { Text(stringResource(R.string.core_sync_replace)) }
                        }
                    } }
                    state.outcome?.let { outcome -> item { Text(stringResource(R.string.core_sync_outcome, outcome.ok.toString(), outcome.pushed.toInt(), outcome.pulled.toInt(), outcome.error?.name ?: "—")) } }
                    status?.corrupt?.let { corrupt -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(if (corrupt.writtenHere) R.string.core_sync_corrupt_here else R.string.core_sync_corrupt_elsewhere))
                        Text("${corrupt.table} · ${corrupt.recordId}")
                        TextButton(onClick = { if (!viewModel.back()) onRecord(corrupt.table, corrupt.recordId) }, enabled = !state.busy) { Text(stringResource(R.string.core_sync_open_record)) }
                    } } } }
                    if (phase != SyncPhase.UPGRADE_REQUIRED && phase != SyncPhase.SIGNED_OUT) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.core_sync_reset_note))
                        OutlinedTextField(resetPassphrase, { resetPassphrase = it }, Modifier.fillMaxWidth(), enabled = canWrite, label = { Text(stringResource(R.string.core_sync_new_passphrase)) },
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        TextButton(onClick = { confirmation = "reset" }, enabled = canWrite && resetPassphrase.isNotEmpty()) { Text(stringResource(R.string.core_sync_reset)) }
                    } } }
                    state.policy?.let { policy -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.core_sync_policy), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.core_sync_both_edited))
                        FlowRow { BothEditedPolicy.entries.forEach { option -> FilterChip(policy.bothEdited == option, { viewModel.policy(policy.copy(bothEdited = option)) }, enabled = canWrite,
                            label = { Text(option.name) }) } }
                        Text(stringResource(R.string.core_sync_delete_edit))
                        FlowRow { DeletePolicy.entries.forEach { option -> FilterChip(policy.deleteVsEdit == option, { viewModel.policy(policy.copy(deleteVsEdit = option)) }, enabled = canWrite,
                            label = { Text(option.name) }) } }
                    } } } }
                    if (state.conflicts.isNotEmpty()) item { Text(stringResource(R.string.core_sync_conflicts), style = MaterialTheme.typography.titleLarge) }
                    items(state.conflicts, key = { "conflict-${it.id}" }) { item -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${item.table} · ${item.recordId} · ${item.kind}")
                        Text(stringResource(if (item.pending) R.string.core_sync_pending else R.string.core_sync_auto_resolved) + " · ${item.applied ?: "—"}")
                        FlowRow { TextButton(onClick = { conflict = item }, enabled = canWrite) { Text(stringResource(R.string.core_sync_review)) }
                            TextButton(onClick = { if (!viewModel.back()) onRecord(item.table, item.recordId) }, enabled = !state.busy) { Text(stringResource(R.string.core_sync_open_record)) }
                            if (!item.pending) TextButton(onClick = { viewModel.dismissConflicts(listOf(item.id)) }, enabled = canWrite) { Text(stringResource(R.string.core_sync_dismiss)) } }
                    } } }
                    if (state.notices.isNotEmpty()) item { Text(stringResource(R.string.core_sync_notices), style = MaterialTheme.typography.titleLarge) }
                    items(state.notices, key = { "notice-${it.id}" }) { notice -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${notice.name} ${notice.last4.orEmpty()} ${notice.currency.orEmpty()}")
                        Text(stringResource(R.string.core_sync_deleted_remote))
                        notice.references.forEach { refs -> Text(refs.table.name)
                            refs.ids.forEach { id -> TextButton(onClick = { if (!viewModel.back()) onRecord(refs.table, id) }, enabled = !state.busy) { Text(id) } } }
                        TextButton(onClick = { viewModel.dismissNotices(listOf(notice.id)) }, enabled = canWrite) { Text(stringResource(R.string.core_sync_dismiss)) }
                    } } }
                    item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.core_sync_v1_note)); Text(state.hasV1?.let { stringResource(if (it) R.string.core_sync_v1_present else R.string.core_sync_v1_absent) } ?: "—")
                        FlowRow { TextButton(onClick = viewModel::checkV1, enabled = canWrite) { Text(stringResource(R.string.core_sync_check_v1)) }
                            TextButton(onClick = { confirmation = "v1" }, enabled = canWrite && state.hasV1 == true) { Text(stringResource(R.string.core_sync_delete_v1)) } }
                    } } }
                }
            }
        }
    }
    confirmation?.let { kind -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(R.string.core_sync_confirm)) },
        text = { Text(stringResource(when (kind) { "reset" -> R.string.core_sync_confirm_reset; "replace" -> R.string.core_sync_confirm_replace; else -> R.string.core_sync_confirm_v1 })) },
        confirmButton = { TextButton(onClick = { confirmation = null; when (kind) {
            "reset" -> { val input = resetPassphrase; resetPassphrase = ""; viewModel.resetCloud(input, true) }
            "replace" -> viewModel.replaceWithCloud(true)
            else -> viewModel.deleteV1(true)
        } }) { Text(stringResource(R.string.core_sync_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.core_sync_cancel)) } }) }
    conflict?.let { selected ->
        val current = state.conflicts.find { it.id == selected.id }
        if (current == null) { LaunchedEffect(selected.id) { conflict = null } }
        else ConflictDialog(current, !state.busy, onDismiss = { conflict = null }, onResolve = { choice -> viewModel.resolve(current.id, choice); conflict = null })
    }
}
private tailrec fun Context.syncActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.syncActivity(); else -> null }
@Composable private fun phaseLabel(phase: SyncPhase) = stringResource(when (phase) {
    SyncPhase.UNAVAILABLE -> R.string.core_sync_phase_unavailable; SyncPhase.SIGNED_OUT -> R.string.core_sync_phase_signed_out
    SyncPhase.NEEDS_PASSPHRASE -> R.string.core_sync_phase_passphrase; SyncPhase.PAUSED -> R.string.core_sync_phase_paused
    SyncPhase.IDLE -> R.string.core_sync_phase_idle; SyncPhase.SYNCING -> R.string.core_sync_phase_syncing
    SyncPhase.FAILED -> R.string.core_sync_phase_failed; SyncPhase.UPGRADE_REQUIRED -> R.string.core_sync_phase_upgrade
    SyncPhase.NEEDS_ACCOUNT_CHOICE -> R.string.core_sync_phase_account_choice
})
@Composable private fun ConflictDialog(conflict: SyncConflict, enabled: Boolean, onDismiss: () -> Unit, onResolve: (ConflictChoice) -> Unit) {
    var sides by remember(conflict) { mutableStateOf(conflict.fields.associate { it.column to ConflictSide.MINE }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.core_sync_review)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${conflict.table} · ${conflict.recordId} · ${conflict.kind}")
            Text(stringResource(R.string.core_sync_mine)); Text(conflict.mine ?: stringResource(R.string.core_sync_deleted));
            Text(stringResource(R.string.core_sync_theirs)); Text(conflict.theirs ?: stringResource(R.string.core_sync_deleted))
            if (conflict.kind == ConflictKind.BOTH_EDITED) conflictGroups(conflict).forEach { fields ->
                Text(fields.joinToString(" / ") { it.column }); fields.forEach { field ->
                    Text("${field.column}\nbase: ${field.base ?: "∅"}\nmine: ${field.mine ?: "∅"}\ntheirs: ${field.theirs ?: "∅"}") }
                FlowRow { ConflictSide.entries.forEach { side -> FilterChip(sides[fields.first().column] == side,
                    onClick = { sides = sides + fields.associate { it.column to side } }, enabled = enabled, label = { Text(side.name) }) } }
            }
        }
    }, confirmButton = { Column {
        FlowRow { TextButton(onClick = { onResolve(ConflictChoice.Mine) }, enabled = enabled) { Text(stringResource(R.string.core_sync_mine)) }
            TextButton(onClick = { onResolve(ConflictChoice.Theirs) }, enabled = enabled) { Text(stringResource(R.string.core_sync_theirs)) } }
        if (conflict.kind == ConflictKind.BOTH_EDITED) TextButton(onClick = { onResolve(ConflictChoice.PerField(sides)) }, enabled = enabled) { Text(stringResource(R.string.core_sync_per_field)) }
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_sync_cancel)) } })
}
