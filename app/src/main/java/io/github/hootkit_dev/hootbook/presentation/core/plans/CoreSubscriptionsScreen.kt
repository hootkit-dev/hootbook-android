@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.SubtitleTag
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.DecimalAmountPad
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
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
    onNewTransaction: (() -> Unit)? = null,
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(if (state.form != null) R.string.add_subscription else R.string.core_plans_subscriptions),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(back) }, actionContent = {
                IconButton(onClick = viewModel::refresh, enabled = !state.saving) { Icon(Icons.Rounded.Refresh, stringResource(R.string.core_plans_refresh)) }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.errorResource?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))}
            PlanStatus(state.loading, state.saving, state.navigationBlocked, state.error, viewModel::clearError)
            state.undoHiddenId?.let {
                TextButton(onClick = viewModel::undoHidden, enabled = !state.saving) { Text(stringResource(R.string.core_plans_undo_hidden)) }
            }
            if (state.form != null) {
                if (state.form!!.id == null && onNewTransaction != null) GenericTypeSwitcher(selectedIndex = 1,
                    onIndexChange = { if (it == 0 && viewModel.canNavigate()) onNewTransaction() },
                    options = listOf(stringResource(R.string.transaction), stringResource(R.string.subscription)), modifier = Modifier.padding(horizontal = 16.dp))
                SubscriptionEditor(state.form!!, state.references, state.saving, viewModel::updateForm, viewModel::save)
            } else {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(selected = state.includeHidden, onClick = { viewModel.includeHidden(!state.includeHidden) },
                        enabled = !state.saving, label = { Text(stringResource(R.string.core_plans_show_hidden)) })
                }
                OutlinedTextField(nameFilter,{nameFilter=it},label={Text(stringResource(R.string.core_detail_name_filter))},enabled=!state.saving,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true)
                FilterChip(selected=sortName,onClick={sortName=!sortName},label={Text(stringResource(R.string.core_detail_sort_name))})
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.summary?.let { summary -> item { CoreSubscriptionSummary(summary) } }
                    if (!state.loading && state.subscriptions.isEmpty()) item { Text(stringResource(R.string.core_plans_empty_subscriptions)) }
                    items(state.subscriptions.filter { (state.includeHidden || !it.hidden) && it.name.contains(nameFilter, ignoreCase = true) }.let { if (sortName) it.sortedBy { row -> row.name.lowercase(java.util.Locale.ROOT) } else it }, key = { it.id }) { row ->
                        CoreSubscriptionRow(row, platform, state.references, !state.saving,
                            onClick = { viewModel.select(row.id) }, onEdit = { viewModel.edit(row) }, onHide = { viewModel.toggleHidden(row) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    if (state.form == null) state.selected?.let { row ->
        HootbookModalBottomSheet(onDismissRequest = { viewModel.select(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
    var amountPad by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AmountInput(form.amount, CurrencyFormatter.getCurrencySymbol(form.currency), onClick = { amountPad = true }, enabled = !saving)
        CoreTextField(form.name, stringResource(R.string.service_name_required), saving) { update(form.copy(name = it)) }
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
        Text(stringResource(R.string.core_parity_subscription_attachments), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = save, enabled = !saving && form.unit != CycleUnit.UNKNOWN, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Rounded.Done, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save)) }
        Spacer(Modifier.height(48.dp))
    }
    if (amountPad) HootbookModalBottomSheet(onDismissRequest = { amountPad = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AmountInput(form.amount, CurrencyFormatter.getCurrencySymbol(form.currency), {}, enabled = false)
            DecimalAmountPad(form.amount, { if (!saving) update(form.copy(amount = it)) })
            Button(onClick = { amountPad = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_plans_confirm)) }
        }
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
    val none = stringResource(R.string.core_plans_none)
    PlanReferenceField(stringResource(R.string.core_plans_account), refs.accounts.find { it.id == accountId }?.name ?: accountId ?: none,
        accountId, listOf(null to none) + refs.accounts.map { it.id to it.name }, !saving, account)
    if (accountId != null && refs.accounts.none { it.id == accountId }) Text(stringResource(R.string.core_plans_missing_reference, accountId), color = MaterialTheme.colorScheme.error)
    val categories = refs.categories.filter { kind == null || it.kind == kind }
    val selected = categories.find { it.id == categoryId }
    PlanReferenceField(stringResource(R.string.core_plans_category), selected?.displayName ?: categoryId ?: none,
        categoryId, listOf(null to none) + categories.map { it.id to it.displayName }, !saving, category)
    if (categoryId != null && selected == null) Text(stringResource(R.string.core_plans_missing_reference, categoryId), color = MaterialTheme.colorScheme.error)
    if (selected != null) {
        PlanReferenceField(stringResource(R.string.core_plans_subcategory), selected.subcategories.find { it.id == subcategoryId }?.displayName ?: subcategoryId ?: none,
            subcategoryId, listOf(null to none) + selected.subcategories.map { it.id to it.displayName }, !saving, subcategory)
        if (subcategoryId != null && selected.subcategories.none { it.id == subcategoryId }) Text(stringResource(R.string.core_plans_missing_reference, subcategoryId), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PlanReferenceField(label: String, value: String, selected: String?, options: List<Pair<String?, String>>, enabled: Boolean, change: (String?) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Surface(onClick = { choosing = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) }
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
    }
    if (choosing) HootbookModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(16.dp)) {
            items(options) { (id, name) ->
                ListItem(headlineContent = { Text(name) }, leadingContent = { RadioButton(id == selected, onClick = { change(id); choosing = false }, enabled = enabled) },
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun PlanChoice(selected: Boolean, label: String, saving: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !saving) { Text("${if (selected) "✓ " else ""}$label") }
}


@Composable
internal fun CoreSubscriptionSummary(summary: SubscriptionSummary) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.total_subscriptions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.active_count_format, summary.count.toInt()), style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Subscriptions, null) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.monthly to summary.monthly, R.string.yearly to summary.yearly).forEach { (label, amount) ->
                    Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .7f), MaterialTheme.shapes.large).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(CurrencyFormatter.formatCurrency(amount, summary.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(stringResource(R.string.core_plans_upcoming, summary.currency, summary.upcoming.toPlainString()), style = MaterialTheme.typography.bodySmall)
            if (!summary.complete) Text(stringResource(R.string.core_plans_missing_rates, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CoreSubscriptionRow(row: Subscription, platform: CorePlanPlatformViewModel, refs: CorePlanReferences,
    enabled: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onHide: () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (enabled) when (value) { SwipeToDismissBoxValue.StartToEnd -> onHide(); SwipeToDismissBoxValue.EndToStart -> onEdit(); else -> Unit }
        false
    })
    SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = enabled, enableDismissFromEndToStart = enabled,
        backgroundContent = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraLarge).padding(16.dp),
            contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
            Icon(if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Icons.Rounded.VisibilityOff else Icons.Rounded.Edit, null)
        } }) {
        Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoreMerchantIcon(row.name, platform.assets, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SubtitleTag(text = when {
                            row.ended -> stringResource(R.string.core_plans_ended)
                            row.overdue -> stringResource(R.string.overdue)
                            row.daysUntil == 0 -> stringResource(R.string.due_today)
                            row.daysUntil == 1 -> stringResource(R.string.due_tomorrow)
                            else -> row.nextDue?.planDateText() ?: stringResource(R.string.core_plans_none)
                        }, color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        SubtitleTag(text = "${row.cycle.every} ${cycleLabel(row.cycle.unit)}", color = MaterialTheme.colorScheme.tertiary)
                        refs.categories.find { it.id == row.categoryId }?.let { SubtitleTag(text = it.displayName, color = MaterialTheme.colorScheme.secondary) }
                    }
                }
                Text(CurrencyFormatter.formatCurrency(row.amount.amount, row.amount.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
