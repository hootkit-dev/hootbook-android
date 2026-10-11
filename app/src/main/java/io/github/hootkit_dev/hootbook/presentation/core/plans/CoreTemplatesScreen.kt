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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreTextField
import io.github.hootkit_dev.hootbook.presentation.format.CoreKindLabel
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
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.selected?.name ?: stringResource(R.string.core_plans_templates)) },
            navigationIcon = { TextButton(onClick = back, enabled = !state.saving) { Text(stringResource(R.string.core_plans_back)) } },
            actions = { TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_plans_refresh)) } })
    }, floatingActionButton = {
        if (state.selectedId == null && state.form == null) FloatingActionButton(onClick = viewModel::create) { Text("＋") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.errorResource?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))}
            PlanStatus(state.loading, state.saving, state.navigationBlocked, state.error, viewModel::clearError)
            if (state.form != null) TemplateEditor(state.form!!, state.references, state.saving, viewModel::updateForm, viewModel::save)
            else if (state.selectedId == null) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.core_plans_template_boundary), style = MaterialTheme.typography.bodySmall) }
                    if (!state.loading && state.templates.isEmpty()) item { Text(stringResource(R.string.core_plans_empty_templates)) }
                    items(state.templates, key = { it.id }) { row ->
                        Card(onClick = { viewModel.select(row.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                CoreMerchantIcon(row.merchant,platform.assets,Modifier.size(48.dp))
                                Text(row.name, style = MaterialTheme.typography.titleLarge)
                                Text("${CoreKindLabel(row.kind)} · ${row.merchant}")
                                row.amount?.let { Text("${row.currency.orEmpty()} ${it.toPlainString()}") }
                                FlowRow {
                                    TextButton(onClick = { if (viewModel.canNavigate()) onUseTemplate(row.id) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_use_template)) }
                                    TextButton(onClick = { viewModel.move(row, -1) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_move_up)) }
                                    TextButton(onClick = { viewModel.move(row, 1) }, enabled = !state.saving) { Text(stringResource(R.string.core_plans_move_down)) }
                                }
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
    if (deleteId != null) AlertDialog(onDismissRequest = { if (!state.saving) deleteId = null },
        title = { Text(stringResource(R.string.core_plans_delete_template_title)) },
        text = { Column { Text(stringResource(R.string.core_plans_delete_template_body)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(onClick = { deleteId = null }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = viewModel::delete, enabled = !state.saving && deleteId == state.selected?.id) { Text(stringResource(R.string.delete)) } })
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
        Button(onClick = save, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
    }
}
