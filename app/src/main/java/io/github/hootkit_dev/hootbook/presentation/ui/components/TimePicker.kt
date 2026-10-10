package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
fun TimePicker(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    timePickerState: TimePickerState,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surface.copy(0.7f),
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surface.copy(0.7f),
                    )
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.ok),
                onClick = onConfirm
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        containerColor = HootbookDialogDefaults.containerColor,
    )
}