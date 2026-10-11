@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreAmountChart
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateField

/** A String ID can open a detail directly; all nested back handling stays local. */
@Composable
fun CoreBudgetsScreen(onBack: () -> Unit, initialBudgetId: String? = null,
    viewModel: CoreBudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(initialBudgetId) { initialBudgetId?.let(viewModel::select) }
    var deleting by remember { mutableStateOf<Budget?>(null) }
    LaunchedEffect(state.commandRevision) { deleting = null }
    Scaffold(topBar = { TopAppBar(title = { Text(state.selected?.name ?: stringResource(R.string.core_budgets_title)) },
        navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_back)) } },
        actions = { TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_refresh)) } }) },
        floatingActionButton = { if (state.selectedId == null) FloatingActionButton(onClick = { if (!state.saving) viewModel.edit() }) {
            Text(stringResource(R.string.core_budgets_add))
        } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.errorResource?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp)) }
            if (state.navigationBlocked) Text(stringResource(R.string.core_budgets_saving), modifier = Modifier.padding(16.dp))
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(12.dp)) {
                Text(error, Modifier.weight(1f))
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_budgets_clear_error)) }
            } } }
            val selected = state.selected
            when {
                state.selectedId == null -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.loading && state.rows.isEmpty()) item { Text(stringResource(R.string.core_budgets_empty)) }
                    items(state.rows, key = { it.id }) { row -> Card(onClick = { viewModel.select(row.id) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleLarge)
                            Text("${kindText(row.kind)} · ${periodText(row.period)}")
                            BudgetSummary(row, state.displayCurrency)
                        }
                    } }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                selected != null && state.showHistory -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {Text(stringResource(R.string.core_history_read_only));CoreAmountChart(state.history.asReversed().map{it.spent})}
                    if(state.historyLoading||state.historyDetailLoading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                    val detail=state.historyDetail
                    if(detail!=null){
                        item{Text("${detail.current.start.budgetDateText()} — ${detail.current.end.budgetDateText()}",style=MaterialTheme.typography.titleLarge);BudgetSummary(detail,state.displayCurrency);Text(stringResource(R.string.core_history_categories),style=MaterialTheme.typography.titleMedium)}
                        items(detail.current.byCategory,key={it.categoryId}){spend->Text("${state.categories.find{it.id==spend.categoryId}?.displayName?:spend.categoryId} · ${spend.spent.toPlainString()} ${detail.amount.currency}")}
                        item{Text(stringResource(R.string.core_history_transaction_gap),style=MaterialTheme.typography.bodySmall)}
                    }else{
                        items(state.history,key={it.start.budgetDateText()}){row->OutlinedCard(onClick={viewModel.historyPeriod(row)},enabled=!state.saving,modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(16.dp)){Text("${row.start.budgetDateText()} — ${row.end.budgetDateText()}",style=MaterialTheme.typography.titleMedium);Text("${selected.amount.currency} ${row.spent.toPlainString()} / ${row.amount.toPlainString()}")
                                if(row.over)Text(stringResource(if(selected.kind==BudgetKind.SAVINGS)R.string.core_budgets_goal else R.string.core_budgets_over));if(!row.complete)Text(stringResource(R.string.core_budgets_history_incomplete),color=MaterialTheme.colorScheme.error)
                            }
                        }}
                        if(state.historyCursor!=null)item{OutlinedButton(onClick=viewModel::loadMoreHistory,enabled=!state.loading&&!state.historyLoading){Text(stringResource(R.string.core_history_load_older))}}
                    }
                }
                selected != null -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("${kindText(selected.kind)} · ${periodText(selected.period)}", style = MaterialTheme.typography.titleMedium)
                        BudgetSummary(selected, state.displayCurrency)
                        Text(stringResource(R.string.core_budgets_days, selected.current.daysTotal.toString(), selected.current.daysLeft.toString()))
                        Text("${stringResource(R.string.core_budgets_daily_allowance)}：${selected.amount.currency} ${selected.current.dailyAllowance.toPlainString()}")
                        Text("${stringResource(R.string.core_budgets_daily_spent)}：${selected.amount.currency} ${selected.current.dailySpent.toPlainString()}")
                        Text(if (selected.accountIds == null) stringResource(R.string.core_budgets_all_accounts)
                            else stringResource(R.string.core_budgets_accounts) + "：" + selected.accountIds.orEmpty().joinToString { id -> state.accounts.find { it.id == id }?.name ?: id })
                        FlowRow {
                            TextButton(onClick = { viewModel.edit(selected) }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_edit)) }
                            TextButton(onClick = { viewModel.history(true) }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_history)) }
                            TextButton(onClick = { viewModel.active(selected) }, enabled = !state.saving) { Text(stringResource(if (selected.active) R.string.core_budgets_pause else R.string.core_budgets_resume)) }
                            TextButton(onClick = { viewModel.clearError(); deleting = selected }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    item { Text(stringResource(R.string.core_budgets_limits), style = MaterialTheme.typography.titleLarge) }
                    val categories = state.categories.filter { it.kind == if (selected.kind == BudgetKind.SAVINGS) CategoryKind.INCOME else CategoryKind.EXPENSE }
                    if (categories.isEmpty()) item { Text(stringResource(R.string.core_budgets_no_categories)) }
                    items(categories, key = { it.id }) { category ->
                        val status = selected.current.limits.find { it.categoryId == category.id }
                        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text(category.displayName, style = MaterialTheme.typography.titleMedium)
                            status?.let {
                                Text("${selected.amount.currency} ${it.amount.toPlainString()}")
                                Text(stringResource(R.string.core_budgets_limit_status, it.spent.toPlainString(), it.remaining.toPlainString()))
                                if (it.over) Text(stringResource(if (selected.kind == BudgetKind.SAVINGS) R.string.core_budgets_goal else R.string.core_budgets_over))
                            }
                            TextButton(onClick = { viewModel.editLimit(selected, category) }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_limit_edit)) }
                        } }
                    }
                    item { Text(stringResource(R.string.core_budgets_by_category), style = MaterialTheme.typography.titleLarge) }
                    items(selected.current.byCategory, key = { it.categoryId }) { spend ->
                        Text("${state.categories.find { it.id == spend.categoryId }?.displayName ?: spend.categoryId}：${selected.amount.currency} ${spend.spent.toPlainString()}")
                    }
                }
            }
        }
    }
    state.draft?.let { draft -> BudgetEditor(draft, state.accounts, state.saving, state.error, viewModel::draftChanged, viewModel::dismissEditor,
        onSave = { defaultName -> viewModel.save(defaultName) }) }
    deleting?.let { row -> AlertDialog(onDismissRequest = { if (!state.saving) deleting = null },
        title = { Text(stringResource(R.string.core_budgets_delete)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_budgets_delete_message))
                BudgetDialogError(state.error)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.delete(row) }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_confirm)) } },
        dismissButton = { TextButton(onClick = { deleting = null }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_cancel)) } }) }
    state.limitDraft?.let { editor -> AlertDialog(onDismissRequest = viewModel::dismissLimit, title = { Text(editor.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = editor.amount, onValueChange = viewModel::limitAmountChanged, enabled = !state.saving,
                label = { Text(stringResource(R.string.core_budgets_limit_amount)) })
            BudgetDialogError(state.error)
        } },
        confirmButton = { TextButton(onClick = { viewModel.saveLimit() }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_save)) } },
        dismissButton = { Row {
            if (editor.existing) TextButton(onClick = { viewModel.saveLimit(remove = true) }, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_limit_remove)) }
            TextButton(onClick = viewModel::dismissLimit, enabled = !state.saving) { Text(stringResource(R.string.core_budgets_cancel)) }
        } }) }
}

