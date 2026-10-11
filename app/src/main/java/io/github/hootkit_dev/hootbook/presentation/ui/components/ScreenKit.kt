@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

/** Space between the sections of a screen. */
val SectionSpacing = 24.dp

/**
 * A secondary screen: a large flexible app bar that collapses as the content scrolls, a back
 * button, the screen's actions, and at most one FAB. [content] receives the scaffold padding.
 */
@Composable
fun SecondaryScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
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
                hasBackButton = true,
                navigationContent = { NavigationContent(onBack) },
                actionContent = { Row(verticalAlignment = Alignment.CenterVertically) { actions() } },
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
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
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(message, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (actionLabel != null && onAction != null) TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel) }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.close)) }
            }
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
 * A form in a modal bottom sheet: headline, scrolling fields, then Cancel and a filled confirm
 * button. While [busy] the sheet cannot be dismissed and its buttons are disabled.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    HootbookModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(title, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
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
            Button(onClick = onConfirm, enabled = !busy && confirmEnabled, shapes = ButtonDefaults.shapes()) { Text(confirmLabel) }
        }
    }
}
