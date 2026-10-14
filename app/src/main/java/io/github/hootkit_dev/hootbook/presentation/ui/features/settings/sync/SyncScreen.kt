@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.selection.SelectionContainer
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.components.hootbookTextFieldColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.DateFormats
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.ZoneId


import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONObject

/**
 * Backup & sync → Firebase sync: Google sign-in, the on/off switch, the sync passphrase, the
 * first-sync choice, conflict resolution, deletion notices, conflict policy, and a collapsed status
 * and debug section (docs/sync.md). Core supplies the current setup phase.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    onOpenRecord: (Table, String) -> Unit = { _, _ -> },
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrongPassphrase by viewModel.wrongPassphrase.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    var reviewingConflictId by rememberSaveable { mutableStateOf<Long?>(null) }
    val reviewingConflict = state.conflicts.find { it.id == reviewingConflictId }
    SyncMessagesEffect(viewModel, snackbar)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.sync_firebase_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                        bottom = Dimensions.Padding.content + paddingValues.calculateBottomPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.sync_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                SyncSetupSection(state, wrongPassphrase, viewModel) {
                    EnabledSection(state, onEnabledChange = viewModel::setEnabled)
                }
                if (state.conflicts.isNotEmpty()) {
                    ConflictsSection(
                        conflicts = state.conflicts,
                        busy = state.busy,
                        onReview = { reviewingConflictId = it.id },
                        onOpenRecord = onOpenRecord,
                        onDismiss = viewModel::dismissConflicts,
                    )
                }
                if (state.notices.isNotEmpty()) {
                    NoticesSection(
                        notices = state.notices,
                        busy = state.busy,
                        onOpenRecord = onOpenRecord,
                        onDismiss = viewModel::dismissNotices,
                    )
                }
                state.policy?.let { policy ->
                    ConflictPolicySection(
                        policy = policy,
                        busy = state.busy,
                        onPolicyChange = viewModel::setPolicy,
                    )
                }
                if (state.status != null && state.status?.phase != SyncPhase.UNAVAILABLE) {
                    DebugSection(state, onSyncNow = { viewModel.syncNow() })
                }
            }
        }
    }

    reviewingConflict?.let { conflict ->
        ConflictResolutionDialog(
            conflict = conflict,
            enabled = !state.busy,
            onDismiss = { reviewingConflictId = null },
            onResolve = { choice ->
                viewModel.resolveConflict(conflict.id, choice)
                reviewingConflictId = null
            },
        )
    }
}

/** Shows [SyncViewModel.messages] as snackbars. */
@Composable
internal fun SyncMessagesEffect(viewModel: SyncViewModel, snackbar: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.sync_failed)
    val done = stringResource(R.string.sync_done)
    val message = (state.error ?: state.control?.error)?.let { stringResource(R.string.onboarding_operation_failed) } ?: state.outcome?.let { if (it.ok) done else failed }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

}

/**
 * The steps that turn sync on: Google sign-in, then the account (with sign-out), the passphrase
 * and the first-sync choice; [active] once sync is set up. Shared by this page and onboarding,
 * inside a column.
 */
