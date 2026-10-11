@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreVisualStylePicker
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreAccountsScreen(
    onBack: () -> Unit,
    onTransactions: (accountId: String) -> Unit,
    viewModel: CoreAccountsViewModel = hiltViewModel(),
    initialAccountId: String? = null,
    initialHistory: Boolean = false,
    onBrokerage: (Long, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialAccountId, initialHistory) { viewModel.initialize(initialAccountId, initialHistory) }
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var pocketDialog by remember { mutableStateOf<PocketEditor?>(null) }
    var deleteAccount by remember { mutableStateOf<Account?>(null) }
    var deletePocket by remember { mutableStateOf<Pocket?>(null) }
    var deleteAnchor by remember { mutableStateOf<BalanceAnchor?>(null) }
    LaunchedEffect(state.commandRevision) {
        pocketDialog = null; deleteAccount = null; deletePocket = null; deleteAnchor = null
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.account?.name ?: stringResource(R.string.accounts)) },
            navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_1)) } },
            actions = { TextButton(onClick = { viewModel.refresh() }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_2)) } })
    }, floatingActionButton = {
        if (state.selectedId == null) FloatingActionButton(onClick = { viewModel.edit() }) { Text("＋") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.navigationBlocked) Text(stringResource(R.string.core_accounts_saving_navigation), modifier = Modifier.padding(16.dp))
            state.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_accounts_3)) }
                    }
                }
            }
            val account = state.account
            if (state.selectedId == null) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.overview?.let { overview -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.core_accounts_4), style = MaterialTheme.typography.labelLarge)
                            Text("${overview.netWorth.currency} ${overview.netWorth.amount.toPlainString()}", style = MaterialTheme.typography.headlineMedium)
                            if (!overview.netWorthComplete) Text(stringResource(R.string.core_accounts_5))
                            overview.creditAvailable?.let { Text(stringResource(R.string.core_full_credit_available, CoreMoneyPresentation.format(it.amount, it.currency, state.customSymbols))) }
                            overview.nearestDue?.let { Text(stringResource(R.string.core_full_nearest_due, "${it.dueDate.year}-${it.dueDate.month}-${it.dueDate.day}")) }
                        } }
                    } }
                    if (state.overview?.accounts?.isEmpty() == true) item { Text(stringResource(R.string.add_account_prompt)) }
                    items(state.overview?.accounts.orEmpty(), key = { it.id }) { row ->
                        Card(onClick = { viewModel.select(row.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CoreRecordIcon(row.icon, Modifier.size(40.dp))
                                Text(listOfNotNull(row.name, row.last4).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                                Text("${kindText(row.kind)}${if (row.isDefault) stringResource(R.string.core_full_label_79) else ""}${if (row.archived) stringResource(R.string.core_full_label_80) else ""}")
                                Text(CoreMoneyPresentation.format(row.total.amount, row.total.currency, state.customSymbols), style = MaterialTheme.typography.titleLarge)
                                state.displayValues[row.id]?.let { money -> Text(CoreMoneyPresentation.format(money.amount, money.currency, state.customSymbols)); if (!money.complete) Text(stringResource(R.string.core_full_conversion_missing)) }
                                if (!row.totalComplete) Text(stringResource(R.string.core_accounts_6))
                                FlowRow {
                                    TextButton(onClick = { viewModel.move(row, -1) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_7)) }
                                    TextButton(onClick = { viewModel.move(row, 1) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_8)) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else if (account != null) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("${account.total.currency} ${account.total.amount.toPlainString()}", style = MaterialTheme.typography.headlineMedium)
                        if (!account.totalComplete) Text(stringResource(R.string.core_accounts_9))
                        if (account.kind == AccountKind.CREDIT_CARD) Text(stringResource(R.string.core_accounts_10))
                        CoreRecordIcon(account.icon, Modifier.size(48.dp))
                        account.broker?.let { broker -> TextButton(enabled = !state.saving, onClick = { onBrokerage(broker.connectionId, broker.brokerAccountId) }) { Text(stringResource(R.string.core_full_open_broker, broker.brokerAccountId)) } }
                        account.securities?.let { Text(stringResource(R.string.core_full_securities, CoreMoneyPresentation.format(it.amount, it.currency, state.customSymbols))) }
                        FlowRow {
                            TextButton(onClick = { viewModel.edit(account) }, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { viewModel.archive(account) }, enabled = !state.saving) { Text(if (account.archived) stringResource(R.string.core_accounts_11) else stringResource(R.string.core_accounts_12)) }
                            TextButton(onClick = { viewModel.setDefault(account) }, enabled = !state.saving) { Text(if (account.isDefault) stringResource(R.string.core_accounts_13) else stringResource(R.string.core_accounts_14)) }
                        }
                        Text(stringResource(R.string.core_accounts_transaction_count, state.transactionCount.toString()))
                        TextButton(onClick = { viewModel.transactionNavigationTarget()?.let(onTransactions) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_view_transactions)) }
                        TextButton(onClick = { deleteAccount = account }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_15), color = MaterialTheme.colorScheme.error) }
                    }
                    state.cardStatus?.let { card -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.core_full_due_date, "${card.dueDate.year}-${card.dueDate.month}-${card.dueDate.day}"))
                            card.statementDate?.let { Text(stringResource(R.string.core_full_statement_date, "${it.year}-${it.month}-${it.day}")) }
                            card.repaidSince?.let { Text(stringResource(R.string.core_full_repaid_since, CoreMoneyPresentation.format(it, card.currency, state.customSymbols))) }
                            card.statementAmount?.let { Text(stringResource(R.string.core_full_statement_amount, CoreMoneyPresentation.format(it, card.currency, state.customSymbols))) }
                            card.outstanding?.let { Text(stringResource(R.string.core_full_outstanding, CoreMoneyPresentation.format(it, card.currency, state.customSymbols))) }
                            if (card.overdue) Text(stringResource(R.string.core_accounts_16), color = MaterialTheme.colorScheme.error)
                        } }
                    } }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.core_accounts_17), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { pocketDialog = PocketEditor(PocketAction.ADD) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_18)) }
                    } }
                    items(account.pockets, key = { it.currency }) { pocket ->
                        OutlinedCard(onClick = { viewModel.selectCurrency(pocket.currency) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${pocket.currency}${if (pocket.currency == account.mainCurrency) stringResource(R.string.core_full_label_77) else ""}${if (pocket.currency == state.currency) stringResource(R.string.core_full_label_78) else ""}", style = MaterialTheme.typography.titleMedium)
                                Text(pocket.balance.toPlainString(), style = MaterialTheme.typography.headlineSmall)
                                pocket.creditLimit?.let { Text(stringResource(R.string.core_full_pocket_limit, CoreMoneyPresentation.format(it, pocket.currency, state.customSymbols))) }
                                pocket.availableCredit?.let { Text(stringResource(R.string.core_full_pocket_available, CoreMoneyPresentation.format(it, pocket.currency, state.customSymbols))) }
                                FlowRow {
                                    TextButton(onClick = { pocketDialog = PocketEditor(PocketAction.CALIBRATE, pocket.currency, pocket.balance.toPlainString()) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_19)) }
                                    if (account.kind == AccountKind.CREDIT_CARD) TextButton(onClick = { pocketDialog = PocketEditor(PocketAction.LIMIT, pocket.currency, pocket.creditLimit?.toPlainString().orEmpty()) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_20)) }
                                    TextButton(onClick = { viewModel.selectCurrency(pocket.currency); deletePocket = pocket }, enabled = !state.saving && pocket.currency != account.mainCurrency) { Text(stringResource(R.string.core_accounts_21)) }
                                }
                            }
                        }
                    }
                    item { AccountHistoryPanel(state, viewModel) }
                    item { Text(stringResource(R.string.core_full_anchors, state.currency.orEmpty()), style = MaterialTheme.typography.titleLarge) }
                    if (state.anchors.isEmpty()) item { Text(stringResource(R.string.core_accounts_22)) }
                    items(state.anchors, key = { it.id }) { anchor ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text("${anchor.at.inputText()} · ${anchor.source}")
                            Text(stringResource(R.string.core_full_anchor_balance, CoreMoneyPresentation.format(anchor.balance, state.currency.orEmpty(), state.customSymbols)), style = MaterialTheme.typography.titleMedium)
                            anchor.difference?.let { Text(stringResource(R.string.core_full_anchor_difference, CoreMoneyPresentation.format(it, state.currency.orEmpty(), state.customSymbols))) }
                            anchor.note?.let { Text(it) }
                            FlowRow {
                                TextButton(onClick = { pocketDialog = PocketEditor(PocketAction.ANCHOR, state.currency.orEmpty(), anchor.balance.toPlainString(), anchor.at.inputText(), anchor.id, anchor.note.orEmpty()) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_23)) }
                                TextButton(onClick = { deleteAnchor = anchor }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_21)) }
                            }
                        } }
                    }
                }
            }
        }
    }
    state.draft?.let { draft -> AccountEditor(draft, state.institutions, state.saving, state.error, viewModel::draftChanged, viewModel::dismissEditor, viewModel::saveAccount) }
    pocketDialog?.let { editor -> PocketDialog(editor, state.saving, state.error, { pocketDialog = null }) { value ->
        val id = state.selectedId ?: return@PocketDialog
        when (value.action) {
            PocketAction.ADD -> viewModel.addPocket(id, value.currency, value.balance, value.at)
            PocketAction.CALIBRATE -> viewModel.calibrate(id, value.currency, value.balance, value.at)
            PocketAction.LIMIT -> viewModel.setPocketLimit(id, value.currency, value.balance)
            PocketAction.ANCHOR -> viewModel.updateAnchor(value.anchorId!!, value.balance, value.at, value.note)
        }
    } }
    deleteAccount?.let { account -> AlertDialog(onDismissRequest = { if (!state.saving) deleteAccount = null },
        title = { Text(stringResource(R.string.core_full_delete_account_name, account.name)) }, text = { Text(stringResource(R.string.core_full_delete_account_note, state.transactionCount.toString())) },
        dismissButton = { TextButton(onClick = { deleteAccount = null }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_24)) } },
        confirmButton = { Column {
            TextButton(onClick = { viewModel.delete(account, false) }, enabled = !state.saving && state.transactionCount == 0u) { Text(stringResource(R.string.core_accounts_25)) }
            TextButton(onClick = { viewModel.delete(account, true) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_26), color = MaterialTheme.colorScheme.error) }
        } }) }
    deletePocket?.let { pocket -> AlertDialog(onDismissRequest = { if (!state.saving) deletePocket = null }, title = { Text(stringResource(R.string.core_full_delete_pocket_name, pocket.currency)) },
        text = { Text(stringResource(R.string.core_accounts_27, if (state.currency == pocket.currency) state.pocketTransactionCount.toString() else stringResource(R.string.core_full_label_73))) },
        dismissButton = { TextButton(onClick = { deletePocket = null }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_24)) } },
        confirmButton = { TextButton(onClick = { viewModel.removePocket(state.selectedId!!, pocket.currency) }, enabled = !state.saving && !state.loading && state.pocketTransactionCount == 0u) { Text(stringResource(R.string.core_accounts_21)) } }) }
    deleteAnchor?.let { anchor -> AlertDialog(onDismissRequest = { if (!state.saving) deleteAnchor = null }, title = { Text(stringResource(R.string.core_accounts_28)) },
        text = { Text(stringResource(R.string.core_accounts_29)) },
        dismissButton = { TextButton(onClick = { deleteAnchor = null }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_24)) } },
        confirmButton = { TextButton(onClick = { viewModel.deleteAnchor(anchor.id) }, enabled = !state.saving) { Text(stringResource(R.string.core_accounts_21)) } }) }
}

