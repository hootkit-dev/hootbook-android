package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.ai.LedgerChange
import io.github.hootkit_dev.hootbook.data.ai.TransactionDraft
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.presentation.common.TransactionLookups
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountField
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountSelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A proposed change as the transaction it would leave in the ledger, for display only. */
internal fun LedgerChange.preview(index: Int): TransactionEntity? = when (this) {
    is LedgerChange.Add -> draft.toEntity(index)
    is LedgerChange.Update -> after
    is LedgerChange.Delete -> transaction
    is LedgerChange.CreateAccount, is LedgerChange.SetBalance, is LedgerChange.UpdateAccount -> null
}

/** The account a change creates or adjusts, as it will look after saving. */
private fun LedgerChange.accountAfter(): AccountBalanceEntity? = when (this) {
    is LedgerChange.CreateAccount -> account
    is LedgerChange.SetBalance -> account.copy(balance = balance, creditLimit = creditLimit ?: account.creditLimit)
    is LedgerChange.UpdateAccount -> after
    else -> null
}

/** Decoration for a preview: a transaction on an account proposed in the same review has none saved yet. */
private fun TransactionLookups.decorateProposed(change: LedgerChange, preview: TransactionEntity) =
    decorate(preview).let { d ->
        val account = (change as? LedgerChange.Add)?.draft?.account
        if (d.account == null && account != null) d.copy(account = account) else d
    }

private fun TransactionDraft.toEntity(index: Int) = TransactionEntity(
    // Negative ids never match a saved transaction
    id = -(index + 1L),
    amount = amount,
    merchantName = merchant,
    category = category,
    subcategory = subcategory,
    transactionType = type,
    dateTime = dateTime,
    description = notes,
    bankName = account?.bankName,
    accountNumber = account?.accountLast4,
    toAccount = toAccount?.accountLast4,
    toAmount = toAmount,
    toCurrency = toCurrency,
    transactionHash = "",
    currency = currency
)

/**
 * The proposed changes grouped into new, edited and deleted, each drawn like a row in the
 * transaction lists. A row left out is dimmed; tapping a row opens its details.
 */
