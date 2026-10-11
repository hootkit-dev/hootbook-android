@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsUiState
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsViewModel
import io.github.hootkit_dev.hootbook.presentation.core.transactions.displayLabel
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.utils.DateFormats

/**
 * One transaction: what it was, its details, attachments and what it is linked to. Edit is in
 * the bar; changing the merchant icon, repeating it as a subscription, finding duplicates and
 * deleting are behind ⋮.
 */
@Composable
fun TransactionDetailScreen(
    state: CoreTransactionsUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMerchantIcon: (String) -> Unit = {},
    onSubscription: (String) -> Unit = {},
    onDebts: () -> Unit = {},
    merchantIcon: Any? = null,
    model: CoreTransactionsViewModel? = null,
) {
    val transaction = state.detail
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var subscriptionSheet by rememberSaveable { mutableStateOf(false) }
    var linkSheet by rememberSaveable { mutableStateOf(false) }
    var checkedDuplicates by rememberSaveable(transaction?.id) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val enabled = !state.writing
    SecondaryScreen(
        title = transaction?.let { it.merchant.ifBlank { state.categories.find { c -> c.id == it.categoryId }?.displayName ?: it.kind.displayLabel() } }
            ?: stringResource(R.string.transaction_details),
        onBack = onBack,
        actions = {
            if (transaction != null) {
                if (!transaction.deleted && transaction.kind != TransactionKind.UNKNOWN)
                    TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_transaction), onEdit, enabled = enabled)
                OverflowMenu(enabled = enabled, actions = listOfNotNull(
                    if (transaction.merchant.isNotBlank()) MenuAction(stringResource(R.string.txn_merchant_icon), Icons.Rounded.Image) { onMerchantIcon(transaction.merchant) } else null,
                    if (model != null && !transaction.deleted && transaction.kind == TransactionKind.EXPENSE && transaction.subscriptionId == null)
                        MenuAction(stringResource(R.string.txn_make_subscription), Icons.Rounded.Repeat) { subscriptionSheet = true } else null,
                    if (model != null) MenuAction(stringResource(R.string.txn_find_duplicates), Icons.Rounded.ContentCopy) { checkedDuplicates = true; model.checkDuplicates() } else null,
                    if (transaction.deleted) { if (model != null) MenuAction(stringResource(R.string.txn_purge), Icons.Rounded.DeleteForever, destructive = true) { confirm = "purge" } else null }
                    else MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { confirm = "delete" },
                ))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                state.error?.let { ErrorBanner(it, { model?.clearError() }) }
                if (transaction == null) {
                    if (!state.loading) Text(stringResource(R.string.txn_not_found), style = MaterialTheme.typography.bodyLarge)
                    return@Column
                }
                TransactionHeader(transaction, state, merchantIcon)
                TransactionDetails(transaction, state)
                val store = model?.attachmentStore
                if (store != null && transaction.attachments.isNotEmpty()) ListSection(title = stringResource(R.string.txn_section_attachments)) {
                    transaction.attachments.forEachIndexed { index, ref ->
                        item(headline = { Text(stringResource(R.string.txn_attachment, index + 1)) },
                            leading = { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage(ref, store, Modifier.size(40.dp)) },
                            chevron = true, onClick = { preview = ref })
                    }
                }
                LinkedSection(transaction, state, onSubscription, onDebts, onLink = { linkSheet = true }, canLinkDebt = model != null)
                if (model != null && checkedDuplicates && !state.checkingDuplicates) DuplicatesSection(state, model, onDelete = { confirm = "duplicates" })
            }
        }
    }
    preview?.let { ref -> model?.attachmentStore?.let { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentPreview(ref, it, onDismiss = { preview = null }) } }
    if (subscriptionSheet && model != null) SubscriptionFromTransactionSheet(state.writing, { subscriptionSheet = false }) { cycle, end -> model.createSubscription(cycle, end); subscriptionSheet = false }
    if (linkSheet && transaction != null && model != null) LinkDebtSheet(state, onDebts, { linkSheet = false }) { person, kind -> model.linkExistingTransaction(person, kind); linkSheet = false }
    confirm?.let { action ->
        val duplicates = state.duplicateCandidates.size
        ConfirmDialog(
            title = when (action) {
                "purge" -> stringResource(R.string.txn_purge_title, 1)
                "duplicates" -> stringResource(R.string.txn_delete_selected_title, duplicates)
                else -> stringResource(R.string.txn_delete_title)
            },
            text = stringResource(when (action) { "purge" -> R.string.txn_purge_note; "duplicates" -> R.string.txn_delete_selected_note; else -> R.string.txn_delete_note }),
            confirmLabel = stringResource(if (action == "purge") R.string.txn_purge else R.string.delete),
            onConfirm = {
                when (action) {
                    "purge" -> model?.run { clearSelection(); transaction?.let { toggleSelected(it.id) }; purgeSelected() }
                    "duplicates" -> model?.run { clearSelection(); selectDuplicateCandidates(); deleteSelected() }
                    else -> onDelete()
                }
                confirm = null
            },
            onDismiss = { confirm = null }, destructive = true, enabled = enabled,
        )
    }
}

