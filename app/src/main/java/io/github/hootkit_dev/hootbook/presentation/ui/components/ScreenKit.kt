@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R

/** Padding of a secondary screen's scrolling content: 16 dp margins, room for a FAB below. */
val ScreenContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)

/**
 * Room a FAB drawn by the host (outside this screen's Scaffold) takes at the bottom end. Snackbars
 * of [SecondaryScreen] sit above it, so the FAB never covers their action.
 */
val LocalHostFabClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Space between the sections of a screen. */
val SectionSpacing = 24.dp

/**
 * A screen with a large flexible app bar that collapses as the content scrolls, the screen's
 * actions, and at most one FAB. [onBack] null means a top-level tab without a back button;
 * [backIcon] replaces the back arrow (a close ✕ to leave selection mode, for example).
 */
@Composable
fun SecondaryScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector? = null,
    backLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val large = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(large.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = title,
                scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
                scrollBehaviorLarge = large,
                hasBackButton = onBack != null,
                navigationContent = {
                    if (onBack != null) {
                        if (backIcon != null) TooltipIconButton(backIcon, backLabel ?: stringResource(R.string.cd_navigate_back), onBack)
                        else NavigationContent(onBack)
                    }
                },
                actionContent = { Row(verticalAlignment = Alignment.CenterVertically) { actions() } },
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { Box(Modifier.padding(bottom = LocalHostFabClearance.current)) { snackbarHost() } },
        content = content,
    )
}

/**
 * The screen's add action: an extended FAB with an add icon. The label is also set as the button's
 * description, because the Material 3 extended FAB keeps its text out of the accessibility tree.
 */
@Composable
fun AddFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.ExtendedFloatingActionButton(onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        icon = { Icon(androidx.compose.material.icons.Icons.Rounded.Add, null) }, text = { Text(label) })
}

/** An error the user can dismiss, with an optional recovery action. */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    NoticeCard(message, modifier, error = true) {
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel) }
        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.close)) }
    }
}

/**
 * A message that needs attention, in a tonal card with its actions at the bottom end: a state
 * the user can act on (secondary), or a problem (error).
 */
@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    error: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, contentColor = content) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = if (actions == null) 16.dp else 4.dp)) {
            title?.let { Text(it, Modifier.padding(end = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium) }
            Text(text, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (actions != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** A confirmation dialog: text dismiss button, filled confirm button (error-colored when destructive). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        containerColor = HootbookDialogDefaults.containerColor,
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss, enabled = enabled) },
        confirmButton = { DialogConfirmButton(confirmLabel, onConfirm, destructive = destructive, enabled = enabled && confirmEnabled) },
    )
}

/** One entry of an [OverflowMenu]. */
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** The ⋮ button: secondary actions of a screen or a row, kept out of the content. */
@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TooltipIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more_options), { open = true }, enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(action.label, color = color) },
                    leadingIcon = action.icon?.let { { Icon(it, null, tint = color) } },
                    enabled = action.enabled,
                    onClick = { open = false; action.onClick() },
                )
            }
        }
    }
}

/**
 * A form in a modal bottom sheet: headline, scrolling fields, then Cancel, an optional
 * [extraAction] and a filled confirm button. While [busy] the sheet cannot be dismissed and its buttons are disabled.
 */
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    busy: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmLabel: String = stringResource(R.string.save),
    error: String? = null,
    titleAction: (@Composable () -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    HootbookModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            titleAction?.invoke()
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            content()
        }
        Row(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
            extraAction?.invoke()
            Button(onClick = onConfirm, enabled = !busy && confirmEnabled, shapes = ButtonDefaults.shapes()) { Text(confirmLabel) }
        }
    }
}

/** A non-interactive status, such as Default, Archived or Deleted. */
@Composable
fun StatusLabel(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Surface(modifier, shape = MaterialTheme.shapes.small,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * A single choice from a list, in a modal bottom sheet: one row per option, the selected one
 * checked. Picking an option reports it and closes the sheet.
 */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    supporting: ((T) -> String?)? = null,
) {
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            ListSection {
                options.forEach { (value, label) ->
                    val detail = supporting?.invoke(value)
                    item(headline = { Text(label) }, supporting = detail?.let { { Text(it) } }, selected = value == selected,
                        trailing = if (value == selected) ({ Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = if (enabled) { { onSelect(value); onDismiss() } } else null)
                }
            }
        }
    }
}
