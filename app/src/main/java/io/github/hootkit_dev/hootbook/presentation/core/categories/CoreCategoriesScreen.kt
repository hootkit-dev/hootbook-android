@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreVisualStylePicker
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
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
fun CoreCategoriesScreen(onBack: () -> Unit, viewModel: CoreCategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var deletion by remember { mutableStateOf<Category?>(null) }
    var subDeletion by remember { mutableStateOf<Subcategory?>(null) }
    LaunchedEffect(state.commandRevision) { deletion = null; subDeletion = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.selected?.displayName ?: stringResource(R.string.core_categories_1)) },
            navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_categories_2)) } },
            actions = { TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_categories_3)) } })
    }, floatingActionButton = {
        FloatingActionButton(onClick = { state.selected?.let(viewModel::createSubcategory) ?: viewModel.createCategory() }) { Text("＋") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.navigationBlocked) Text(stringResource(R.string.core_categories_saving_navigation), modifier = Modifier.padding(16.dp))
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_categories_4)) }
                }
            } }
            if (state.selectedId == null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = state.kind == CategoryKind.EXPENSE, onClick = { viewModel.filter(CategoryKind.EXPENSE) }, label = { Text(stringResource(R.string.core_categories_5)) }, enabled = !state.saving)
                    FilterChip(selected = state.kind == CategoryKind.INCOME, onClick = { viewModel.filter(CategoryKind.INCOME) }, label = { Text(stringResource(R.string.core_categories_6)) }, enabled = !state.saving)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.loading && state.categories.isEmpty()) item { Text(stringResource(R.string.core_categories_7)) }
                    items(state.categories, key = { it.id }) { category ->
                        Card(onClick = { viewModel.select(category.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                CoreRecordIcon(category.icon, Modifier.size(40.dp))
                                Text(category.displayName, style = MaterialTheme.typography.titleLarge)
                                Text("${if (category.builtinKey != null) stringResource(R.string.core_full_label_74) else stringResource(R.string.core_full_label_75)} · ${stringResource(R.string.core_full_child_count, category.subcategories.size.toString())}${if (category.hidden) stringResource(R.string.core_full_label_76) else ""}")
                                FlowRow {
                                    TextButton(onClick = { viewModel.move(category, -1) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_8)) }
                                    TextButton(onClick = { viewModel.move(category, 1) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_9)) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else state.selected?.let { category ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(category.displayName, style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.core_full_category_count, state.transactionCount.toString()))
                        if (category.hidden) Text(stringResource(R.string.core_categories_10))
                        FlowRow {
                            TextButton(onClick = { viewModel.editCategory(category) }, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { viewModel.hide(category) }, enabled = !state.saving && canHide(category)) { Text(if (category.hidden) stringResource(R.string.core_categories_11) else stringResource(R.string.core_categories_12)) }
                            if (category.builtinKey != null) TextButton(onClick = { viewModel.reset(category) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_13)) }
                            else TextButton(onClick = { deletion = category }, enabled = !state.saving) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                        if (!canHide(category)) Text(stringResource(R.string.core_categories_14))
                        Text(stringResource(R.string.core_categories_15), style = MaterialTheme.typography.titleLarge)
                    }
                    if (category.subcategories.isEmpty()) item { Text(stringResource(R.string.core_categories_16)) }
                    items(category.subcategories, key = { it.id }) { row ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                CoreRecordIcon(row.icon, Modifier.size(40.dp))
                                Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${if (row.builtinKey != null) stringResource(R.string.core_full_label_74) else stringResource(R.string.core_full_label_75)}${if (row.hidden) stringResource(R.string.core_full_label_76) else ""}")
                                FlowRow {
                                    TextButton(onClick = { viewModel.editSubcategory(category, row) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_17)) }
                                    TextButton(onClick = { viewModel.hideSubcategory(row) }, enabled = !state.saving) { Text(if (row.hidden) stringResource(R.string.core_categories_11) else stringResource(R.string.core_categories_12)) }
                                    if (row.builtinKey != null) TextButton(onClick = { viewModel.resetSubcategory(row) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_13)) }
                                    else TextButton(onClick = { subDeletion = row }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_18)) }
                                }
                                FlowRow {
                                    TextButton(onClick = { viewModel.moveSubcategory(category, row, -1) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_8)) }
                                    TextButton(onClick = { viewModel.moveSubcategory(category, row, 1) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_9)) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    state.draft?.let { draft -> AlertDialog(onDismissRequest = viewModel::dismissEditor,
        title = { Text(if (draft.parentId != null) stringResource(R.string.core_categories_15) else stringResource(R.string.core_categories_19)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.saving) Text(stringResource(R.string.core_categories_saving_navigation))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            CoreTextField(draft.name, stringResource(R.string.core_categories_20), state.saving) { viewModel.draftChanged(draft.copy(name = it)) }
            CoreTextField(draft.icon, stringResource(R.string.core_categories_21), state.saving) { viewModel.draftChanged(draft.copy(icon = it)) }
            CoreTextField(draft.color, stringResource(R.string.core_categories_22), state.saving) { viewModel.draftChanged(draft.copy(color = it)) }
            CoreVisualStylePicker(draft.icon, draft.color, !state.saving,
                { viewModel.draftChanged(draft.copy(icon = it)) }, { viewModel.draftChanged(draft.copy(color = it)) })
            Text(stringResource(R.string.core_categories_23), style = MaterialTheme.typography.bodySmall)
        } }, dismissButton = { TextButton(onClick = viewModel::dismissEditor, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = viewModel::save, enabled = !state.saving && draft.name.isNotBlank()) { Text(stringResource(R.string.save)) } }) }
    deletion?.let { category -> CategoryDeletionDialog(category, state.categories, state.transactionCount, state.saving,
        dismiss = { deletion = null }, confirm = { viewModel.delete(category, it) }) }
    subDeletion?.let { row -> AlertDialog(onDismissRequest = { if (!state.saving) subDeletion = null },
        title = { Text(stringResource(R.string.core_full_delete_child_name, row.displayName)) }, text = { Text(stringResource(R.string.core_categories_24)) },
        dismissButton = { TextButton(onClick = { subDeletion = null }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_25)) } },
        confirmButton = { TextButton(onClick = { viewModel.deleteSubcategory(row) }, enabled = !state.saving) { Text(stringResource(R.string.core_categories_18)) } }) }
}