@Composable
internal fun SyncSetupSection(
    state: SyncUiState,
    wrongPassphrase: Boolean,
    viewModel: SyncViewModel,
    active: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    if (state.loading || state.status == null) { BusyRow(); return }
    when (state.status?.phase) {
        SyncPhase.UNAVAILABLE -> SyncRow(
            icon = Icons.Rounded.CloudOff,
            title = stringResource(R.string.sync_unavailable_title),
            supporting = stringResource(R.string.sync_unavailable_body),
            position = ListItemPosition.Single
        )
        SyncPhase.SIGNED_OUT -> Button(
            onClick = { context.findActivity()?.let(viewModel::signIn) },
            enabled = !state.busy,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.busy) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
            else Text(stringResource(R.string.sync_sign_in))
        }
        else -> {
            SectionHeader(title = stringResource(R.string.sync_account), modifier = Modifier.padding(start = Spacing.md))
            ListItem(
                headline = { Text(state.status?.accountEmail ?: stringResource(R.string.sync_signed_in_as)) },
                supporting = { Text(stringResource(R.string.sync_signed_in_as)) },
                leading = { RowIcon(Icons.Rounded.AccountCircle) },
                trailing = {
                    TextButton(onClick = { confirmSignOut = true }, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.sync_sign_out))
                    }
                },
                shape = ListItemPosition.Single.toShape(),
                padding = PaddingValues(0.dp)
            )
            when (state.status?.phase) {
                SyncPhase.NEEDS_PASSPHRASE -> PassphraseSection(
                    state = state,
                    wrong = wrongPassphrase,
                    onEdit = viewModel::clearWrongPassphrase,
                    onSubmit = { viewModel.submitPassphrase(it) }
                )
                SyncPhase.NEEDS_FIRST_SYNC_CHOICE -> FirstSyncChoiceSection(
                    state = state,
                    onChoose = viewModel::chooseFirstSync,
                )
                SyncPhase.NEEDS_ACCOUNT_CHOICE -> SyncRow(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(R.string.sync_problem_title),
                    supporting = state.control?.error?.let { stringResource(R.string.onboarding_operation_failed) } ?: stringResource(R.string.sync_problem_other),
                    position = ListItemPosition.Single, error = true
                )
                else -> active()
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = HootbookDialogDefaults.containerColor,
            title = { Text(stringResource(R.string.sync_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sync_sign_out_confirm_body)) },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.sync_sign_out), onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                })
            },
            dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onClick = { confirmSignOut = false }) }
        )
    }
}

