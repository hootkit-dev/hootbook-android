package io.github.hootkit_dev.hootbook.presentation.core.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AddScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.*

/** Navigation/side effects only. The original feature surfaces consume direct Core state. */
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
    initialMerchant: String? = null,
    onNewSubscription: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var routedNew by rememberSaveable { mutableStateOf(false) }
    var enteredForm by rememberSaveable { mutableStateOf(false) }
    var pendingLeave by rememberSaveable { mutableStateOf<String?>(null) }
    val merchantUnsupportedMessage = stringResource(R.string.transaction_unsupported_merchant)
    val externalEntry = initialNewKind != null || subscriptionId != null || templateId != null
    val externalDetail = initialTransactionId != null
    LaunchedEffect(accountId, initialTransactionId, subscriptionId, templateId, initialCategoryId, initialNewKind, initialFrom, initialTo, initialCurrency, initialMerchant, initialKinds) {
        viewModel.initialize(accountId, initialTransactionId, subscriptionId, templateId)
        if (initialCategoryId != null || initialFrom != null || initialTo != null || initialCurrency != null || initialMerchant != null || initialKinds.isNotEmpty())
            viewModel.applyQuery(viewModel.state.value.query.copy(categoryId = initialCategoryId, fromDate = initialFrom.orEmpty(), toDateExclusive = initialTo.orEmpty(), currency = initialCurrency.orEmpty(), kinds = initialKinds))
        if (initialMerchant != null) viewModel.reportUnsupportedMerchantFilter(merchantUnsupportedMessage)
        if (initialNewKind != null && !routedNew) { routedNew = true; if (viewModel.state.value.form == null) viewModel.newTransaction(initialNewKind) }
    }
    val leave: (String) -> Unit = { destination ->
        val current = viewModel.state.value
        if (!current.writing && !current.importingAttachment) {
            if (destination == "subscription") { viewModel.back(); onNewSubscription?.invoke() }
            else if (externalEntry) { viewModel.back(); onBack() }
            else if (externalDetail && current.form == null) onBack()
            else if (!viewModel.back()) onBack()
        }
    }
    val requestLeave: (String) -> Unit = { destination ->
        if (!viewModel.state.value.writing && !viewModel.state.value.importingAttachment) {
            if (viewModel.hasUnsavedDraft()) pendingLeave = destination else leave(destination)
        }
    }
    val back: () -> Unit = { requestLeave("back") }
    BackHandler(enabled = state.detailId != null || state.form != null || state.writing) { back() }
    LaunchedEffect(state.form != null, state.writing, state.detailId) {
        if (state.form != null) enteredForm = true
        if (externalEntry && enteredForm && state.form == null && state.detailId != null && !state.writing) onBack()
    }
    val actions: @Composable () -> Unit = { CoreTransactionFullActions(state, viewModel, onManageSubscriptions, onManageMerchantIcon) }
    when {
        state.form != null -> AddScreen(state, viewModel::updateForm, viewModel::save, back, viewModel::addTargetCurrency,
            onNewSubscription?.let { { requestLeave("subscription") } }, actions)
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp && !externalDetail
            val detail: @Composable () -> Unit = {
                TransactionDetailScreen(state, back, viewModel::editTransaction, viewModel::delete, onManageMerchantIcon, onManageSubscriptions, onManageDebts, merchantIcon = state.detail?.let { viewModel.platformAssets?.merchantIcon(it.merchant) }) {
                    state.detail?.let { row -> if (!row.deleted && row.debtId == null && row.kind in setOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN)) ExistingTransactionDebtLink(row.id, state.people, state.writing, onManageDebts, viewModel::linkExistingTransaction) }
                    actions()
                }
            }
            if (state.detailId != null && !wide) detail()
            else Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { TransactionsScreen(state, viewModel, onBack, viewModel::openDetail, showSummarySheet = !wide, showBackButton = accountId != null || externalDetail, actions = { CoreTransactionFullActions(state.copy(detailId = null, detail = null), viewModel, onManageSubscriptions, onManageMerchantIcon) }) }
                if (wide && state.detailId != null) { VerticalDivider(); Box(Modifier.weight(1f)) { detail() } }
            }
        }
    }
    pendingLeave?.let { destination ->
        TransactionDiscardDialog(
            onContinue = { pendingLeave = null },
            onDiscard = { pendingLeave = null; leave(destination) },
        )
    }
}

@Composable
internal fun TransactionDiscardDialog(onContinue: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text(stringResource(R.string.transaction_discard_title)) },
        text = { Text(stringResource(R.string.transaction_discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.transaction_discard_action)) } },
        dismissButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.transaction_continue_editing)) } },
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
