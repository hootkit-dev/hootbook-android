@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import java.time.LocalDateTime as JavaDateTime
import java.time.Instant
import java.time.ZoneOffset

/** Full-screen entry retains the original amount-first, connected account/category layout. */
@Composable
fun AddScreen(state: CoreTransactionsUiState, update: (CoreTransactionForm) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit,
    addCurrency: (String, String) -> Unit = { _, _ -> },
    onNewSubscription: (() -> Unit)? = null,
    attachments: @Composable () -> Unit = {}) {
    val form = state.form ?: return
    val enabled = !state.writing && !state.loadingSource
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var amountPad by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var addCurrencyText by rememberSaveable { mutableStateOf("") }
    var pendingCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var newCurrency by rememberSaveable { mutableStateOf(false) }
    val source = state.accounts.find { it.id == form.accountId }
    val target = state.accounts.find { it.id == form.toAccountId }
    val category = state.categories.find { it.id == form.categoryId }
    val child = category?.subcategories?.find { it.id == form.subcategoryId }
    val dateTime = runCatching { JavaDateTime.parse(form.dateTime) }.getOrNull() ?: JavaDateTime.now()
    val primaryKinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (form.editingId == null) R.string.add_transaction else R.string.edit_transaction)) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.writing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.core_ui_back)) } },
            actions = { TextButton(onClick = onSave, enabled = enabled && form.amount.isNotBlank() && !state.importingAttachment) { Text(stringResource(R.string.save)) } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onNewSubscription != null && form.editingId == null && form.subscriptionId == null) {
                    GenericTypeSwitcher(0, { if (it == 1 && enabled) onNewSubscription() }, listOf(stringResource(R.string.transaction), stringResource(R.string.subscription)))
                }
                GenericTypeSwitcher(primaryKinds.indexOf(form.kind), { index -> update(form.copy(kind = primaryKinds[index], categoryId = null, subcategoryId = null, toAccountId = null, toCurrency = "", toAmount = "")) }, primaryKinds.map { it.displayLabel() })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { picker = "kind" }, enabled = enabled) { Text(if (form.kind in primaryKinds) stringResource(R.string.more_options) else form.kind.displayLabel()) }
                }
                AmountInput(form.amount, form.currency, {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    amountPad = true
                }, enabled = enabled)
                AccountCurrencyChoice((source?.pockets?.map { it.currency }.orEmpty() + form.currency).distinct(), form.currency,
                    { if (enabled) update(form.copy(currency = it, toAmount = "")) })
                if (source == null) OutlinedTextField(form.currency, { update(form.copy(currency = it.uppercase())) }, enabled = enabled,
                    label = { Text(stringResource(R.string.select_currency)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = "date" }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(dateTime.toLocalDate().toString())
                    }
                    OutlinedButton(onClick = { picker = "time" }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("%02d:%02d".format(dateTime.hour, dateTime.minute))
                    }
                }
                if (form.kind == TransactionKind.TRANSFER) {
                    EntryChoice(stringResource(R.string.core_full_label_57), source?.name ?: stringResource(R.string.select_account), source?.icon, enabled) { picker = "source" }
                    IconButton(onClick = { update(form.copy(accountId = form.toAccountId, toAccountId = form.accountId,
                        currency = form.toCurrency.ifBlank { form.currency }, toCurrency = form.currency,
                        amount = form.toAmount.ifBlank { form.amount }, toAmount = form.amount)) }, enabled = enabled && target != null, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Rounded.SwapVert, stringResource(R.string.transfer_action))
                    }
                    EntryChoice(stringResource(R.string.core_full_label_61), target?.name ?: stringResource(R.string.select_account), target?.icon, enabled) { picker = "target" }
                    if (target != null) AccountCurrencyChoice(target.pockets.map { it.currency }, form.toCurrency,
                        { if (enabled) update(form.copy(toCurrency = it, toAmount = "")) },
                        suggested = form.currency.takeIf { value -> target.pockets.none { it.currency == value } },
                        onAddSuggested = { if (enabled) pendingCurrency = form.currency }, onAddOther = { if (enabled) newCurrency = true })
                    if (form.toCurrency.isNotBlank() && form.toCurrency != form.currency) {
                        OutlinedTextField(form.toAmount, { update(form.copy(toAmount = it)) }, enabled = enabled,
                            label = { Text(stringResource(R.string.transfer_received_amount, form.toCurrency)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("transfer_received_amount"))
                        Text(stringResource(R.string.core_full_label_64), style = MaterialTheme.typography.bodySmall)
                        state.transferEstimate?.let { estimate ->
                            TextButton(onClick = { update(form.copy(toAmount = estimate.toPlainString())) }, enabled = enabled) {
                                Text(stringResource(R.string.transaction_use_conversion, estimate.toPlainString(), form.toCurrency))
                            }
                        }
                    }
                } else {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column {
                            EntryChoice(stringResource(R.string.account), source?.name ?: stringResource(R.string.core_full_label_58), source?.icon, enabled) { picker = "source" }
                            if (form.kind in listOf(TransactionKind.EXPENSE, TransactionKind.INCOME)) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                EntryChoice(stringResource(R.string.category), category?.displayName ?: stringResource(R.string.core_full_label_24), category?.icon, enabled) { picker = "category" }
                            }
                        }
                    }
                    if (category?.subcategories?.isNotEmpty() == true) EntryChoice(stringResource(R.string.subcategory), child?.displayName ?: stringResource(R.string.core_full_label_66), child?.icon, enabled) { picker = "subcategory" }
                }
                OutlinedTextField(form.merchant, { update(form.copy(merchant = it)) }, label = { Text(stringResource(R.string.core_full_label_67)) },
                    leadingIcon = { Icon(Icons.Rounded.Storefront, null) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                if (state.merchants.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.merchants.take(8).forEach { merchant -> SuggestionChip(onClick = { update(form.copy(merchant = merchant)) }, label = { Text(merchant) }, enabled = enabled) }
                }
                OutlinedTextField(form.notes, { update(form.copy(notes = it)) }, label = { Text(stringResource(R.string.notes_optional)) },
                    leadingIcon = { Icon(Icons.Rounded.Notes, null) }, enabled = enabled, minLines = 2, modifier = Modifier.fillMaxWidth())
                form.subscriptionId?.let { Text(stringResource(R.string.core_full_txn_payment, it), style = MaterialTheme.typography.bodySmall) }
                attachments()
                Button(onClick = onSave, enabled = enabled && !state.importingAttachment && form.amount.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp).testTag("save_transaction")) {
                    Text(stringResource(R.string.save))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (amountPad) HootbookModalBottomSheet(
        onDismissRequest = { amountPad = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val finishAmount: () -> Unit = {
            focusManager.clearFocus()
            keyboard?.hide()
            amountPad = false
        }
        // ModalBottomSheet handles the IME insets. Keep the remaining content scrollable,
        // and show exactly one keypad so its confirmation stays reachable on small screens.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(form.amount, { update(form.copy(amount = it)) }, label = { Text(stringResource(R.string.amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishAmount() }),
                modifier = Modifier.fillMaxWidth().testTag("amount_editor"))
            if (!WindowInsets.isImeVisible) DecimalAmountPad(form.amount, { update(form.copy(amount = it)) }, Modifier.testTag("amount_keypad"))
            Button(onClick = finishAmount, modifier = Modifier.fillMaxWidth().testTag("amount_done")) { Text(stringResource(R.string.done)) }
        }
    }
    when (picker) {
        "source", "target" -> EntryPicker(stringResource(R.string.select_account), { picker = null }) {
            if (picker == "source" && form.kind != TransactionKind.TRANSFER) TextButton(onClick = { update(form.copy(accountId = null)); picker = null }) { Text(stringResource(R.string.core_full_label_58)) }
            state.accounts.filter { !it.archived || it.id in listOf(form.accountId, form.toAccountId) }.forEach { account ->
                EntryChoice(account.name, account.pockets.joinToString(" · ") { it.currency }, account.icon, true) {
                    if (picker == "source") update(form.withAccount(account.id, account.mainCurrency))
                    else update(form.copy(toAccountId = account.id, toCurrency = account.mainCurrency, toAmount = ""))
                    picker = null
                }
            }
        }
        "category" -> EntryPicker(stringResource(R.string.category), { picker = null }) {
            TextButton(onClick = { update(form.copy(categoryId = null, subcategoryId = null)); picker = null }) { Text(stringResource(R.string.core_full_label_24)) }
            state.categories.filter { (!it.hidden || it.id == form.categoryId) && it.kind == if (form.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }.forEach { row ->
                EntryChoice(row.displayName, "", row.icon, true) { update(form.copy(categoryId = row.id, subcategoryId = null)); picker = null }
            }
        }
        "subcategory" -> EntryPicker(stringResource(R.string.subcategory), { picker = null }) {
            TextButton(onClick = { update(form.copy(subcategoryId = null)); picker = null }) { Text(stringResource(R.string.core_full_label_66)) }
            category?.subcategories?.filter { !it.hidden || it.id == form.subcategoryId }?.forEach { row ->
                EntryChoice(row.displayName, "", row.icon, true) { update(form.copy(subcategoryId = row.id)); picker = null }
            }
        }
        "kind" -> EntryPicker(stringResource(R.string.core_full_label_42), { picker = null }) {
            TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                TextButton(onClick = { update(form.copy(kind = kind, categoryId = null, subcategoryId = null)); picker = null }) { Text(kind.displayLabel()) }
            }
        }
        "date" -> {
            val selected = rememberDatePickerState(initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            io.github.hootkit_dev.hootbook.presentation.ui.components.DatePicker({ picker = null }, {
                selected.selectedDateMillis?.let { millis -> update(form.copy(dateTime = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atTime(dateTime.toLocalTime()).toString())) }; picker = null
            }, selected)
        }
        "time" -> {
            val selected = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute)
            io.github.hootkit_dev.hootbook.presentation.ui.components.TimePicker({ picker = null }, {
                update(form.copy(dateTime = dateTime.withHour(selected.hour).withMinute(selected.minute).toString())); picker = null
            }, selected)
        }
    }
    if (newCurrency) AlertDialog(onDismissRequest = { newCurrency = false }, title = { Text(stringResource(R.string.account_add_currency)) },
        text = { OutlinedTextField(addCurrencyText, { addCurrencyText = it.uppercase() }, label = { Text(stringResource(R.string.select_currency)) }) },
        confirmButton = { TextButton(onClick = { pendingCurrency = addCurrencyText.trim(); newCurrency = false }, enabled = addCurrencyText.isNotBlank()) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = { newCurrency = false }) { Text(stringResource(R.string.cancel)) } })
    pendingCurrency?.let { currency -> AddCurrencyConfirmation(currency, onConfirm = { target?.let { addCurrency(it.id, currency) }; pendingCurrency = null }, onDismiss = { pendingCurrency = null }) }
}

@Composable
internal fun EntryChoice(label: String, value: String, icon: String?, enabled: Boolean, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(value.ifBlank { label }) }, supportingContent = if (value.isNotBlank()) ({ Text(label) }) else null,
        leadingContent = { CoreRecordIcon(icon.orEmpty(), Modifier.size(40.dp)) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}

@Composable
private fun EntryPicker(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall); content(); Spacer(Modifier.height(24.dp))
        }
    }
}
