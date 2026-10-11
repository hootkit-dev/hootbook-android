@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
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
    var search by rememberSaveable { mutableStateOf("") }
    var filterMenu by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = state.selected?.displayName ?: stringResource(R.string.categories),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(goBack) }, actionContent = {
                IconButton(onClick = { filterMenu = true }, enabled = !state.saving) { Icon(Icons.Rounded.FilterList, stringResource(R.string.filters)) }
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    listOf(null to R.string.all, CategoryKind.EXPENSE to R.string.expense, CategoryKind.INCOME to R.string.income).forEach { (kind, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { viewModel.filter(kind); filterMenu = false })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.core_categories_3)) }, onClick = { viewModel.refresh(); filterMenu = false })
                }
            })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { if (!state.saving) state.selected?.let(viewModel::createSubcategory) ?: viewModel.createCategory() },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(if (state.selected == null) R.string.add_category else R.string.add_subcategory)) })
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
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        SearchBarBox(searchQuery = TextFieldValue(search), onSearchQueryChange = { search = it.text },
                            label = { Text(stringResource(R.string.search_fruits)) },
                            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } })
                    }
                    if (!state.loading && state.categories.isEmpty()) item { Text(stringResource(R.string.core_categories_7)) }
                    items(state.categories.filter { category -> search.isBlank() || category.displayName.contains(search, true) || category.subcategories.any { it.displayName.contains(search, true) } }, key = { it.id }) { category ->
                        CoreCategoryItem(category, !state.saving,
                            onEdit = { viewModel.editCategory(category) }, onDetails = { viewModel.select(category.id) },
                            onAddSubcategory = { viewModel.createSubcategory(category) }, onEditSubcategory = { viewModel.editSubcategory(category, it) },
                            onMove = { direction -> viewModel.move(category, direction) },
                            onDelete = { viewModel.select(category.id); deletion = category })
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
    state.draft?.let { draft ->
        HootbookModalBottomSheet(onDismissRequest = viewModel::dismissEditor, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val color = runCatching { Color(android.graphics.Color.parseColor(draft.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(64.dp).background(color.copy(alpha = .2f), MaterialTheme.shapes.large), contentAlignment = Alignment.Center) { CoreRecordIcon(draft.icon, Modifier.size(40.dp)) }
                        Text(draft.name.ifBlank { stringResource(R.string.category_name) }, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                CoreTextField(draft.name, stringResource(R.string.core_categories_20), state.saving) { viewModel.draftChanged(draft.copy(name = it)) }
                if (draft.parentId == null && draft.id == null) GenericTypeSwitcher(
                    selectedIndex = if (draft.kind == CategoryKind.INCOME) 1 else 0,
                    onIndexChange = { if (!state.saving) viewModel.draftChanged(draft.copy(kind = if (it == 0) CategoryKind.EXPENSE else CategoryKind.INCOME)) },
                    options = listOf(stringResource(R.string.expense), stringResource(R.string.income)))
                Text(stringResource(R.string.core_parity_category_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CoreVisualStylePicker(draft.icon, draft.color, !state.saving,
                    { viewModel.draftChanged(draft.copy(icon = it)) }, { viewModel.draftChanged(draft.copy(color = it)) })
                ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(draft.color) }.getOrDefault(0xff33b5e5.toInt()),
                    onColorChanged = { if (!state.saving) viewModel.draftChanged(draft.copy(color = String.format(java.util.Locale.ROOT, "#%06X", 0xffffff and it))) })
                Text(stringResource(R.string.core_categories_23), style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::save, enabled = !state.saving && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save)) }
                TextButton(onClick = viewModel::dismissEditor, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    deletion?.let { category -> CategoryDeletionDialog(category, state.categories, state.transactionCount, state.saving || state.loading || state.selected?.id != category.id,
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


/** Original category card with a horizontal subcategory chip strip, using generated Core records. */
@Composable
internal fun CoreCategoryItem(category: Category, enabled: Boolean = true, onEdit: () -> Unit,
    onDetails: () -> Unit, onAddSubcategory: () -> Unit, onEditSubcategory: (Subcategory) -> Unit, onMove: (Int) -> Unit, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val dismissState = rememberSwipeToDismissBoxState()
    val deleteAction by rememberUpdatedState(onDelete)
    val canDelete by rememberUpdatedState(enabled && category.builtinKey == null)
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            // confirmValueChange is a repeatedly evaluated predicate, not a gesture event.
            // Restore the row before requesting confirmation so cancelling allows another swipe.
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            if (canDelete) deleteAction?.invoke()
        }
    }
    SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled && category.builtinKey == null && onDelete != null,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onError)
            }
        }) {
    Card(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(color.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { CoreRecordIcon(category.icon, Modifier.size(32.dp)) }
            Column(Modifier.weight(1f)) {
                Text(category.displayName, style = MaterialTheme.typography.titleMedium)
                if (category.hidden) Text(stringResource(R.string.core_categories_10), style = MaterialTheme.typography.labelSmall)
            }
            if (category.subcategories.isEmpty()) IconButton(onClick = onAddSubcategory, enabled = enabled) { Icon(Icons.Rounded.AddCircle, stringResource(R.string.add_subcategory), tint = MaterialTheme.colorScheme.primary) }
            Box {
                IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.core_categories_19)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.core_categories_19)) }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.core_categories_8)) }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.core_categories_9)) }, onClick = { menu = false; onMove(1) })
                }
            }
        }
        if (category.subcategories.isNotEmpty()) LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(category.subcategories, key = { it.id }) { row ->
                AssistChip(onClick = { onEditSubcategory(row) }, enabled = enabled, label = { Text(row.displayName) }, leadingIcon = { CoreRecordIcon(row.icon, Modifier.size(18.dp)) })
            }
            item { AssistChip(onClick = onAddSubcategory, enabled = enabled, label = { Icon(Icons.Rounded.Add, stringResource(R.string.add_subcategory)) }) }
        }
    }
}
}
