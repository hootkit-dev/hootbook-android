package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A titled group of rows drawn as one segmented surface: the Material 3 Expressive list the
 * Settings screen uses, shared so every screen groups its content the same way. Rows get their
 * top / middle / bottom shape from their position, so callers never pass shapes.
 */
@Composable
fun ListSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: ListSectionScope.() -> Unit,
) {
    val rows = ListSectionScope().apply(content).rows
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) SectionTitle(title, action = action)
        rows.forEachIndexed { index, row -> row(ListItemPosition.from(index, rows.size).toShape()) }
    }
}

class ListSectionScope internal constructor() {
    internal val rows = mutableListOf<@Composable (CornerBasedShape) -> Unit>()

    /** A row drawn by the caller with the group's shape for its position. */
    fun row(content: @Composable (shape: CornerBasedShape) -> Unit) {
        rows += content
    }

    /** A standard row. [onClick] makes it a button; [chevron] marks one that opens another page. */
    fun item(
        headline: @Composable () -> Unit,
        supporting: (@Composable () -> Unit)? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        chevron: Boolean = false,
        selected: Boolean = false,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) = row { shape ->
        ListItem(
            headline = headline, supporting = supporting, leading = leading,
            trailing = if (chevron) ({ ChevronTrailing(trailing) }) else trailing,
            selected = selected, shape = shape, padding = PaddingValues(0.dp),
            onClick = onClick, modifier = modifier,
        )
    }
}

@Composable
private fun ChevronTrailing(value: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        value?.invoke()
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A section heading: title-small in primary, announced as a heading, with an optional action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
        action?.invoke()
    }
}