@Composable private fun BudgetSummary(row: Budget, displayCurrency: String) {
    Text("${row.current.start.budgetDateText()} — ${row.current.end.budgetDateText()}")
    val fraction = budgetProgressFraction(row.current.spent, row.amount.amount)
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = when {
        row.kind == BudgetKind.SAVINGS -> MaterialTheme.colorScheme.primary
        row.current.over -> MaterialTheme.colorScheme.error
        fraction > 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    })
    Text("${row.amount.currency} ${row.current.spent.toPlainString()} / ${row.amount.amount.toPlainString()}", style = MaterialTheme.typography.titleLarge)
    Text("${stringResource(if (row.kind == BudgetKind.SAVINGS) R.string.core_budgets_saved else R.string.core_budgets_spent)}：${row.current.spent.toPlainString()}")
    Text("${stringResource(R.string.core_budgets_remaining)}：${row.current.remaining.toPlainString()}")
    if (!row.active) Text(stringResource(R.string.core_budgets_inactive))
    if (row.accountIds?.isEmpty() == true) Text(stringResource(R.string.core_budgets_removed_accounts), color = MaterialTheme.colorScheme.error)
    if (row.current.over) Text(stringResource(if (row.kind == BudgetKind.SAVINGS) R.string.core_budgets_goal else R.string.core_budgets_over),
        color = if (row.kind == BudgetKind.SAVINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    if (!row.current.complete) Text(stringResource(R.string.core_budgets_missing, row.current.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
    row.display?.let { Text(stringResource(R.string.core_budgets_display, it.currency, it.spent.toPlainString(), it.amount.toPlainString())) }
    if (row.display == null && displayCurrency.isNotBlank() && displayCurrency != row.amount.currency) {
        Text(stringResource(R.string.core_budgets_display_missing, displayCurrency))
    }
}

@Composable private fun BudgetDialogError(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable private fun BudgetEditor(draft: CoreBudgetDraft, accounts: List<Account>, saving: Boolean, error: String?,
    onChange: (CoreBudgetDraft) -> Unit, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val defaultName = periodText(draft.period)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.core_budgets_edit)) },
        text = { Column(Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetDialogError(error)
            BudgetField(draft.name, R.string.core_budgets_name, saving) { onChange(draft.copy(name = it)) }
            Text(stringResource(R.string.core_budgets_kind))
            FlowRow { listOf(BudgetKind.EXPENSE, BudgetKind.SAVINGS).forEach { kind -> FilterChip(selected = draft.kind == kind,
                onClick = { onChange(draft.copy(kind = kind)) }, enabled = !saving, label = { Text(kindText(kind)) }) } }
            Text(stringResource(R.string.core_budgets_period))
            FlowRow { BudgetPeriod.entries.filter { it != BudgetPeriod.UNKNOWN }.forEach { period -> FilterChip(selected = draft.period == period,
                onClick = { onChange(draft.copy(period = period)) }, enabled = !saving, label = { Text(periodText(period)) }) } }
            BudgetField(draft.amount, R.string.core_budgets_amount, saving) { onChange(draft.copy(amount = it)) }
            BudgetField(draft.currency, R.string.core_budgets_currency, saving) { onChange(draft.copy(currency = it)) }
            CorePlanDateField(draft.start, stringResource(R.string.core_budgets_start), saving,allowClear=false) { onChange(draft.copy(start = it)) }
            if (draft.period == BudgetPeriod.CUSTOM) CorePlanDateField(draft.end, stringResource(R.string.core_budgets_end), saving,allowClear=false) { onChange(draft.copy(end = it)) }
            BudgetField(draft.color, R.string.core_budgets_color, saving) { onChange(draft.copy(color = it)) }
            Row { Checkbox(draft.allAccounts, onCheckedChange = { onChange(draft.copy(allAccounts = it)) }, enabled = !saving)
                Text(stringResource(R.string.core_budgets_all_accounts)) }
            if (!draft.allAccounts) {
                Text(stringResource(R.string.core_budgets_accounts))
                if (accounts.isEmpty()) Text(stringResource(R.string.core_budgets_no_accounts))
                accounts.forEach { account -> Row {
                    Checkbox(account.id in draft.accountIds, onCheckedChange = { checked -> onChange(draft.copy(accountIds = if (checked) draft.accountIds + account.id else draft.accountIds - account.id)) }, enabled = !saving)
                    Text(listOfNotNull(account.name, account.last4).joinToString(" · "))
                } }
                (draft.accountIds - accounts.map { it.id }.toSet()).forEach { id -> Row {
                    Checkbox(true, onCheckedChange = { onChange(draft.copy(accountIds = draft.accountIds - id)) }, enabled = !saving)
                    Text(stringResource(R.string.core_budgets_unavailable_account, id))
                } }
            }
            Text(stringResource(R.string.core_budgets_limits_note))
        } }, confirmButton = { TextButton(onClick = { onSave(defaultName) }, enabled = !saving) { Text(stringResource(R.string.core_budgets_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.core_budgets_cancel)) } })
}
@Composable private fun BudgetField(value: String, label: Int, saving: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable private fun kindText(kind: BudgetKind) = stringResource(when (kind) {
    BudgetKind.EXPENSE -> R.string.core_budgets_expense; BudgetKind.SAVINGS -> R.string.core_budgets_savings; BudgetKind.UNKNOWN -> R.string.core_budgets_unknown
})
@Composable private fun periodText(period: BudgetPeriod) = stringResource(when (period) {
    BudgetPeriod.DAILY -> R.string.core_budgets_daily; BudgetPeriod.WEEKLY -> R.string.core_budgets_weekly
    BudgetPeriod.MONTHLY -> R.string.core_budgets_monthly; BudgetPeriod.YEARLY -> R.string.core_budgets_yearly
    BudgetPeriod.CUSTOM -> R.string.core_budgets_custom; BudgetPeriod.UNKNOWN -> R.string.core_budgets_unknown
})
