@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.navigation.HideHostChrome
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountPadSheet

/**
 * Subscriptions: a searchable list with the monthly and yearly totals, one subscription's page
 * (edit in the bar, hide and delete behind ⋮, record or mark a payment), and the editor.
 */
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
    val snackbar = remember { SnackbarHostState() }
    val hiddenText = stringResource(R.string.plan_hidden_done)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(state.undoHiddenId) {
        if (state.undoHiddenId != null && snackbar.showSnackbar(hiddenText, undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.undoHidden()
    }
    val form = state.form
    val selected = state.selected.takeIf { form == null }
    HideHostChrome(form != null || selected != null)
    val enabled = !state.saving
    SecondaryScreen(
        title = when {
            form != null -> stringResource(if (form.id == null) R.string.add_subscription else R.string.plan_edit)
            selected != null -> selected.name
            else -> stringResource(R.string.core_plans_subscriptions)
        },
        onBack = back,
        backIcon = if (form != null) Icons.Rounded.Close else null,
        backLabel = if (form != null) stringResource(R.string.close) else null,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            when {
                form != null -> Button(onClick = viewModel::save, enabled = enabled && form.unit != CycleUnit.UNKNOWN, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.save)) }
                selected != null -> {
                    TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.plan_edit), viewModel::edit, enabled = enabled)
                    OverflowMenu(enabled = enabled, actions = listOf(
                        MenuAction(stringResource(if (selected.hidden) R.string.core_plans_unhide else R.string.core_plans_hide),
                            if (selected.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { viewModel.toggleHidden() },
                        MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { deleteId = selected.id },
                    ))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.core_plans_saving_navigation), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            (state.error ?: state.errorResource?.let { stringResource(it) })?.let {
                ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                form != null -> SubscriptionEditor(form, state.references, state.saving, viewModel::updateForm,
                    onTransaction = if (form.id == null && onNewTransaction != null) ({ if (viewModel.canNavigate()) onNewTransaction() }) else null)
                selected != null -> SubscriptionDetail(selected, state, platform,
                    onRecord = { if (viewModel.canNavigate()) onRecordPayment(selected.id) }, onMarkPaid = { markPaidId = selected.id })
                else -> SubscriptionList(state, platform, viewModel)
            }
        }
    }
    if (deleteId != null) ConfirmDialog(stringResource(R.string.core_plans_delete_subscription_title), stringResource(R.string.core_plans_delete_subscription_body),
        stringResource(R.string.delete), viewModel::delete, { deleteId = null }, destructive = true, enabled = enabled, confirmEnabled = deleteId == state.selected?.id)
    if (markPaidId != null) ConfirmDialog(stringResource(R.string.core_plans_mark_paid), stringResource(R.string.core_plans_mark_paid_body),
        stringResource(R.string.core_plans_mark_paid), viewModel::markPaid, { markPaidId = null }, enabled = enabled, confirmEnabled = markPaidId == state.selected?.id)
}

@Composable
private fun SubscriptionList(state: CoreSubscriptionsUiState, platform: CorePlanPlatformViewModel, viewModel: CoreSubscriptionsViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(false) }
    val rows = state.subscriptions.filter { (state.includeHidden || !it.hidden) && it.name.contains(search, ignoreCase = true) }
        .let { if (sortName) it.sortedBy { row -> row.name.lowercase(java.util.Locale.ROOT) } else it }
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "search") {
            SearchBarBox(searchQuery = TextFieldValue(search), onSearchQueryChange = { search = it.text },
                label = { Text(stringResource(R.string.core_detail_name_filter)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.clear_search), { search = "" }) })
        }
        item(key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(state.includeHidden, { viewModel.includeHidden(!state.includeHidden) }, enabled = !state.saving,
                    label = { Text(stringResource(R.string.core_plans_show_hidden)) }) }
                item { FilterChip(sortName, { sortName = !sortName }, label = { Text(stringResource(R.string.core_detail_sort_name)) },
                    leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(FilterChipDefaults.IconSize)) }) }
            }
        }
        state.summary?.let { summary -> item(key = "summary") { CoreSubscriptionSummary(summary) } }
        if (!state.loading && rows.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.core_plans_empty_subscriptions), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rows.isNotEmpty()) item(key = "rows") {
            ListSection { rows.forEach { row -> subscriptionRow(row, platform) { viewModel.select(row.id) } } }
        }
    }
}

