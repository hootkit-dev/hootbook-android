@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.DateFormats
import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A single-line outlined field. [decimal] brings up the number keyboard for amounts. */
@Composable
fun FormTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    decimal: Boolean = false,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, enabled = enabled,
        singleLine = true, isError = isError, supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = if (decimal) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A day, picked from the Material date picker and shown as 9月1日 / Sep 1. [value] is ISO
 * (2026-09-01) or blank; [emptyText] says what blank means (for example "None").
 */
@Composable
fun DateField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowClear: Boolean = true,
    emptyText: String = "",
) {
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    var picking by remember { mutableStateOf(false) }
    PickerField(
        text = date?.let { DateFormats.shortDate(it) } ?: emptyText, label = label, enabled = enabled,
        icon = { Icon(Icons.Rounded.Event, null) },
        onClear = if (allowClear && date != null) ({ onValueChange("") }) else null,
        onClick = { picking = true }, modifier = modifier,
    )
    if (picking) DayPicker(date, { picking = false }) { onValueChange(it.toString()) }
}

/**
 * A date and time: the date picker, then the time picker. [value] is an ISO local date-time or
 * blank; [emptyText] says what blank means (for example "Now").
 */
@Composable
fun DateTimeField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowClear: Boolean = true,
    emptyText: String = "",
) {
    val dateTime = runCatching { LocalDateTime.parse(value) }.getOrNull()
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    PickerField(
        text = dateTime?.let { DateFormats.shortDate(it.toLocalDate()) + " " + DateFormats.time(it) } ?: emptyText,
        label = label, enabled = enabled, icon = { Icon(Icons.Rounded.Schedule, null) },
        onClear = if (allowClear && dateTime != null) ({ onValueChange("") }) else null,
        onClick = { pickingDate = true }, modifier = modifier,
    )
    if (pickingDate) DayPicker(dateTime?.toLocalDate(), { pickingDate = false }) { pickedDay = it }
    pickedDay?.let { day ->
        val initial = dateTime?.toLocalTime() ?: LocalTime.now()
        val clock = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            containerColor = HootbookDialogDefaults.containerColor,
            text = { TimePicker(clock) },
            dismissButton = { DialogDismissButton(stringResource(R.string.cancel), { pickedDay = null }) },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.ok), {
                    onValueChange(day.atTime(clock.hour, clock.minute).toString()); pickedDay = null
                })
            },
        )
    }
}

@Composable
private fun DayPicker(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).toPickerMillis(), yearRange = 1900..2200)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.ok), {
                state.selectedDateMillis?.let { onPicked(pickerDate(it)) }; onDismiss()
            }, enabled = state.selectedDateMillis != null)
        },
    ) { DatePicker(state) }
}

/** A read-only outlined field that opens a picker when tapped. */
@Composable
private fun PickerField(
    text: String,
    label: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClear: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label) }, leadingIcon = icon,
            trailingIcon = onClear?.let { clear ->
                { TooltipIconButton(Icons.Rounded.Clear, stringResource(R.string.ui_clear), clear, enabled = enabled) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // The field itself is read-only; this layer makes it one button. It stops short of the
        // clear button so that stays reachable.
        Box(Modifier.matchParentSize().padding(end = if (onClear != null) 56.dp else 0.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick))
    }
}

/** One choice from a short list: a read-only outlined field with the Material exposed dropdown. */
@Composable
fun <T> SelectField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text(label) }, supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, text) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}
