@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.analytics.BudgetCategoryPieChart
import io.github.hootkit_dev.hootbook.presentation.core.analytics.BudgetHistoryLineChart
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreColorPickerRow
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateField
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.utils.DateFormats

/**
 * Budgets: the cards, one budget's page (its card, category split and limits), and its period
 * history. Edit is in the bar; history, details, pause and delete are behind ⋮.
 */
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
    var showInfo by remember { mutableStateOf(false) }
    var pickLimit by remember { mutableStateOf(false) }
    var showTypeWizard by rememberSaveable { mutableStateOf(false) }
    val selected = state.selected
    val enabled = !state.saving
    SecondaryScreen(
        title = when {
            selected != null && state.showHistory -> stringResource(R.string.core_budgets_history)
            selected != null -> selected.name
            else -> stringResource(R.string.budgets)
        },
        onBack = goBack,
        actions = {
            if (selected != null && !state.showHistory) {
                TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_budget), { viewModel.edit(selected) }, enabled = enabled)
                OverflowMenu(enabled = enabled, actions = listOf(
                    MenuAction(stringResource(R.string.core_budgets_history), Icons.Rounded.History) { viewModel.history(true) },
                    MenuAction(stringResource(R.string.budget_details), Icons.Rounded.Info) { showInfo = true },
                    MenuAction(stringResource(if (selected.active) R.string.core_budgets_pause else R.string.core_budgets_resume),
                        if (selected.active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility) { viewModel.active(selected) },
                    MenuAction(stringResource(R.string.core_budgets_delete), Icons.Rounded.Delete, destructive = true) { viewModel.clearError(); deleting = selected },
                ))
            }
        },
        floatingActionButton = {
            if (state.selectedId == null) ExtendedFloatingActionButton(onClick = { if (enabled) showTypeWizard = true },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.new_budget)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.core_budgets_saving), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            (state.error ?: state.errorResource?.let { stringResource(it) })?.let {
                ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                state.selectedId == null -> BudgetList(state, viewModel, onCreate = { showTypeWizard = true })
                selected != null && state.showHistory -> BudgetHistory(selected, state, viewModel)
                selected != null -> BudgetDetail(selected, state, viewModel, onAddLimit = { pickLimit = true })
            }
        }
    }
    if (showInfo) selected?.let { BudgetInfoSheet(it, state) { showInfo = false } }
    if (pickLimit) selected?.let { budget -> CategoryPickerSheet(state.categories, budget.kind, { pickLimit = false }) { category ->
        pickLimit = false; viewModel.editLimit(budget, category)
    } }
    if (showTypeWizard) HootbookModalBottomSheet(onDismissRequest = { showTypeWizard = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BudgetKindSelectionSheet(onTypeSelected = { kind ->
            showTypeWizard = false; viewModel.edit(); viewModel.state.value.draft?.let { viewModel.draftChanged(it.copy(kind = kind)) }
        }, onDismiss = { showTypeWizard = false })
    }
    state.draft?.let { draft -> BudgetEditor(draft, state.accounts, state.categories, state.saving, state.error, viewModel::draftChanged, viewModel::dismissEditor,
        onSave = { defaultName -> viewModel.save(defaultName) }) }
    deleting?.let { row -> ConfirmDialog(stringResource(R.string.core_budgets_delete), stringResource(R.string.core_budgets_delete_message),
        stringResource(R.string.delete), { viewModel.delete(row) }, { deleting = null }, destructive = true, enabled = enabled) }
    state.limitDraft?.let { editor ->
        FormSheet(editor.name, viewModel::dismissLimit, { viewModel.saveLimit() }, busy = state.saving, error = state.error,
            confirmEnabled = editor.amount.toBigDecimalOrNull() != null,
            titleAction = if (editor.existing) ({ TextButton(onClick = { viewModel.saveLimit(remove = true) }, enabled = enabled) {
                Text(stringResource(R.string.core_budgets_limit_remove), color = MaterialTheme.colorScheme.error) } }) else null) {
            FormTextField(editor.amount, stringResource(R.string.core_budgets_limit_amount), viewModel::limitAmountChanged, enabled = enabled, decimal = true,
                supporting = state.selected?.amount?.currency)
        }
    }
}

