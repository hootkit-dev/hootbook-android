@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
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
import io.github.hootkit_dev.hootbook.presentation.core.analytics.BudgetCategoryPieChart
import io.github.hootkit_dev.hootbook.presentation.core.analytics.BudgetHistoryLineChart
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.DecimalAmountPad
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
    var actionMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var pickLimit by remember { mutableStateOf(false) }
    var showTypeWizard by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = state.selected?.name ?: stringResource(R.string.budgets),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(goBack) },
            actionContent = {
                state.selected?.let { selected ->
                    IconButton(onClick = { viewModel.edit(selected) }, enabled = !state.saving) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.edit_budget))
                    }
                    Box {
                        IconButton(onClick = { actionMenu = true }, enabled = !state.saving) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.budget_details)) }
                        DropdownMenu(actionMenu, { actionMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.core_budgets_history)) }, leadingIcon = { Icon(Icons.Rounded.History, null) }, onClick = { actionMenu = false; viewModel.history(true) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.budget_details)) }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { actionMenu = false; showInfo = true })
                            DropdownMenuItem(text = { Text(stringResource(if (selected.active) R.string.core_budgets_pause else R.string.core_budgets_resume)) }, onClick = { actionMenu = false; viewModel.active(selected) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.core_budgets_delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { actionMenu = false; viewModel.clearError(); deleting = selected })
                        }
                    }
                }
                IconButton(onClick = viewModel::refresh, enabled = !state.saving) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.core_budgets_refresh))
                }
            })
    }, floatingActionButton = {
        if (state.selectedId == null) ExtendedFloatingActionButton(onClick = { if (!state.saving) showTypeWizard = true },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.new_budget)) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.errorResource?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp)) }
            if (state.navigationBlocked) Text(stringResource(R.string.core_budgets_saving), modifier = Modifier.padding(16.dp))
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(12.dp)) {
                Text(error, Modifier.weight(1f))
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_budgets_clear_error)) }
            } } }
            val selected = state.selected
            when {
                state.selectedId == null -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.loading && state.rows.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🎯", style = MaterialTheme.typography.displayLarge)
                            Text(stringResource(R.string.no_budgets_yet), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.create_first_budget_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showTypeWizard = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.create_budget)) }
                        }
                    }
                    items(state.rows, key = { it.id }) { row ->
                        CoreBudgetCard(budget = row, onClick = { if (!state.saving) viewModel.select(row.id) },
                            onHistoryClick = { id -> if (!state.saving) viewModel.selectHistory(id) })
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                selected != null && state.showHistory -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    val detail = state.historyDetail
                    if (detail != null) {
                        item { CoreBudgetCard(budget = detail, onHistoryClick = { viewModel.history(true) }); CoreBudgetPeriodDivider(detail) }
                        if (detail.current.byCategory.isNotEmpty()) item { BudgetCategoryPieChart(categories = detail.current.byCategory, currency = detail.amount.currency, references = state.categories) }
                        item { Spacer(Modifier.height(24.dp)); CoreBudgetTransactionsUnavailable() }
                    } else {
                        if (state.history.isNotEmpty()) item {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(16.dp)) { BudgetHistoryLineChart(state.history.asReversed(), selected.amount.currency, selected.kind) }
                            }
                            Text(stringResource(R.string.core_history_read_only), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                        }
                        items(state.history, key = { it.start.budgetDateText() }) { row ->
                            CoreBudgetHistoryRow(row, selected.amount.currency, ListItemPosition.from(state.history.indexOf(row), state.history.size)) { if (!state.saving) viewModel.historyPeriod(row) }
                        }
                        if (state.historyCursor != null) item { OutlinedButton(onClick = viewModel::loadMoreHistory, enabled = !state.loading && !state.historyLoading) { Text(stringResource(R.string.core_history_load_older)) } }
                    }
                }
                selected != null -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { CoreBudgetCard(budget = selected, onHistoryClick = { viewModel.history(true) }); CoreBudgetPeriodDivider(selected) }
                    if (selected.current.byCategory.isNotEmpty()) item { BudgetCategoryPieChart(categories = selected.current.byCategory, currency = selected.amount.currency, references = state.categories) }
                    item {
                        CoreBudgetLimitsCard(selected, state.categories, !state.saving, onAdd = { pickLimit = true },
                            onEdit = { viewModel.editLimit(selected, it) }, onRemove = { viewModel.setLimit(selected, it, null) })
                    }
                    item { CoreBudgetTransactionsUnavailable() }
                }
            }
        }
    }
    if (showInfo) state.selected?.let { selected -> HootbookModalBottomSheet(onDismissRequest = { showInfo = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.budget_details), style = MaterialTheme.typography.headlineSmall)
            BudgetSummary(selected, state.displayCurrency)
            Text("${kindText(selected.kind)} · ${periodText(selected.period)}", style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.core_budgets_days, selected.current.daysTotal.toString(), selected.current.daysLeft.toString()))
            Text("${stringResource(R.string.core_budgets_daily_spent)}: ${selected.amount.currency} ${selected.current.dailySpent.toPlainString()}")
            Text(if (selected.accountIds == null) stringResource(R.string.core_budgets_all_accounts) else stringResource(R.string.core_budgets_accounts) + ": " + selected.accountIds.orEmpty().joinToString { id -> state.accounts.find { it.id == id }?.name ?: id })
        }
    } }
    if (pickLimit) state.selected?.let { selected -> HootbookModalBottomSheet(onDismissRequest = { pickLimit = false }) {
        Text(stringResource(R.string.add_limit), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            val categories = state.categories.filter { it.kind == if (selected.kind == BudgetKind.SAVINGS) CategoryKind.INCOME else CategoryKind.EXPENSE }
            if (categories.isEmpty()) item { Text(stringResource(R.string.core_budgets_no_categories), Modifier.padding(16.dp)) }
            items(categories, key = { it.id }) { category ->
                ListItem(headlineContent = { Text(category.displayName) }, leadingContent = { CoreRecordIcon(category.icon, Modifier.size(36.dp)) },
                    trailingContent = { IconButton(onClick = { pickLimit = false; viewModel.editLimit(selected, category) }, enabled = !state.saving) { Icon(Icons.Rounded.Add, stringResource(R.string.add_limit)) } })
            }
        }
    } }
    if (showTypeWizard) HootbookModalBottomSheet(onDismissRequest = { showTypeWizard = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BudgetKindSelectionSheet(onTypeSelected = { kind ->
            showTypeWizard = false; viewModel.edit(); viewModel.state.value.draft?.let { viewModel.draftChanged(it.copy(kind = kind)) }
        }, onDismiss = { showTypeWizard = false })
    }
    state.draft?.let { draft -> BudgetEditor(draft, state.accounts, state.categories, state.saving, state.error, viewModel::draftChanged, viewModel::dismissEditor,
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
    state.limitDraft?.let { editor -> HootbookModalBottomSheet(onDismissRequest = viewModel::dismissLimit) {
        Column(Modifier.fillMaxWidth().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(editor.name, style = MaterialTheme.typography.headlineSmall)
            AmountInput(editor.amount, state.selected?.amount?.currency.orEmpty(), {}, enabled = !state.saving)
            DecimalAmountPad(editor.amount, { if (!state.saving) viewModel.limitAmountChanged(it) })
            BudgetDialogError(state.error)
            Button(onClick = { viewModel.saveLimit() }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.core_budgets_save)) }
            if (editor.existing) TextButton(onClick = { viewModel.saveLimit(remove = true) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_budgets_limit_remove)) }
        }
    } }
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

