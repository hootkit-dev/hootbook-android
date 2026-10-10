package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity

/**
 * Stops scroll and fling that a list inside the sheet did not consume from reaching the sheet's
 * own drag state. Without this, a fast fling that hits the end of the content hands its leftover
 * velocity to the sheet, which then runs a settle animation and visibly jolts. The drag handle
 * and the scrim still dismiss the sheet.
 */
private object SheetScrollIsolation : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Drop-in replacement for [ModalBottomSheet] used throughout the app. Same parameters; the
 * content is wrapped so its scrolling never drives the sheet (see [SheetScrollIsolation]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HootbookModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 0.dp,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        dragHandle = dragHandle
    ) {
        Column(modifier = Modifier.nestedScroll(SheetScrollIsolation)) {
            content()
        }
    }
}
