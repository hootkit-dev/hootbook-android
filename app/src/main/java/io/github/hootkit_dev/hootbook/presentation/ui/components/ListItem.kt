package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

enum class ListItemPosition {
    Top,
    Middle,
    Bottom,
    Single;

    companion object {
        fun from(index: Int, size: Int) =
            if (size == 1) Single
            else
                when (index) {
                    0 -> Top
                    size - 1 -> Bottom
                    else -> Middle
                }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listTopItemShape: CornerBasedShape
    @Composable
    get() {
        val shapes = MaterialTheme.shapes
        return androidx.compose.runtime.remember(shapes) {
            shapes.largeIncreased.copy(
                bottomStart = shapes.small.bottomStart,
                bottomEnd = shapes.small.bottomEnd
            )
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listMiddleItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.small

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listBottomItemShape: CornerBasedShape
    @Composable
    get() {
        val shapes = MaterialTheme.shapes
        return androidx.compose.runtime.remember(shapes) {
            shapes.largeIncreased.copy(
                topStart = shapes.small.topStart,
                topEnd = shapes.small.topEnd
            )
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listSingleItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.largeIncreased

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListItemPosition.toShape(): CornerBasedShape {
    val shapes = MaterialTheme.shapes
    return androidx.compose.runtime.remember(shapes, this) {
        when (this) {
            ListItemPosition.Top -> shapes.largeIncreased.copy(
                bottomStart = shapes.small.bottomStart,
                bottomEnd = shapes.small.bottomEnd
            )
            ListItemPosition.Middle -> shapes.small
            ListItemPosition.Bottom -> shapes.largeIncreased.copy(
                topStart = shapes.small.topStart,
                topEnd = shapes.small.topEnd
            )
            ListItemPosition.Single -> shapes.largeIncreased
        }
    }
}

val listItemPadding = PaddingValues(horizontal = 16.dp, vertical = 1.5.dp)

@Composable
fun ListItem(
        headline: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        supporting: (@Composable () -> Unit)? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        selected: Boolean = false,
        shape: CornerBasedShape? = listSingleItemShape,
        padding: PaddingValues = listItemPadding,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        listColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        selectedListColor: Color = MaterialTheme.colorScheme.primaryContainer,
        // A row that is one switch: the whole row toggles, announced as a switch
        toggled: Boolean? = null,
        onToggle: ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .then(
                if (shape != null) {
                    Modifier
                        .clip(shape)
                        .background(
                            if (selected) selectedListColor
                            else listColor
                        )
                } else Modifier
            )
            .then(
                if (toggled != null && onToggle != null) {
                    Modifier.toggleable(value = toggled, role = Role.Switch, onValueChange = onToggle)
                } else if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        role = Role.Button,
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leading != null) {
                CompositionLocalProvider(
                        LocalContentColor provides
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.secondary
                ) { Box(contentAlignment = Alignment.Center) { leading() } }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompositionLocalProvider(
                        LocalContentColor provides
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                ) { ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) { headline() } }

                if (supporting != null) {
                    CompositionLocalProvider(
                            LocalContentColor provides
                                    if (selected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                    alpha = 0.7f
                                            )
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                            supporting()
                        }
                    }
                }
            }

            if (trailing != null) {
                CompositionLocalProvider(
                        LocalContentColor provides
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                ) { Box(contentAlignment = Alignment.Center) { trailing() } }
            }
        }
    }
}