@Composable private fun BudgetEditor(draft: CoreBudgetDraft, accounts: List<Account>, categories: List<Category>, saving: Boolean, error: String?,
    onChange: (CoreBudgetDraft) -> Unit, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val defaultName = periodText(draft.period)
    var pickingCategory by remember { mutableStateOf(false) }
    var editingLimit by remember { mutableStateOf<String?>(null) }
    var limitAmount by remember { mutableStateOf("") }
    HootbookModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (draft.id == null) R.string.new_budget else R.string.edit_budget), style = MaterialTheme.typography.headlineSmall)
            val previewColor = runCatching { Color(android.graphics.Color.parseColor(draft.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
            BudgetAnimatedGradientMeshCard(previewColor) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(draft.name.ifBlank { defaultName }, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(draft.amount, { onChange(draft.copy(amount = it)) },
                        label = { Text(stringResource(R.string.core_budgets_amount)) }, singleLine = true, enabled = !saving,
                        textStyle = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
                }
            }
            BudgetDialogError(error)
            BudgetField(draft.name, R.string.core_budgets_name, saving) { onChange(draft.copy(name = it)) }
            Text(stringResource(R.string.core_budgets_kind))
            FlowRow { listOf(BudgetKind.EXPENSE, BudgetKind.SAVINGS).forEach { kind -> FilterChip(selected = draft.kind == kind,
                onClick = { onChange(draft.copy(kind = kind)) }, enabled = !saving, label = { Text(kindText(kind)) }) } }
            Text(stringResource(R.string.core_budgets_period))
            FlowRow { BudgetPeriod.entries.filter { it != BudgetPeriod.UNKNOWN }.forEach { period -> FilterChip(selected = draft.period == period,
                onClick = { onChange(draft.copy(period = period)) }, enabled = !saving, label = { Text(periodText(period)) }) } }
            BudgetField(draft.currency, R.string.core_budgets_currency, saving) { onChange(draft.copy(currency = it)) }
            CorePlanDateField(draft.start, stringResource(R.string.core_budgets_start), saving,allowClear=false) { onChange(draft.copy(start = it)) }
            if (draft.period == BudgetPeriod.CUSTOM) CorePlanDateField(draft.end, stringResource(R.string.core_budgets_end), saving,allowClear=false) { onChange(draft.copy(end = it)) }
            ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(draft.color) }.getOrDefault(0xff4caf50.toInt()),
                onColorChanged = { if (!saving) onChange(draft.copy(color = String.format(java.util.Locale.ROOT, "#%06X", 0xffffff and it))) }, showTitle = true)
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
            if (draft.id == null) Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.category_limits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { pickingCategory = true }, enabled = !saving) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.add_limit)) }
                    }
                    if (draft.limits.isEmpty()) Text(stringResource(R.string.no_category_limits_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.limits.forEach { limit ->
                        val category = categories.find { it.id == limit.categoryId }
                        Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { editingLimit = limit.categoryId; limitAmount = limit.amount.toPlainString() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(category?.displayName ?: limit.categoryId); Text(CurrencyFormatter.formatCurrency(limit.amount, draft.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { onChange(draft.copy(limits = draft.limits.filterNot { it.categoryId == limit.categoryId })) }, enabled = !saving) { Icon(Icons.Rounded.Delete, stringResource(R.string.core_budgets_limit_remove)) }
                        }
                    }
                }
            } else Text(stringResource(R.string.core_budgets_limits_note))
            Button(onClick = { onSave(defaultName) }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Done, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.core_budgets_save))
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_budgets_cancel)) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingCategory) HootbookModalBottomSheet(onDismissRequest = { pickingCategory = false }) {
        Text(stringResource(R.string.add_limit), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(categories.filter { it.kind == if (draft.kind == BudgetKind.SAVINGS) CategoryKind.INCOME else CategoryKind.EXPENSE }, key = { it.id }) { category ->
                ListItem(headlineContent = { Text(category.displayName) }, leadingContent = { CoreRecordIcon(category.icon, Modifier.size(36.dp)) }, modifier = Modifier.clickable(enabled = !saving) {
                    pickingCategory = false; editingLimit = category.id; limitAmount = draft.limits.find { it.categoryId == category.id }?.amount?.toPlainString().orEmpty()
                })
            }
        }
    }
    editingLimit?.let { categoryId -> HootbookModalBottomSheet(onDismissRequest = { editingLimit = null }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(categories.find { it.id == categoryId }?.displayName ?: categoryId, style = MaterialTheme.typography.headlineSmall)
            AmountInput(limitAmount, draft.currency, {}, enabled = !saving)
            DecimalAmountPad(limitAmount, { if (!saving) limitAmount = it })
            Button(onClick = { limitAmount.toBigDecimalOrNull()?.let { amount -> onChange(draft.copy(limits = draft.limits.filterNot { it.categoryId == categoryId } + NewBudgetLimit(categoryId, amount))); editingLimit = null } }, enabled = !saving && limitAmount.toBigDecimalOrNull() != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save)) }
        }
    } }
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
