@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreKindLabel
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.detailRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Quick templates: a reorderable list (drag the handle), suggestions from recent entries, one
 * template's page (use it, edit in the bar, delete behind ⋮) and the editor.
 */
@Composable
fun CoreTemplatesScreen(onBack: () -> Unit, onUseTemplate: (templateId: String) -> Unit,
    viewModel: CoreTemplatesViewModel = hiltViewModel(), platform: CorePlanPlatformViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = back)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var deleteId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.commandRevision) { deleteId = null }
    val selected = state.selected
    val enabled = !state.saving
    SecondaryScreen(
        title = selected?.name ?: stringResource(R.string.quick_add_templates), onBack = back,
        actions = {
            if (selected != null) {
                TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.plan_template_edit), viewModel::edit, enabled = enabled)
                OverflowMenu(enabled = enabled, actions = listOf(
                    MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { deleteId = selected.id },
                ))
            }
        },
        floatingActionButton = {
            if (state.selectedId == null && state.form == null) AddFab(stringResource(R.string.plan_template_new), viewModel::create)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.core_plans_saving_navigation), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            (state.error.takeIf { state.form == null } ?: state.errorResource?.let { stringResource(it) })?.let {
                ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (state.selectedId == null) TemplateList(state, platform, viewModel)
            else if (selected != null) TemplateDetail(selected, state, platform) { if (viewModel.canNavigate()) onUseTemplate(selected.id) }
        }
    }
    state.form?.let { form -> TemplateEditor(form, state, viewModel) }
    if (deleteId != null) ConfirmDialog(stringResource(R.string.core_plans_delete_template_title), stringResource(R.string.core_plans_delete_template_body),
        stringResource(R.string.delete), { deleteId?.let(viewModel::delete) }, { deleteId = null }, destructive = true, enabled = enabled)
}