@Composable
private fun FirstSyncChoiceSection(
    state: SyncUiState,
    onChoose: (FirstSyncChoice) -> Unit,
) {
    var confirmReplaceLocal by rememberSaveable { mutableStateOf(false) }
    SectionHeader(
        title = stringResource(R.string.sync_first_sync_choice_title),
        modifier = Modifier.padding(start = Spacing.md),
    )
    Text(
        text = stringResource(R.string.sync_first_sync_choice_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        SyncRow(
            icon = Icons.Rounded.Merge,
            title = stringResource(R.string.sync_first_sync_merge),
            supporting = stringResource(R.string.sync_first_sync_merge_body),
            position = ListItemPosition.Top,
            onClick = if (state.busy) null else ({ onChoose(FirstSyncChoice.MERGE) }),
        )
        SyncRow(
            icon = Icons.Rounded.CloudDownload,
            title = stringResource(R.string.sync_first_sync_replace),
            supporting = stringResource(R.string.sync_first_sync_replace_body),
            position = ListItemPosition.Bottom,
            onClick = if (state.busy) null else ({ confirmReplaceLocal = true }),
        )
    }
    if (confirmReplaceLocal) {
        AlertDialog(
            onDismissRequest = { confirmReplaceLocal = false },
            containerColor = HootbookDialogDefaults.containerColor,
            title = { Text(stringResource(R.string.sync_first_sync_replace_confirm_title)) },
            text = { Text(stringResource(R.string.sync_first_sync_replace_confirm_body)) },
            confirmButton = {
                DialogConfirmButton(
                    stringResource(R.string.sync_first_sync_replace),
                    destructive = true,
                    onClick = {
                        confirmReplaceLocal = false
                        onChoose(FirstSyncChoice.REPLACE_LOCAL)
                    },
                )
            },
            dismissButton = {
                DialogDismissButton(
                    stringResource(R.string.cancel),
                    onClick = { confirmReplaceLocal = false },
                )
            },
        )
    }
}

/** The last sync's problem, as an error row. */
@Composable
internal fun SyncErrorKindRow(problem: SyncErrorKind) {
    SyncRow(
        icon = Icons.Rounded.ErrorOutline,
        title = stringResource(R.string.sync_problem_title),
        supporting = stringResource(problemText(problem)),
        position = ListItemPosition.Single,
        error = true
    )
}

@Composable
private fun PassphraseSection(
    state: SyncUiState,
    wrong: Boolean,
    onEdit: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val ready = passphrase.isNotEmpty()
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    SectionHeader(
        title = stringResource(R.string.sync_passphrase_enter_title),
        modifier = Modifier.padding(start = Spacing.md)
    )
    Text(
        text = stringResource(R.string.sync_passphrase_enter_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.md)
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
            Text(stringResource(R.string.sync_passphrase_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
    TextField(
        value = passphrase,
        onValueChange = { passphrase = it; onEdit() },
        label = { Text(stringResource(R.string.sync_passphrase)) },
        singleLine = true,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        isError = wrong,
        supportingText = when {
            wrong -> { { Text(stringResource(R.string.sync_passphrase_wrong)) } }
            else -> null
        },
        trailingIcon = {
            TooltipIconButton(
                icon = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                label = stringResource(if (visible) R.string.sync_passphrase_hide else R.string.sync_passphrase_show),
                onClick = { visible = !visible }
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = hootbookTextFieldColors(),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onSubmit(passphrase) },
        enabled = ready && !state.busy,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.busy) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
        else Text(stringResource(R.string.sync_passphrase_unlock))
    }
}

/** The on/off switch, and the last problem so a failure is never hidden behind the debug section. */
@Composable
private fun EnabledSection(state: SyncUiState, onEnabledChange: (Boolean) -> Unit) {
    SectionHeader(title = stringResource(R.string.sync_status), modifier = Modifier.padding(start = Spacing.md))
    val problem = state.status?.lastError
    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        PreferenceSwitch(
            title = stringResource(R.string.sync_enabled),
            subtitle = stringResource(if ((state.status?.phase == SyncPhase.PAUSED)) R.string.sync_paused_body else R.string.sync_enabled_body),
            checked = !(state.status?.phase == SyncPhase.PAUSED),
            onCheckedChange = onEnabledChange,
            leadingIcon = { RowIcon(if ((state.status?.phase == SyncPhase.PAUSED)) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync) },
            padding = PaddingValues(0.dp),
            isFirst = problem != null && !(state.status?.phase == SyncPhase.PAUSED),
            isSingle = problem == null || (state.status?.phase == SyncPhase.PAUSED)
        )
        if (problem != null && !(state.status?.phase == SyncPhase.PAUSED)) {
            SyncRow(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.sync_problem_title),
                supporting = stringResource(problemText(problem)),
                position = ListItemPosition.Bottom,
                error = true
            )
        }
    }
}

/**
 * "Status & debug info", collapsed by default: who and where (account, device, project), how far
 * (last sync, pull cursor, queued and held records), the last error, and Sync now.
 */
@Composable
private fun DebugSection(state: SyncUiState, onSyncNow: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val notSet = stringResource(R.string.sync_debug_none)
    val rows = listOf(
        R.string.sync_debug_account to listOfNotNull(state.status?.accountEmail, state.status?.accountUid).joinToString("\n").ifEmpty { notSet },
        R.string.sync_debug_device to (state.status?.deviceId ?: notSet),
        R.string.sync_last_synced to (state.status?.lastSuccess?.coreDateText() ?: stringResource(R.string.sync_never)),
        R.string.sync_debug_cursor to (state.status?.cursor?.toString() ?: notSet),
        R.string.sync_pending to state.status?.outboxCount.toString(),
        R.string.sync_debug_problem to (state.status?.lastError?.let { stringResource(problemText(it)) } ?: notSet),
        R.string.sync_debug_error to (state.status?.lastError?.name ?: notSet),
        R.string.sync_debug_protocol to state.status?.protocolVersion.toString(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        val expandLabel = stringResource(if (expanded) R.string.sync_debug_collapse else R.string.sync_debug_expand)
        ListItem(
            headline = { Text(stringResource(R.string.sync_debug_title)) },
            leading = { RowIcon(Icons.Rounded.BugReport) },
            trailing = {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = expandLabel
                )
            },
            onClick = { expanded = !expanded },
            shape = (if (expanded) ListItemPosition.Top else ListItemPosition.Single).toShape(),
            padding = PaddingValues(0.dp)
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    ListItem(
                        headline = { Text(stringResource(label)) },
                        supporting = {
                            SelectionContainer {
                                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = (if (index == rows.lastIndex) ListItemPosition.Bottom else ListItemPosition.Middle).toShape(),
                        padding = PaddingValues(0.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = onSyncNow,
                    enabled = !state.busy && state.status?.phase == SyncPhase.IDLE && !(state.status?.phase == SyncPhase.PAUSED),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.busy) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
                    else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.sync_now))
                    }
                }
            }
        }
    }
}

