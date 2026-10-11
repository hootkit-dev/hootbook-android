package io.github.hootkit_dev.hootbook.presentation.core.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreTransactionsScreen(
    onBack: () -> Unit,
    accountId: String? = null,
    initialTransactionId: String? = null,
    subscriptionId: String? = null,
    templateId: String? = null,
    onManageDebts: () -> Unit = {},
    viewModel: CoreTransactionsViewModel = hiltViewModel(),
    onManageSubscriptions: (String) -> Unit = {},
    onManageMerchantIcon: (String) -> Unit = {},
    initialCategoryId: String? = null,
    initialNewKind: TransactionKind? = null,
    initialFrom: String? = null, initialTo: String? = null, initialCurrency: String? = null,
    initialKinds: List<TransactionKind> = emptyList(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var routedNew by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accountId, initialTransactionId, subscriptionId, templateId, initialCategoryId, initialNewKind) {
        viewModel.initialize(accountId, initialTransactionId, subscriptionId, templateId)
        if (initialCategoryId != null || initialFrom != null || initialTo != null || initialCurrency != null || initialKinds.isNotEmpty())
            viewModel.applyQuery(viewModel.state.value.query.copy(categoryId = initialCategoryId, fromDate = initialFrom.orEmpty(), toDateExclusive = initialTo.orEmpty(), currency = initialCurrency.orEmpty(), kinds = initialKinds))
        if (initialNewKind != null && !routedNew) {
            routedNew = true
            if (viewModel.state.value.form == null) viewModel.newTransaction(initialNewKind)
        }
    }
    BackHandler(enabled = state.detailId != null || state.form != null || state.writing) { viewModel.back() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (!viewModel.back()) onBack() }, enabled = !state.writing) { Text(stringResource(R.string.core_full_label_1)) }
            Text(stringResource(R.string.core_transactions_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::refresh, enabled = !state.loading && !state.writing) { Text(stringResource(R.string.core_full_label_2)) }
        }
        if (state.loading || state.writing || state.loadingSource) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_attachment_close)) }
        }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.undoToken != null) OutlinedButton(onClick = viewModel::undo, enabled = !state.writing) { Text(stringResource(R.string.core_full_label_3)) }
        CoreTransactionFullActions(state, viewModel, onManageSubscriptions, onManageMerchantIcon)
        when {
            state.form != null -> TransactionEditor(state, viewModel::updateForm, viewModel::save)
            state.detailId != null -> {
                if (state.detail == null && !state.loading) Text(stringResource(R.string.core_full_label_4))
                state.detail?.let { transaction ->
                    TransactionDetails(transaction, state.accounts, state.categories)
                    if (!transaction.deleted && transaction.debtId == null &&
                        transaction.kind in setOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN)) {
                        ExistingTransactionDebtLink(transaction.id, state.people, state.writing, onManageDebts, viewModel::linkExistingTransaction)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::editTransaction, enabled = !state.writing && transaction.kind != TransactionKind.UNKNOWN) { Text(stringResource(R.string.core_full_label_5)) }
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !state.writing && !transaction.deleted) { Text(stringResource(R.string.core_full_label_6)) }
                    }
                }
            }
            else -> {
                Button(onClick = { viewModel.newTransaction() }, enabled = !state.writing && !state.loading) { Text(stringResource(R.string.core_full_label_7)) }
                TransactionFilters(state, viewModel::applyQuery)
                state.summary?.let { summary ->
                    Text(stringResource(R.string.core_full_txn_summary, summary.currency, summary.income.toPlainString(), summary.expense.toPlainString(), summary.net.toPlainString()))
                    if (!summary.complete) Text(stringResource(R.string.core_full_missing_currencies, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
                    if (state.query.deleted == DeletedFilter.ONLY) Text(stringResource(R.string.core_full_label_9))
                }
                state.page?.let { page ->
                    Text(stringResource(R.string.core_full_txn_page, page.total.toString(), (if (page.total == 0u) 0u else state.offset + 1u).toString()))
                    page.items.forEach { transaction ->
                        OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = !state.writing) { viewModel.openDetail(transaction.id) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { Checkbox(transaction.id in state.selectedIds, onCheckedChange = { viewModel.toggleSelected(transaction.id) }, enabled = !state.writing)
                                    Text("${transaction.kind.displayLabel()} · ${CoreMoneyPresentation.format(transaction.amount, transaction.currency, state.customSymbols)}", style = MaterialTheme.typography.titleMedium) }
                                state.displayValues[transaction.id]?.let { money -> Text(CoreMoneyPresentation.format(money.amount, money.currency, state.customSymbols)); if (!money.complete) Text(stringResource(R.string.core_full_conversion_missing)) }
                                Text(transaction.merchant.ifBlank { stringResource(R.string.core_full_label_10) })
                                Text(transaction.dateTime.inputText(), style = MaterialTheme.typography.bodySmall)
                                Text(accountLabel(transaction.accountId, state.accounts))
                                categoryLabel(transaction.categoryId, transaction.subcategoryId, state.categories)?.let { Text(it) }
                                if (transaction.kind == TransactionKind.TRANSFER) Text("→ ${accountLabel(transaction.toAccountId, state.accounts)} · ${transaction.toAmount?.toPlainString() ?: transaction.amount.toPlainString()} ${transaction.toCurrency ?: transaction.currency}")
                                if (transaction.deleted) Text(stringResource(R.string.core_full_label_11), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (page.items.isEmpty()) Text(stringResource(R.string.core_full_label_12))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = viewModel::previousPage, enabled = state.offset > 0u && !state.loading && !state.writing) { Text(stringResource(R.string.core_full_label_13)) }
                        OutlinedButton(onClick = viewModel::nextPage, enabled = state.offset + 50u < page.total && !state.loading && !state.writing) { Text(stringResource(R.string.core_full_label_14)) }
                    }
                }
                Text(stringResource(R.string.core_transactions_limits), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.core_full_label_15)) },
        text = { Text(stringResource(R.string.core_full_label_16)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.core_full_label_17)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.core_full_label_18)) } },
    )
}