internal fun LazyListScope.reviewItems(
    review: List<ReviewItem>,
    lookups: TransactionLookups,
    mainCurrency: String?,
    // Null once saved: the rows are then a read-only record
    onOpen: ((Int) -> Unit)?
) {
    accountGroup(
        R.string.ai_section_accounts,
        review.withIndex().filter { it.value.change is LedgerChange.CreateAccount },
        onOpen
    )
    accountGroup(
        R.string.ai_section_account_changes,
        review.withIndex().filter { it.value.change is LedgerChange.SetBalance || it.value.change is LedgerChange.UpdateAccount },
        onOpen
    )
    val groups = listOf(
        R.string.ai_section_add to review.withIndex().filter { it.value.change is LedgerChange.Add },
        R.string.ai_section_update to review.withIndex().filter { it.value.change is LedgerChange.Update },
        R.string.ai_section_delete to review.withIndex().filter { it.value.change is LedgerChange.Delete }
    )
    groups.forEach { (titleRes, items) ->
        if (items.isEmpty()) return@forEach
        item(key = "header_$titleRes") {
            Text(
                text = stringResource(titleRes, items.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
        items.forEachIndexed { position, (index, item) ->
            item(key = "change_$index") {
                val preview = remember(item.change, index) { item.change.preview(index) } ?: return@item
                TransactionItem(
                    transaction = preview,
                    decoration = lookups.decorateProposed(item.change, preview),
                    mainCurrency = mainCurrency,
                    subtitleOverride = subtitle(item),
                    onClick = { onOpen?.invoke(index) },
                    shape = ListItemPosition.from(position, items.size).toShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (item.included) 1f else 0.4f)
                )
            }
        }
    }
}

private fun LazyListScope.accountGroup(
    titleRes: Int,
    items: List<IndexedValue<ReviewItem>>,
    onOpen: ((Int) -> Unit)?
) {
    if (items.isEmpty()) return
    item(key = "header_$titleRes") {
        Text(
            text = stringResource(titleRes, items.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
    items.forEachIndexed { position, (index, item) ->
        item(key = "change_$index") {
            val account = item.change.accountAfter() ?: return@item
            AccountRow(
                account = account,
                shape = ListItemPosition.from(position, items.size).toShape(),
                onClick = onOpen?.let { open -> { open(index) } },
                note = accountChangeLine(item),
                modifier = Modifier.fillMaxWidth().alpha(if (item.included) 1f else 0.4f)
            )
        }
    }
}

/** What an account change does, in one line: "Balance ¥1,200.00 → ¥980.50". */
@Composable
private fun accountChangeLine(item: ReviewItem): String? {
    val change = item.change
    val parts = when (change) {
        is LedgerChange.SetBalance -> listOfNotNull(
            stringResource(R.string.ai_balance_change,
                CurrencyFormatter.formatCurrency(change.account.balance, change.account.currency),
                CurrencyFormatter.formatCurrency(change.balance, change.account.currency)),
            change.creditLimit?.let {
                stringResource(R.string.ai_limit_change,
                    change.account.creditLimit?.let { l -> CurrencyFormatter.formatCurrency(l, change.account.currency) } ?: "—",
                    CurrencyFormatter.formatCurrency(it, change.account.currency))
            }
        )
        is LedgerChange.UpdateAccount -> listOfNotNull(
            "${change.before.bankName} → ${change.after.bankName}".takeIf { change.before.bankName != change.after.bankName },
            stringResource(R.string.ai_kind_changed).takeIf {
                change.before.isCreditCard != change.after.isCreditCard || change.before.isWallet != change.after.isWallet
            },
            change.addedCurrencies.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.ai_currencies_added, it.joinToString(", "))
            },
            change.after.creditLimit?.takeIf { change.after.creditLimit != change.before.creditLimit }?.let {
                stringResource(R.string.ai_limit_change,
                    change.before.creditLimit?.let { l -> CurrencyFormatter.formatCurrency(l, change.after.currency) } ?: "—",
                    CurrencyFormatter.formatCurrency(it, change.after.currency))
            }
        )
        else -> emptyList()
    }
    val line = parts.joinToString(" · ")
    return if (!item.included) listOf(stringResource(R.string.ai_left_out), line).filter { it.isNotEmpty() }.joinToString(" · ")
    else line.ifEmpty { null }
}

/** What the row says under the payee when the default (date and category) is not enough. */
@Composable
private fun subtitle(item: ReviewItem): String? {
    val formatter = rememberDayFormatter()
    val change = item.change
    return when {
        !item.included -> stringResource(R.string.ai_left_out)
        change is LedgerChange.Add && change.draft.possibleDuplicate != null -> {
            val existing = change.draft.possibleDuplicate
            stringResource(R.string.ai_possible_duplicate) + " · " +
                existing.dateTime.format(formatter) + " " + existing.merchantName
        }
        change is LedgerChange.Update -> stringResource(R.string.ai_was, describeFields(change.before))
        change is LedgerChange.Delete -> change.reason
        else -> null
    }
}

private fun describeFields(t: TransactionEntity) =
    listOfNotNull(t.merchantName.takeIf { it.isNotBlank() }, listOfNotNull(t.category, t.subcategory).joinToString(" · ") { io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.display(it) })
        .joinToString(" · ")

@Composable
private fun rememberDayFormatter(): DateTimeFormatter {
    val pattern = stringResource(R.string.ai_date_pattern)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
}

/**
 * One proposed change in full. A new transaction can be corrected here; any change can be left
 * out or put back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReviewDetailSheet(
    item: ReviewItem,
    index: Int,
    lookups: TransactionLookups,
    mainCurrency: String?,
    onEdit: (TransactionDraft) -> Unit,
    onIncludedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val change = item.change
    var draft by remember(change) { mutableStateOf((change as? LedgerChange.Add)?.draft) }

    HootbookModalBottomSheet(
        onDismissRequest = {
            draft?.let(onEdit)
            onDismiss()
        },
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .navigationBarsPadding()
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val current = draft
            if (current != null) {
                DraftForm(current, lookups) { draft = it }
                current.possibleDuplicate?.let { existing ->
                    Text(
                        stringResource(R.string.ai_possible_duplicate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    TransactionItem(
                        transaction = existing,
                        decoration = lookups.decorate(existing),
                        mainCurrency = mainCurrency,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (change is LedgerChange.CreateAccount) {
                AccountRow(account = change.account, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.ai_new_account_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (change is LedgerChange.SetBalance || change is LedgerChange.UpdateAccount) {
                val before = (change as? LedgerChange.SetBalance)?.account ?: (change as LedgerChange.UpdateAccount).before
                Text(stringResource(R.string.ai_before), style = MaterialTheme.typography.labelLarge)
                AccountRow(account = before, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.ai_after), style = MaterialTheme.typography.labelLarge)
                change.accountAfter()?.let { AccountRow(account = it, modifier = Modifier.fillMaxWidth()) }
                if (change is LedgerChange.UpdateAccount && change.before.bankName != change.after.bankName) {
                    Text(
                        stringResource(R.string.ai_rename_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (change is LedgerChange.Update) {
                    Text(stringResource(R.string.ai_before), style = MaterialTheme.typography.labelLarge)
                    TransactionItem(
                        transaction = change.before,
                        decoration = lookups.decorate(change.before),
                        mainCurrency = mainCurrency,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.ai_after), style = MaterialTheme.typography.labelLarge)
                }
                val preview = change.preview(index) ?: return@Column
                TransactionItem(
                    transaction = preview,
                    decoration = lookups.decorateProposed(change, preview),
                    mainCurrency = mainCurrency,
                    modifier = Modifier.fillMaxWidth()
                )
                (change as? LedgerChange.Delete)?.reason?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, androidx.compose.ui.Alignment.End)
            ) {
                OutlinedButton(
                    onClick = {
                        onIncludedChange(!item.included)
                        draft?.let(onEdit)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(if (item.included) R.string.ai_leave_out else R.string.ai_put_back))
                }
                Button(
                    onClick = {
                        draft?.let(onEdit)
                        onDismiss()
                    },
                    // A transfer needs where the money goes
                    enabled = draft?.let { it.type != TransactionType.TRANSFER || it.toAccount != null } ?: true,
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

/** Fields of a proposed transaction, laid out like the Add form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftForm(draft: TransactionDraft, lookups: TransactionLookups, onChange: (TransactionDraft) -> Unit) {
    var amountText by remember { mutableStateOf(draft.amount.toPlainString()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingAccount by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    val accounts = remember(lookups) { lookups.accounts.values.toList() }

    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { i, type ->
            SegmentedButton(
                selected = draft.type == type,
                onClick = { onChange(draft.copy(type = type, toAccount = if (type == TransactionType.TRANSFER) draft.toAccount else null)) },
                shape = SegmentedButtonDefaults.itemShape(i, types.size),
                // No check mark: the filled segment already shows the choice
                icon = {}
            ) { Text(stringResource(type.labelRes)) }
        }
    }
    OutlinedTextField(
        value = draft.merchant,
        onValueChange = { onChange(draft.copy(merchant = it)) },
        label = { Text(stringResource(R.string.ai_merchant)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = amountText,
        onValueChange = { text ->
            amountText = text
            text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { onChange(draft.copy(amount = it)) }
        },
        label = { Text(stringResource(R.string.amount)) },
        suffix = { Text(draft.currency) },
        isError = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    FieldButton(
        label = stringResource(R.string.date),
        value = draft.dateTime.format(DateTimeFormatter.ofPattern(stringResource(R.string.ai_date_time_pattern))),
        onClick = { pickingDate = true }
    )
    FieldButton(
        label = stringResource(R.string.category),
        value = listOfNotNull(draft.category, draft.subcategory).joinToString(" · ") { io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.display(it) },
        onClick = { pickingCategory = true }
    )
    AccountField(account = draft.account, onClick = { pickingAccount = true })
    if (draft.type == TransactionType.TRANSFER) {
        AccountField(
            account = draft.toAccount,
            onClick = { pickingTarget = true },
            placeholder = stringResource(R.string.select_target_account)
        )
    }
    OutlinedTextField(
        value = draft.notes.orEmpty(),
        onValueChange = { onChange(draft.copy(notes = it.ifBlank { null })) },
        label = { Text(stringResource(R.string.notes)) },
        modifier = Modifier.fillMaxWidth()
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.dateTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.done), onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(draft.copy(dateTime = day.atTime(draft.dateTime.toLocalTime())))
                    }
                    pickingDate = false
                })
            },
            dismissButton = { DialogDismissButton(stringResource(R.string.ai_cancel), onClick = { pickingDate = false }) }
        ) { DatePicker(state = state) }
    }
    if (pickingCategory) {
        HootbookModalBottomSheet(onDismissRequest = { pickingCategory = false }) {
            val subcategories = remember(lookups) { lookups.subcategories.values.groupBy { it.categoryId } }
            CategorySelectionSheet(
                categories = lookups.categories.values.sortedBy { it.displayOrder },
                subcategoriesMap = subcategories,
                onSelectionComplete = { category, sub ->
                    onChange(draft.copy(category = category.name, subcategory = sub?.name))
                    pickingCategory = false
                },
                onDismiss = { pickingCategory = false }
            )
        }
    }
    if (pickingAccount || pickingTarget) {
        val target = pickingTarget
        HootbookModalBottomSheet(onDismissRequest = { pickingAccount = false; pickingTarget = false }) {
            AccountSelectionSheet(
                accounts = if (target) accounts.filter { it.id != draft.account?.id } else accounts,
                selectedAccount = if (target) draft.toAccount else draft.account,
                title = stringResource(if (target) R.string.select_target_account else R.string.select_account),
                onAccountSelected = { account: AccountBalanceEntity? ->
                    onChange(
                        if (target) draft.copy(toAccount = account)
                        // The amount stays in its currency; saving puts it in the account's own
                        // pocket for it, or converts it into the main one
                        else draft.copy(account = account)
                    )
                    pickingAccount = false
                    pickingTarget = false
                },
                showNoneOption = !target
            )
        }
    }
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
