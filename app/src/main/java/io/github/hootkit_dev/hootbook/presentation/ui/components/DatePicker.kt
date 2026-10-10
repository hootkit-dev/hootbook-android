package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalHazeApi::class)
@Composable
fun DatePicker(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    datePickerState: DatePickerState,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogConfirmButton(
                text = "OK",
                onClick = onConfirm
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        colors = DatePickerDefaults.colors(
            containerColor = HootbookDialogDefaults.containerColor,
        ),
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = HootbookDialogDefaults.containerColor
            )
        )
    }
}