@Composable
private fun ExistingTransactionDebtLink(transactionId: String, people: List<Person>, writing: Boolean, managePeople: () -> Unit,
    link: (String, DebtKind?) -> Unit) {
    var personId by rememberSaveable(transactionId) { mutableStateOf<String?>(null) }
    var kind by rememberSaveable(transactionId) { mutableStateOf<DebtKind?>(null) }
    Text(stringResource(R.string.core_full_label_19), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.core_full_label_20))
    if (people.isEmpty()) {
        Text(stringResource(R.string.core_full_label_21))
        OutlinedButton(onClick = managePeople, enabled = !writing) { Text(stringResource(R.string.core_full_label_22)) }
    }
    Choice(stringResource(R.string.core_full_label_23), personId, listOf(null to stringResource(R.string.core_full_label_24)) + people.map { it.id to it.name }, !writing) { personId = it }
    Choice(stringResource(R.string.core_full_label_25), kind, listOf(null to stringResource(R.string.core_full_label_26)) + DebtKind.entries.filter { it != DebtKind.UNKNOWN }.map {
        it to when (it) { DebtKind.LENT -> stringResource(R.string.core_full_label_27); DebtKind.BORROWED -> stringResource(R.string.core_full_label_28);
            DebtKind.REPAID_TO_ME -> stringResource(R.string.core_full_label_29); DebtKind.REPAID_BY_ME -> stringResource(R.string.core_full_label_30); else -> it.name }
    }, !writing) { kind = it }
    OutlinedButton(onClick = { personId?.let { link(it, kind) } },
        enabled = !writing && people.any { it.id == personId }) { Text(stringResource(R.string.core_full_label_31)) }
}

