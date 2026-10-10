package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.ui.res.stringResource

import io.github.hootkit_dev.hootbook.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
fun DateRangePickerDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: (startDate: LocalDate, endDate: LocalDate) -> Unit,
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    // Convert LocalDate to milliseconds using epoch day to avoid timezone issues
    // Using epoch day (days since 1970-01-01) ensures consistent date handling across timezones
    val initialStartMillis = initialStartDate?.toEpochDay()?.let { it * 86400000L }
    val initialEndMillis = initialEndDate?.toEpochDay()?.let { it * 86400000L }

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )


    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogConfirmButton(
                text = "OK",
                onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis

                    if (startMillis != null && endMillis != null) {
                        // Convert milliseconds to LocalDate using epoch day to avoid timezone issues
                        val startDate = LocalDate.ofEpochDay(startMillis / 86400000L)
                        val endDate = LocalDate.ofEpochDay(endMillis / 86400000L)

                        // Validate date range (should always be true with Material DateRangePicker, but be defensive)
                        if (startDate <= endDate) {
                            onConfirm(startDate, endDate)
                        }
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null &&
                        dateRangePickerState.selectedEndDateMillis != null
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
        },
        colors = DatePickerDefaults.colors(
            containerColor = HootbookDialogDefaults.containerColor,
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier,
                title = {
                    Text(
                        text = stringResource(R.string.select_date_range),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp)
                    )
                },
                headline = {
                    DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                        selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                        displayMode = dateRangePickerState.displayMode,

                        dateFormatter = DatePickerDefaults.dateFormatter(),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 12.dp)
                    )
                },
                showModeToggle = true, // Allow switching between calendar and text input
                colors = DatePickerDefaults.colors(
                    containerColor = HootbookDialogDefaults.containerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurface,
                    subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    currentYearContentColor = MaterialTheme.colorScheme.primary,
                    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledSelectedDayContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    disabledSelectedDayContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}
