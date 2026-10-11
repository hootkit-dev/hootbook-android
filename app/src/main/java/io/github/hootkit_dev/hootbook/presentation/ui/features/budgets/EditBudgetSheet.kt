@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.budgetProgressFraction

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis

import androidx.compose.ui.platform.LocalContext
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.icons.CategoryMapping
import io.github.hootkit_dev.hootbook.utils.titlecaseFirst
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.BudgetAnimatedGradientMeshCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteBudgetDialog
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.History
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Wallet3
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditBudgetSheet(
    budgetState: EditBudgetState,
    saving: Boolean = false,
    error: String? = null,
    categories: List<Category>,
    subcategoriesMap: Map<String, List<Subcategory>> = emptyMap(),
    allAccounts: List<Account> = emptyList(),
    onAmountChange: (BigDecimal) -> Unit,
    onNameChange: (String) -> Unit,
    onStartDateChange: (LocalDateTime) -> Unit,
    onEndDateChange: (LocalDateTime) -> Unit,
    onPeriodTypeChange: (BudgetPeriod) -> Unit,
    onBudgetKindChange: (BudgetKind) -> Unit,
    onAccountIdsChange: (List<String>?) -> Unit,
    onColorChange: (String) -> Unit,
    onAddCategoryLimit: (String, BigDecimal) -> Unit,
    onRemoveCategoryLimit: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    blurEffects: Boolean
) {
    var showNumberPad by remember { mutableStateOf(false) }
    var editingCategoryLimit by remember { mutableStateOf<String?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pendingCategoryName by remember { mutableStateOf<String?>(null) }
    var showTypeInfoSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = budgetState.startDate.toLocalDate().toPickerMillis()
        )
        DatePicker(
            onDismiss = { showStartDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let {
                    onStartDateChange(pickerDate(it).atStartOfDay())
                }
                showStartDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = budgetState.endDate.toLocalDate().toPickerMillis()
        )
        DatePicker(
            onDismiss = { showEndDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let {
                    onEndDateChange(pickerDate(it).atStartOfDay())
                }
                showEndDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showDeleteConfirmation) {
        DeleteBudgetDialog(
            onDismiss = { showDeleteConfirmation = false },
            onDelete = {
                onDelete?.invoke()
                showDeleteConfirmation = false
            },
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Account Selection Sheet
    if (showAccountSheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AccountMultiSelectionSheet(
                allAccounts = allAccounts,
                selectedAccountIds = budgetState.accountIds,
                onSelectionChange = onAccountIdsChange,
                onDismiss = { showAccountSheet = false }
            )
        }
    }

    // Color Picker Sheet
    if (showColorPicker) {
        HootbookModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box{
                ColorPickerContent(
                    initialColor = budgetState.color.toColorInt(),
                    onColorChanged = { colorInt ->
                        onColorChange(String.format("#%06X", 0xFFFFFF and colorInt))
                        showColorPicker = false
                    },
                    showTitle = true
                )
            }
        }
    }

    // Number pad for budget amount
    if (showNumberPad && editingCategoryLimit == null && pendingCategoryName == null) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = budgetState.amount.toString(),
                onDone = { value ->
                    onAmountChange(value.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                    showNumberPad = false
                },
                title = if (budgetState.isNewBudget) stringResource(R.string.set_budget_amount) else stringResource(R.string.update_budget_amount)
            )
        }
    }

    // Number pad for category limit
    if (showNumberPad && (editingCategoryLimit != null || pendingCategoryName != null)) {
        val categoryName = editingCategoryLimit ?: pendingCategoryName ?: ""
        val existingLimit = budgetState.categoryLimits.find { it.categoryId == categoryName }

        HootbookModalBottomSheet(
            onDismissRequest = {
                showNumberPad = false
                editingCategoryLimit = null
                pendingCategoryName = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = existingLimit?.amount?.toString() ?: "0",
                onDone = { value ->
                    val amount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (amount > BigDecimal.ZERO) {
                        onAddCategoryLimit(categoryName, amount)
                    }
                    showNumberPad = false
                    editingCategoryLimit = null
                    pendingCategoryName = null
                },
                title = stringResource(R.string.set_limit_for_format, categories.find { it.id == categoryName }?.displayName.orEmpty())
            )
        }
    }

    // Category selection sheet
    if (showCategorySheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            CategorySelectionSheet(
                categories = categories.filter { it.kind == CategoryKind.EXPENSE },
                subcategoriesMap = subcategoriesMap,
                onSelectionComplete = { category, _ ->
                    showCategorySheet = false
                    pendingCategoryName = category.id
                    showNumberPad = true
                },
                onDismiss = { showCategorySheet = false }
            )
        }
    }

    if (showTypeInfoSheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showTypeInfoSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            BudgetTypeSelectionSheet(
                onTypeSelected = {
                    scope.launch {
                        sheetState.hide()
                        onBudgetKindChange(it)
                        showTypeInfoSheet = false
                    }
                },
                onDismiss = { showTypeInfoSheet = false }
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth().hazeSource(hazeState)) {
        Column(
            modifier = Modifier
                .imePadding()
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .verticalScroll(
                    state = rememberScrollState()
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = if (budgetState.isNewBudget) stringResource(R.string.create_budget) else stringResource(R.string.edit_budget),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Bold
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            // Budget Preview Card
            BudgetPreviewCard(
                name = budgetState.name.ifBlank { budgetState.getDefaultName() },
                amount = budgetState.amount,
                startDate = budgetState.startDate,
                endDate = budgetState.endDate,
                currency = budgetState.currency,
                budgetType = budgetState.budgetType,
                color = Color(budgetState.color.toColorInt())
            )

            // Periodic Type Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.budget_period),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BudgetPeriod.entries.filter { it != BudgetPeriod.UNKNOWN }.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = budgetState.periodType == period,
                            onClick = { onPeriodTypeChange(period) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = BudgetPeriod.entries.filter { it != BudgetPeriod.UNKNOWN }.size),
                            colors = SegmentedButtonDefaults.colors(
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveBorderColor = Color.Transparent,
                                activeBorderColor = Color.Transparent
                            ),
                            icon = {},
                            label = {
                                Text(
                                    stringResource(period.labelRes()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }
            }

            // Date Range Display/Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                 Surface(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.starts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = budgetState.startDate.format(DateFormats.fullDateFormatter()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (budgetState.periodType == BudgetPeriod.CUSTOM) {
                    Surface(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.ends), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = budgetState.endDate.format(DateFormats.fullDateFormatter()),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Budget Type and Tracking
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Budget Type
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.type_info),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { showTypeInfoSheet = true },
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = budgetState.budgetType == BudgetKind.EXPENSE,
                            onClick = { onBudgetKindChange(BudgetKind.EXPENSE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveBorderColor = Color.Transparent,
                                activeBorderColor = Color.Transparent
                            ),
                            label = { Text(stringResource(R.string.expense), style = MaterialTheme.typography.labelSmall) }
                        )
                        SegmentedButton(
                            selected = budgetState.budgetType == BudgetKind.SAVINGS,
                            onClick = { onBudgetKindChange(BudgetKind.SAVINGS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveBorderColor = Color.Transparent,
                                activeBorderColor = Color.Transparent
                            ),
                            label = { Text(stringResource(R.string.savings), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Accounts Selection
            Surface(
                onClick = { showAccountSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.accounts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (budgetState.accountIds == null) stringResource(R.string.all_accounts)
                                   else stringResource(R.string.accounts_selected_format, budgetState.accountIds?.size ?: 0),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }

            // Stats/Amount and Color Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Budget Amount Input
                Surface(
                    onClick = {
                        editingCategoryLimit = null
                        pendingCategoryName = null
                        showNumberPad = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Iconax.Wallet3, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = CurrencyFormatter.formatCurrency(budgetState.amount, budgetState.currency),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Color Picker Trigger
                Surface(
                    onClick = { showColorPicker = true },
                    modifier = Modifier.width(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.color), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(budgetState.color.toColorInt()))
                        )
                    }
                }
            }

            // Budget Name Input
            TextField(
                value = budgetState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.budget_name_optional), fontWeight = FontWeight.SemiBold) },
                placeholder = { Text(budgetState.getDefaultName()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = { Icon(Iconax.Edit2, contentDescription = null) }
            )

            // Category Limits Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.category_limits),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { showCategorySheet = true }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_limit))
                        }
                    }

                    if (budgetState.categoryLimits.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_category_limits_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.sm)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        budgetState.categoryLimits.forEach { limit ->
                            val category = categories.find { it.id == limit.categoryId }


                            val iconRes = category?.let { io.github.hootkit_dev.hootbook.utils.IconResolutionUtils.nameToResId(LocalContext.current, it.icon) } ?: R.drawable.type_stationary_clipboard
                            val colorHex = category?.color ?: "#91CC4D"

                            CategoryLimitItem(
                                categoryName = categories.find { it.id == limit.categoryId }?.displayName.orEmpty(),
                                limitAmount = limit.amount,
                                currency = budgetState.currency,
                                iconResId = iconRes,
                                backgroundColor = Color(colorHex.toColorInt()),
                                onEdit = {
                                    editingCategoryLimit = limit.categoryId
                                    showNumberPad = true
                                },
                                onRemove = { onRemoveCategoryLimit(limit.categoryId) }
                            )

                            if (limit != budgetState.categoryLimits.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = Spacing.xs),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button (only for existing budgets)
                if (onDelete != null && !budgetState.isNewBudget) {
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.error
                                )
                            )
                        )
                    ) {
                        Icon(
                            imageVector = Iconax.Bag,
                            contentDescription = stringResource(R.string.delete_budget)
                        )
                    }
                }

                // Save button
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !saving && budgetState.amount > BigDecimal.ZERO,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = if (budgetState.isNewBudget) stringResource(R.string.create_budget) else stringResource(R.string.save_changes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLimitItem(
    categoryName: String,
    limitAmount: BigDecimal,
    currency: String,
    iconResId: Int,
    backgroundColor: Color,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(backgroundColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.Unspecified
                )
            }

            Column {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(limitAmount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRemove) {
            Icon(
                imageVector = Iconax.Bag,
                contentDescription = "Remove limit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BudgetPreviewCard(
    name: String,
    amount: BigDecimal,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    currency: String,
    budgetType: BudgetKind,
    color: Color
) {
    val isSavings = budgetType == BudgetKind.SAVINGS
    BudgetAnimatedGradientMeshCard(
        budgetColor = color
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Icon
                Icon(
                    imageVector = Icons.Rounded.Api,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Budget Name
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // History Icon Placeholder
                IconButton(
                    onClick = { },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Iconax.History,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Main Content Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isSavings) stringResource(R.string.saved_goal) else stringResource(R.string.spend_limit),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(amount, currency).replace(".00", ""),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))


        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountMultiSelectionSheet(
    allAccounts: List<Account>,
    selectedAccountIds: List<String>?,
    onSelectionChange: (List<String>?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sortedAccounts = allAccounts
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.link_accounts),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(Spacing.md)),
                contentPadding = PaddingValues(horizontal = Spacing.md)
            ) {
                item {
                    Surface(
                        onClick = { onSelectionChange(null) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(Spacing.md),
                        color = if (selectedAccountIds == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.5f
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AccountBalance, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.all_accounts),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedAccountIds == null) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(sortedAccounts) { account ->
                    val accountId = account.id
                    val isSelected = selectedAccountIds?.contains(accountId) == true

                    AccountRow(
                        account = account,
                        selected = isSelected,
                        showBalance = false,
                        modifier = Modifier.padding(vertical = 2.dp),
                        onClick = {
                            onSelectionChange(if (isSelected) selectedAccountIds.orEmpty() - accountId else selectedAccountIds.orEmpty() + accountId)
                        },
                        trailing = {
                            HootbookCheckbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    onSelectionChange(if (checked) selectedAccountIds.orEmpty() + accountId else selectedAccountIds.orEmpty() - accountId)
                                }
                            )
                        }
                    )
                }
                item{Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
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
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

/** What the period chips call each [BudgetPeriod]. */
internal fun BudgetPeriod.labelRes(): Int = when (this) {
    BudgetPeriod.CUSTOM -> R.string.budget_period_custom
    BudgetPeriod.DAILY -> R.string.budget_period_daily
    BudgetPeriod.WEEKLY -> R.string.budget_period_weekly
    BudgetPeriod.MONTHLY -> R.string.budget_period_monthly
    BudgetPeriod.YEARLY -> R.string.budget_period_yearly
    BudgetPeriod.UNKNOWN -> R.string.budget_period_custom
}
