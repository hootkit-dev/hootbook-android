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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.hootkit_dev.hootbook.data.sync.SyncManager
import io.github.hootkit_dev.hootbook.data.sync.SyncManager.Stage
import io.github.hootkit_dev.hootbook.data.sync.SyncProblem
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

private const val MIN_PASSPHRASE = 8

/**
 * Backup & sync → Firebase sync: Google sign-in, the on/off switch, the sync passphrase, the
 * first-sync choice and a collapsed status and debug section (docs/sync.md). The F-Droid build
 * only says sync is unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrongPassphrase by viewModel.wrongPassphrase.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
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
                if (state.stage != Stage.UNAVAILABLE) {
                    DebugSection(state, onSyncNow = { viewModel.syncNow() })
                }
            }
        }
    }
}

/** Shows [SyncViewModel.messages] as snackbars. */
@Composable
internal fun SyncMessagesEffect(viewModel: SyncViewModel, snackbar: SnackbarHostState) {
    val synced = stringResource(R.string.sync_done)
    val failed = stringResource(R.string.sync_failed)
    val signInFailedFormat = stringResource(R.string.sync_sign_in_failed_detail)
    val signInCancelled = stringResource(R.string.sync_sign_in_cancelled)
    val merged = stringResource(R.string.sync_merged)
    val replacedFormat = stringResource(R.string.sync_replaced)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    SyncMessage.Synced -> synced
                    SyncMessage.Failed -> failed
                    is SyncMessage.SignInFailed -> signInFailedFormat.format(message.detail)
                    SyncMessage.SignInCancelled -> signInCancelled
                    SyncMessage.Merged -> merged
                    is SyncMessage.Replaced -> replacedFormat.format(message.backupName)
                }
            )
        }
    }
}

/**
 * The steps that turn sync on: Google sign-in, then the account (with sign-out), the passphrase
 * and the first-sync choice; [active] once sync is set up. Shared by this page and onboarding,
 * inside a column.
 */
@Composable
internal fun SyncSetupSection(
    state: SyncManager.State,
    wrongPassphrase: Boolean,
    viewModel: SyncViewModel,
    active: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showChoice by rememberSaveable { mutableStateOf(true) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    when (state.stage) {
        Stage.UNAVAILABLE -> SyncRow(
            icon = Icons.Rounded.CloudOff,
            title = stringResource(R.string.sync_unavailable_title),
            supporting = stringResource(R.string.sync_unavailable_body),
            position = ListItemPosition.Single
        )
        Stage.SIGNED_OUT -> Button(
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
                headline = { Text(state.email ?: stringResource(R.string.sync_signed_in_as)) },
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
            when (state.stage) {
                Stage.PASSPHRASE -> PassphraseSection(
                    state = state,
                    wrong = wrongPassphrase,
                    onEdit = viewModel::clearWrongPassphrase,
                    onSubmit = { viewModel.submitPassphrase(it) },
                    onRetry = { viewModel.refresh() }
                )
                Stage.CHOICE -> {
                    SyncRow(
                        icon = Icons.Rounded.Merge,
                        title = stringResource(R.string.sync_choice_pending),
                        supporting = stringResource(R.string.sync_choice_body),
                        position = ListItemPosition.Single,
                        onClick = { showChoice = true }
                    )
                    if (showChoice && !state.busy) {
                        ChoiceDialog(
                            onMerge = { showChoice = false; viewModel.merge() },
                            onReplace = { showChoice = false; viewModel.replaceLocal() },
                            onDismiss = { showChoice = false }
                        )
                    }
                    if (state.busy) BusyRow()
                }
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

/** The last sync's problem, as an error row. */
@Composable
internal fun SyncProblemRow(problem: SyncProblem) {
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
    state: SyncManager.State,
    wrong: Boolean,
    onEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val creating = state.passphraseExists == false
    if (state.passphraseExists == null) {
        if (state.problem != null && !state.busy) {
            SyncRow(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.sync_problem_title),
                supporting = stringResource(problemText(state.problem)),
                position = ListItemPosition.Single,
                onClick = onRetry,
                error = true
            )
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_retry))
            }
        } else {
            BusyRow(stringResource(R.string.sync_checking))
        }
        return
    }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val tooShort = creating && passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE
    val mismatch = creating && repeat.isNotEmpty() && repeat != passphrase
    val ready = passphrase.isNotEmpty() && (!creating || (passphrase.length >= MIN_PASSPHRASE && repeat == passphrase))
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    SectionHeader(
        title = stringResource(if (creating) R.string.sync_passphrase_create_title else R.string.sync_passphrase_enter_title),
        modifier = Modifier.padding(start = Spacing.md)
    )
    Text(
        text = stringResource(if (creating) R.string.sync_passphrase_create_body else R.string.sync_passphrase_enter_body),
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
    OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it; onEdit() },
        label = { Text(stringResource(R.string.sync_passphrase)) },
        singleLine = true,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (creating) ImeAction.Next else ImeAction.Done),
        isError = wrong || tooShort,
        supportingText = when {
            wrong -> { { Text(stringResource(R.string.sync_passphrase_wrong)) } }
            tooShort -> { { Text(pluralStringResource(R.plurals.sync_passphrase_too_short, MIN_PASSPHRASE, MIN_PASSPHRASE)) } }
            else -> null
        },
        trailingIcon = {
            TooltipIconButton(
                icon = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                label = stringResource(if (visible) R.string.sync_passphrase_hide else R.string.sync_passphrase_show),
                onClick = { visible = !visible }
            )
        },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth()
    )
    if (creating) {
        OutlinedTextField(
            value = repeat,
            onValueChange = { repeat = it },
            label = { Text(stringResource(R.string.sync_passphrase_confirm)) },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            isError = mismatch,
            supportingText = if (mismatch) { { Text(stringResource(R.string.sync_passphrase_mismatch)) } } else null,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Button(
        onClick = { onSubmit(passphrase) },
        enabled = ready && !state.busy,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.busy) LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
        else Text(stringResource(if (creating) R.string.sync_passphrase_set else R.string.sync_passphrase_unlock))
    }
}

