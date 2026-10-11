@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreVisualStylePicker
import io.github.hootkit_dev.hootbook.presentation.ui.components.*

/**
 * Categories: a list filtered by kind and search, and one category's page with its
 * subcategories. A row opens its category; every change is made from the category's page.
 */
@Composable
fun CoreCategoriesScreen(onBack: () -> Unit, viewModel: CoreCategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(onBack = goBack)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var deletion by remember { mutableStateOf<Category?>(null) }
    var subDeletion by remember { mutableStateOf<Subcategory?>(null) }
    LaunchedEffect(state.commandRevision) { deletion = null; subDeletion = null }
    val selected = state.selected
    val enabled = !state.saving
    SecondaryScreen(
        title = selected?.displayName ?: stringResource(R.string.categories), onBack = goBack,
        actions = { if (selected != null) CategoryActions(selected, enabled, viewModel, onDelete = { deletion = it }) },
        floatingActionButton = {
            AddFab(stringResource(if (selected == null) R.string.add_category else R.string.add_subcategory),
                { if (enabled) selected?.let(viewModel::createSubcategory) ?: viewModel.createCategory() })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.cat_saving), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            state.error?.let { ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (state.selectedId == null) CategoryList(state, viewModel)
            else if (selected != null) CategoryDetail(selected, state, viewModel, onDeleteChild = { subDeletion = it })
        }
    }
    state.draft?.let { draft -> CategoryEditorSheet(draft, state, viewModel) }
    deletion?.let { category -> CategoryDeletionDialog(category, state.categories, state.transactionCount, state.saving || state.loading || state.selected?.id != category.id,
        dismiss = { deletion = null }, confirm = { viewModel.delete(category, it) }) }
    subDeletion?.let { row -> ConfirmDialog(
        title = stringResource(R.string.core_full_delete_child_name, row.displayName), text = stringResource(R.string.cat_sub_delete_note),
        confirmLabel = stringResource(R.string.delete), onConfirm = { viewModel.deleteSubcategory(row) }, onDismiss = { subDeletion = null },
        destructive = true, enabled = enabled) }
}

/** Kind switch, search, then one segmented row per category with its subcategories named. */
@Composable
private fun CategoryList(state: CoreCategoriesUiState, viewModel: CoreCategoriesViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    val kinds = listOf(null to R.string.cat_all, CategoryKind.EXPENSE to R.string.expense, CategoryKind.INCOME to R.string.income)
    val rows = state.categories.filter { category -> search.isBlank() || category.displayName.contains(search, true) ||
        category.subcategories.any { it.displayName.contains(search, true) } }
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "kind") {
            GenericTypeSwitcher(kinds.indexOfFirst { it.first == state.kind }.coerceAtLeast(0), { viewModel.filter(kinds[it].first) },
                kinds.map { stringResource(it.second) }, Modifier.fillMaxWidth())
        }
        item(key = "search") {
            SearchBarBox(searchQuery = TextFieldValue(search), onSearchQueryChange = { search = it.text },
                label = { Text(stringResource(R.string.search_categories)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.clear_search), { search = "" }) })
        }
        if (!state.loading && rows.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.cat_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rows.isNotEmpty()) item(key = "rows") {
            ListSection { rows.forEach { category -> categoryRow(category) { viewModel.select(category.id) } } }
        }
    }
}

/** A category: its icon on its color, its name, and its subcategories (or Hidden). */
internal fun ListSectionScope.categoryRow(category: Category, onClick: () -> Unit) = item(
    leading = { CategoryIcon(category.icon, category.color) },
    headline = { Text(category.displayName) },
    supporting = {
        val parts = listOfNotNull(if (category.hidden) stringResource(R.string.cat_hidden) else null,
            category.subcategories.joinToString(" · ") { it.displayName }.ifBlank { null })
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
    },
    chevron = true, onClick = onClick,
)

