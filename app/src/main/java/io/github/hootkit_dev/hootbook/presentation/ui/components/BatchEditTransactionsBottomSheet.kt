@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Category
import dev.hootkit.core.Subcategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Folder2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.icons.VideoTime
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CoreMoneyPresentation
import dev.chrisbanes.haze.HazeState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BatchEditTransactionsBottomSheet(
    selectedIdsCount: Int,
    categories: Map<String, Category>,
    subcategoriesMap: Map<String, List<Subcategory>>,
    tags: List<dev.hootkit.core.Tag> = emptyList(),
    removableTags: List<dev.hootkit.core.Tag> = tags,
    onDismiss: () -> Unit,
    onApply: (
        newDate: LocalDate?,
        newTime: LocalTime?,
        newCategory: String?,
        newSubcategory: String?,
        newAmount: BigDecimal?,
        newNote: String?,
        addTagIds: List<String>,
        removeTagIds: List<String>
    ) -> Unit,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var updateDate by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var updateTime by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf(LocalTime.now()) }
    var showTimePicker by remember { mutableStateOf(false) }

    var updateCategory by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }

    var updateAmount by remember { mutableStateOf(false) }
    var selectedAmount by remember { mutableStateOf<BigDecimal?>(null) }
    var showNumberPad by remember { mutableStateOf(false) }

    var updateNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    var updateTags by remember { mutableStateOf(false) }
    var addTagIds by remember { mutableStateOf(setOf<String>()) }
    var removeTagIds by remember { mutableStateOf(setOf<String>()) }

    val hasAnySelection = updateDate || updateTime || updateCategory || updateAmount || updateNote || (updateTags && (addTagIds.isNotEmpty() || removeTagIds.isNotEmpty()))
    val lazyListState = rememberLazyListState()

    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(bottom = 0.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Header
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Iconax.Edit2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.batch_edit_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.batch_edit_updating_format, selectedIdsCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    DashedLine(
                        modifier = Modifier,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Date Card
                item {
                    BatchEditOptionCard(
                        title = stringResource(R.string.batch_edit_date),
                        icon = Iconax.Calendar,
                        checked = updateDate,
                        onCheckedChange = {
                            updateDate = it
                            if (it && selectedDate == LocalDate.now()) {
                                showDatePicker = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDate.format(DateFormats.fullDateFormatter()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDatePicker = true }) {
                                Text(stringResource(R.string.batch_edit_pick_date))
                            }
                        }
                    }
                }

                // Time Card
                item {
                    BatchEditOptionCard(
                        title = stringResource(R.string.batch_edit_time),
                        icon = Iconax.VideoTime,
                        checked = updateTime,
                        onCheckedChange = {
                            updateTime = it
                            if (it) {
                                showTimePicker = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .clickable { showTimePicker = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showTimePicker = true }) {
                                Text(stringResource(R.string.batch_edit_pick_time))
                            }
                        }
                    }
                }

                // Category Card
                item {
                    BatchEditOptionCard(
                        title = stringResource(R.string.batch_edit_category_subcategory),
                        icon = Iconax.Folder2,
                        checked = updateCategory,
                        onCheckedChange = {
                            updateCategory = it
                            if (it && selectedCategory == null) {
                                showCategorySheet = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .clickable { showCategorySheet = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedCategory?.displayName ?: stringResource(R.string.batch_edit_select_category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedCategory != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selectedSubcategory != null) {
                                    Text(
                                        text = selectedSubcategory?.displayName.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showCategorySheet = true }) {
                                Text(stringResource(R.string.batch_edit_choose))
                            }
                        }
                    }
                }

                // Amount Card
                item {
                    BatchEditOptionCard(
                        title = stringResource(R.string.batch_edit_amount),
                        icon = Iconax.ReceiptItem,
                        checked = updateAmount,
                        onCheckedChange = {
                            updateAmount = it
                            if (it && selectedAmount == null) {
                                showNumberPad = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .clickable { showNumberPad = true }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedAmount?.let { CoreMoneyPresentation.formatAmount(it) }
                                    ?: "0.00",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedAmount != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showNumberPad = true }) {
                                Text(stringResource(R.string.batch_edit_set_amount))
                            }
                        }
                    }
                }

                // Note Card
                item {
                    BatchEditOptionCard(
                        title = stringResource(R.string.batch_edit_note_description),
                        icon = Iconax.DocumentText2,
                        checked = updateNote,
                        onCheckedChange = { updateNote = it }
                    ) {
                        TextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text(stringResource(R.string.batch_edit_note_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 3
                        )
                    }
                }

                // Tags Card
                if (tags.isNotEmpty() || removableTags.isNotEmpty()) {
                    item {
                        BatchEditOptionCard(
                            title = stringResource(R.string.core_tags_title),
                            icon = Iconax.Folder2,
                            checked = updateTags,
                            onCheckedChange = { updateTags = it }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                if (tags.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.core_tags_bulk_add),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        tags.forEach { tag ->
                                            val selected = tag.id in addTagIds
                                            androidx.compose.material3.FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    if (selected) {
                                                        addTagIds = addTagIds - tag.id
                                                    } else {
                                                        addTagIds = addTagIds + tag.id
                                                        removeTagIds = removeTagIds - tag.id
                                                    }
                                                },
                                                label = { Text(tag.name) }
                                            )
                                        }
                                    }
                                }
                                if (removableTags.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.core_tags_bulk_remove),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        removableTags.forEach { tag ->
                                            val selected = tag.id in removeTagIds
                                            androidx.compose.material3.FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    if (selected) {
                                                        removeTagIds = removeTagIds - tag.id
                                                    } else {
                                                        removeTagIds = removeTagIds + tag.id
                                                        addTagIds = addTagIds - tag.id
                                                    }
                                                },
                                                label = { Text(tag.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }


            }
            // Action Buttons at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Apply Button
                Button(
                    onClick = {
                        onApply(
                            if (updateDate) selectedDate else null,
                            if (updateTime) selectedTime else null,
                            if (updateCategory) selectedCategory?.id else null,
                            if (updateCategory) selectedSubcategory?.id else null,
                            if (updateAmount) selectedAmount else null,
                            if (updateNote) noteText else null,
                            if (updateTags) addTagIds.toList() else emptyList(),
                            if (updateTags) removeTagIds.toList() else emptyList()
                        )
                        onDismiss()
                    },
                    enabled = hasAnySelection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (hasAnySelection) stringResource(R.string.batch_edit_apply_format, selectedIdsCount) else stringResource(R.string.batch_edit_check_options),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // DatePicker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toPickerMillis()
        )
        DatePicker(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    selectedDate = pickerDate(millis)
                }
                showDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // TimePicker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        TimePicker(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                showTimePicker = false
            },
            timePickerState = timePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Category Selection Sheet
    if (showCategorySheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CategorySelectionSheet(
                categories = categories.values.toList(),
                subcategoriesMap = subcategoriesMap,
                onSelectionComplete = { category, subcategory ->
                    selectedCategory = category
                    selectedSubcategory = subcategory
                    showCategorySheet = false
                },
                onDismiss = { showCategorySheet = false }
            )
        }
    }

    // NumberPad for Amount
    if (showNumberPad) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = selectedAmount?.toPlainString() ?: "0",
                onDone = { newAmount: String ->
                    selectedAmount = newAmount.toBigDecimalOrNull()
                    showNumberPad = false
                },
                title = stringResource(R.string.batch_edit_set_batch_amount)
            )
        }
    }
}

@Composable
private fun BatchEditOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (checked) 1.5.dp else 1.dp,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
            )
            .padding(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                HootbookSwitch(checked = checked, onCheckedChange = onCheckedChange)
            }

            if (checked) {
                Box(modifier = Modifier.padding(top = Spacing.xs)) {
                    content()
                }
            }
        }
    }
}
