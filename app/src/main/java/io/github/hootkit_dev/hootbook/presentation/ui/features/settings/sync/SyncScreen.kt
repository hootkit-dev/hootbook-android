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


/**
 * Backup & sync → Firebase sync: Google sign-in, the on/off switch, the sync passphrase, the
 * first-sync choice and a collapsed status and debug section (docs/sync.md). Core supplies the current setup phase.
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
                if (state.status != null && state.status?.phase != SyncPhase.UNAVAILABLE) {
                    DebugSection(state, onSyncNow = { viewModel.syncNow() })
                }
            }
        }
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

/** Credential Manager shows its sheet over an Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
