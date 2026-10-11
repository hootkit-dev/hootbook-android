@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.*

/**
 * Add or edit a transaction, as a full-screen form: ✕ to leave, Save in the bar. Amount first,
 * then when, where from and what for, then merchant, notes and attachments.
 */
@Composable
fun AddScreen(state: CoreTransactionsUiState, update: (CoreTransactionForm) -> Unit, onSave: () -> Unit, onBack: () -> Unit,
    addCurrency: (String, String) -> Unit = { _, _ -> }, onNewSubscription: (() -> Unit)? = null,
    model: CoreTransactionsViewModel? = null) {
    val form = state.form ?: return
    val enabled = !state.writing && !state.loadingSource
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var amountPad by rememberSaveable { mutableStateOf(false) }
    var pendingCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var currencyDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var checkedDuplicates by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model?.importAttachment(it) } }
    val source = state.accounts.find { it.id == form.accountId }
    val target = state.accounts.find { it.id == form.toAccountId }
    val category = state.categories.find { it.id == form.categoryId }
    val child = category?.subcategories?.find { it.id == form.subcategoryId }
    val primaryKinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER)
    val canSave = enabled && form.amount.isNotBlank() && !state.importingAttachment
    SecondaryScreen(
        title = stringResource(if (form.editingId == null) R.string.add_transaction else R.string.edit_transaction),
        onBack = onBack, backIcon = Icons.Rounded.Close, backLabel = stringResource(R.string.close),
        actions = {
            if (model != null) OverflowMenu(enabled = enabled, actions = listOf(
                MenuAction(stringResource(R.string.txn_find_duplicates), Icons.Rounded.ContentCopy) { checkedDuplicates = true; model.checkDuplicates() },
            ))
            Button(onClick = onSave, enabled = canSave, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(end = 8.dp).testTag("save_transaction")) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.error?.let { ErrorBanner(it, { model?.clearError() }) }
                if (state.writing && model != null) TextButton(onClick = model::cancelWrite) { Text(stringResource(R.string.txn_cancel_saving)) }
                if (onNewSubscription != null && form.editingId == null && form.subscriptionId == null) {
                    GenericTypeSwitcher(0, { if (it == 1 && enabled) onNewSubscription() },
                        listOf(stringResource(R.string.transaction), stringResource(R.string.subscription)), Modifier.fillMaxWidth())
                }
                GenericTypeSwitcher(primaryKinds.indexOf(form.kind), { index ->
                    update(form.copy(kind = primaryKinds[index], categoryId = null, subcategoryId = null, toAccountId = null, toCurrency = "", toAmount = ""))
                }, primaryKinds.map { it.displayLabel() }, Modifier.fillMaxWidth())
                TextButton(onClick = { picker = "kind" }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (form.kind in primaryKinds) stringResource(R.string.txn_more_kinds) else stringResource(R.string.txn_kind_value, form.kind.displayLabel()))
                }
                AmountInput(form.amount, runCatching { java.util.Currency.getInstance(form.currency).symbol }.getOrDefault(form.currency), { amountPad = true }, enabled = enabled)
                CurrencyMenu(
                    currencies = (source?.pockets?.map { it.currency }.orEmpty() + form.currency).distinct(),
                    selected = form.currency, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally),
                    onSelect = { update(form.copy(currency = it, toAmount = "")) },
                    onOther = if (source == null) ({ currencyDialog = "source" }) else null,
                )
                DateTimeField(form.dateTime, stringResource(R.string.txn_date_time), { update(form.copy(dateTime = it)) }, enabled = enabled, allowClear = false)
                if (form.kind == TransactionKind.TRANSFER) TransferSection(form, state, source, target, enabled, update,
                    onPick = { picker = it }, onAddCurrency = { pendingCurrency = it }, onOtherCurrency = { currencyDialog = "target" })
                else ListSection {
                    choiceRow(stringResource(R.string.account), source?.name ?: stringResource(R.string.txn_no_account), source?.icon, enabled) { picker = "source" }
                    if (form.kind == TransactionKind.EXPENSE || form.kind == TransactionKind.INCOME)
                        choiceRow(stringResource(R.string.category), category?.displayName ?: stringResource(R.string.txn_choose), category?.icon, enabled, Icons.Rounded.Category) { picker = "category" }
                    if (category?.subcategories?.isNotEmpty() == true)
                        choiceRow(stringResource(R.string.txn_subcategory), child?.displayName ?: stringResource(R.string.txn_no_subcategory), child?.icon, enabled, Icons.Rounded.Category) { picker = "subcategory" }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(form.merchant, { update(form.copy(merchant = it)) }, label = { Text(stringResource(R.string.txn_merchant)) },
                        leadingIcon = { Icon(Icons.Rounded.Storefront, null) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (state.merchants.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.merchants.take(8).forEach { merchant -> SuggestionChip(onClick = { update(form.copy(merchant = merchant)) }, label = { Text(merchant) }, enabled = enabled) }
                    }
                }
                OutlinedTextField(form.notes, { update(form.copy(notes = it)) }, label = { Text(stringResource(R.string.notes_optional)) },
                    leadingIcon = { Icon(Icons.Rounded.Notes, null) }, enabled = enabled, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (form.subscriptionId != null) StatusLabel(stringResource(R.string.txn_subscription_payment))
                model?.attachmentStore?.let { store ->
                    ListSection(title = stringResource(R.string.txn_section_attachments)) {
                        form.attachments.forEachIndexed { index, ref ->
                            item(headline = { Text(stringResource(R.string.txn_attachment, index + 1)) },
                                leading = { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage(ref, store, Modifier.size(40.dp)) },
                                trailing = { TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.txn_attachment_remove), { model?.removeAttachment(ref) }, enabled = enabled) },
                                onClick = { preview = ref })
                        }
                        item(headline = { Text(stringResource(R.string.txn_attachment_add)) }, leading = { Icon(Icons.Rounded.AttachFile, null) },
                            onClick = { if (enabled && !state.importingAttachment) pickAttachment.launch(arrayOf("image/*", "application/pdf", "text/plain")) })
                    }
                }
                if (checkedDuplicates && !state.checkingDuplicates) ListSection(title = stringResource(R.string.txn_duplicates)) {
                    if (state.duplicateCandidates.isEmpty()) item(headline = { Text(stringResource(R.string.txn_duplicates_none)) })
                    state.duplicateCandidates.forEach { row ->
                        row { shape -> TransactionItem(row, account = state.accounts.find { it.id == row.accountId },
                            category = state.categories.find { it.id == row.categoryId }, customSymbols = state.customSymbols, shape = shape) }
                    }
                }
            }
        }
    }
    if (amountPad) AmountPadSheet(form.amount, { update(form.copy(amount = it)) }) { amountPad = false }
    when (picker) {
        "source", "target" -> PickerSheet(stringResource(R.string.select_account), { picker = null }) {
            if (picker == "source" && form.kind != TransactionKind.TRANSFER) pickRow(stringResource(R.string.txn_no_account), null, form.accountId == null) {
                update(form.copy(accountId = null)); picker = null
            }
            state.accounts.filter { !it.archived || it.id in listOf(form.accountId, form.toAccountId) }.forEach { account ->
                val selected = account.id == if (picker == "source") form.accountId else form.toAccountId
                pickRow(account.name, account.icon, selected, account.pockets.joinToString(" · ") { it.currency }) {
                    if (picker == "source") update(form.withAccount(account.id, account.mainCurrency))
                    else update(form.copy(toAccountId = account.id, toCurrency = account.mainCurrency, toAmount = ""))
                    picker = null
                }
            }
        }
        "category" -> PickerSheet(stringResource(R.string.category), { picker = null }) {
            pickRow(stringResource(R.string.txn_no_category), null, form.categoryId == null) { update(form.copy(categoryId = null, subcategoryId = null)); picker = null }
            state.categories.filter { (!it.hidden || it.id == form.categoryId) && it.kind == if (form.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }.forEach { row ->
                pickRow(row.displayName, row.icon, row.id == form.categoryId) { update(form.copy(categoryId = row.id, subcategoryId = null)); picker = null }
            }
        }
        "subcategory" -> PickerSheet(stringResource(R.string.txn_subcategory), { picker = null }) {
            pickRow(stringResource(R.string.txn_no_subcategory), null, form.subcategoryId == null) { update(form.copy(subcategoryId = null)); picker = null }
            category?.subcategories?.filter { !it.hidden || it.id == form.subcategoryId }?.forEach { row ->
                pickRow(row.displayName, row.icon, row.id == form.subcategoryId) { update(form.copy(subcategoryId = row.id)); picker = null }
            }
        }
        "kind" -> PickerSheet(stringResource(R.string.txn_kind), { picker = null }) {
            TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                pickRow(kind.displayLabel(), null, kind == form.kind) { update(form.copy(kind = kind, categoryId = null, subcategoryId = null)); picker = null }
            }
        }
    }
    currencyDialog?.let { side ->
        CurrencyCodeDialog({ currencyDialog = null }) { code ->
            if (side == "source") update(form.copy(currency = code, toAmount = "")) else pendingCurrency = code
            currencyDialog = null
        }
    }
    pendingCurrency?.let { currency -> AddCurrencyConfirmation(currency, onConfirm = { target?.let { addCurrency(it.id, currency) }; pendingCurrency = null },
        onDismiss = { pendingCurrency = null }) }
    preview?.let { ref -> model?.attachmentStore?.let { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentPreview(ref, it, onDismiss = { preview = null }) } }
}

