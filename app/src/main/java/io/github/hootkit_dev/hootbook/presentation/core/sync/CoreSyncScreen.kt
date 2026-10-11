@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
    viewModel: CoreSyncViewModel = hiltViewModel(), embedded: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val context = LocalContext.current
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val back = { if (!viewModel.back()) onBack() }; BackHandler(onBack = back)
    var passphrase by remember { mutableStateOf("") }
    var resetPassphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf<SyncConflict?>(null) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haze = remember { HazeState() }
    val status = state.status; val phase = status?.phase
    val canWrite = !state.busy && !state.loading && state.control?.initialized == true && state.control?.identityDecision == io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.ACCEPTED
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).imePadding(), topBar = {
        if (!embedded) CustomTitleTopAppBar(title = stringResource(R.string.sync_firebase_title), scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
            scrollBehaviorLarge = scroll, hazeState = haze, hasBackButton = true, navigationContent = { NavigationContent(back) })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().hazeSource(haze), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (state.loading || state.busy || phase == SyncPhase.SYNCING || state.control?.authChanging == true) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.busy || state.navigationBlocked) item { Text(stringResource(R.string.core_sync_wait)) }
                (state.error ?: state.control?.error)?.let { error -> item { Surface(color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(12.dp)) {
                    Text(error); TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_sync_dismiss)) }
                } } } }
                if (phase == SyncPhase.UNAVAILABLE) item {
                    SyncSettingRow(Icons.Rounded.CloudOff, stringResource(R.string.sync_unavailable_title), stringResource(R.string.sync_unavailable_body))
                }
                else {
                    item { Text(stringResource(R.string.core_sync_limitations), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }

                    if (phase == SyncPhase.SIGNED_OUT || state.control?.identityDecision != io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.ACCEPTED) item {
                        Button(onClick = { viewModel.signIn(context) }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.core_sync_sign_in)) }
                    }
                    if (status?.accountUid != null || state.control?.identityDecision == io.github.hootkit_dev.hootbook.core.sync.CoreIdentityDecision.DIFFERENT_UID_BLOCKED) item {
                        SectionHeader(title = stringResource(R.string.sync_account), modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                        ListItem(headline = { Text(status?.accountEmail ?: status?.accountUid ?: stringResource(R.string.sync_signed_in_as)) },
                            supporting = { Text(stringResource(R.string.core_sync_single_account)) }, leading = { SyncRowIcon(Icons.Rounded.AccountCircle) },
                            trailing = { TextButton(onClick = { confirmSignOut = true }, enabled = !state.busy) { Text(stringResource(R.string.sync_sign_out)) } }, padding = PaddingValues(0.dp))
                    }
                    if (phase == SyncPhase.NEEDS_PASSPHRASE) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = stringResource(R.string.sync_passphrase_enter_title))
                        Text(stringResource(R.string.core_sync_passphrase_note))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber, null); Text(stringResource(R.string.sync_passphrase_warning), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        OutlinedTextField(passphrase, { passphrase = it }, Modifier.fillMaxWidth(), enabled = canWrite, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text(stringResource(R.string.core_sync_passphrase)) })
                        Button(onClick = { val input = passphrase; passphrase = ""; viewModel.passphrase(input) }, enabled = canWrite && passphrase.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.core_sync_unlock)) }
                    } } }
                    if (phase == SyncPhase.NEEDS_ACCOUNT_CHOICE) item { Column {
                        Text(stringResource(R.string.core_sync_account_choice_blocked))
                        OutlinedButton(onClick = onBackup) { Text(stringResource(R.string.core_sync_backup)) }
                    } }
                    if (phase == SyncPhase.UPGRADE_REQUIRED) item { Text(stringResource(R.string.core_sync_upgrade)) }
                    if (phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED, SyncPhase.SYNCING)) item {
                        SectionHeader(title = stringResource(R.string.sync_status), modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                        ListItem(headline = { Text(stringResource(R.string.sync_enabled)) },
                            supporting = { Text(stringResource(if (phase == SyncPhase.PAUSED) R.string.sync_paused_body else R.string.sync_enabled_body)) },
                            leading = { SyncRowIcon(if (phase == SyncPhase.PAUSED) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync) },
                            trailing = { HootbookSwitch(phase != SyncPhase.PAUSED, null, enabled = canWrite) },
                            toggled = phase != SyncPhase.PAUSED, onToggle = if (canWrite) { { viewModel.paused(!it) } } else null, padding = PaddingValues(0.dp))
                        if (phase == SyncPhase.FAILED) SyncSettingRow(Icons.Rounded.ErrorOutline, stringResource(R.string.sync_problem_title),
                            status?.lastError?.name ?: stringResource(R.string.sync_problem_other))
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            ListItem(headline = { Text(stringResource(R.string.sync_debug_title)) }, leading = { SyncRowIcon(Icons.Rounded.BugReport) },
                                trailing = { Icon(if (showDebug) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    stringResource(if (showDebug) R.string.sync_debug_collapse else R.string.sync_debug_expand)) },
                                onClick = { showDebug = !showDebug }, shape = (if (showDebug) ListItemPosition.Top else ListItemPosition.Single).toShape(), padding = PaddingValues(0.dp))
                            AnimatedVisibility(showDebug) {
                                Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                                    status?.let { info ->
                                        val details = listOf(
                                            R.string.sync_status to phaseLabel(info.phase),
                                            R.string.sync_debug_account to listOfNotNull(info.accountEmail, info.accountUid).joinToString("\n"),
                                            R.string.sync_debug_device to info.deviceId,
                                            R.string.sync_last_synced to (info.lastSuccess?.toString() ?: stringResource(R.string.sync_never)),
                                            R.string.sync_pending to info.outboxCount.toString(),
                                            R.string.core_sync_conflicts to "${info.conflictCount} · ${info.pendingConflictCount}",
                                            R.string.sync_debug_error to (info.lastError?.name ?: stringResource(R.string.sync_debug_none)),
                                            R.string.sync_debug_protocol to info.protocolVersion.toString(),
                                            R.string.sync_debug_cursor to (info.cursor?.let { "${it.time.seconds}.${it.time.nanos} · ${it.docId}" } ?: stringResource(R.string.sync_debug_none)))
                                        details.forEachIndexed { index, (label, value) ->
                                            ListItem(headline = { Text(stringResource(label)) }, supporting = { SelectionContainer { Text(value) } },
                                                shape = (if (index == details.lastIndex) ListItemPosition.Bottom else ListItemPosition.Middle).toShape(), padding = PaddingValues(0.dp))
                                        }
                                        info.retryAfterMs?.let { Text(stringResource(R.string.core_sync_retry, it.toLong()), Modifier.padding(16.dp)) }
                                    }
                                    Button(onClick = viewModel::syncNow, enabled = canWrite && phase in setOf(SyncPhase.IDLE, SyncPhase.FAILED), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sync_now))
                                    }
                                    TextButton(onClick = viewModel::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_sync_refresh)) }
                                }
                            }
                        }
                    }
                    state.outcome?.let { outcome -> item { Text(stringResource(R.string.core_sync_outcome, outcome.ok.toString(), outcome.pushed.toInt(), outcome.pulled.toInt(), outcome.error?.name ?: "—")) } }
                    status?.corrupt?.let { corrupt -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(if (corrupt.writtenHere) R.string.core_sync_corrupt_here else R.string.core_sync_corrupt_elsewhere))
                        Text("${corrupt.table} · ${corrupt.recordId}")
                        TextButton(onClick = { if (!viewModel.back()) onRecord(corrupt.table, corrupt.recordId) }, enabled = !state.busy) { Text(stringResource(R.string.core_sync_open_record)) }
                    } } } }
                    item {
                        ListItem(headline = { Text(stringResource(R.string.platform_sync_advanced)) },
                            supporting = { Text(stringResource(R.string.platform_sync_advanced_note)) },
                            trailing = { Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                            onClick = { showAdvanced = !showAdvanced }, padding = PaddingValues(0.dp))
                    }
                    if (showAdvanced && phase in setOf(SyncPhase.IDLE, SyncPhase.PAUSED, SyncPhase.FAILED)) item {
                        OutlinedButton(onClick = { confirmation = "replace" }, enabled = canWrite, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_sync_replace)) }
                    }
                    if (showAdvanced && phase != SyncPhase.UPGRADE_REQUIRED && phase != SyncPhase.SIGNED_OUT) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.core_sync_reset_note))
                        OutlinedTextField(resetPassphrase, { resetPassphrase = it }, Modifier.fillMaxWidth(), enabled = canWrite, label = { Text(stringResource(R.string.core_sync_new_passphrase)) },
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        TextButton(onClick = { confirmation = "reset" }, enabled = canWrite && resetPassphrase.isNotEmpty()) { Text(stringResource(R.string.core_sync_reset)) }
                    } } }
                    if (showAdvanced) state.policy?.let { policy -> item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
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
                    if (showAdvanced) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.core_sync_v1_note)); Text(state.hasV1?.let { stringResource(if (it) R.string.core_sync_v1_present else R.string.core_sync_v1_absent) } ?: "—")
                        FlowRow { TextButton(onClick = viewModel::checkV1, enabled = canWrite) { Text(stringResource(R.string.core_sync_check_v1)) }
                            TextButton(onClick = { confirmation = "v1" }, enabled = canWrite && state.hasV1 == true) { Text(stringResource(R.string.core_sync_delete_v1)) } }
                    } } }
                }
            }
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.sync_sign_out_confirm_title)) }, text = { Text(stringResource(R.string.sync_sign_out_confirm_body)) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }, enabled = !state.busy) { Text(stringResource(R.string.sync_sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } })
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

@Composable
private fun SyncSettingRow(icon: ImageVector, title: String, body: String) {
    ListItem(headline = { Text(title) }, supporting = { Text(body) }, leading = { SyncRowIcon(icon) }, padding = PaddingValues(0.dp))
}

@Composable
private fun SyncRowIcon(icon: ImageVector) {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