@Composable
private fun BudgetList(state: CoreBudgetsUiState, viewModel: CoreBudgetsViewModel, onCreate: () -> Unit) {
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.loading && state.rows.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Savings, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_budgets_yet), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.create_first_budget_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.create_budget)) }
            }
        }
        items(state.rows, key = { it.id }) { row ->
            CoreBudgetCard(budget = row, onClick = { if (!state.saving) viewModel.select(row.id) },
                onHistoryClick = { id -> if (!state.saving) viewModel.selectHistory(id) })
        }
    }
}

/** The budget's card and period, how it splits by category, then its category limits. */
@Composable
private fun BudgetDetail(budget: Budget, state: CoreBudgetsUiState, viewModel: CoreBudgetsViewModel, onAddLimit: () -> Unit) {
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item(key = "card") { CoreBudgetCard(budget = budget, onHistoryClick = { viewModel.history(true) }); CoreBudgetPeriodDivider(budget) }
        if (budget.current.byCategory.isNotEmpty()) item(key = "split") {
            Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                BudgetCategoryPieChart(Modifier.padding(16.dp), categories = budget.current.byCategory, currency = budget.amount.currency, references = state.categories)
            }
        }
        item(key = "limits") {
            CoreBudgetLimitsSection(budget, state.categories, !state.saving, onAdd = onAddLimit, onEdit = { viewModel.editLimit(budget, it) })
        }
    }
}

/** Past periods: a chart of all loaded, then one row each; a row opens that period's card. */
@Composable
private fun BudgetHistory(budget: Budget, state: CoreBudgetsUiState, viewModel: CoreBudgetsViewModel) {
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val detail = state.historyDetail
        if (detail != null) {
            item(key = "card") { CoreBudgetCard(budget = detail, onHistoryClick = { viewModel.history(true) }); CoreBudgetPeriodDivider(detail) }
            if (detail.current.byCategory.isNotEmpty()) item(key = "split") {
                Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(top = 16.dp)) {
                    BudgetCategoryPieChart(Modifier.padding(16.dp), categories = detail.current.byCategory, currency = detail.amount.currency, references = state.categories)
                }
            }
            return@LazyColumn
        }
        if (state.history.isNotEmpty()) item(key = "chart") {
            Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) { BudgetHistoryLineChart(state.history.asReversed(), budget.amount.currency, budget.kind) }
            }
            Text(stringResource(R.string.core_history_read_only), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.history, key = { it.start.budgetDateText() }) { row ->
            CoreBudgetHistoryRow(row, budget.amount.currency, ListItemPosition.from(state.history.indexOf(row), state.history.size)) { if (!state.saving) viewModel.historyPeriod(row) }
        }
        if (state.historyCursor != null) item(key = "more") {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = viewModel::loadMoreHistory, enabled = !state.loading && !state.historyLoading) { Text(stringResource(R.string.core_history_load_older)) }
            }
        }
    }
}

/** The budget's numbers as label / value rows. */
@Composable
private fun BudgetInfoSheet(row: Budget, state: CoreBudgetsUiState, onDismiss: () -> Unit) {
    val money = { amount: java.math.BigDecimal -> CoreMoneyPresentation.format(amount, row.amount.currency) }
    HootbookModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.budget_details), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall)
            ListSection {
                infoRow(R.string.core_budgets_kind, kindText(row.kind) + " · " + periodText(row.period))
                infoRow(R.string.core_budgets_period, DateFormats.range(row.current.start.historyJavaDate(), row.current.end.historyJavaDate()))
                infoRow(if (row.kind == BudgetKind.SAVINGS) R.string.core_budgets_saved else R.string.core_budgets_spent, money(row.current.spent))
                infoRow(R.string.core_budgets_remaining, money(row.current.remaining))
                infoRow(R.string.core_budgets_daily_spent, money(row.current.dailySpent))
                item(headline = { Text(stringResource(R.string.core_budgets_days, row.current.daysTotal.toString(), row.current.daysLeft.toString())) })
                item(headline = { Text(if (row.accountIds == null) stringResource(R.string.core_budgets_all_accounts) else stringResource(R.string.core_budgets_accounts)) },
                    supporting = row.accountIds?.let { ids -> { Text(ids.joinToString { id -> state.accounts.find { it.id == id }?.name ?: id }) } })
                row.display?.let { item(headline = { Text(stringResource(R.string.core_budgets_display, it.currency,
                    CoreMoneyPresentation.format(it.spent, it.currency), CoreMoneyPresentation.format(it.amount, it.currency))) }) }
            }
            val notes = buildList {
                if (!row.active) add(stringResource(R.string.core_budgets_inactive) to false)
                if (row.accountIds?.isEmpty() == true) add(stringResource(R.string.core_budgets_removed_accounts) to true)
                if (row.current.over) add(stringResource(if (row.kind == BudgetKind.SAVINGS) R.string.core_budgets_goal else R.string.core_budgets_over) to (row.kind != BudgetKind.SAVINGS))
                if (!row.current.complete) add(stringResource(R.string.core_budgets_missing, row.current.missingCurrencies.joinToString()) to true)
                if (row.display == null && state.displayCurrency.isNotBlank() && state.displayCurrency != row.amount.currency)
                    add(stringResource(R.string.core_budgets_display_missing, state.displayCurrency) to false)
            }
            notes.forEach { (text, error) -> Text(text, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun ListSectionScope.infoRow(label: Int, value: String) = item(
    headline = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant) },
    trailing = { Text(value, style = MaterialTheme.typography.bodyLarge) },
)

