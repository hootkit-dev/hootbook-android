@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreTextField

@Composable
fun CoreSubscriptionsScreen(
    onBack: () -> Unit,
    onRecordPayment: (subscriptionId: String) -> Unit,
    subscriptionId: String? = null,
    viewModel: CoreSubscriptionsViewModel = hiltViewModel(),
    platform: CorePlanPlatformViewModel = hiltViewModel(),
    initialCreate: Boolean = false,
) {
    var nameFilter by rememberSaveable {mutableStateOf("")}
    var sortName by rememberSaveable {mutableStateOf(false)}
    val state by viewModel.state.collectAsStateWithLifecycle()
    var routedCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(subscriptionId) { viewModel.initialize(subscriptionId) }
    LaunchedEffect(initialCreate, state.loading) { if (initialCreate && !state.loading && !routedCreate) {
        routedCreate = true; if (state.form == null) viewModel.create()
    } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val back: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = back)
    var deleteId by remember { mutableStateOf<String?>(null) }
    var markPaidId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.commandRevision) { deleteId = null; markPaidId = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.selected?.name ?: stringResource(R.string.core_plans_subscriptions)) },
            navigationIcon = { TextButton(onClick = back, enabled = !state.saving) { Text(stringResource(R.string.core_plans_back)) } },
            actions = { TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_plans_refresh)) } })
    }, floatingActionButton = {
        if (state.selectedId == null && state.form == null) FloatingActionButton(onClick = viewModel::create) { Text("＋") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.errorResource?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))}
            PlanStatus(state.loading, state.saving, state.navigationBlocked, state.error, viewModel::clearError)
            state.undoHiddenId?.let {
                TextButton(onClick = viewModel::undoHidden, enabled = !state.saving) { Text(stringResource(R.string.core_plans_undo_hidden)) }
            }
            if (state.form != null) SubscriptionEditor(state.form!!, state.references, state.saving, viewModel::updateForm, viewModel::save)
            else if (state.selectedId == null) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(selected = state.includeHidden, onClick = { viewModel.includeHidden(!state.includeHidden) },
                        enabled = !state.saving, label = { Text(stringResource(R.string.core_plans_show_hidden)) })
                }
                OutlinedTextField(nameFilter,{nameFilter=it},label={Text(stringResource(R.string.core_detail_name_filter))},enabled=!state.saving,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true)
                FilterChip(selected=sortName,onClick={sortName=!sortName},label={Text(stringResource(R.string.core_detail_sort_name))})
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.summary?.let { summary -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.core_plans_summary, summary.count.toString()), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.core_plans_monthly, summary.currency, summary.monthly.toPlainString()))
                            Text(stringResource(R.string.core_plans_yearly, summary.currency, summary.yearly.toPlainString()))
                            Text(stringResource(R.string.core_plans_upcoming, summary.currency, summary.upcoming.toPlainString()))
                            if (!summary.complete) Text(stringResource(R.string.core_plans_missing_rates, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
                        } }
                    } }
                    if (!state.loading && state.subscriptions.isEmpty()) item { Text(stringResource(R.string.core_plans_empty_subscriptions)) }
                    items(state.subscriptions.filter{it.name.contains(nameFilter,ignoreCase=true)}.let{if(sortName)it.sortedBy{row->row.name.lowercase(java.util.Locale.ROOT)}else it}, key = { it.id }) { row ->
                        Card(onClick = { viewModel.select(row.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {CoreMerchantIcon(row.name,platform.assets,Modifier.size(48.dp));SubscriptionDescription(row) }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else state.selected?.let { row ->
                Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreMerchantIcon(row.name,platform.assets,Modifier.size(64.dp))
                    SubscriptionDescription(row)
                    Text(stringResource(R.string.core_plans_last_paid, row.lastPaid?.planDateText() ?: stringResource(R.string.core_plans_none)))
                    Text(stringResource(R.string.core_plans_end_value, row.endDate?.planDateText() ?: stringResource(R.string.core_plans_none)))
                    row.monthlyEquivalent?.let { Text(stringResource(R.string.core_plans_monthly, row.amount.currency, it.toPlainString())) }
                    PlanReferenceDescription(state.references, row.accountId, row.categoryId, row.subcategoryId)
                    row.notes?.let { Text(it) }
                    FlowRow {
                        TextButton(onClick = viewModel::edit, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = viewModel::toggleHidden, enabled = !state.saving) { Text(stringResource(if (row.hidden) R.string.core_plans_unhide else R.string.core_plans_hide)) }
                        TextButton(onClick = { deleteId = row.id }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_delete_permanent), color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = { if (viewModel.canNavigate()) onRecordPayment(row.id) }, enabled = !state.saving && !row.ended) { Text(stringResource(R.string.core_plans_record_payment)) }
                    OutlinedButton(onClick = { markPaidId = row.id }, enabled = !state.saving && row.cycle.unit != CycleUnit.UNKNOWN && !row.ended) { Text(stringResource(R.string.core_plans_mark_paid)) }
                    Text(stringResource(R.string.core_plans_payment_boundary), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { if (!state.saving) deleteId = null },
        title = { Text(stringResource(R.string.core_plans_delete_subscription_title)) },
        text = { Column { Text(stringResource(R.string.core_plans_delete_subscription_body)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(onClick = { deleteId = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = viewModel::delete, enabled = !state.saving && deleteId == state.selected?.id) { Text(stringResource(R.string.delete)) } })
    if (markPaidId != null) AlertDialog(onDismissRequest = { if (!state.saving) markPaidId = null },
        title = { Text(stringResource(R.string.core_plans_mark_paid)) }, text = { Column { Text(stringResource(R.string.core_plans_mark_paid_body)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(onClick = { markPaidId = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = viewModel::markPaid, enabled = !state.saving && markPaidId == state.selected?.id) { Text(stringResource(R.string.core_plans_confirm)) } })
}

@Composable
private fun SubscriptionDescription(row: Subscription) {
    Text(row.name, style = MaterialTheme.typography.titleLarge)
    Text("${row.amount.currency} ${row.amount.amount.toPlainString()}", style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.core_plans_cycle_value, row.cycle.every.toString(), cycleLabel(row.cycle.unit)))
    Text(stringResource(R.string.core_plans_next_due_value, row.nextDue?.planDateText() ?: stringResource(R.string.core_plans_none)))
    when {
        row.ended -> Text(stringResource(R.string.core_plans_ended))
        row.overdue -> Text(stringResource(R.string.core_plans_overdue), color = MaterialTheme.colorScheme.error)
        row.daysUntil != null -> Text(stringResource(R.string.core_plans_days_until, row.daysUntil.toString()))
    }
    if (row.hidden) Text(stringResource(R.string.core_plans_hidden))
}

@Composable
private fun SubscriptionEditor(form: CoreSubscriptionForm, refs: CorePlanReferences, saving: Boolean,
    update: (CoreSubscriptionForm) -> Unit, save: () -> Unit) {
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoreTextField(form.name, stringResource(R.string.core_plans_name), saving) { update(form.copy(name = it)) }
        CoreTextField(form.amount, stringResource(R.string.core_plans_amount), saving) { update(form.copy(amount = it)) }
        CoreTextField(form.currency, stringResource(R.string.core_plans_currency), saving) { update(form.copy(currency = it)) }
        CoreTextField(form.every, stringResource(R.string.core_plans_cycle_every), saving) { update(form.copy(every = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(CycleUnit.DAY, CycleUnit.WEEK, CycleUnit.MONTH, CycleUnit.YEAR).forEach { unit ->
                FilterChip(selected = form.unit == unit, onClick = { update(form.copy(unit = unit)) }, enabled = !saving, label = { Text(cycleLabel(unit)) })
            }
        }
        if (form.unit == CycleUnit.UNKNOWN) Text(stringResource(R.string.core_plans_unknown_cycle_edit), color = MaterialTheme.colorScheme.error)
        CorePlanDateField(form.nextDue, stringResource(R.string.core_plans_next_due), saving) { update(form.copy(nextDue = it)) }
        CorePlanDateField(form.endDate, stringResource(R.string.core_plans_end_date), saving) { update(form.copy(endDate = it)) }
        PlanReferencePicker(refs, form.accountId, form.categoryId, form.subcategoryId, CategoryKind.EXPENSE, saving,
            account = { update(form.copy(accountId = it)) }, category = { update(form.copy(categoryId = it, subcategoryId = null)) },
            subcategory = { update(form.copy(subcategoryId = it)) })
        CoreTextField(form.notes, stringResource(R.string.core_plans_notes), saving) { update(form.copy(notes = it)) }
        Button(onClick = save, enabled = !saving && form.unit != CycleUnit.UNKNOWN, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
    }
}

@Composable internal fun cycleLabel(unit: CycleUnit) = stringResource(when (unit) {
    CycleUnit.DAY -> R.string.core_plans_day; CycleUnit.WEEK -> R.string.core_plans_week
    CycleUnit.MONTH -> R.string.core_plans_month; CycleUnit.YEAR -> R.string.core_plans_year
    CycleUnit.UNKNOWN -> R.string.core_plans_unknown_cycle
})

@Composable internal fun PlanStatus(loading: Boolean, saving: Boolean, blocked: Boolean, error: String?, clear: () -> Unit) {
    if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (blocked) Text(stringResource(R.string.core_plans_saving_navigation), modifier = Modifier.padding(12.dp))
    error?.let { Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = clear) { Text(stringResource(R.string.core_plans_dismiss)) }
        }
    } }
}

@Composable internal fun PlanReferenceDescription(refs: CorePlanReferences, accountId: String?, categoryId: String?, subcategoryId: String?) {
    val none = stringResource(R.string.core_plans_none)
    Text(stringResource(R.string.core_plans_account_value, refs.accounts.find { it.id == accountId }?.name ?: accountId ?: none))
    val category = refs.categories.find { it.id == categoryId }
    Text(stringResource(R.string.core_plans_category_value, category?.displayName ?: categoryId ?: none))
    if (subcategoryId != null) Text(stringResource(R.string.core_plans_subcategory_value, category?.subcategories?.find { it.id == subcategoryId }?.displayName ?: subcategoryId))
}

/** Picks generated String IDs; a missing/deleted reference is displayed rather than silently discarded. */
@Composable internal fun PlanReferencePicker(refs: CorePlanReferences, accountId: String?, categoryId: String?, subcategoryId: String?,
    kind: CategoryKind?, saving: Boolean, account: (String?) -> Unit, category: (String?) -> Unit, subcategory: (String?) -> Unit) {
    Text(stringResource(R.string.core_plans_account), style = MaterialTheme.typography.titleSmall)
    PlanChoice(accountId == null, stringResource(R.string.core_plans_none), saving) { account(null) }
    refs.accounts.forEach { row -> PlanChoice(accountId == row.id, "${row.name} · ${row.mainCurrency}", saving) { account(row.id) } }
    if (accountId != null && refs.accounts.none { it.id == accountId }) Text(stringResource(R.string.core_plans_missing_reference, accountId), color = MaterialTheme.colorScheme.error)
    if (kind != null) {
        Text(stringResource(R.string.core_plans_category), style = MaterialTheme.typography.titleSmall)
        PlanChoice(categoryId == null, stringResource(R.string.core_plans_none), saving) { category(null) }
        refs.categories.filter { it.kind == kind }.forEach { row -> PlanChoice(categoryId == row.id, row.displayName, saving) { category(row.id) } }
        val selected = refs.categories.find { it.id == categoryId }
        if (categoryId != null && selected == null) Text(stringResource(R.string.core_plans_missing_reference, categoryId), color = MaterialTheme.colorScheme.error)
        selected?.let { row ->
            Text(stringResource(R.string.core_plans_subcategory), style = MaterialTheme.typography.titleSmall)
            PlanChoice(subcategoryId == null, stringResource(R.string.core_plans_none), saving) { subcategory(null) }
            row.subcategories.forEach { sub -> PlanChoice(subcategoryId == sub.id, sub.displayName, saving) { subcategory(sub.id) } }
            if (subcategoryId != null && row.subcategories.none { it.id == subcategoryId }) Text(stringResource(R.string.core_plans_missing_reference, subcategoryId), color = MaterialTheme.colorScheme.error)
        }
    }
}
@Composable private fun PlanChoice(selected: Boolean, label: String, saving: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !saving) { Text("${if (selected) "✓ " else ""}$label") }
}