/** The on/off switch, and the last problem so a failure is never hidden behind the debug section. */
@Composable
private fun EnabledSection(state: SyncManager.State, onEnabledChange: (Boolean) -> Unit) {
    SectionHeader(title = stringResource(R.string.sync_status), modifier = Modifier.padding(start = Spacing.md))
    val problem = state.problem
    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        PreferenceSwitch(
            title = stringResource(R.string.sync_enabled),
            subtitle = stringResource(if (state.paused) R.string.sync_paused_body else R.string.sync_enabled_body),
            checked = !state.paused,
            onCheckedChange = onEnabledChange,
            leadingIcon = { RowIcon(if (state.paused) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync) },
            padding = PaddingValues(0.dp),
            isFirst = problem != null && !state.paused,
            isSingle = problem == null || state.paused
        )
        if (problem != null && !state.paused) {
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
private fun DebugSection(state: SyncManager.State, onSyncNow: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val notSet = stringResource(R.string.sync_debug_none)
    val rows = listOf(
        R.string.sync_debug_account to listOfNotNull(state.email, state.uid).joinToString("\n").ifEmpty { notSet },
        R.string.sync_debug_device to (state.deviceId ?: notSet),
        R.string.sync_last_synced to if (state.lastSyncAt > 0) {
            DateFormats.dayTime(Instant.ofEpochMilli(state.lastSyncAt).atZone(ZoneId.systemDefault()))
        } else stringResource(R.string.sync_never),
        R.string.sync_debug_cursor to (state.cursor ?: notSet),
        R.string.sync_pending to state.pending.toString(),
        R.string.sync_debug_held to state.held.toString(),
        R.string.sync_debug_problem to (state.problem?.let { stringResource(problemText(it)) } ?: notSet),
        R.string.sync_debug_error to (state.lastError ?: notSet),
        R.string.sync_debug_project to (state.projectId ?: notSet),
        R.string.sync_debug_protocol to state.protocolVersion.toString(),
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
                    enabled = !state.busy && state.stage == Stage.ACTIVE && !state.paused,
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

private fun problemText(problem: SyncProblem): Int = when (problem) {
    SyncProblem.NETWORK -> R.string.sync_problem_network
    SyncProblem.PERMISSION -> R.string.sync_problem_permission
    SyncProblem.UNREADABLE -> R.string.sync_problem_unreadable
    SyncProblem.NEWER_VERSION -> R.string.sync_problem_newer
    SyncProblem.OTHER -> R.string.sync_problem_other
}

@Composable
private fun ChoiceDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HootbookDialogDefaults.containerColor,
        title = { Text(stringResource(R.string.sync_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                Text(
                    stringResource(R.string.sync_choice_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
                SyncRow(
                    icon = Icons.Rounded.Merge,
                    title = stringResource(R.string.sync_choice_merge),
                    supporting = stringResource(R.string.sync_choice_merge_body),
                    position = ListItemPosition.Top,
                    onClick = onMerge
                )
                SyncRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = stringResource(R.string.sync_choice_replace),
                    supporting = stringResource(R.string.sync_choice_replace_body),
                    position = ListItemPosition.Bottom,
                    onClick = onReplace
                )
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.sync_choice_later), onClick = onDismiss) }
    )
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

/** Credential Manager shows its sheet over an Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