@Composable
private fun TransactionFilters(state: CoreTransactionsUiState, apply: (CoreTransactionQuery) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by remember(state.query) { mutableStateOf(state.query) }
    TextButton(onClick = { expanded = !expanded }, enabled = !state.writing) { Text(if (expanded) stringResource(R.string.core_full_label_32) else stringResource(R.string.core_full_label_33)) }
    if (!expanded) return
    CoreInput(stringResource(R.string.core_full_label_34), query.text, enabled = !state.writing) { query = query.copy(text = it) }
    CoreInput(stringResource(R.string.core_full_label_35), query.currency, enabled = !state.writing) { query = query.copy(currency = it) }
    CoreInput(stringResource(R.string.core_full_label_36), query.fromDate, enabled = !state.writing) { query = query.copy(fromDate = it) }
    CoreInput(stringResource(R.string.core_full_label_37), query.toDateExclusive, enabled = !state.writing) { query = query.copy(toDateExclusive = it) }
    Choice(stringResource(R.string.core_full_label_38), query.accountId, listOf(null to stringResource(R.string.core_full_label_39)) + state.accounts.map { it.id to accountLabel(it.id, state.accounts) }, enabled = !state.writing) { query = query.copy(accountId = it) }
    Choice(stringResource(R.string.core_full_label_40), query.categoryId, listOf(null to stringResource(R.string.core_full_label_41)) + state.categories.map { it.id to it.displayName }, enabled = !state.writing) { query = query.copy(categoryId = it) }
    Text(stringResource(R.string.core_full_multi_filter))
    for (account in state.accounts) Row { Checkbox(account.id in query.accountIds,
        onCheckedChange = { selected -> query = query.copy(accountId = null, accountIds = if (selected) query.accountIds + account.id else query.accountIds - account.id) }, enabled = !state.writing); Text(account.name) }
    for (category in state.categories) Row { Checkbox(category.id in query.categoryIds,
        onCheckedChange = { selected -> query = query.copy(categoryId = null, categoryIds = if (selected) query.categoryIds + category.id else query.categoryIds - category.id) }, enabled = !state.writing); Text(category.displayName) }
    for (kind in TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }) Row { Checkbox(kind in query.kinds,
        onCheckedChange = { selected -> query = query.copy(kind = null, kinds = if (selected) query.kinds + kind else query.kinds - kind) }, enabled = !state.writing); Text(kind.displayLabel()) }
    Choice(stringResource(R.string.core_full_label_42), query.kind, listOf(null to stringResource(R.string.core_full_label_43)) + TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.map { it to it.displayLabel() }, enabled = !state.writing) { query = query.copy(kind = it) }
    Choice(stringResource(R.string.core_full_label_44), query.sort, listOf(SortOrder.NEWEST_FIRST to stringResource(R.string.core_full_label_45), SortOrder.OLDEST_FIRST to stringResource(R.string.core_full_label_46), SortOrder.LARGEST_FIRST to stringResource(R.string.core_full_label_47)), enabled = !state.writing) { query = query.copy(sort = it) }
    Choice(stringResource(R.string.core_full_label_48), query.deleted, listOf(DeletedFilter.EXCLUDE to stringResource(R.string.core_full_label_49), DeletedFilter.ONLY to stringResource(R.string.core_full_label_50), DeletedFilter.INCLUDE to stringResource(R.string.core_full_label_51)), enabled = !state.writing) { query = query.copy(deleted = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { apply(query) }, enabled = !state.writing) { Text(stringResource(R.string.core_full_label_52)) }
        OutlinedButton(onClick = { query = CoreTransactionQuery(); apply(query) }, enabled = !state.writing) { Text(stringResource(R.string.core_full_label_53)) }
    }
}

