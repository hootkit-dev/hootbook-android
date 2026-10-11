package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A stable 40dp slot avoids two visibility/blur layout trees on every transaction row. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TransactionLeadingSlot(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Crossfade(
        targetState = isSelectionMode,
        modifier = modifier.size(40.dp),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "transactionLeadingSelection"
    ) { selectionMode ->
        if (selectionMode) {
            HootbookCheckbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            icon()
        }
    }
}
