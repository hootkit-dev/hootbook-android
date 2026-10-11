package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import io.github.hootkit_dev.hootbook.utils.DateFormats
import io.github.hootkit_dev.hootbook.utils.pickerDate

import androidx.compose.material3.ripple
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.*
import dev.hootkit.core.CategoryKind
import dev.hootkit.core.PersonRelation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AttachmentSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.VideoPlay
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.EntryMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import java.time.ZoneId
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomBillingCycleCard
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.icons.VideoTime
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit,
    isTransitioning: Boolean = false,
    blurEffects: Boolean,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val draft = state.subscription
    val categories = state.categories.values.toList()
    val accounts = state.accounts.values.toList()
    val selectedAccount = state.accounts[draft.accountId]
    val allSubcategories = state.categories.mapValues { it.value.subcategories }
    val selectedCategoryObj = state.categories[draft.categoryId]
    val selectedSubcategoryObj2 = selectedCategoryObj?.subcategories?.find { it.id == draft.subcategoryId }
    val nextDate = draft.nextDue?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()) }
    val endDate = draft.endDate?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showNumberPad by remember { mutableStateOf(false) }
    var showCustomUnitMenu by remember { mutableStateOf(false) }
    var showCustomCountPad by remember { mutableStateOf(false) }
    var showCustomEndDatePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Stored as keys, shown translated: a saved label would not be understood when computing dates
    val billingCycles = entryCycleKeys
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // Handle keyboard properly
                .verticalScroll(
                    state = scrollState,
                    enabled = !isTransitioning
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Error Card
            state.error?.let { errorMessage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Iconax.Information,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.subscription_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Amount Input
            AmountInput(
                amount = state.subscriptionAmount.ifEmpty { "0" },
                currencySymbol = CurrencyFormatter.getCurrencySymbol(
                    draft.amount.currency
                ),
                onClick = {
                    showNumberPad = true
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Billing Cycle Dropdown and Date Selection
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Billing Cycle Dropdown
                    ExposedDropdownMenuBox(
                        expanded = showBillingCycleMenu,
                        onExpandedChange = { showBillingCycleMenu = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = if (entryCycleKey(draft.cycle).isBlank()) "" else entryCycleLabel(entryCycleKey(draft.cycle)),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.billing_cycle_label)) },
                            leadingIcon = {
                                Icon(
                                    Iconax.VideoTime,
                                    contentDescription = null
                                )
                            },
                            shape = RoundedCornerShape(Spacing.md),
                            modifier =
                                Modifier.weight(1f)
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            isError = false,
                            singleLine = true,
                            supportingText = null,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            ),
                        )

                        ExposedDropdownMenu(
                            expanded = showBillingCycleMenu,
                            onDismissRequest = { showBillingCycleMenu = false },
                            shape = MaterialTheme.shapes.large,
                        ) {
                            billingCycles.forEachIndexed { index, cycle ->
                                val isFirstItem = index == 0
                                val isLastItem = index == billingCycles.lastIndex
                                val isMiddleItem = !isFirstItem && !isLastItem
                                DropdownMenuItem(
                                    text = { Text(entryCycleLabel(cycle)) },
                                    onClick = {
                                        viewModel.updateSubscriptionBillingCycle(cycle)
                                        showBillingCycleMenu = false
                                    }
                                )
                                // Add a Spacer for middle items
                                if (isMiddleItem || (isFirstItem && billingCycles.size > 2)) {
                                    HorizontalDivider(
                                        thickness = 1.5.dp,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                    // Date Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(Dimensions.Radius.md)
                            )
                            .padding(8.dp)
                            .clickable(
                                onClick = { showDatePicker = true },
                                indication = ripple(),
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val themeColors = MaterialTheme.colorScheme
                            Icon(
                                imageVector = Iconax.Calendar,
                                contentDescription = stringResource(R.string.date_picker_desc),
                                tint = themeColors.onSurface
                            )
                            Spacer(Modifier.size(8.dp))

                            val dateLabel =
                                nextDate?.format(DateFormats.monthDayFormatter())
                            val yearLabel =
                                nextDate?.format(DateTimeFormatter.ofPattern("yyyy"))
                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = yearLabel.orEmpty(),

                                    textAlign = TextAlign.Start,
                                    color = themeColors.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = dateLabel ?: "—",

                                    textAlign = TextAlign.Start,
                                    color = themeColors.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                        }
                    }
                }

                // Custom Billing Cycle Card
                BlurredAnimatedVisibility(
                    visible = state.customCycle,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }) {
                    CustomBillingCycleCard(
                        count = draft.cycle.every.toInt(),
                        unit = draft.cycle.unit.name.lowercase(),
                        endDate = endDate,
                        onCountClick = { showCustomCountPad = true },
                        onUnitSelected = { viewModel.updateSubscriptionCustomCycleUnit(it) },
                        onEndDateClick = { showCustomEndDatePicker = true },
                        onClearEndDate = { viewModel.updateSubscriptionCustomCycleEndDate(null) },
                        shape = RoundedCornerShape(Spacing.md)
                    )
                }
            }

            // Accounts Section
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                AccountField(
                    account = selectedAccount,
                    onClick = { showAccountSheet = true },
                    placeholder = stringResource(R.string.select_account),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                )

                // Category Selection
                val categoryInteractionSource = remember { MutableInteractionSource() }
                TextField(
                    value = selectedCategoryObj?.displayName.orEmpty(),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.category_label), fontWeight = FontWeight.SemiBold) },
                    readOnly = true,
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(
                                interactionSource = categoryInteractionSource,
                                indication = ripple()
                            ) {
                                showCategoryMenu = true
                            },
                    shape =
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        ),
                    leadingIcon = {
                        val context = LocalContext.current
                        val resolvedResId = remember(selectedCategoryObj) {
                            selectedCategoryObj?.let { cat ->
                                if (!cat.icon.isNullOrEmpty()) {
                                    val res = IconResolutionUtils.nameToResId(context, cat.icon)
                                    if (res != 0) res else 0
                                } else 0
                            } ?: 0
                        }

                        if (resolvedResId != 0) {
                            Icon(
                                painter = painterResource(id = resolvedResId),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Iconax.Box2, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    },
                    isError = false,
                    supportingText = null,
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Subcategory Display
                if (draft.subcategoryId != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    TextField(
                        value = state.categories[draft.categoryId]?.subcategories?.find { it.id == draft.subcategoryId }?.displayName ?: stringResource(R.string.none_label),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.subcategory_label)) },
                        leadingIcon = {
                                val context = LocalContext.current
                                val resolvedResId = remember(selectedSubcategoryObj2) {
                                    selectedSubcategoryObj2?.let { sub ->
                                        if (!sub.icon.isNullOrEmpty()) {
                                            val res = IconResolutionUtils.nameToResId(context, sub.icon)
                                            if (res != 0) res else 0
                                        } else 0
                                    } ?: 0
                                }

                                if (resolvedResId != 0) {
                                    Icon(
                                        painter = painterResource(id = resolvedResId),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null
                                    )
                                }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = if (selectedSubcategoryObj2 != null) {
                            val color = try {
                                Color(selectedSubcategoryObj2.color.toColorInt())
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            TextFieldDefaults.colors(
                                focusedContainerColor = color.copy(alpha = 0.2f),
                                unfocusedContainerColor = color.copy(alpha = 0.2f),
                                disabledContainerColor = color.copy(alpha = 0.2f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    0.7f
                                ),
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    0.7f
                                ),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                disabledIndicatorColor = Color.Transparent,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }

            if (showAccountSheet) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showAccountSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    AccountSelectionSheet(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAccountSelected = { account ->
                            viewModel.updateSubscriptionAccount(account)
                            showAccountSheet = false
                        },
                        isTransitioning = isTransitioning,
                        showNoneOption = false
                    )
                }
            }

            // NumberPad for Amount Input
            if (showNumberPad) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showNumberPad = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    NumberPad(
                        initialValue = state.subscriptionAmount.ifEmpty { "0" },
                        onDone = { newAmount ->
                            viewModel.updateSubscriptionAmount(newAmount)
                            showNumberPad = false
                        },
                        title = stringResource(R.string.enter_amount)
                    )
                }
            }

            // NumberPad for Custom Cycle Count
            if (showCustomCountPad) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showCustomCountPad = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    NumberPad(
                        initialValue = draft.cycle.every.toInt().toString(),
                        onDone = { newCount ->
                            viewModel.updateSubscriptionCustomCycleCount(newCount.toIntOrNull() ?: 1)
                            showCustomCountPad = false
                        },
                        title = stringResource(R.string.enter_interval_title)
                    )
                }
            }

            // Category Selection Sheet
            if (showCategoryMenu) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showCategoryMenu = false },
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    CategorySelectionSheet(
                        categories = categories.filter { !it.hidden && it.kind == CategoryKind.EXPENSE },
                        subcategoriesMap = allSubcategories,
                        onSelectionComplete = { category, subcategory ->
                            viewModel.updateSubscriptionCategory(category.id)
                            viewModel.updateSubscriptionSubcategory(subcategory?.id)
                            showCategoryMenu = false
                        },
                        onDismiss = { showCategoryMenu = false }
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Service Name Input
                TextField(
                    value = draft.name,
                    onValueChange = viewModel::updateSubscriptionService,
                    label = { Text(stringResource(R.string.service_name_required)) },
                    placeholder = { Text(stringResource(R.string.service_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        ),
                    leadingIcon = { Icon(Iconax.VideoPlay, contentDescription = null) },
                    isError = false,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    ),
                    supportingText = null,
                )

                // Notes/Description (Optional)
                TextField(
                    value = draft.notes.orEmpty(),
                    onValueChange = viewModel::updateSubscriptionNotes,
                    label = { Text(stringResource(R.string.notes_optional)) },
                    leadingIcon = { Icon(Iconax.DocumentText2, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    ),
                )
            }

            // Attachments Section


            // Bottom padding for keyboard
            Spacer(modifier = Modifier.height(80.dp))
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
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { viewModel.saveSubscription(onSuccess = onSave) },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimensions.Padding.content)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(56.dp),
                shapes = ButtonDefaults.shapes(),
                enabled = state.subscriptionAmount.toBigDecimalOrNull() != null && !(state.writing || state.loadingSource),
            ) {
                if ((state.writing || state.loadingSource)) {
                    LoadingCircle(modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (nextDate ?: java.time.LocalDate.now())
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
            )

        DatePicker(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    viewModel.updateSubscriptionNextPaymentDate(millis)
                }
                showDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Custom End Date Picker Dialog
    if (showCustomEndDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (endDate ?: LocalDate.now())
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
            )

        DatePicker(
            onDismiss = { showCustomEndDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val localDate = pickerDate(millis)
                    viewModel.updateSubscriptionCustomCycleEndDate(localDate)
                }
                showCustomEndDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState,
            // Add a "Forever" option if the DatePicker supports it, or just use a button elsewhere.
            // For now, let's assume confirm sets the date.
            // I'll add a way to clear it later if needed.
        )
    }
}