@Composable
private fun kindText(kind: AccountKind) = when (kind) {
    AccountKind.BANK -> stringResource(R.string.core_accounts_30); AccountKind.CREDIT_CARD -> stringResource(R.string.core_accounts_31); AccountKind.WALLET -> stringResource(R.string.core_accounts_32); AccountKind.INVESTMENT -> stringResource(R.string.core_accounts_33); AccountKind.UNKNOWN -> stringResource(R.string.core_accounts_34)
}

@Composable
private fun AccountEditor(draft: CoreAccountDraft, institutions: List<Institution>, saving: Boolean, error: String?,
    changed: (CoreAccountDraft) -> Unit, dismiss: () -> Unit, save: () -> Unit) {
    var institutionsOpen by remember { mutableStateOf(false) }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (draft.id == null) stringResource(R.string.core_accounts_35) else stringResource(R.string.core_accounts_36)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saving) Text(stringResource(R.string.core_accounts_saving_navigation))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            CoreTextField(draft.name, stringResource(R.string.core_accounts_37), saving) { changed(draft.copy(name = it)) }
            AccountKind.entries.filter { it != AccountKind.UNKNOWN }.chunked(2).forEach { kinds -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { kind -> FilterChip(selected = draft.kind == kind, onClick = { changed(draft.copy(kind = kind)) }, label = { Text(kindText(kind)) }, enabled = !saving) }
            } }
            if (draft.kind != AccountKind.WALLET) CoreTextField(draft.last4, stringResource(R.string.core_accounts_38), saving) { changed(draft.copy(last4 = it)) }
            CoreTextField(draft.currency, stringResource(R.string.core_accounts_39), saving) { changed(draft.copy(currency = it)) }
            if (draft.id == null) {
                CoreTextField(draft.openingBalance, if (draft.kind == AccountKind.CREDIT_CARD) stringResource(R.string.core_accounts_40) else stringResource(R.string.core_accounts_41), saving) { changed(draft.copy(openingBalance = it)) }
                CoreTextField(draft.openingAt, stringResource(R.string.core_accounts_42), saving) { changed(draft.copy(openingAt = it)) }
            }
            if (draft.kind == AccountKind.CREDIT_CARD) {
                CoreTextField(draft.creditLimit, stringResource(R.string.core_accounts_43), saving) { changed(draft.copy(creditLimit = it)) }
                CoreTextField(draft.statementDay, stringResource(R.string.core_accounts_44), saving) { changed(draft.copy(statementDay = it)) }
                CoreTextField(draft.dueDay, stringResource(R.string.core_accounts_45), saving) { changed(draft.copy(dueDay = it)) }
            }
            TextButton(onClick = { institutionsOpen = !institutionsOpen }, enabled = !saving) { Text(stringResource(R.string.core_full_institution, institutions.find { it.key == draft.institutionKey }?.let { if (chinese) it.chineseName else it.englishName } ?: stringResource(R.string.core_full_none))) }
            if (institutionsOpen) {
                TextButton(onClick = { changed(draft.copy(institutionKey = "")); institutionsOpen = false }) { Text(stringResource(R.string.core_accounts_46)) }
                CoreTextField(draft.institutionKey, stringResource(R.string.core_accounts_47), saving) { changed(draft.copy(institutionKey = it)) }
                institutions.filter { draft.kind != AccountKind.INVESTMENT || it.kind == InstitutionKind.BROKER }.filter { draft.institutionKey.isBlank() || it.key.contains(draft.institutionKey, true) || it.chineseName.contains(draft.institutionKey) || it.englishName.contains(draft.institutionKey, true) }.take(20).forEach { institution ->
                    TextButton(onClick = { changed(draft.copy(institutionKey = institution.key, currency = if (draft.id == null) institution.currency else draft.currency, icon = institution.iconName, color = institution.color)); institutionsOpen = false }) { Text(if (chinese) institution.chineseName else institution.englishName) }
                }
            }
            CoreTextField(draft.icon, stringResource(R.string.core_accounts_48), saving) { changed(draft.copy(icon = it)) }
            CoreTextField(draft.color, stringResource(R.string.core_accounts_49), saving) { changed(draft.copy(color = it)) }
            CoreVisualStylePicker(draft.icon, draft.color, !saving, { changed(draft.copy(icon = it)) }, { changed(draft.copy(color = it)) })
        } }, confirmButton = { TextButton(onClick = save, enabled = !saving && draft.name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}

private enum class PocketAction { ADD, CALIBRATE, LIMIT, ANCHOR }
private data class PocketEditor(val action: PocketAction, val currency: String = "", val balance: String = "", val at: String = "", val anchorId: String? = null, val note: String = "")
@Composable
private fun PocketDialog(initial: PocketEditor, saving: Boolean, error: String?, dismiss: () -> Unit, save: (PocketEditor) -> Unit) {
    var editor by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text(when (editor.action) {
        PocketAction.ADD -> stringResource(R.string.core_accounts_50); PocketAction.CALIBRATE -> stringResource(R.string.core_accounts_19); PocketAction.LIMIT -> stringResource(R.string.core_accounts_51); PocketAction.ANCHOR -> stringResource(R.string.core_accounts_52)
    }) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (saving) Text(stringResource(R.string.core_accounts_saving_navigation))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (editor.action == PocketAction.ADD) CoreTextField(editor.currency, stringResource(R.string.core_accounts_53), saving) { editor = editor.copy(currency = it) } else Text(editor.currency)
        CoreTextField(editor.balance, if (editor.action == PocketAction.LIMIT) stringResource(R.string.core_accounts_54) else stringResource(R.string.core_accounts_55), saving) { editor = editor.copy(balance = it) }
        if (editor.action != PocketAction.LIMIT) {
            CoreTextField(editor.at, stringResource(R.string.core_accounts_56), saving) { editor = editor.copy(at = it) }
            Text(stringResource(R.string.core_accounts_57), style = MaterialTheme.typography.bodySmall)
        }
        if (editor.action == PocketAction.ANCHOR) CoreTextField(editor.note, stringResource(R.string.core_accounts_58), saving) { editor = editor.copy(note = it) }
    } }, dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text(stringResource(R.string.core_accounts_24)) } },
        confirmButton = { TextButton(onClick = { save(editor) }, enabled = !saving) { Text(stringResource(R.string.core_accounts_59)) } })
}