@Composable
private fun TransactionEditor(state: CoreTransactionsUiState, update: (CoreTransactionForm) -> Unit, save: () -> Unit) {
    val form = state.form ?: return
    Text(if (form.editingId == null) stringResource(R.string.core_full_label_7) else stringResource(R.string.core_full_label_8), style = MaterialTheme.typography.titleLarge)
    val enabled = !state.writing
    if (state.loadingSource) Text(stringResource(R.string.core_full_label_54))
    Choice(stringResource(R.string.core_full_label_42), form.kind, TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.map { it to it.displayLabel() }, enabled) {
        update(form.copy(kind = it, categoryId = null, subcategoryId = null, toAccountId = null, toCurrency = "", toAmount = ""))
    }
    CoreInput(stringResource(R.string.core_full_label_55), form.amount, enabled) { update(form.copy(amount = it)) }
    CoreInput(stringResource(R.string.core_full_label_56), form.dateTime, enabled) { update(form.copy(dateTime = it)) }
    val accounts = state.accounts.filter { !it.archived || it.id == form.accountId || it.id == form.toAccountId }
    Choice(if (form.kind == TransactionKind.TRANSFER) stringResource(R.string.core_full_label_57) else stringResource(R.string.core_full_label_38), form.accountId,
        listOf(null to stringResource(R.string.core_full_label_58)) + accounts.map { it.id to accountLabel(it.id, state.accounts) }, enabled) { id ->
        val currency = accounts.find { it.id == id }?.mainCurrency ?: state.mainCurrency
        update(form.withAccount(id, currency))
    }
    val source = state.accounts.find { it.id == form.accountId }
    if (source == null) CoreInput(stringResource(R.string.core_full_label_59), form.currency, enabled) { update(form.copy(currency = it)) }
    else Choice(stringResource(R.string.core_full_label_60), form.currency, source.pockets.map { it.currency to it.currency }, enabled) { update(form.copy(currency = it)) }
    if (form.kind == TransactionKind.TRANSFER) {
        Choice(stringResource(R.string.core_full_label_61), form.toAccountId, listOf(null to stringResource(R.string.core_full_label_24)) + accounts.map { it.id to accountLabel(it.id, state.accounts) }, enabled) { id ->
            update(form.copy(toAccountId = id, toCurrency = accounts.find { it.id == id }?.mainCurrency.orEmpty(), toAmount = ""))
        }
        val target = state.accounts.find { it.id == form.toAccountId }
        if (target == null) CoreInput(stringResource(R.string.core_full_label_62), form.toCurrency, enabled) { update(form.copy(toCurrency = it)) }
        else Choice(stringResource(R.string.core_full_label_62), form.toCurrency, target.pockets.map { it.currency to it.currency }, enabled) { update(form.copy(toCurrency = it)) }
        CoreInput(stringResource(R.string.core_full_label_63), form.toAmount, enabled) { update(form.copy(toAmount = it)) }
        Text(stringResource(R.string.core_full_label_64), style = MaterialTheme.typography.bodySmall)
    }
    if (form.kind == TransactionKind.EXPENSE || form.kind == TransactionKind.INCOME) {
        val kind = if (form.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val categories = state.categories.filter { it.kind == kind && (!it.hidden || it.id == form.categoryId) }
        Choice(stringResource(R.string.core_full_label_40), form.categoryId, listOf(null to stringResource(R.string.core_full_label_24)) + categories.map { it.id to it.displayName }, enabled) {
            update(form.copy(categoryId = it, subcategoryId = null))
        }
        val children = categories.find { it.id == form.categoryId }?.subcategories.orEmpty().filter { !it.hidden || it.id == form.subcategoryId }
        Choice(stringResource(R.string.core_full_label_65), form.subcategoryId, listOf(null to stringResource(R.string.core_full_label_66)) + children.map { it.id to it.displayName }, enabled) { update(form.copy(subcategoryId = it)) }
    }
    CoreInput(stringResource(R.string.core_full_label_67), form.merchant, enabled) { update(form.copy(merchant = it)) }
    CoreInput(stringResource(R.string.core_full_label_68), form.notes, enabled) { update(form.copy(notes = it)) }
    form.subscriptionId?.let {
        Text(stringResource(R.string.core_full_txn_payment, it))
        if (form.accountId == null || form.categoryId == null) Text(stringResource(R.string.core_full_label_69), color = MaterialTheme.colorScheme.error)
    }
    if (state.merchants.isNotEmpty()) FlowRow { state.merchants.forEach { merchant -> TextButton(enabled = enabled, onClick = { update(form.copy(merchant = merchant)) }) { Text(merchant) } } }
    Text(stringResource(R.string.core_full_attachment_note), style = MaterialTheme.typography.bodySmall)
    Button(onClick = save, enabled = enabled && !state.loadingSource && !state.importingAttachment, modifier = Modifier.fillMaxWidth()) { Text(if (state.writing) stringResource(R.string.core_full_label_70) else stringResource(R.string.core_full_label_71)) }
}

@Composable
private fun TransactionDetails(transaction: Transaction, accounts: List<Account>, categories: List<Category>) {
    Text("${transaction.kind.displayLabel()} · ${transaction.amount.toPlainString()} ${transaction.currency}", style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.core_full_txn_date, transaction.dateTime.inputText()))
    Text(stringResource(R.string.core_full_txn_account, accountLabel(transaction.accountId, accounts)))
    if (transaction.kind == TransactionKind.TRANSFER) Text(stringResource(R.string.core_full_txn_destination, accountLabel(transaction.toAccountId, accounts), (transaction.toAmount ?: transaction.amount.takeIf { transaction.toCurrency == null || transaction.toCurrency == transaction.currency })?.toPlainString() ?: "—", transaction.toCurrency ?: transaction.currency))
    categoryLabel(transaction.categoryId, transaction.subcategoryId, categories)?.let { Text(stringResource(R.string.core_full_txn_category, it)) }
    Text(stringResource(R.string.core_full_txn_merchant, transaction.merchant.ifBlank { "—" }))
    Text(stringResource(R.string.core_full_txn_notes, transaction.notes ?: "—"))
    if (transaction.deleted) Text(stringResource(R.string.core_full_label_11), color = MaterialTheme.colorScheme.error)
    Text(stringResource(R.string.core_full_txn_id, transaction.id), style = MaterialTheme.typography.bodySmall)
    transaction.subscriptionId?.let { Text(stringResource(R.string.core_full_txn_subscription, it), style = MaterialTheme.typography.bodySmall) }
    transaction.debtId?.let { Text(stringResource(R.string.core_full_txn_debt, it), style = MaterialTheme.typography.bodySmall) }
    transaction.attachments.forEach { Text(stringResource(R.string.core_full_txn_attachment, it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun accountLabel(id: String?, accounts: List<Account>): String {
    if (id == null) return stringResource(R.string.core_full_label_58)
    val account = accounts.find { it.id == id } ?: return id
    return account.name + account.last4?.let { " · $it" }.orEmpty()
}
private fun categoryLabel(id: String?, childId: String?, categories: List<Category>): String? {
    if (id == null) return null
    val category = categories.find { it.id == id } ?: return id
    return category.displayName + category.subcategories.find { it.id == childId }?.let { " / ${it.displayName}" }.orEmpty()
}

@Composable
private fun CoreInput(label: String, value: String, enabled: Boolean = true, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, enabled = enabled, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> Choice(label: String, selected: T, entries: List<Pair<T, String>>, enabled: Boolean = true, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${entries.find { it.first == selected }?.second ?: selected?.toString() ?: stringResource(R.string.core_full_label_72)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            entries.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; change(value) }) }
        }
    }
}
