@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis

import io.github.hootkit_dev.hootbook.presentation.ui.components.AddCurrencyConfirmation
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCurrencyChoice
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.rounded.Bolt

import androidx.compose.material3.Switch

import androidx.compose.material3.AssistChip

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.*
import dev.hootkit.core.CategoryKind
import dev.hootkit.core.PersonRelation
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.core.graphics.toColorInt
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AttachmentSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.TimePicker
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Shop
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import io.github.hootkit_dev.hootbook.utils.horizontalFadingEdge
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit,
    isTransitioning: Boolean = false,
    blurEffects: Boolean,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val draft = state.transaction
    val categories = state.categories.values.toList()
    val persons = state.people.values.toList()
    val accounts = state.accounts.values.toList()
    val selectedAccount = state.accounts[draft.accountId]
    val targetAccount = state.accounts[draft.toAccountId]
    val selectedCategoryObj = state.categories[draft.categoryId]
    val transactionSubcategories = selectedCategoryObj?.subcategories.orEmpty()
    val selectedSubcategoryObj = transactionSubcategories.find { it.id == draft.subcategoryId }
    val transactionAttachments = draft.attachments
    val allSubcategories = state.categories.mapValues { it.value.subcategories }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(draft) { viewModel.checkDuplicates() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showNumberPad by remember { mutableStateOf(false) }

    // A template without an amount (tapped here, or from a shortcut or the widget) asks for it
    val amountPadRequested = state.amountPadRequested
    LaunchedEffect(amountPadRequested) {
        if (amountPadRequested) {
            showNumberPad = true
            viewModel.onAmountPadOpened()
        }
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .fillMaxSize()
                .imePadding() // Handle keyboard properly
                .verticalScroll(
                    state = scrollState,
                    enabled = !isTransitioning
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Saved and suggested templates. A tap fills the form and, unless the template
            // carries the amount, opens the number pad for it.
            val quickTemplates = state.templates
            val suggestedTemplates = state.suggestions
            if (quickTemplates.isNotEmpty() || suggestedTemplates.isNotEmpty()) {
                TemplateRow(
                    templates = quickTemplates,
                    suggested = suggestedTemplates,
                    categories = categories,
                    onSelect = viewModel::applyQuickTemplate,
                    onSuggestedSelect = viewModel::applySuggestedTemplate,
                    onPin = viewModel::pinSuggestedTemplate,
                    onDismiss = viewModel::dismissSuggestedTemplate
                )
            }

            // Amount Input
            AmountInput(
                amount = state.amount.ifEmpty { "0" },
                currencySymbol = CoreMoneyPresentation.getCurrencySymbol(draft.currency.orEmpty()),
                onClick = {
                    showNumberPad = true
                },
                modifier = Modifier.fillMaxWidth()
            )

            // An account holding several currencies: which one the money is in
            val sourceCurrencies = remember(selectedAccount) { viewModel.currenciesOf(selectedAccount) }
            if (sourceCurrencies.size > 1) {
                AccountCurrencyChoice(
                    currencies = sourceCurrencies,
                    selected = draft.currency.orEmpty(),
                    onSelect = viewModel::updateTransactionCurrency
                )
            }

            // Transaction Type Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.transaction_type_required),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { type ->
                        FilterChip(
                            selected = draft.kind == type,
                            onClick = { viewModel.updateTransactionType(type) },
                            label = {
                                Text(stringResource(type.transactionKindLabelRes()))
                            },
                            leadingIcon =
                                if (draft.kind == type) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderWidth = 0.dp,
                                selected = draft.kind == type,
                                enabled = true
                            ),
                        )
                    }
                }
            }
            // Date and Time Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            draft.dateTime.toJavaDateTime().format(DateFormats.monthDayFormatter())
                        val yearLabel =
                            draft.dateTime.toJavaDateTime().format(DateTimeFormatter.ofPattern("yyyy"))
                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = yearLabel,

                                textAlign = TextAlign.Start,
                                color = themeColors.primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = dateLabel,

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


                // Time Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .clickable { showTimePicker = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        val hour = draft.dateTime.toJavaDateTime().hour
                        val minute = draft.dateTime.toJavaDateTime().minute
                        Box(modifier = Modifier
                            .padding(5.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = String.format("%02d", hour),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(5.dp)
                            )
                        }

                        Text(
                            text = ":",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Box(
                            modifier = Modifier
                                .padding(5.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                text = String.format("%02d", minute),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                    }
                }
            }

            // Person Selection for Lent / Borrowed
            BlurredAnimatedVisibility(
                draft.kind == TransactionKind.LOAN_OUT ||
                    draft.kind == TransactionKind.LOAN_IN
            ) {
                var showAddPersonSheet by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_person),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val personLazyListState = rememberLazyListState()
                    LazyRow(
                        state = personLazyListState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalFadingEdge(
                                canScrollBackward = personLazyListState.canScrollBackward,
                                canScrollForward = personLazyListState.canScrollForward
                            )
                    ) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAddPersonSheet = true }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.add_person),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.add_new),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(persons, key = { it.id }) { person ->
                            val isSelected = state.personId == person.id
                            val colorInt = try {
                                android.graphics.Color.parseColor(person.color)
                            } catch (_: Exception) {
                                android.graphics.Color.parseColor("#4CAF50")
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateTransactionPersonId(person.id) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (person.avatar != null) {
                                        AsyncImage(
                                            model = person.avatar?.let(viewModel.attachmentStore::resolve),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = person.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(56.dp).padding(top = 4.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (showAddPersonSheet) {
                    AddEditPersonSheet(
                        attachmentStore = viewModel.attachmentStore,
                        saving = state.writing, error = state.error,
                        onDismiss = { if (!state.writing) showAddPersonSheet = false },
                        onSave = { name, phone, notes, color, avatar, category ->
                            viewModel.addPerson(name, phone, notes, color, avatar, category) {
                                showAddPersonSheet = false
                            }
                        }
                    )
                }
            }

            // Accounts Section

            var showAccountSheet by remember { mutableStateOf(false) }
            var showTargetAccountSheet by remember { mutableStateOf(false) }
            var pendingTargetCurrency by remember { mutableStateOf<String?>(null) }

            // Conditional UI based on transaction type
            BlurredAnimatedVisibility(draft.kind == TransactionKind.TRANSFER) {
                // Transfer Type UI: Source Account + Exchange Icon + Target Account + Category
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            // Source Account Card
                            AccountField(
                                account = selectedAccount,
                                onClick = { showAccountSheet = true },
                                placeholder = stringResource(R.string.select_source_account),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                            )

                            // Target Account Card
                            AccountField(
                                account = targetAccount,
                                onClick = { showTargetAccountSheet = true },
                                placeholder = stringResource(R.string.select_target_account),
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            )
                        }
                        // Exchange Icon
                        Box(
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .shadow(elevation = 3.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = stringResource(R.string.transfer_desc),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Into an account in another currency: what arrives there
                    // Which of the target's currencies it goes into; one it lacks can be added
                    val target = targetAccount
                    if (target != null) {
                        val targetCurrencies = remember(target) { viewModel.currenciesOf(target) }
                        val missing = draft.currency.orEmpty().takeIf { it !in targetCurrencies }
                        if (targetCurrencies.size > 1 || missing != null) {
                            if (missing != null) {
                                Text(
                                    text = stringResource(R.string.transfer_target_lacks_currency, target.name, missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AccountCurrencyChoice(
                                currencies = targetCurrencies,
                                selected = draft.toCurrency,
                                onSelect = viewModel::updateTransactionTargetCurrency,
                                suggested = missing,
                                onAddSuggested = { pendingTargetCurrency = missing }
                            )
                        }
                    }
                    pendingTargetCurrency?.let { currency ->
                        AddCurrencyConfirmation(
                            currency = currency,
                            onConfirm = {
                                targetAccount?.let { viewModel.addCurrencyToAccount(it, currency, asTarget = true) }
                                pendingTargetCurrency = null
                            },
                            onDismiss = { pendingTargetCurrency = null }
                        )
                    }

                    val targetCurrency = draft.toCurrency
                    if (targetCurrency != null && targetCurrency != draft.currency.orEmpty()) {
                        TextField(
                            value = state.targetAmount,
                            onValueChange = viewModel::updateTargetAmount,
                            label = {
                                Text(stringResource(R.string.transfer_received_amount, targetCurrency), fontWeight = FontWeight.SemiBold)
                            },
                            supportingText = {
                                Text(stringResource(R.string.transfer_received_amount_hint, draft.currency.orEmpty(), targetCurrency))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))


                }
            }
            BlurredAnimatedVisibility(draft.kind != TransactionKind.TRANSFER){
                // Non-Transfer Type UI: Original layout with connected sections
                Column(
                    modifier = Modifier.animateContentSize().fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    AccountField(
                        account = selectedAccount,
                        onClick = { showAccountSheet = true },
                        placeholder = stringResource(R.string.select_account),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    )

                    if (draft.kind == TransactionKind.EXPENSE || draft.kind == TransactionKind.INCOME) {
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
                        enabled = false, // Disable typing, handle click above
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

                    // Subcategory Display (Read-only, selected via sheet)
                    if (draft.subcategoryId != null) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        TextField(
                            value = state.categories[draft.categoryId]?.subcategories?.find { it.id == draft.subcategoryId }?.displayName ?: stringResource(R.string.none_label),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.subcategory_label)) },
                            leadingIcon = {
                                val context = LocalContext.current
                                val resolvedResId = remember(selectedSubcategoryObj) {
                                    selectedSubcategoryObj?.let { sub ->
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
                            colors = if (selectedSubcategoryObj != null) {
                                val color = try {
                                    Color(selectedSubcategoryObj.color.toColorInt())
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
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
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
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    disabledIndicatorColor = Color.Transparent,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                    }
                }
            }

            // Show subcategory for Transfer type outside the conditional
            if (showAccountSheet) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showAccountSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    AccountSelectionSheet(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAccountSelected = {
                            viewModel.updateTransactionAccount(it)
                            showAccountSheet = false
                        },
                        isTransitioning = isTransitioning,
                        showNoneOption = false
                    )
                }
            }

            // Target Account BottomSheet (for Transfer type)
            if (showTargetAccountSheet) {
                HootbookModalBottomSheet(
                    onDismissRequest = { showTargetAccountSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    // Filter out the source account from target selection
                    // The source itself only when it holds another currency to exchange into
                    val availableTargetAccounts = accounts.filter {
                        it.id != selectedAccount?.id || viewModel.currenciesOf(it).size > 1
                    }
                    AccountSelectionSheet(
                        accounts = availableTargetAccounts,
                        selectedAccount = targetAccount,
                        title = stringResource(R.string.select_target_account),
                        onAccountSelected = {
                            viewModel.updateTransactionTargetAccount(it)
                            showTargetAccountSheet = false
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
                    sheetState = sheetState ,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    NumberPad(
                        initialValue = state.amount.ifEmpty { "0" },
                        onDone = { newAmount ->
                            viewModel.updateTransactionAmount(newAmount)
                            showNumberPad = false
                        },
                        title = stringResource(R.string.enter_amount)
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
                        categories = categories.filter { !it.hidden && it.kind == if (draft.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE },
                        subcategoriesMap = allSubcategories,
                        onSelectionComplete = { category, subcategory ->
                            viewModel.updateTransactionCategory(category.id)
                            viewModel.updateTransactionSubcategory(subcategory?.id)
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
                BlurredAnimatedVisibility(
                    draft.kind != TransactionKind.LOAN_OUT &&
                        draft.kind != TransactionKind.LOAN_IN
                ) {
                // Merchant Name Input (optional), completed from earlier entries
                var merchantFocused by remember { mutableStateOf(false) }
                val merchantMatches = state.merchants
                Column {
                TextField(
                    value = draft.merchant,
                    onValueChange = viewModel::updateTransactionMerchant,
                    label = { Text(stringResource(R.string.merchant_optional), fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { merchantFocused = it.isFocused },
                    shape =
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        ),
                    leadingIcon = { Icon(Iconax.Shop, contentDescription = null) },
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
                if (merchantFocused && merchantMatches.isNotEmpty()) {
                    MerchantSuggestionRow(
                        suggestions = merchantMatches,
                        onSelect = viewModel::applyMerchantSuggestion
                    )
                }
                }
                }

                // Save this entry as a quick-add template (not for transfers / loans)
                if (draft.kind != TransactionKind.TRANSFER &&
                    draft.kind != TransactionKind.LOAN_OUT &&
                    draft.kind != TransactionKind.LOAN_IN
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .toggleable(
                                value = state.saveAsTemplate,
                                role = Role.Switch,
                                onValueChange = viewModel::updateSaveAsQuickTemplate
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.quick_template_save_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        HootbookSwitch(checked = state.saveAsTemplate, onCheckedChange = null)
                    }
                }

                // Notes/Description (Optional)
                TextField(
                    value = draft.notes.orEmpty(),
                    onValueChange = viewModel::updateTransactionNotes,
                label = { Text(stringResource(R.string.notes_optional), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.fillMaxWidth(),
                shape = if (draft.kind == TransactionKind.LOAN_OUT ||
                    draft.kind == TransactionKind.LOAN_IN
                ) RoundedCornerShape(16.dp) else
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    leadingIcon = {
                        Icon(Iconax.DocumentText2, contentDescription = null)
                    },
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
            AttachmentSection(
                attachments = transactionAttachments,
                attachmentStore = viewModel.attachmentStore,
                onAddAttachment = viewModel::addTransactionAttachment,
                onRemoveAttachment = viewModel::removeTransactionAttachment,

                isEditable = true
            )

            state.error?.let { message ->
                Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
            }
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
                onClick = { viewModel.saveTransaction(onSuccess = onSave) },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimensions.Padding.content)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(56.dp),
                shapes = ButtonDefaults.shapes(),
                enabled = state.amount.toBigDecimalOrNull() != null && !(state.writing || state.loadingSource || state.importing),
            ) {
                if ((state.writing || state.loadingSource || state.importing)) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
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
                    draft.dateTime.toJavaDateTime()
                        .toLocalDate()
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
            )

        DatePicker(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    viewModel.updateTransactionDate(millis)
                }
                showDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState =
            rememberTimePickerState(
                initialHour = draft.dateTime.toJavaDateTime().hour,
                initialMinute = draft.dateTime.toJavaDateTime().minute,
                is24Hour = true
            )

        TimePicker(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.updateTransactionTime(
                    timePickerState.hour,
                    timePickerState.minute
                )
                showTimePicker = false
            },
            timePickerState = timePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }
}