/** Icon, amount (and its conversion), and the type; Deleted when it is in the recycle bin. */
@Composable
private fun TransactionHeader(transaction: Transaction, state: CoreTransactionsUiState, merchantIcon: Any?) {
    val brandIcon = io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons.getIconResource(transaction.merchant)
    val category = state.categories.find { it.id == transaction.categoryId }
    val child = category?.subcategories?.find { it.id == transaction.subcategoryId }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (merchantIcon != null || brandIcon != null) coil3.compose.AsyncImage(merchantIcon ?: brandIcon, null, Modifier.size(44.dp))
                else CoreRecordIcon(child?.icon ?: category?.icon ?: state.accounts.find { it.id == transaction.accountId }?.icon.orEmpty(), Modifier.size(44.dp))
            }
        }
        Text(CoreMoneyPresentation.format(transaction.amount, transaction.currency, state.customSymbols), style = MaterialTheme.typography.displaySmall,
            color = transactionColor(transaction.kind), textAlign = TextAlign.Center, modifier = Modifier.testTag("detail_amount"))
        state.displayValues[transaction.id]?.takeIf { it.currency != transaction.currency }?.let { value ->
            Text(CoreMoneyPresentation.format(value.amount, value.currency, state.customSymbols), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!value.complete) Text(stringResource(R.string.core_full_conversion_missing), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLabel(transaction.kind.displayLabel())
            if (transaction.deleted) StatusLabel(stringResource(R.string.txn_deleted), error = true)
        }
    }
}

@Composable
private fun TransactionDetails(transaction: Transaction, state: CoreTransactionsUiState) {
    val category = state.categories.find { it.id == transaction.categoryId }
    val child = category?.subcategories?.find { it.id == transaction.subcategoryId }
    val account = state.accounts.find { it.id == transaction.accountId }
    val target = state.accounts.find { it.id == transaction.toAccountId }
    val dateTime = transaction.dateTime.let { java.time.LocalDateTime.of(it.year, it.month.toInt(), it.day.toInt(), it.hour.toInt(), it.minute.toInt()) }
    val noAccount = stringResource(R.string.txn_no_account)
    ListSection(title = stringResource(R.string.txn_section_details)) {
        detail(R.string.txn_date_time, DateFormats.fullDate(dateTime) + " " + DateFormats.time(dateTime))
        if (transaction.kind == TransactionKind.TRANSFER) {
            detail(R.string.txn_from_account, account?.name ?: noAccount)
            detail(R.string.txn_to_account, target?.name ?: noAccount)
            val incomingCurrency = transaction.toCurrency ?: transaction.currency
            if (incomingCurrency != transaction.currency) {
                row { shape -> ListItem(headline = { Text(stringResource(R.string.transfer_received_amount, incomingCurrency)) },
                    trailing = { Text(CoreMoneyPresentation.format(transaction.toAmount, incomingCurrency, state.customSymbols)) },
                    shape = shape, padding = PaddingValues(0.dp)) }
            }
        } else detail(R.string.account, account?.name ?: noAccount)
        category?.let { detail(R.string.category, listOfNotNull(it.displayName, child?.displayName).joinToString(" · ")) }
        if (transaction.merchant.isNotBlank()) detail(R.string.txn_merchant, transaction.merchant)
        transaction.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item(headline = { Text(stringResource(R.string.notes)) }, supporting = { Text(notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) })
        }
    }
}

private fun ListSectionScope.detail(label: Int, value: String) = item(
    headline = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant) },
    trailing = { Text(value, Modifier.widthIn(max = 220.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End) },
)

/** The subscription it pays and the person it is lent to or borrowed from, or a row to link one. */
@Composable
private fun LinkedSection(transaction: Transaction, state: CoreTransactionsUiState, onSubscription: (String) -> Unit, onDebts: () -> Unit, onLink: () -> Unit, canLinkDebt: Boolean) {
    val subscription = transaction.subscriptionId ?: state.createdSubscriptionId
    val canLink = canLinkDebt && !transaction.deleted && transaction.debtId == null && transaction.kind in setOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN)
    if (subscription == null && transaction.debtId == null && !canLink) return
    ListSection(title = stringResource(R.string.txn_section_linked)) {
        subscription?.let { id -> item(headline = { Text(stringResource(R.string.txn_open_subscription)) }, leading = { Icon(Icons.Rounded.Repeat, null) },
            chevron = true, onClick = { onSubscription(id) }) }
        if (transaction.debtId != null) item(headline = { Text(stringResource(R.string.txn_open_debt)) }, leading = { Icon(Icons.Rounded.People, null) },
            chevron = true, onClick = onDebts)
        if (canLink) item(headline = { Text(stringResource(R.string.txn_link_debt)) }, supporting = { Text(stringResource(R.string.txn_link_debt_note)) },
            leading = { Icon(Icons.Rounded.PersonAdd, null) }, chevron = true, onClick = { if (!state.writing) onLink() })
    }
}