@Composable
private fun CategoryIcon(icon: String, color: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val tint = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    Box(Modifier.size(size).background(tint.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
        CoreRecordIcon(icon, Modifier.size(size * 0.62f))
    }
}

/** Edit in the bar; hide or show, reorder, and reset (built-in) or delete (custom) behind ⋮. */
@Composable
private fun CategoryActions(category: Category, enabled: Boolean, viewModel: CoreCategoriesViewModel, onDelete: (Category) -> Unit) {
    TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.cat_edit), { viewModel.editCategory(category) }, enabled = enabled)
    OverflowMenu(enabled = enabled, actions = listOfNotNull(
        if (canHide(category)) MenuAction(stringResource(if (category.hidden) R.string.cat_show else R.string.cat_hide),
            if (category.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { viewModel.hide(category) } else null,
        MenuAction(stringResource(R.string.ui_move_up), Icons.Rounded.ArrowUpward) { viewModel.move(category, -1) },
        MenuAction(stringResource(R.string.ui_move_down), Icons.Rounded.ArrowDownward) { viewModel.move(category, 1) },
        if (category.builtinKey != null) MenuAction(stringResource(R.string.cat_reset), Icons.Rounded.RestartAlt) { viewModel.reset(category) }
        else MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { onDelete(category) },
    ))
}

/** The category's look, transaction count and status, then its subcategories. */
@Composable
private fun CategoryDetail(category: Category, state: CoreCategoriesUiState, viewModel: CoreCategoriesViewModel, onDeleteChild: (Subcategory) -> Unit) {
    val enabled = !state.saving
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item(key = "summary") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                CategoryIcon(category.icon, category.color, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cat_transactions, state.transactionCount.toString()), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusLabel(stringResource(if (category.builtinKey != null) R.string.cat_builtin else R.string.cat_custom))
                        if (category.hidden) StatusLabel(stringResource(R.string.cat_hidden))
                    }
                }
            }
            if (!canHide(category)) Text(stringResource(R.string.cat_fallback_note), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "children") {
            ListSection(title = stringResource(R.string.cat_subcategories)) {
                if (category.subcategories.isEmpty()) item(headline = { Text(stringResource(R.string.cat_no_subcategories)) })
                category.subcategories.forEach { row ->
                    item(
                        leading = { CategoryIcon(row.icon, row.color, 40.dp) },
                        headline = { Text(row.displayName) },
                        supporting = { Text(listOfNotNull(stringResource(if (row.builtinKey != null) R.string.cat_builtin else R.string.cat_custom),
                            if (row.hidden) stringResource(R.string.cat_hidden) else null).joinToString(" · ")) },
                        trailing = { OverflowMenu(enabled = enabled, actions = listOf(
                            MenuAction(stringResource(R.string.edit), Icons.Rounded.Edit) { viewModel.editSubcategory(category, row) },
                            MenuAction(stringResource(if (row.hidden) R.string.cat_show else R.string.cat_hide),
                                if (row.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { viewModel.hideSubcategory(row) },
                            MenuAction(stringResource(R.string.ui_move_up), Icons.Rounded.ArrowUpward) { viewModel.moveSubcategory(category, row, -1) },
                            MenuAction(stringResource(R.string.ui_move_down), Icons.Rounded.ArrowDownward) { viewModel.moveSubcategory(category, row, 1) },
                            if (row.builtinKey != null) MenuAction(stringResource(R.string.cat_reset), Icons.Rounded.RestartAlt) { viewModel.resetSubcategory(row) }
                            else MenuAction(stringResource(R.string.delete), Icons.Rounded.Delete, destructive = true) { onDeleteChild(row) },
                        )) },
                        onClick = { if (enabled) viewModel.editSubcategory(category, row) },
                    )
                }
            }
        }
    }
}

/** Name, kind (new top-level categories only), icon and color. */
@Composable
private fun CategoryEditorSheet(draft: CoreCategoryDraft, state: CoreCategoriesUiState, viewModel: CoreCategoriesViewModel) {
    val title = stringResource(when {
        draft.parentId != null && draft.id == null -> R.string.add_subcategory
        draft.parentId != null -> R.string.cat_edit_sub
        draft.id == null -> R.string.add_category
        else -> R.string.cat_edit
    })
    FormSheet(title, viewModel::dismissEditor, viewModel::save, busy = state.saving, confirmEnabled = draft.name.isNotBlank(), error = state.error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryIcon(draft.icon, draft.color, 56.dp)
            FormTextField(draft.name, stringResource(R.string.cat_name), { viewModel.draftChanged(draft.copy(name = it)) }, Modifier.weight(1f), enabled = !state.saving)
        }
        if (draft.parentId == null && draft.id == null) GenericTypeSwitcher(
            selectedIndex = if (draft.kind == CategoryKind.INCOME) 1 else 0,
            onIndexChange = { if (!state.saving) viewModel.draftChanged(draft.copy(kind = if (it == 0) CategoryKind.EXPENSE else CategoryKind.INCOME)) },
            options = listOf(stringResource(R.string.expense), stringResource(R.string.income)), modifier = Modifier.fillMaxWidth())
        CoreVisualStylePicker(draft.icon, draft.color, !state.saving,
            { viewModel.draftChanged(draft.copy(icon = it)) }, { viewModel.draftChanged(draft.copy(color = it)) })
    }
}

/** Deleting a category moves what used it to another category (or the default one) first. */
@Composable
private fun CategoryDeletionDialog(category: Category, rows: List<Category>, count: UInt, saving: Boolean,
    dismiss: () -> Unit, confirm: (CategoryRef?) -> Unit) {
    var target by remember(category.id) { mutableStateOf<CategoryRef?>(null) }
    val candidates = rows.filter { it.id != category.id && it.kind == category.kind }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, containerColor = HootbookDialogDefaults.containerColor,
        title = { Text(stringResource(R.string.core_full_delete_category_name, category.displayName)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.cat_delete_note, count.toString()), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.cat_move_to), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
            TargetRow(stringResource(R.string.cat_fallback_target), target == null, 0.dp) { target = null }
            candidates.forEach { destination ->
                TargetRow(destination.displayName, target?.categoryId == destination.id && target?.subcategoryId == null, 0.dp) { target = CategoryRef(destination.id, null) }
                if (target?.categoryId == destination.id) destination.subcategories.forEach { sub ->
                    TargetRow(sub.displayName, target?.subcategoryId == sub.id, 32.dp) { target = CategoryRef(destination.id, sub.id) }
                }
            }
        } },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), dismiss, enabled = !saving) },
        confirmButton = { DialogConfirmButton(stringResource(R.string.cat_delete_confirm), { confirm(target) }, destructive = true, enabled = !saving) })
}

@Composable
private fun TargetRow(label: String, selected: Boolean, indent: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(start = indent),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected, null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
