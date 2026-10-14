package io.github.hootkit_dev.hootbook.presentation.ui.features.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.DuplicateGroup
import dev.hootkit.core.NewTag
import dev.hootkit.core.Table
import dev.hootkit.core.Tag
import dev.hootkit.core.TagPatch
import dev.hootkit.core.TagsInterface
import dev.hootkit.core.Transaction
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val TAG_COLOR_PRESETS = listOf(
    "#6366F1",
    "#EC4899",
    "#10B981",
    "#F59E0B",
    "#3B82F6",
    "#8B5CF6",
    "#EF4444",
    "#14B8A6",
)

private fun parseTagColor(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

data class TagsManagementUiState(
    val activeTags: List<Tag> = emptyList(),
    val archivedTags: List<Tag> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorRes: Int? = null,
)

@HiltViewModel
class TagsManagementViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsManagementUiState())
    val uiState: StateFlow<TagsManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.observe(changes, setOf(Table.TAGS, Table.TRANSACTION_TAGS)) { core ->
                coreQuery {
                    val tags = core.tags().use { it.list(true) }
                    val (archived, active) = tags.partition { it.archived }
                    TagsManagementUiState(
                        activeTags = active.sortedBy { tag -> tag.sortOrder },
                        archivedTags = archived.sortedBy { tag -> tag.name.lowercase() },
                        isLoading = false,
                    )
                }
            }.collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        _uiState.update {
                            snapshot.copy(
                                isSaving = it.isSaving,
                                errorRes = it.errorRes,
                            )
                        }
                    },
                    onFailure = { failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorRes = coreFailureText(failure),
                            )
                        }
                    },
                )
            }
        }
    }

    fun createTag(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate {
            it.create(NewTag(name = trimmed, color = color.ifBlank { "#6366F1" }))
        }
    }

    fun updateTag(tagId: String, name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate {
            it.update(
                tagId,
                TagPatch(
                    name = trimmed,
                    color = color.ifBlank { "#6366F1" },
                    archived = null,
                ),
            )
        }
    }

    fun setArchived(tagId: String, archived: Boolean) {
        mutate {
            it.update(
                tagId,
                TagPatch(
                    name = null,
                    color = null,
                    archived = archived,
                ),
            )
        }
    }

    fun moveActiveTag(index: Int, delta: Int) {
        val current = _uiState.value.activeTags
        val targetIndex = index + delta
        if (index !in current.indices || targetIndex !in current.indices) return
        val reordered = current.toMutableList().apply {
            val item = removeAt(index)
            add(targetIndex, item)
        }
        mutate {
            it.reorder(reordered.map { tag -> tag.id })
        }
    }

    fun deleteTag(tagId: String) {
        mutate {
            it.delete(tagId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun mutate(block: suspend (TagsInterface) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorRes = null) }
            try {
                session.withCore { core ->
                    core.tags().use { block(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(errorRes = coreFailureText(failure)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsManagementScreen(
    onBack: () -> Unit,
    onOpenTagTransactions: (String) -> Unit,
    viewModel: TagsManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditor by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf("#6366F1") }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }

    LaunchedEffect(state.errorRes) {
        state.errorRes?.let { res ->
            snackbarHostState.showSnackbar(context.getString(res))
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.core_tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingTag = null
                    draftName = ""
                    draftColor = "#6366F1"
                    showEditor = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.core_tags_add)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isLoading && state.activeTags.isEmpty() && state.archivedTags.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.core_tags_empty),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.core_tags_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.activeTags.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.core_tags_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                itemsIndexed(state.activeTags, key = { _, tag -> tag.id }) { index, tag ->
                    TagRowCard(
                        tag = tag,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.activeTags.lastIndex,
                        onMoveUp = { viewModel.moveActiveTag(index, -1) },
                        onMoveDown = { viewModel.moveActiveTag(index, 1) },
                        onClick = { onOpenTagTransactions(tag.id) },
                        onEdit = {
                            editingTag = tag
                            draftName = tag.name
                            draftColor = tag.color
                            showEditor = true
                        },
                        onToggleArchive = { viewModel.setArchived(tag.id, true) },
                        onDelete = { deletingTag = tag },
                    )
                }
            }

            if (state.archivedTags.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.core_tags_archived_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(state.archivedTags, key = { _, tag -> tag.id }) { _, tag ->
                    TagRowCard(
                        tag = tag,
                        canMoveUp = false,
                        canMoveDown = false,
                        onMoveUp = {},
                        onMoveDown = {},
                        onClick = { onOpenTagTransactions(tag.id) },
                        onEdit = {
                            editingTag = tag
                            draftName = tag.name
                            draftColor = tag.color
                            showEditor = true
                        },
                        onToggleArchive = { viewModel.setArchived(tag.id, false) },
                        onDelete = { deletingTag = tag },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = {
                Text(
                    if (editingTag == null) {
                        stringResource(R.string.core_tags_add)
                    } else {
                        stringResource(R.string.core_tags_edit)
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text(stringResource(R.string.core_tags_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftColor,
                        onValueChange = { draftColor = it },
                        label = { Text(stringResource(R.string.core_tags_color)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TAG_COLOR_PRESETS.forEach { hex ->
                            val isSelected = draftColor.equals(hex, ignoreCase = true)
                            val color = parseTagColor(hex) ?: MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { draftColor = hex },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = editingTag
                        if (target == null) {
                            viewModel.createTag(draftName, draftColor)
                        } else {
                            viewModel.updateTag(target.id, draftName, draftColor)
                        }
                        showEditor = false
                    },
                    enabled = draftName.isNotBlank(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text(stringResource(R.string.core_full_label_6)) },
            text = {
                Text(stringResource(R.string.core_tags_delete_confirm, tag.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(tag.id)
                        deletingTag = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.core_full_label_6),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TagRowCard(
    tag: Tag,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val badgeColor = parseTagColor(tag.color) ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.core_analytics_count,
                            tag.transactionCount.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!tag.archived && (canMoveUp || canMoveDown)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.account_move_up),
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = stringResource(R.string.account_move_down),
                        )
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.core_tags_edit),
                    )
                }
                IconButton(
                    onClick = onToggleArchive,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (tag.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = stringResource(
                            if (tag.archived) R.string.core_tags_unarchive else R.string.core_tags_archive,
                        ),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.core_full_label_6),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

data class DuplicateGroupUiModel(
    val group: DuplicateGroup,
    val keepTransaction: Transaction? = null,
    val duplicateTransactions: Map<String, Transaction> = emptyMap(),
)

data class DuplicateDetectionUiState(
    val groups: List<DuplicateGroupUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isRemoving: Boolean = false,
    val removedCount: Int? = null,
    val errorRes: Int? = null,
)

@HiltViewModel
class DuplicateDetectionViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateDetectionUiState())
    val uiState: StateFlow<DuplicateDetectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tables = setOf(
                Table.TRANSACTIONS,
                Table.TRANSACTION_TAGS,
                Table.ACCOUNTS,
                Table.CATEGORIES,
                Table.SUBCATEGORIES,
                Table.SUBSCRIPTIONS,
                Table.BUDGETS,
                Table.QUICK_TEMPLATES,
                Table.PEOPLE,
                Table.DEBTS,
                Table.TAGS,
            )
            session.observe(changes, tables) { core ->
                coreQuery {
                    core.ledger().use { ledger ->
                        val groups = ledger.duplicates()
                        val models = groups.map { group ->
                            if (group.table == Table.TRANSACTIONS) {
                                val keepTx = runCatching { ledger.get(group.keepId) }.getOrNull()
                                val dupTxs = group.duplicateIds.mapNotNull { id ->
                                    runCatching { ledger.get(id) }.getOrNull()?.let { id to it }
                                }.toMap()
                                DuplicateGroupUiModel(
                                    group = group,
                                    keepTransaction = keepTx,
                                    duplicateTransactions = dupTxs,
                                )
                            } else {
                                DuplicateGroupUiModel(group = group)
                            }
                        }
                        DuplicateDetectionUiState(
                            groups = models,
                            isLoading = false,
                        )
                    }
                }
            }.collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        _uiState.update {
                            snapshot.copy(
                                isRemoving = it.isRemoving,
                                removedCount = it.removedCount,
                                errorRes = it.errorRes,
                            )
                        }
                    },
                    onFailure = { failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorRes = coreFailureText(failure),
                            )
                        }
                    },
                )
            }
        }
    }

    fun removeGroup(group: DuplicateGroup) {
        removeGroups(listOf(group))
    }

    fun removeAll() {
        val all = _uiState.value.groups.map { it.group }
        if (all.isEmpty()) return
        removeGroups(all)
    }

    fun clearNotices() {
        _uiState.update { it.copy(removedCount = null, errorRes = null) }
    }

    private fun removeGroups(groups: List<DuplicateGroup>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoving = true, removedCount = null, errorRes = null) }
            try {
                val removedCount = session.withCore { core ->
                    core.ledger().use { it.removeDuplicates(groups) }
                }
                _uiState.update {
                    it.copy(removedCount = removedCount.toInt())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(errorRes = coreFailureText(failure))
                }
            } finally {
                _uiState.update { it.copy(isRemoving = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateDetectionScreen(
    onBack: () -> Unit,
    viewModel: DuplicateDetectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.removedCount, state.errorRes) {
        val notice = state.removedCount?.let {
            context.getString(R.string.core_duplicates_cleaned, it)
        } ?: state.errorRes?.let { context.getString(it) }
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.core_duplicates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.groups.isNotEmpty()) {
                        TextButton(
                            onClick = viewModel::removeAll,
                            enabled = !state.isRemoving,
                        ) {
                            Text(
                                stringResource(
                                    R.string.core_duplicates_clean_selected,
                                    state.groups.size,
                                ),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isLoading && state.groups.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.core_duplicates_empty),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.core_duplicates_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    state.groups,
                    key = { _, item -> "${item.group.table.name}:${item.group.keepId}" },
                ) { _, item ->
                    DuplicateGroupCard(
                        model = item,
                        enabled = !state.isRemoving,
                        onRemoveGroup = { viewModel.removeGroup(item.group) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    model: DuplicateGroupUiModel,
    enabled: Boolean,
    onRemoveGroup: () -> Unit,
) {
    val group = model.group
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = group.table.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = onRemoveGroup,
                    enabled = enabled,
                ) {
                    Text(
                        stringResource(
                            R.string.core_duplicates_remove_count,
                            group.duplicateIds.size,
                        ),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.core_duplicates_keep, group.keepId.take(8)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                model.keepTransaction?.let { tx ->
                    DuplicateTransactionSummaryRow(tx)
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.duplicateIds.forEach { dupId ->
                    Text(
                        text = "${stringResource(R.string.core_full_label_6)}: ${dupId.take(8)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    model.duplicateTransactions[dupId]?.let { tx ->
                        DuplicateTransactionSummaryRow(tx)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateTransactionSummaryRow(tx: Transaction) {
    val title = tx.merchant.ifBlank { tx.kind.name }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = tx.dateTime.coreDateText().substringBefore('T'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = CurrencyFormatter.formatCurrency(tx.amount, tx.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