@Composable
private fun DuplicatesSection(state: CoreTransactionsUiState, model: CoreTransactionsViewModel, onDelete: () -> Unit) {
    val rows = state.duplicateCandidates
    ListSection(
        title = stringResource(R.string.txn_duplicates),
        action = if (rows.isNotEmpty()) ({ TextButton(onClick = onDelete, enabled = !state.writing) { Text(stringResource(R.string.txn_duplicates_delete)) } }) else null,
    ) {
        if (rows.isEmpty()) item(headline = { Text(stringResource(R.string.txn_duplicates_none)) })
        else item(headline = { Text(stringResource(R.string.txn_duplicates_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        rows.forEach { row ->
            row { shape ->
                TransactionItem(row, account = state.accounts.find { it.id == row.accountId }, category = state.categories.find { it.id == row.categoryId },
                    targetAccount = state.accounts.find { it.id == row.toAccountId }, customSymbols = state.customSymbols, shape = shape,
                    onClick = { model.openDetail(row.id) })
            }
        }
    }
}

/** Repeats an expense as a subscription: every N days/weeks/months/years, until an optional end. */
@Composable
private fun SubscriptionFromTransactionSheet(writing: Boolean, dismiss: () -> Unit, submit: (Cycle, LocalDate?) -> Unit) {
    var every by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(CycleUnit.MONTH) }
    var end by rememberSaveable { mutableStateOf("") }
    val units = listOf(CycleUnit.DAY to R.string.txn_unit_day, CycleUnit.WEEK to R.string.txn_unit_week,
        CycleUnit.MONTH to R.string.txn_unit_month, CycleUnit.YEAR to R.string.txn_unit_year)
    val count = every.toUIntOrNull()?.takeIf { it > 0u }
    FormSheet(stringResource(R.string.txn_make_subscription), dismiss, {
        val date = end.takeIf(String::isNotBlank)?.let(java.time.LocalDate::parse)?.let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
        submit(Cycle(count!!, unit), date)
    }, busy = writing, confirmEnabled = count != null, confirmLabel = stringResource(R.string.txn_create)) {
        FormTextField(every, stringResource(R.string.txn_subscription_every), { every = it }, decimal = true, isError = count == null)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, (value, label) ->
                SegmentedButton(unit == value, { unit = value }, SegmentedButtonDefaults.itemShape(index, units.size), icon = {}) { Text(stringResource(label)) }
            }
        }
        DateField(end, stringResource(R.string.txn_subscription_end), { end = it }, emptyText = stringResource(R.string.txn_subscription_never))
    }
}

/** Counts a loan transaction toward a person: choose the person and, optionally, the direction. */
@Composable
private fun LinkDebtSheet(state: CoreTransactionsUiState, onManagePeople: () -> Unit, dismiss: () -> Unit, link: (String, DebtKind?) -> Unit) {
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<DebtKind?>(null) }
    FormSheet(stringResource(R.string.txn_link_debt), dismiss, { personId?.let { link(it, kind) } }, busy = state.writing,
        confirmEnabled = state.people.any { it.id == personId }, confirmLabel = stringResource(R.string.txn_link)) {
        Text(stringResource(R.string.txn_link_debt_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.people.isEmpty()) {
            Text(stringResource(R.string.txn_link_no_people))
            OutlinedButton(onClick = { dismiss(); onManagePeople() }) { Text(stringResource(R.string.txn_manage_people)) }
        } else {
            SectionTitle(stringResource(R.string.txn_link_person), indent = 0.dp)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.people.forEach { person -> FilterChip(person.id == personId, { personId = person.id }, label = { Text(person.name) }) }
            }
            SectionTitle(stringResource(R.string.txn_link_direction), indent = 0.dp)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to R.string.txn_link_direction_auto, DebtKind.LENT to R.string.type_lent, DebtKind.BORROWED to R.string.type_borrowed,
                    DebtKind.REPAID_TO_ME to R.string.txn_debt_repaid_to_me, DebtKind.REPAID_BY_ME to R.string.txn_debt_repaid_by_me).forEach { (value, label) ->
                    FilterChip(kind == value, { kind = value }, label = { Text(stringResource(label)) })
                }
            }
        }
    }
}