/** From, a swap button, to; then the destination's currency and, across currencies, what arrived. */
@Composable
private fun TransferSection(form: CoreTransactionForm, state: CoreTransactionsUiState, source: Account?, target: Account?, enabled: Boolean,
    update: (CoreTransactionForm) -> Unit, onPick: (String) -> Unit, onAddCurrency: (String) -> Unit, onOtherCurrency: () -> Unit) {
    val choose = stringResource(R.string.select_account)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListSection {
            choiceRow(stringResource(R.string.txn_from_account), source?.name ?: choose, source?.icon, enabled) { onPick("source") }
            choiceRow(stringResource(R.string.txn_to_account), target?.name ?: choose, target?.icon, enabled) { onPick("target") }
        }
        TextButton(onClick = { update(form.copy(accountId = form.toAccountId, toAccountId = form.accountId,
            currency = form.toCurrency.ifBlank { form.currency }, toCurrency = form.currency,
            amount = form.toAmount.ifBlank { form.amount }, toAmount = form.amount)) },
            enabled = enabled && target != null, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.SwapVert, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txn_swap))
        }
        if (target != null) AccountCurrencyChoice(target.pockets.map { it.currency }, form.toCurrency,
            { if (enabled) update(form.copy(toCurrency = it, toAmount = "")) },
            suggested = form.currency.takeIf { value -> target.pockets.none { it.currency == value } },
            onAddSuggested = { if (enabled) onAddCurrency(form.currency) }, onAddOther = { if (enabled) onOtherCurrency() })
        if (form.toCurrency.isNotBlank() && form.toCurrency != form.currency) {
            OutlinedTextField(form.toAmount, { update(form.copy(toAmount = it)) }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.transfer_received_amount, form.toCurrency)) },
                supportingText = { Text(stringResource(R.string.txn_received_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("transfer_received_amount"))
            state.transferEstimate?.let { estimate ->
                AssistChip(onClick = { update(form.copy(toAmount = estimate.toPlainString())) }, enabled = enabled,
                    label = { Text(stringResource(R.string.txn_use_estimate, "${estimate.toPlainString()} ${form.toCurrency}")) })
            }
        }
    }
}