@Composable
private fun CategoryDeletionDialog(category: Category, rows: List<Category>, count: UInt, saving: Boolean,
    dismiss: () -> Unit, confirm: (CategoryRef?) -> Unit) {
    var target by remember(category.id) { mutableStateOf<CategoryRef?>(null) }
    val candidates = rows.filter { it.id != category.id && it.kind == category.kind }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text(stringResource(R.string.core_full_delete_category_name, category.displayName)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.core_full_delete_category_note, count.toString()))
            Text(stringResource(R.string.core_categories_26), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { target = null }, enabled = !saving) { Text("${if (target == null) "✓ " else ""}${stringResource(R.string.core_categories_27)}") }
            candidates.forEach { destination ->
                TextButton(onClick = { target = CategoryRef(destination.id, null) }, enabled = !saving) {
                    Text("${if (target?.categoryId == destination.id && target?.subcategoryId == null) "✓ " else ""}${destination.displayName}")
                }
                if (target?.categoryId == destination.id) destination.subcategories.forEach { sub ->
                    TextButton(onClick = { target = CategoryRef(destination.id, sub.id) }, enabled = !saving) {
                        Text("  ${if (target?.subcategoryId == sub.id) "✓ " else ""}${sub.displayName}")
                    }
                }
            }
        } }, dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text(stringResource(R.string.core_categories_25)) } },
        confirmButton = { TextButton(onClick = { confirm(target) }, enabled = !saving) { Text(stringResource(R.string.core_categories_28)) } })
}