/** The templates as one segmented list whose rows can be dragged by their handle, then suggestions. */
@Composable
private fun TemplateList(state: CoreTemplatesUiState, platform: CorePlanPlatformViewModel, viewModel: CoreTemplatesViewModel) {
    var ordered by remember(state.templates) { mutableStateOf(state.templates) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (!state.saving) {
            val fromIndex = ordered.indexOfFirst { it.id == from.key }
            val toIndex = ordered.indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    LazyColumn(state = listState, contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "note") {
            Text(stringResource(R.string.core_plans_template_boundary), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.loading && state.templates.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.Bolt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.quick_templates_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(ordered, key = { it.id }) { row ->
            ReorderableItem(state = reorderState, key = row.id) { dragging ->
                val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "templateDrag")
                val shape = ListItemPosition.from(ordered.indexOf(row), ordered.size).toShape()
                val category = state.references.categories.find { it.id == row.categoryId }?.displayName
                val notPrefilled = stringResource(R.string.quick_template_amount_not_prefilled)
                ListItem(
                    modifier = Modifier.shadow(elevation, shape),
                    leading = { CoreMerchantIcon(row.merchant, platform.assets, Modifier.size(40.dp)) },
                    headline = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supporting = { Text(listOfNotNull(category, row.amount?.let { CoreMoneyPresentation.format(it, row.currency ?: state.references.mainCurrency) +
                        if (row.prefillAmount) "" else " ($notPrefilled)" }).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailing = { Icon(Icons.Rounded.DragHandle, stringResource(R.string.reorder),
                        Modifier.draggableHandle(onDragStopped = { viewModel.reorder(ordered.map { it.id }) }).padding(8.dp)) },
                    shape = shape, padding = PaddingValues(0.dp),
                    onClick = if (!state.saving) ({ viewModel.select(row.id) }) else null,
                )
            }
        }
        if (state.suggestions.isNotEmpty()) item(key = "suggestions") {
            ListSection(Modifier.padding(top = SectionSpacing), title = stringResource(R.string.core_plans_suggestions)) {
                state.suggestions.forEach { row ->
                    item(
                        headline = { Text(row.name) },
                        supporting = { Text(stringResource(R.string.core_plans_suggestion_count, row.count.toString())) },
                        trailing = { Row {
                            TooltipIconButton(Icons.Rounded.BookmarkAdd, stringResource(R.string.core_plans_save_suggestion), { viewModel.useSuggestion(row) }, enabled = !state.saving)
                            TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.core_plans_dismiss_suggestion), { viewModel.dismissSuggestion(row) }, enabled = !state.saving)
                        } },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateDetail(row: QuickTemplate, state: CoreTemplatesUiState, platform: CorePlanPlatformViewModel, onUse: () -> Unit) {
    val none = stringResource(R.string.core_plans_none)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenContentPadding), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoreMerchantIcon(row.merchant, platform.assets, Modifier.size(72.dp))
            StatusLabel(CoreKindLabel(row.kind))
        }
        Button(onClick = onUse, enabled = !state.saving, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.core_plans_use_template)) }
        ListSection(title = stringResource(R.string.plan_details)) {
            if (row.merchant.isNotBlank()) detailRow(stringResource(R.string.core_plans_merchant), row.merchant)
            detailRow(stringResource(R.string.amount), row.amount?.let { CoreMoneyPresentation.format(it, row.currency ?: state.references.mainCurrency) } ?: none)
            item(headline = { Text(stringResource(R.string.core_plans_prefill_amount)) },
                trailing = { Text(stringResource(if (row.prefillAmount) R.string.ui_yes else R.string.ui_no), style = MaterialTheme.typography.bodyLarge) })
            detailRow(stringResource(R.string.core_plans_currency), row.currency ?: none)
            planReferenceRows(state.references, row.accountId, row.categoryId, row.subcategoryId)
            row.notes?.takeIf { it.isNotBlank() }?.let { notes -> item(headline = { Text(stringResource(R.string.core_plans_notes)) },
                supporting = { Text(notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }) }
        }
    }
}

@Composable
private fun TemplateEditor(form: CoreTemplateForm, state: CoreTemplatesUiState, viewModel: CoreTemplatesViewModel) {
    val saving = state.saving
    val update = viewModel::updateForm
    val kinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.INVESTMENT)
    FormSheet(stringResource(if (form.id == null) R.string.plan_template_new else R.string.plan_template_edit), { if (!saving) viewModel.back() }, viewModel::save,
        busy = saving, error = state.error, confirmEnabled = form.name.isNotBlank()) {
        FormTextField(form.name, stringResource(R.string.core_plans_name), { update(form.copy(name = it)) }, enabled = !saving)
        GenericTypeSwitcher(kinds.indexOf(form.kind).coerceAtLeast(0), { if (!saving) update(form.copy(kind = kinds[it], categoryId = null, subcategoryId = null)) },
            kinds.map { CoreKindLabel(it) }, Modifier.fillMaxWidth())
        FormTextField(form.merchant, stringResource(R.string.core_plans_merchant), { update(form.copy(merchant = it)) }, enabled = !saving)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(form.amount, stringResource(R.string.core_plans_optional_amount), { update(form.copy(amount = it)) }, Modifier.weight(1f), enabled = !saving, decimal = true)
            FormTextField(form.currency, stringResource(R.string.core_plans_optional_currency), { update(form.copy(currency = it.uppercase())) }, Modifier.weight(1f), enabled = !saving)
        }
        ListSection {
            item(headline = { Text(stringResource(R.string.core_plans_prefill_amount)) }, trailing = { HootbookSwitch(form.prefillAmount, null, enabled = !saving) },
                toggled = form.prefillAmount, onToggle = { if (!saving) update(form.copy(prefillAmount = it)) })
        }
        val kind = when (form.kind) { TransactionKind.EXPENSE -> CategoryKind.EXPENSE; TransactionKind.INCOME -> CategoryKind.INCOME; else -> null }
        PlanReferencePicker(state.references, form.accountId, form.categoryId, form.subcategoryId, kind, saving,
            account = { update(form.copy(accountId = it)) }, category = { update(form.copy(categoryId = it, subcategoryId = null)) },
            subcategory = { update(form.copy(subcategoryId = it)) })
        FormTextField(form.notes, stringResource(R.string.core_plans_notes), { update(form.copy(notes = it)) }, enabled = !saving)
    }
}