private fun problemText(problem: SyncErrorKind): Int = when (problem) {
    SyncErrorKind.OFFLINE -> R.string.sync_problem_network
    SyncErrorKind.REMOTE -> R.string.sync_problem_permission
    SyncErrorKind.CORRUPT, SyncErrorKind.WRONG_PASSPHRASE -> R.string.sync_problem_unreadable
    SyncErrorKind.UPGRADE_REQUIRED -> R.string.sync_problem_newer
    SyncErrorKind.NEEDS_FIRST_SYNC_CHOICE -> R.string.sync_first_sync_choice_body
    else -> R.string.sync_problem_other
}

@Composable
private fun BusyRow(text: String? = null) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(Modifier.size(32.dp))
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SyncRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    position: ListItemPosition,
    onClick: (() -> Unit)? = null,
    error: Boolean = false,
) {
    ListItem(
        headline = { Text(title) },
        supporting = {
            Text(supporting, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leading = { RowIcon(icon, error) },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@Composable
private fun RowIcon(icon: ImageVector, error: Boolean = false) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictsSection(
    conflicts: List<SyncConflict>,
    busy: Boolean,
    onReview: (SyncConflict) -> Unit,
    onOpenRecord: (Table, String) -> Unit,
    onDismiss: (List<Long>) -> Unit,
) {
    val autoResolvedIds = conflicts.filter { !it.pending }.map { it.id }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.sync_conflicts_title)} · ${conflicts.size}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (autoResolvedIds.isNotEmpty()) {
            TextButton(
                onClick = { onDismiss(autoResolvedIds) },
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.sync_conflict_dismiss_all))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        conflicts.forEach { item ->
            val summary = remember(item.mine, item.theirs, item.recordId) { conflictRecordSummary(item) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (item.pending) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("sync_conflict_${item.id}"),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${syncTableLabel(item.table)} · $summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = if (item.pending) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (item.pending) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = when {
                                    item.pending -> stringResource(R.string.sync_conflicts_pending_badge)
                                    item.applied == ConflictSide.MINE -> stringResource(R.string.sync_conflicts_auto_mine_badge)
                                    else -> stringResource(R.string.sync_conflicts_auto_theirs_badge)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "${conflictKindLabel(item.kind)} · ${item.at.coreDateText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.fields.isNotEmpty()) {
                        val secretLabel = stringResource(R.string.sync_conflict_secret_value)
                        item.fields.take(3).forEach { field ->
                            val mineVal = formatConflictFieldValue(field.column, field.mine, secretLabel)
                            val theirsVal = formatConflictFieldValue(field.column, field.theirs, secretLabel)
                            Text(
                                text = "${field.column}: $mineVal ↔ $theirsVal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                    ) {
                        if (!item.pending) {
                            TextButton(
                                onClick = { onDismiss(listOf(item.id)) },
                                enabled = !busy,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.sync_conflict_dismiss))
                            }
                        }
                        TextButton(
                            onClick = { onOpenRecord(item.table, item.recordId) },
                            enabled = !busy,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.sync_conflict_open_record))
                        }
                        FilledTonalButton(
                            onClick = { onReview(item) },
                            enabled = !busy,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.testTag("sync_conflict_review_${item.id}"),
                        ) {
                            Text(
                                stringResource(
                                    if (item.pending) R.string.sync_conflict_review
                                    else R.string.sync_conflict_review_done
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticesSection(
    notices: List<SyncNotice>,
    busy: Boolean,
    onOpenRecord: (Table, String) -> Unit,
    onDismiss: (List<Long>) -> Unit,
) {
    SectionHeader(
        title = "${stringResource(R.string.sync_notices_title)} · ${notices.size}",
        modifier = Modifier.padding(start = Spacing.md),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        notices.forEach { notice ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("sync_notice_${notice.id}"),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    val header = listOfNotNull(
                        notice.name.takeIf { it.isNotBlank() },
                        notice.last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                        notice.currency?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.sync_notice_deleted_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    notice.references.forEach { refs ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            refs.ids.forEach { id ->
                                AssistChip(
                                    onClick = { onOpenRecord(refs.table, id) },
                                    enabled = !busy,
                                    label = { Text("${syncTableLabel(refs.table)} · ${id.take(8)}") },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { onDismiss(listOf(notice.id)) },
                            enabled = !busy,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.sync_conflict_dismiss))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictPolicySection(
    policy: ConflictPolicy,
    busy: Boolean,
    onPolicyChange: (ConflictPolicy) -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.sync_policy_title),
        modifier = Modifier.padding(start = Spacing.md),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.sync_policy_both_edited),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BothEditedPolicy.entries.forEach { option ->
                    FilterChip(
                        selected = policy.bothEdited == option,
                        onClick = { if (policy.bothEdited != option) onPolicyChange(policy.copy(bothEdited = option)) },
                        enabled = !busy,
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        BothEditedPolicy.MINE -> R.string.sync_policy_option_mine
                                        BothEditedPolicy.THEIRS -> R.string.sync_policy_option_theirs
                                        BothEditedPolicy.ASK -> R.string.sync_policy_option_ask
                                    }
                                )
                            )
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                text = stringResource(R.string.sync_policy_delete_vs_edit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DeletePolicy.entries.forEach { option ->
                    FilterChip(
                        selected = policy.deleteVsEdit == option,
                        onClick = { if (policy.deleteVsEdit != option) onPolicyChange(policy.copy(deleteVsEdit = option)) },
                        enabled = !busy,
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        DeletePolicy.DELETE -> R.string.sync_policy_option_delete
                                        DeletePolicy.ASK -> R.string.sync_policy_option_ask
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictResolutionDialog(
    conflict: SyncConflict,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onResolve: (ConflictChoice) -> Unit,
) {
    val groups = remember(conflict) { conflictGroups(conflict) }
    var sides by remember(conflict) {
        mutableStateOf(
            conflict.fields.associate {
                it.column to (conflict.applied ?: ConflictSide.MINE)
            }
        )
    }
    val summary = remember(conflict.mine, conflict.theirs, conflict.recordId) { conflictRecordSummary(conflict) }
    val secretLabel = stringResource(R.string.sync_conflict_secret_value)
    val deletedLabel = stringResource(R.string.sync_conflict_deleted_value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HootbookDialogDefaults.containerColor,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sync_conflict_dialog_title))
                Text(
                    text = "${syncTableLabel(conflict.table)} · $summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "${conflictKindLabel(conflict.kind)} · ${conflict.at.coreDateText()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (conflict.kind == ConflictKind.BOTH_EDITED && groups.isNotEmpty()) {
                    groups.forEach { fields ->
                        val unitSide = sides[fields.first().column] ?: ConflictSide.MINE
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(
                                    text = fields.joinToString(" / ") { it.column },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                fields.forEach { field ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        if (fields.size > 1) {
                                            Text(
                                                text = field.column,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        field.base?.let { base ->
                                            Text(
                                                text = "${stringResource(R.string.sync_conflict_base)}: ${formatConflictFieldValue(field.column, base, secretLabel)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = "${stringResource(R.string.sync_conflict_mine)}: ${formatConflictFieldValue(field.column, field.mine, secretLabel)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (unitSide == ConflictSide.MINE) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = "${stringResource(R.string.sync_conflict_theirs)}: ${formatConflictFieldValue(field.column, field.theirs, secretLabel)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (unitSide == ConflictSide.THEIRS) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                                if (groups.size > 1) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        FilterChip(
                                            selected = unitSide == ConflictSide.MINE,
                                            onClick = {
                                                sides = sides + fields.associate { it.column to ConflictSide.MINE }
                                            },
                                            enabled = enabled,
                                            label = { Text(stringResource(R.string.sync_conflict_keep_mine)) },
                                        )
                                        FilterChip(
                                            selected = unitSide == ConflictSide.THEIRS,
                                            onClick = {
                                                sides = sides + fields.associate { it.column to ConflictSide.THEIRS }
                                            },
                                            enabled = enabled,
                                            label = { Text(stringResource(R.string.sync_conflict_keep_theirs)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = stringResource(R.string.sync_conflict_mine),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = conflict.mine?.let { summarizeJsonRow(it) } ?: deletedLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.sync_conflict_theirs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = conflict.theirs?.let { summarizeJsonRow(it) } ?: deletedLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = { onResolve(ConflictChoice.Mine) },
                        enabled = enabled,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.sync_conflict_keep_mine))
                    }
                    OutlinedButton(
                        onClick = { onResolve(ConflictChoice.Theirs) },
                        enabled = enabled,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.sync_conflict_keep_theirs))
                    }
                }
                if (conflict.kind == ConflictKind.BOTH_EDITED && groups.size > 1) {
                    Button(
                        onClick = { onResolve(ConflictChoice.PerField(sides)) },
                        enabled = enabled,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.sync_conflict_apply_per_field))
                    }
                }
            }
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun syncTableLabel(table: Table): String = stringResource(
    when (table) {
        Table.TRANSACTIONS -> R.string.sync_table_transactions
        Table.ACCOUNTS -> R.string.sync_table_accounts
        Table.BUDGETS -> R.string.sync_table_budgets
        Table.SUBSCRIPTIONS -> R.string.sync_table_subscriptions
        Table.DEBTS -> R.string.sync_table_debts
        Table.PEOPLE -> R.string.sync_table_people
        Table.CATEGORIES -> R.string.sync_table_categories
        Table.SUBCATEGORIES -> R.string.sync_table_subcategories
        Table.TAGS -> R.string.sync_table_tags
        Table.QUICK_TEMPLATES -> R.string.sync_table_quick_templates
        Table.AI_SETTINGS -> R.string.sync_table_ai_settings
        Table.BROKERAGE_CONNECTIONS -> R.string.sync_table_brokerage_connections
        Table.SETTINGS -> R.string.sync_table_settings
        else -> R.string.sync_firebase_title
    }
)

@Composable
private fun conflictKindLabel(kind: ConflictKind): String = stringResource(
    when (kind) {
        ConflictKind.BOTH_EDITED -> R.string.sync_conflict_kind_both_edited
        ConflictKind.EDITED_DELETED -> R.string.sync_conflict_kind_edited_deleted
        ConflictKind.DELETED_EDITED -> R.string.sync_conflict_kind_deleted_edited
    }
)

private fun conflictRecordSummary(conflict: SyncConflict): String {
    val source = conflict.mine ?: conflict.theirs
    if (source != null) {
        try {
            val obj = JSONObject(source)
            val label = obj.optString("name").ifBlank {
                obj.optString("label").ifBlank {
                    obj.optString("note").ifBlank {
                        obj.optString("model")
                    }
                }
            }
            val amount = obj.optString("amount")
            val currency = obj.optString("currency")
            val parts = buildList {
                if (label.isNotBlank()) add(label)
                if (amount.isNotBlank()) add(if (currency.isNotBlank()) "$amount $currency" else amount)
            }
            if (parts.isNotEmpty()) return parts.joinToString(" · ")
        } catch (_: Exception) {
        }
    }
    return conflict.recordId.take(12)
}

private fun summarizeJsonRow(raw: String): String = try {
    val obj = JSONObject(raw)
    val keys = obj.keys().asSequence().toList()
        .filterNot { it in setOf("id", "created_at", "updated_at") }
    keys.joinToString("\n") { key -> "$key: ${obj.opt(key)}" }.ifBlank { raw }
} catch (_: Exception) {
    raw
}

private fun formatConflictFieldValue(column: String, value: String?, secretLabel: String): String {
    if (value.isNullOrBlank()) return "—"
    if (column == "key_hash" || column == "cred_hash") {
        return "$secretLabel (${value.take(8)}…)"
    }
    return value
}

/** Credential Manager shows its sheet over an Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