/** A subscription: its icon, name, cycle and when it is next due (red when overdue), and its amount. */
private fun ListSectionScope.subscriptionRow(row: Subscription, platform: CorePlanPlatformViewModel, onClick: () -> Unit) = item(
    leading = { CoreMerchantIcon(row.name, platform.assets, Modifier.size(44.dp)) },
    headline = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supporting = {
        val due = when {
            row.ended -> stringResource(R.string.core_plans_ended)
            row.overdue -> stringResource(R.string.overdue)
            row.daysUntil == 0 -> stringResource(R.string.due_today)
            row.daysUntil == 1 -> stringResource(R.string.due_tomorrow)
            else -> stringResource(R.string.plan_next, row.nextDue.planDisplay())
        }
        val parts = listOfNotNull(stringResource(R.string.core_plans_cycle_value, row.cycle.every.toString(), cycleLabel(row.cycle.unit)), due,
            if (row.hidden) stringResource(R.string.core_plans_hidden) else null)
        Text(parts.joinToString(" · "), color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    },
    trailing = { Text(CoreMoneyPresentation.format(row.amount.amount, row.amount.currency), style = MaterialTheme.typography.titleMedium) },
    chevron = true, onClick = onClick,
)

@Composable
private fun SubscriptionDetail(row: Subscription, state: CoreSubscriptionsUiState, platform: CorePlanPlatformViewModel,
    onRecord: () -> Unit, onMarkPaid: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenContentPadding), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoreMerchantIcon(row.name, platform.assets, Modifier.size(72.dp))
            Text(CoreMoneyPresentation.format(row.amount.amount, row.amount.currency), style = MaterialTheme.typography.displaySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLabel(stringResource(R.string.core_plans_cycle_value, row.cycle.every.toString(), cycleLabel(row.cycle.unit)))
                when {
                    row.ended -> StatusLabel(stringResource(R.string.core_plans_ended))
                    row.overdue -> StatusLabel(stringResource(R.string.core_plans_overdue), error = true)
                    row.daysUntil != null -> StatusLabel(stringResource(R.string.plan_due_in, row.daysUntil.toString()))
                }
                if (row.hidden) StatusLabel(stringResource(R.string.core_plans_hidden))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRecord, enabled = !state.saving && !row.ended, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.core_plans_record_payment)) }
            OutlinedButton(onClick = onMarkPaid, enabled = !state.saving && row.cycle.unit != CycleUnit.UNKNOWN && !row.ended, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f)) { Text(stringResource(R.string.core_plans_mark_paid)) }
        }
        Text(stringResource(R.string.core_plans_payment_boundary), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ListSection(title = stringResource(R.string.plan_details)) {
            detailRow(stringResource(R.string.core_plans_next_due), row.nextDue.planDisplay(), error = row.overdue)
            detailRow(stringResource(R.string.plan_last_paid), row.lastPaid.planDisplay())
            detailRow(stringResource(R.string.core_plans_end_date), row.endDate.planDisplay())
            row.monthlyEquivalent?.let { detailRow(stringResource(R.string.plan_monthly), CoreMoneyPresentation.format(it, row.amount.currency)) }
            planReferenceRows(state.references, row.accountId, row.categoryId, row.subcategoryId)
            row.notes?.takeIf { it.isNotBlank() }?.let { notes -> item(headline = { Text(stringResource(R.string.core_plans_notes)) },
                supporting = { Text(notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }) }
        }
    }
}

/** The amount first, then name, currency, cycle, dates, account and category, notes. */
@Composable
private fun SubscriptionEditor(form: CoreSubscriptionForm, refs: CorePlanReferences, saving: Boolean,
    update: (CoreSubscriptionForm) -> Unit, onTransaction: (() -> Unit)?) {
    var amountPad by rememberSaveable { mutableStateOf(false) }
    val units = listOf(CycleUnit.DAY, CycleUnit.WEEK, CycleUnit.MONTH, CycleUnit.YEAR)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(ScreenContentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onTransaction != null) GenericTypeSwitcher(1, { if (it == 0) onTransaction() },
            listOf(stringResource(R.string.transaction), stringResource(R.string.subscription)), Modifier.fillMaxWidth())
        AmountInput(form.amount, runCatching { java.util.Currency.getInstance(form.currency).symbol }.getOrDefault(form.currency), onClick = { amountPad = true }, enabled = !saving)
        FormTextField(form.name, stringResource(R.string.service_name_required), { update(form.copy(name = it)) }, enabled = !saving)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(form.currency, stringResource(R.string.core_plans_currency), { update(form.copy(currency = it.uppercase())) }, Modifier.weight(1f), enabled = !saving)
            FormTextField(form.every, stringResource(R.string.core_plans_cycle_every), { update(form.copy(every = it)) }, Modifier.weight(1f), enabled = !saving, decimal = true)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, unit ->
                SegmentedButton(form.unit == unit, { update(form.copy(unit = unit)) }, SegmentedButtonDefaults.itemShape(index, units.size), enabled = !saving, icon = {}) {
                    Text(cycleLabel(unit)) }
            }
        }
        if (form.unit == CycleUnit.UNKNOWN) Text(stringResource(R.string.core_plans_unknown_cycle_edit), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { CorePlanDateField(form.nextDue, stringResource(R.string.core_plans_next_due), saving) { update(form.copy(nextDue = it)) } }
            Box(Modifier.weight(1f)) { CorePlanDateField(form.endDate, stringResource(R.string.core_plans_end_date), saving) { update(form.copy(endDate = it)) } }
        }
        PlanReferencePicker(refs, form.accountId, form.categoryId, form.subcategoryId, CategoryKind.EXPENSE, saving,
            account = { update(form.copy(accountId = it)) }, category = { update(form.copy(categoryId = it, subcategoryId = null)) },
            subcategory = { update(form.copy(subcategoryId = it)) })
        FormTextField(form.notes, stringResource(R.string.core_plans_notes), { update(form.copy(notes = it)) }, enabled = !saving)
    }
    if (amountPad) AmountPadSheet(form.amount, { if (!saving) update(form.copy(amount = it)) }) { amountPad = false }
}

/** How many are active, what they cost per month and per year, and what is due next. */
@Composable
internal fun CoreSubscriptionSummary(summary: SubscriptionSummary) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.total_subscriptions), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.active_count_format, summary.count.toInt()), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Subscriptions, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.monthly to summary.monthly, R.string.yearly to summary.yearly).forEach { (label, amount) ->
                    Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(CoreMoneyPresentation.format(amount, summary.currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Text(stringResource(R.string.plan_upcoming, CoreMoneyPresentation.format(summary.upcoming, summary.currency)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!summary.complete) Text(stringResource(R.string.core_plans_missing_rates, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
        }
    }
}
