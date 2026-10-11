package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

import io.github.hootkit_dev.hootbook.utils.DateFormats
import io.github.hootkit_dev.hootbook.utils.pickerDate
import io.github.hootkit_dev.hootbook.utils.toPickerMillis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.AttachmentSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.TimePicker
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Information
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import io.github.hootkit_dev.hootbook.utils.horizontalFadingEdge
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class)
@Composable
fun AddEditLendBorrowTransactionSheet(
    personsList: List<Person>,
    initialPerson: Person? = null,
    accounts: List<Account> = emptyList(),
    attachmentStore: CoreAttachmentStore,
    mainCurrency: String,
    saving: Boolean = false,
    error: String? = null,
    showPersonSelection: Boolean = true,
    transactionToEdit: DebtEntry? = null,
    initialType: DebtKind? = null,
    initialAmount: BigDecimal? = null,
    initialTitle: String? = null,
    blurEffects: Boolean = LocalBlurEffects.current,
    onDismiss: () -> Unit,
    onAddPerson: (PersonDraft, (String) -> Unit) -> Unit,
    onSave: (DebtDraft) -> Unit,
    onUpdate: (String, DebtDraft) -> Unit = { _, _ -> }
) {
    var selectedPerson by remember {
        mutableStateOf(
            if (transactionToEdit != null) personsList.find { it.id == transactionToEdit.personId } ?: initialPerson
            else initialPerson ?: personsList.firstOrNull()
        )
    }
    var selectedType by remember { mutableStateOf(transactionToEdit?.kind ?: initialType ?: DebtKind.LENT) }
    var amountText by remember {
        mutableStateOf(transactionToEdit?.amount?.amount?.stripTrailingZeros()?.toPlainString()
            ?: initialAmount?.stripTrailingZeros()?.toPlainString()
            ?: "0")
    }

    var lastAddedPersonId by remember { mutableStateOf<String?>(null) }

    // Update selected person if list changes and nothing was selected,
    // or auto-select a person that was just added via the in-sheet Add Person flow
    LaunchedEffect(personsList) {
        val lastAdded = lastAddedPersonId
        if (lastAdded != null) {
            personsList.firstOrNull { it.id == lastAdded }?.let { selectedPerson = it }
            if (personsList.any { it.id == lastAdded }) lastAddedPersonId = null
        } else if (selectedPerson == null && personsList.isNotEmpty()) {
            selectedPerson = personsList.first()
        }
    }

    val context = LocalContext.current
    var selectedAccount by remember {
        mutableStateOf(if (transactionToEdit != null) accounts.find { it.id == transactionToEdit.accountId } else accounts.firstOrNull())
    }

    var titleText by remember { mutableStateOf(transactionToEdit?.title ?: initialTitle ?: "") }
    var attachments by remember { mutableStateOf(transactionToEdit?.attachments ?: emptyList()) }
    var dateTime by remember { mutableStateOf(transactionToEdit?.date?.toJavaDateTime() ?: LocalDateTime.now()) }
    var dueDate by remember { mutableStateOf<LocalDateTime?>(transactionToEdit?.dueDate?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()).atStartOfDay() }) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNumberPad by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAddPersonSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    // Handle dismissal with animation when parent state changes
    LaunchedEffect(Unit) {
        // Optional: ensure sheet is expanded
    }

    fun animateDismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    HootbookModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Box(modifier = Modifier.fillMaxWidth().hazeSource(hazeState)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(if (transactionToEdit != null) R.string.edit_record else R.string.add_record),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // Type Switcher
                if (selectedType == DebtKind.LENT || selectedType == DebtKind.BORROWED) GenericTypeSwitcher(
                    selectedIndex = if (selectedType == DebtKind.LENT) 0 else 1,
                    onIndexChange = { index ->
                        selectedType =
                            if (index == 0) DebtKind.LENT else DebtKind.BORROWED
                    },
                    options = listOf(
                        stringResource(R.string.type_lent),
                        stringResource(R.string.type_borrowed)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.Radius.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Iconax.Information,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(when (selectedType) {
                                DebtKind.LENT -> R.string.amount_they_owe_me
                                DebtKind.BORROWED -> R.string.amount_i_owe_them
                                DebtKind.REPAID_TO_ME -> R.string.settlement_received
                                DebtKind.REPAID_BY_ME -> R.string.settlement_paid
                                DebtKind.UNKNOWN -> R.string.record_options
                            }),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Amount Input
                AmountInput(
                    amount = amountText,
                    currencySymbol = CurrencyFormatter.getCurrencySymbol(
                        transactionToEdit?.amount?.currency ?: selectedAccount?.mainCurrency ?: mainCurrency
                    ),
                    onClick = { showNumberPad = true },
                    modifier = Modifier.fillMaxWidth()
                )

                // Date and Time Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(Dimensions.Radius.md)
                            )
                            .clip(RoundedCornerShape(Dimensions.Radius.md))
                            .clickable { showDatePicker = true }
                            .padding(8.dp),
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

                            val dateLabel = dateTime.format(DateFormats.monthDayFormatter())
                            val yearLabel = dateTime.format(DateTimeFormatter.ofPattern("yyyy"))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = yearLabel,

                                    color = themeColors.primary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = dateLabel,

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
                            .clip(RoundedCornerShape(Dimensions.Radius.md))
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            val hour = dateTime.hour
                            val minute = dateTime.minute

                            Box(
                                modifier = Modifier.padding(5.dp).background(
                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ) {
                                Text(
                                    text = String.format("%02d", hour),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                            Text(
                                text = ":",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Box(
                                modifier = Modifier.padding(5.dp).background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ) {
                                Text(
                                    text = String.format("%02d", minute),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                        }
                    }
                }

                // Due Date Selection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Dimensions.Radius.md)
                        )
                        .clip(RoundedCornerShape(Dimensions.Radius.md))
                        .clickable { showDueDatePicker = true }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Iconax.Calendar,
                            contentDescription = null,
                            tint = if (dueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.due_date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dueDate?.format(DateFormats.fullDateFormatter())
                                    ?: stringResource(R.string.no_due_date),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (dueDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f
                                )
                            )
                        }
                        if (dueDate != null) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.clear_due_date),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { dueDate = null },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Person Selection Carousel
                if (showPersonSelection) {
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

                            items(personsList, key = { it.id }) { person ->
                                val isSelected = selectedPerson?.id == person.id
                                val colorInt = try {
                                    android.graphics.Color.parseColor(person.color)
                                } catch (_: Exception) {
                                    android.graphics.Color.parseColor("#4CAF50")
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedPerson = person }
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
                                                model = person.avatar?.let(attachmentStore::resolve),
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
                                        modifier = Modifier
                                            .width(56.dp)
                                            .padding(top = 4.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Account and Category
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    AccountField(
                        account = selectedAccount,
                        onClick = { showAccountSheet = true },
                        placeholder = stringResource(R.string.select_account),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    )

                }

                TextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(stringResource(R.string.description)) },
                    leadingIcon = { Icon(Iconax.DocumentText2, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                var importError by remember { mutableStateOf<String?>(null) }
                importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AttachmentSection(
                    attachments = attachments,
                    attachmentStore = attachmentStore,
                    onAddAttachment = { uri -> scope.launch {
                        try { attachments = attachments + attachmentStore.importFrom(uri) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { importError = failure.message }
                    } },
                    onRemoveAttachment = { path -> attachments = attachments - path }
                )

                Spacer(modifier = Modifier.height(48.dp))
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
                    onClick = {
                        val parsedAmount = amountText.toBigDecimalOrNull()
                        val personId = selectedPerson?.id
                        if (personId != null && parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                            val draft = DebtDraft(personId, selectedType, parsedAmount,
                                transactionToEdit?.amount?.currency ?: selectedAccount?.mainCurrency ?: mainCurrency,
                                dateTime.toCoreDateTime(), dueDate?.let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) },
                                titleText, selectedAccount?.id, attachments)
                            if (transactionToEdit != null) onUpdate(transactionToEdit.id, draft) else onSave(draft)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(56.dp),
                    enabled = !saving && titleText.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showNumberPad) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NumberPad(
                initialValue = amountText,
                onDone = {
                    amountText = it
                    showNumberPad = false
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().toPickerMillis()
        )
        DatePicker(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    dateTime = pickerDate(millis).atTime(dateTime.toLocalTime())
                }
                showDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showDueDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: LocalDateTime.now()).toLocalDate().toPickerMillis()
        )
        DatePicker(
            onDismiss = { showDueDatePicker = false },
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    dueDate = pickerDate(millis).atStartOfDay()
                }
                showDueDatePicker = false
            },
            datePickerState = datePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true
        )
        TimePicker(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                dateTime = dateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute)
                showTimePicker = false
            },
            timePickerState = timePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showAccountSheet) {
        HootbookModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AccountSelectionSheet(
                accounts = accounts,
                selectedAccount = selectedAccount,
                showNoneOption = true,
                onAccountSelected = {
                    selectedAccount = it
                    showAccountSheet = false
                }
            )
        }
    }

    if (showAddPersonSheet) {
        AddEditPersonSheet(
            attachmentStore = attachmentStore,
            saving = saving, error = error,
            onDismiss = { if (!saving) showAddPersonSheet = false },
            onSave = { name, phone, notes, color, avatar, category ->
                onAddPerson(PersonDraft(name, phone, notes, color, avatar, category)) { id ->
                    lastAddedPersonId = id
                    showAddPersonSheet = false
                }
            }
        )
    }
}