@Composable
internal fun CoreTextField(value: String, label: String, saving: Boolean, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AccountHistoryPanel(state: CoreAccountsUiState, model: CoreAccountsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !state.saving, onClick = model::openHistory) { Text(stringResource(R.string.core_full_cash_history)) }
        if (state.historyOpen) {
            Text(stringResource(R.string.core_full_cash_history_note), style = MaterialTheme.typography.bodySmall)
            CoreTextField(state.historyFrom, stringResource(R.string.core_full_history_from), state.saving) { model.historyInputs(it, state.historyTo, state.balanceAtInput) }
            CoreTextField(state.historyTo, stringResource(R.string.core_full_history_to), state.saving) { model.historyInputs(state.historyFrom, it, state.balanceAtInput) }
            CoreTextField(state.balanceAtInput, stringResource(R.string.core_full_history_at), state.saving) { model.historyInputs(state.historyFrom, state.historyTo, it) }
            Button(enabled = !state.saving && !state.historyLoading, onClick = model::queryHistory) { Text(stringResource(R.string.core_full_history_query)) }
            if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.historicalBalance?.let { Text(stringResource(R.string.core_full_balance_at, CoreMoneyPresentation.format(it, state.currency.orEmpty(), state.customSymbols))) }
            for (day in state.history) Text("${day.date.year}-${day.date.month}-${day.date.day} · ${CoreMoneyPresentation.format(day.balance, state.currency.orEmpty(), state.customSymbols)}")
        }
    }
}