/** A form row: what it is, the current choice in primary, and a chevron to change it. */
private fun ListSectionScope.choiceRow(label: String, value: String, icon: String?, enabled: Boolean,
    fallback: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.AccountBalanceWallet, onClick: () -> Unit) = item(
    headline = { Text(label) },
    leading = { if (icon.isNullOrBlank()) Icon(fallback, null, Modifier.size(24.dp)) else CoreRecordIcon(icon, Modifier.size(32.dp)) },
    trailing = { Text(value, Modifier.widthIn(max = 180.dp), color = MaterialTheme.colorScheme.primary, maxLines = 1) },
    chevron = true, onClick = if (enabled) onClick else null,
)

private fun ListSectionScope.pickRow(label: String, icon: String?, selected: Boolean, supporting: String? = null, onClick: () -> Unit) = item(
    headline = { Text(label) },
    supporting = supporting?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
    leading = icon?.let { { CoreRecordIcon(it, Modifier.size(32.dp)) } },
    trailing = if (selected) ({ Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
    selected = selected, onClick = onClick,
)

@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ListSectionScope.() -> Unit) {
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            ListSection(content = content)
        }
    }
}

/** The transaction's currency as a chip that opens the account's currencies (or any code without an account). */
@Composable
private fun CurrencyMenu(currencies: List<String>, selected: String, enabled: Boolean, modifier: Modifier,
    onSelect: (String) -> Unit, onOther: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(onClick = { open = true }, enabled = enabled, label = { Text(selected) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) })
        DropdownMenu(open, { open = false }) {
            currencies.forEach { code -> DropdownMenuItem(text = { Text(code) }, onClick = { open = false; onSelect(code) },
                trailingIcon = if (code == selected) ({ Icon(Icons.Rounded.Check, null) }) else null) }
            onOther?.let { other -> DropdownMenuItem(text = { Text(stringResource(R.string.txn_currency_add)) }, onClick = { open = false; other() }) }
        }
    }
}

@Composable
private fun CurrencyCodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = HootbookDialogDefaults.containerColor,
        title = { Text(stringResource(R.string.account_add_currency)) },
        text = { OutlinedTextField(code, { code = it.uppercase().take(8) }, singleLine = true, label = { Text(stringResource(R.string.account_currency_code)) }) },
        confirmButton = { DialogConfirmButton(stringResource(R.string.apply), { onConfirm(code.trim()) }, enabled = code.isNotBlank()) },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) })
}

/** The amount: the system decimal keyboard, or a keypad while the keyboard is hidden. */
@Composable
private fun AmountPadSheet(amount: String, change: (String) -> Unit, onDone: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val finish: () -> Unit = { focusManager.clearFocus(); keyboard?.hide(); onDone() }
    HootbookModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // ModalBottomSheet handles the IME insets. Keep the rest scrollable and show one keypad,
        // so its confirmation stays reachable on small screens.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(amount, change, label = { Text(stringResource(R.string.amount)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish() }), modifier = Modifier.fillMaxWidth().testTag("amount_editor"))
            if (!WindowInsets.isImeVisible) DecimalAmountPad(amount, change, Modifier.testTag("amount_keypad"))
            Button(onClick = finish, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().testTag("amount_done")) { Text(stringResource(R.string.done)) }
        }
    }
}