/** The categories a limit can be set for: expense categories, or income ones for a savings goal. */
@Composable
private fun CategoryPickerSheet(categories: List<Category>, kind: BudgetKind, onDismiss: () -> Unit, onPick: (Category) -> Unit) {
    val rows = categories.filter { it.kind == if (kind == BudgetKind.SAVINGS) CategoryKind.INCOME else CategoryKind.EXPENSE }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.add_limit), Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (rows.isEmpty()) item { Text(stringResource(R.string.core_budgets_no_categories), Modifier.padding(16.dp)) }
            else item { ListSection { rows.forEach { category ->
                item(leading = { CoreRecordIcon(category.icon, Modifier.size(36.dp)) }, headline = { Text(category.displayName) }, onClick = { onPick(category) })
            } } }
        }
    }
}

/** New or changed budget: amount, name, kind, period and dates, currency, color, accounts, limits. */
@Composable private fun BudgetEditor(draft: CoreBudgetDraft, accounts: List<Account>, categories: List<Category>, saving: Boolean, error: String?,
    onChange: (CoreBudgetDraft) -> Unit, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val defaultName = periodText(draft.period)
    var pickingCategory by remember { mutableStateOf(false) }
    var editingLimit by remember { mutableStateOf<String?>(null) }
    var limitAmount by remember { mutableStateOf("") }
    FormSheet(stringResource(if (draft.id == null) R.string.new_budget else R.string.edit_budget), onDismiss, { onSave(defaultName) },
        busy = saving, error = error, confirmEnabled = draft.amount.toBigDecimalOrNull() != null) {
        FormTextField(draft.amount, stringResource(R.string.core_budgets_amount), { onChange(draft.copy(amount = it)) }, enabled = !saving, decimal = true)
        FormTextField(draft.name, stringResource(R.string.core_budgets_name), { onChange(draft.copy(name = it)) }, enabled = !saving)
        GenericTypeSwitcher(listOf(BudgetKind.EXPENSE, BudgetKind.SAVINGS).indexOf(draft.kind).coerceAtLeast(0),
            { if (!saving) onChange(draft.copy(kind = listOf(BudgetKind.EXPENSE, BudgetKind.SAVINGS)[it])) },
            listOf(kindText(BudgetKind.EXPENSE), kindText(BudgetKind.SAVINGS)), Modifier.fillMaxWidth())
        SectionTitle(stringResource(R.string.core_budgets_period), indent = 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetPeriod.entries.filter { it != BudgetPeriod.UNKNOWN }.forEach { period -> FilterChip(selected = draft.period == period,
                onClick = { onChange(draft.copy(period = period)) }, enabled = !saving, label = { Text(periodText(period)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { CorePlanDateField(draft.start, stringResource(R.string.core_budgets_start), saving, allowClear = false) { onChange(draft.copy(start = it)) } }
            if (draft.period == BudgetPeriod.CUSTOM) Box(Modifier.weight(1f)) {
                CorePlanDateField(draft.end, stringResource(R.string.core_budgets_end), saving, allowClear = false) { onChange(draft.copy(end = it)) }
            }
        }
        FormTextField(draft.currency, stringResource(R.string.core_budgets_currency), { onChange(draft.copy(currency = it.uppercase())) }, enabled = !saving)
        CoreColorPickerRow(draft.color, !saving) { onChange(draft.copy(color = it)) }
        SectionTitle(stringResource(R.string.core_budgets_accounts), indent = 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(draft.allAccounts, { onChange(draft.copy(allAccounts = true)) }, enabled = !saving,
                label = { Text(stringResource(R.string.core_budgets_all_accounts)) })
            accounts.forEach { account ->
                FilterChip(!draft.allAccounts && account.id in draft.accountIds, {
                    val ids = if (account.id in draft.accountIds && !draft.allAccounts) draft.accountIds - account.id else draft.accountIds + account.id
                    onChange(draft.copy(allAccounts = ids.isEmpty(), accountIds = ids))
                }, enabled = !saving, label = { Text(account.name) })
            }
            (draft.accountIds - accounts.map { it.id }.toSet()).forEach { id ->
                FilterChip(true, { onChange(draft.copy(accountIds = draft.accountIds - id)) }, enabled = !saving,
                    label = { Text(stringResource(R.string.core_budgets_unavailable_account, id)) })
            }
        }
        if (draft.id == null) ListSection(
            title = stringResource(R.string.core_budgets_limits),
            action = { TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.add_limit), { pickingCategory = true }, enabled = !saving) },
        ) {
            if (draft.limits.isEmpty()) item(headline = { Text(stringResource(R.string.no_category_limits_desc), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) })
            draft.limits.forEach { limit ->
                val category = categories.find { it.id == limit.categoryId }
                item(leading = { CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(36.dp)) }, headline = { Text(category?.displayName ?: limit.categoryId) },
                    trailing = { Text(CoreMoneyPresentation.format(limit.amount, draft.currency)) },
                    onClick = { if (!saving) { editingLimit = limit.categoryId; limitAmount = limit.amount.toPlainString() } })
            }
        } else Text(stringResource(R.string.core_budgets_limits_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pickingCategory) CategoryPickerSheet(categories, draft.kind, { pickingCategory = false }) { category ->
        pickingCategory = false; editingLimit = category.id; limitAmount = draft.limits.find { it.categoryId == category.id }?.amount?.toPlainString().orEmpty()
    }
    editingLimit?.let { categoryId ->
        val existing = draft.limits.any { it.categoryId == categoryId }
        FormSheet(categories.find { it.id == categoryId }?.displayName ?: categoryId, { editingLimit = null }, {
            limitAmount.toBigDecimalOrNull()?.let { amount -> onChange(draft.copy(limits = draft.limits.filterNot { it.categoryId == categoryId } + NewBudgetLimit(categoryId, amount))) }
            editingLimit = null
        }, confirmEnabled = limitAmount.toBigDecimalOrNull() != null,
            titleAction = if (existing) ({ TextButton(onClick = { onChange(draft.copy(limits = draft.limits.filterNot { it.categoryId == categoryId })); editingLimit = null }) {
                Text(stringResource(R.string.core_budgets_limit_remove), color = MaterialTheme.colorScheme.error) } }) else null) {
            FormTextField(limitAmount, stringResource(R.string.core_budgets_limit_amount), { limitAmount = it }, decimal = true, supporting = draft.currency)
        }
    }
}

@Composable private fun kindText(kind: BudgetKind) = stringResource(when (kind) {
    BudgetKind.EXPENSE -> R.string.core_budgets_expense; BudgetKind.SAVINGS -> R.string.core_budgets_savings; BudgetKind.UNKNOWN -> R.string.core_budgets_unknown
})
@Composable private fun periodText(period: BudgetPeriod) = stringResource(when (period) {
    BudgetPeriod.DAILY -> R.string.core_budgets_daily; BudgetPeriod.WEEKLY -> R.string.core_budgets_weekly
    BudgetPeriod.MONTHLY -> R.string.core_budgets_monthly; BudgetPeriod.YEARLY -> R.string.core_budgets_yearly
    BudgetPeriod.CUSTOM -> R.string.core_budgets_custom; BudgetPeriod.UNKNOWN -> R.string.core_budgets_unknown
})
