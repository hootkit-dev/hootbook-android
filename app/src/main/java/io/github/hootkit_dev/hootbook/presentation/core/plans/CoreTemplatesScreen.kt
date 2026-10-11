@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreTextField
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreKindLabel
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon

@Composable
fun CoreTemplatesScreen(onBack: () -> Unit, onUseTemplate: (templateId: String) -> Unit,
    viewModel: CoreTemplatesViewModel = hiltViewModel(),platform:CorePlanPlatformViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = back)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var deleteId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.commandRevision) { deleteId = null }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var ordered by remember(state.templates) { mutableStateOf(state.templates) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (!state.saving) {
            val fromIndex = ordered.indexOfFirst { it.id == from.key }
            val toIndex = ordered.indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    Scaffold(topBar = {
        CustomTitleTopAppBar(title = state.selected?.name ?: stringResource(R.string.quick_add_templates), scrollBehaviorSmall = scroll, scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(back) }, actionContent = {
                IconButton(onClick = viewModel::refresh, enabled = !state.saving) { Icon(Icons.Rounded.Refresh, stringResource(R.string.core_plans_refresh)) }
            })
    }, floatingActionButton = {
        if (state.selectedId == null && state.form == null) FloatingActionButton(onClick = viewModel::create) { Text("＋") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.errorResource?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))}
            PlanStatus(state.loading, state.saving, state.navigationBlocked, state.error, viewModel::clearError)
            if (state.selectedId == null) {
                LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(stringResource(R.string.core_plans_template_boundary), style = MaterialTheme.typography.bodySmall) }
                    if (!state.loading && state.templates.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Bolt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.quick_templates_empty))
                        }
                    }
                    items(ordered, key = { it.id }) { row ->
                        ReorderableItem(state = reorderState, key = row.id) { dragging ->
                            val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "templateDrag")
                            Row(Modifier.fillMaxWidth().shadow(elevation, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(enabled = !state.saving) { viewModel.select(row.id) }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CoreMerchantIcon(row.merchant, platform.assets, Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val category = state.references.categories.find { it.id == row.categoryId }?.displayName
                                    val notPrefilled = stringResource(R.string.quick_template_amount_not_prefilled)
                                    Text(listOfNotNull(category, row.amount?.let { CurrencyFormatter.formatCurrency(it, row.currency ?: state.references.mainCurrency) + if (row.prefillAmount) "" else " ($notPrefilled)" }).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { viewModel.edit(row) }, enabled = !state.saving) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                                IconButton(onClick = { deleteId = row.id }, enabled = !state.saving) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                                Icon(Icons.Default.DragHandle, stringResource(R.string.reorder), Modifier.draggableHandle(onDragStopped = { viewModel.reorder(ordered.map { it.id }) }).padding(horizontal = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.suggestions.isNotEmpty()) item { Text(stringResource(R.string.core_plans_suggestions), style = MaterialTheme.typography.titleLarge) }
                    items(state.suggestions, key = { "suggestion:${it.key}" }) { row ->
                        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.core_plans_suggestion_count, row.count.toString()))
                            FlowRow {
                                TextButton(onClick = { viewModel.useSuggestion(row) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_save_suggestion)) }
                                TextButton(onClick = { viewModel.dismissSuggestion(row) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_dismiss_suggestion)) }
                            }
                        } }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else state.selected?.let { row ->
                Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreMerchantIcon(row.merchant,platform.assets,Modifier.size(64.dp))
                    Text(row.name, style = MaterialTheme.typography.headlineMedium)
                    Text("${CoreKindLabel(row.kind)} · ${row.merchant}")
                    Text(stringResource(R.string.core_plans_template_amount_value, row.amount?.toPlainString() ?: stringResource(R.string.core_plans_none)))
                    Text(stringResource(if (row.prefillAmount) R.string.core_plans_prefill_on else R.string.core_plans_prefill_off))
                    Text(stringResource(R.string.core_plans_currency_value, row.currency ?: stringResource(R.string.core_plans_none)))
                    PlanReferenceDescription(state.references, row.accountId, row.categoryId, row.subcategoryId)
                    row.notes?.let { Text(it) }
                    FlowRow {
                        TextButton(onClick = viewModel::edit, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { deleteId = row.id }, enabled = !state.saving) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = { if (viewModel.canNavigate()) onUseTemplate(row.id) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_use_template)) }
                    Text(stringResource(R.string.core_plans_template_boundary), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    state.form?.let { form -> HootbookModalBottomSheet(onDismissRequest = { if (!state.saving) viewModel.back() }) {
        Text(stringResource(if (form.id == null) R.string.core_plans_templates else R.string.edit), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        TemplateEditor(form, state.references, state.saving, viewModel::updateForm, viewModel::save)
    } }
    if (deleteId != null) AlertDialog(onDismissRequest = { if (!state.saving) deleteId = null },
        title = { Text(stringResource(R.string.core_plans_delete_template_title)) },
        text = { Column { Text(stringResource(R.string.core_plans_delete_template_body)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(onClick = { deleteId = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { deleteId?.let(viewModel::delete) }, enabled = !state.saving) { Text(stringResource(R.string.delete)) } })
}

@Composable
private fun TemplateEditor(form: CoreTemplateForm, refs: CorePlanReferences, saving: Boolean,
    update: (CoreTemplateForm) -> Unit, save: () -> Unit) {
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoreTextField(form.name, stringResource(R.string.core_plans_name), saving) { update(form.copy(name = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.INVESTMENT).forEach { kind ->
                FilterChip(selected = form.kind == kind, onClick = { update(form.copy(kind = kind, categoryId = null, subcategoryId = null)) },
                    enabled = !saving, label = { Text(CoreKindLabel(kind)) })
            }
        }
        CoreTextField(form.merchant, stringResource(R.string.core_plans_merchant), saving) { update(form.copy(merchant = it)) }
        CoreTextField(form.amount, stringResource(R.string.core_plans_optional_amount), saving) { update(form.copy(amount = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.core_plans_prefill_amount))
            Switch(checked = form.prefillAmount, onCheckedChange = { update(form.copy(prefillAmount = it)) }, enabled = !saving)
        }
        CoreTextField(form.currency, stringResource(R.string.core_plans_optional_currency), saving) { update(form.copy(currency = it)) }
        val kind = when (form.kind) { TransactionKind.EXPENSE -> CategoryKind.EXPENSE; TransactionKind.INCOME -> CategoryKind.INCOME; else -> null }
        PlanReferencePicker(refs, form.accountId, form.categoryId, form.subcategoryId, kind, saving,
            account = { update(form.copy(accountId = it)) }, category = { update(form.copy(categoryId = it, subcategoryId = null)) },
            subcategory = { update(form.copy(subcategoryId = it)) })
        CoreTextField(form.notes, stringResource(R.string.core_plans_notes), saving) { update(form.copy(notes = it)) }
        Text(stringResource(R.string.core_plans_template_boundary), style = MaterialTheme.typography.bodySmall)
        Button(onClick = save, enabled = !saving, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save)) }
    }
}
