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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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

data class DuplicateRecordSummary(
    val title: String,
    val subtitle: String,
    val amountText: String? = null,
)

data class DuplicateGroupUiModel(
    val group: DuplicateGroup,
    val keepSummary: DuplicateRecordSummary,
    val duplicateSummaries: List<Pair<String, DuplicateRecordSummary>> = emptyList(),
) {
    val key: String
        get() = "${group.table.name}:${group.keepId}"
}

data class DuplicateDetectionUiState(
    val groups: List<DuplicateGroupUiModel> = emptyList(),
    val selectedGroupKeys: Set<String> = emptySet(),
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
                    val today = java.time.LocalDate.now().let {
                        dev.hootkit.core.LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte())
                    }
                    val categories = core.categories().use { api ->
                        val all = api.list(null, true)
                        all.associateBy { it.id } to all.flatMap { it.subcategories }.associateBy { it.id }
                    }
                    val (categoriesById, subcategoriesById) = categories
                    val accountsById = core.accounts().use { it.overview().accounts.associateBy { a -> a.id } }
                    val budgetsById = core.budgets().use { it.list(today, true, null).associateBy { b -> b.id } }
                    val (subscriptionsById, peopleById, debtsById, templatesById) = core.plans().use { plans ->
                        val subs = plans.subscriptions(today, true).associateBy { it.id }
                        val people = plans.people(true, today)
                        val debts = people.flatMap { p -> plans.debts(p.id) }.associateBy { it.id }
                        val templates = plans.templates().associateBy { it.id }
                        Quadruple(subs, people.associateBy { it.id }, debts, templates)
                    }

                    core.ledger().use { ledger ->
                        val groups = ledger.duplicates()
                        suspend fun summarize(table: Table, id: String): DuplicateRecordSummary =
                            when (table) {
                                Table.TRANSACTIONS -> {
                                    val tx = runCatching { ledger.get(id) }.getOrNull()
                                    if (tx != null) {
                                        val catName = tx.subcategoryId?.let { subcategoriesById[it]?.name }
                                            ?: tx.categoryId?.let { categoriesById[it]?.name }
                                        val accName = tx.accountId?.let { accountsById[it]?.name }
                                        val title = tx.merchant.ifBlank {
                                            catName ?: accName ?: tx.notes?.takeIf { it.isNotBlank() } ?: tx.kind.name
                                        }
                                        val datePart = tx.dateTime.coreDateText().replace('T', ' ').take(16)
                                        val meta = listOfNotNull(
                                            datePart,
                                            accName?.takeIf { it != title },
                                            catName?.takeIf { it != title },
                                        ).joinToString(" · ")
                                        DuplicateRecordSummary(
                                            title = title,
                                            subtitle = meta,
                                            amountText = CurrencyFormatter.formatCurrency(tx.amount, tx.currency),
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                Table.SUBSCRIPTIONS -> {
                                    val sub = subscriptionsById[id]
                                    if (sub != null) {
                                        val accName = sub.accountId?.let { accountsById[it]?.name }
                                        val meta = listOfNotNull(sub.nextDue?.coreDateText(), accName).joinToString(" · ")
                                        DuplicateRecordSummary(
                                            title = sub.name,
                                            subtitle = meta,
                                            amountText = CurrencyFormatter.formatCurrency(sub.amount.amount, sub.amount.currency),
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                Table.BUDGETS -> {
                                    val budget = budgetsById[id]
                                    if (budget != null) {
                                        DuplicateRecordSummary(
                                            title = budget.name.ifBlank { budget.period.name },
                                            subtitle = "${budget.current.start.coreDateText()} – ${budget.current.end.coreDateText()}",
                                            amountText = CurrencyFormatter.formatCurrency(budget.amount.amount, budget.amount.currency),
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                Table.PEOPLE -> {
                                    val person = peopleById[id]
                                    if (person != null) {
                                        DuplicateRecordSummary(
                                            title = person.name,
                                            subtitle = listOfNotNull(
                                                person.phone?.takeIf { it.isNotBlank() },
                                                person.notes?.takeIf { it.isNotBlank() },
                                            ).joinToString(" · "),
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                Table.DEBTS -> {
                                    val debt = debtsById[id]
                                    if (debt != null) {
                                        val personName = peopleById[debt.personId]?.name
                                        val title = listOfNotNull(
                                            personName,
                                            debt.title.takeIf { it.isNotBlank() },
                                        ).joinToString(" · ").ifBlank { debt.kind.name }
                                        DuplicateRecordSummary(
                                            title = title,
                                            subtitle = debt.date.coreDateText().substringBefore('T'),
                                            amountText = CurrencyFormatter.formatCurrency(debt.amount.amount, debt.amount.currency),
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                Table.QUICK_TEMPLATES -> {
                                    val tpl = templatesById[id]
                                    if (tpl != null) {
                                        val catName = tpl.categoryId?.let { categoriesById[it]?.name }
                                        val accName = tpl.accountId?.let { accountsById[it]?.name }
                                        DuplicateRecordSummary(
                                            title = tpl.name.ifBlank { tpl.merchant.ifBlank { catName ?: tpl.kind.name } },
                                            subtitle = listOfNotNull(catName, accName).joinToString(" · "),
                                            amountText = tpl.amount?.let { amt -> CurrencyFormatter.formatCurrency(amt, tpl.currency ?: "CNY") },
                                        )
                                    } else {
                                        DuplicateRecordSummary(title = id.take(8), subtitle = "")
                                    }
                                }
                                else -> DuplicateRecordSummary(title = id.take(8), subtitle = "")
                            }

                        val models = groups.map { group ->
                            DuplicateGroupUiModel(
                                group = group,
                                keepSummary = summarize(group.table, group.keepId),
                                duplicateSummaries = group.duplicateIds.map { dupId ->
                                    dupId to summarize(group.table, dupId)
                                },
                            )
                        }
                        DuplicateDetectionUiState(
                            groups = models,
                            selectedGroupKeys = models.map { it.key }.toSet(),
                            isLoading = false,
                        )
                    }
                }
            }.collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        _uiState.update { prev ->
                            val allNewKeys = snapshot.groups.map { it.key }.toSet()
                            val nextSelected = if (prev.isLoading) {
                                allNewKeys
                            } else {
                                val keptSelection = prev.selectedGroupKeys.intersect(allNewKeys)
                                if (keptSelection.isEmpty() && prev.selectedGroupKeys.isNotEmpty()) allNewKeys else keptSelection
                            }
                            snapshot.copy(
                                selectedGroupKeys = nextSelected,
                                isRemoving = prev.isRemoving,
                                removedCount = prev.removedCount,
                                errorRes = prev.errorRes,
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

    fun toggleGroupSelection(key: String) {
        _uiState.update { state ->
            val next = if (key in state.selectedGroupKeys) {
                state.selectedGroupKeys - key
            } else {
                state.selectedGroupKeys + key
            }
            state.copy(selectedGroupKeys = next)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val allKeys = state.groups.map { it.key }.toSet()
            val next = if (state.selectedGroupKeys.size == allKeys.size) emptySet() else allKeys
            state.copy(selectedGroupKeys = next)
        }
    }

    fun removeGroup(group: DuplicateGroup) {
        removeGroups(listOf(group))
    }

    fun removeSelected() {
        val state = _uiState.value
        val selected = state.groups.filter { it.key in state.selectedGroupKeys }.map { it.group }
        if (selected.isEmpty()) return
        removeGroups(selected)
    }

    fun clearNotices() {
        _uiState.update { it.copy(removedCount = null, errorRes = null) }
    }

    fun removeGroups(groups: List<DuplicateGroup>) {
        if (groups.isEmpty()) return
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

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@androidx.annotation.StringRes
private fun duplicateTableLabelRes(table: Table): Int =
    when (table) {
        Table.TRANSACTIONS -> R.string.sync_table_transactions
        Table.ACCOUNTS -> R.string.sync_table_accounts
        Table.BUDGETS -> R.string.sync_table_budgets
        Table.SUBSCRIPTIONS -> R.string.sync_table_subscriptions
        Table.DEBTS -> R.string.sync_table_debts
        Table.PEOPLE -> R.string.sync_table_people
        Table.CATEGORIES -> R.string.sync_table_categories
        Table.SUBCATEGORIES -> R.string.sync_table_subcategories
        Table.TAGS -> R.string.sync_table_tags
        Table.QUICK_TEMPLATES -> R.string.sync_table_quick_templates
        Table.AI_SETTINGS -> R.string.sync_table_ai_settings
        Table.BROKERAGE_CONNECTIONS -> R.string.sync_table_brokerage_connections
        Table.SETTINGS -> R.string.sync_table_settings
        else -> R.string.sync_table_transactions
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DuplicateDetectionScreen(
    onBack: () -> Unit,
    viewModel: DuplicateDetectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingGroupsToClean by remember { mutableStateOf<List<DuplicateGroup>?>(null) }

    LaunchedEffect(state.removedCount, state.errorRes) {
        val notice = state.removedCount?.let {
            context.getString(R.string.core_duplicates_cleaned, it)
        } ?: state.errorRes?.let { context.getString(it) }
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotices()
        }
    }

    val selectedGroups = remember(state.groups, state.selectedGroupKeys) {
        state.groups.filter { it.key in state.selectedGroupKeys }.map { it.group }
    }
    val allSelected = state.groups.isNotEmpty() && state.selectedGroupKeys.size == state.groups.size

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
                            onClick = viewModel::toggleSelectAll,
                            enabled = !state.isRemoving,
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) R.string.core_duplicates_deselect_all
                                    else R.string.core_duplicates_select_all
                                )
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.groups.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = { pendingGroupsToClean = selectedGroups },
                            enabled = !state.isRemoving && selectedGroups.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.core_duplicates_clean_selected,
                                    selectedGroups.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
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
            } else if (state.groups.isNotEmpty()) {
                item(key = "duplicates_guide") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.core_duplicates_guide),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                itemsIndexed(
                    state.groups,
                    key = { _, item -> item.key },
                ) { _, item ->
                    DuplicateGroupCard(
                        model = item,
                        selected = item.key in state.selectedGroupKeys,
                        enabled = !state.isRemoving,
                        onToggleSelect = { viewModel.toggleGroupSelection(item.key) },
                    )
                }
            }
        }
    }

    pendingGroupsToClean?.let { groupsToClean ->
        val totalDuplicatesCount = groupsToClean.sumOf { it.duplicateIds.size }
        AlertDialog(
            onDismissRequest = { pendingGroupsToClean = null },
            title = { Text(stringResource(R.string.core_duplicates_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.core_duplicates_confirm_body,
                        totalDuplicatesCount,
                        groupsToClean.size,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = groupsToClean
                        pendingGroupsToClean = null
                        viewModel.removeGroups(target)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.core_full_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupsToClean = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DuplicateGroupCard(
    model: DuplicateGroupUiModel,
    selected: Boolean,
    enabled: Boolean,
    onToggleSelect: () -> Unit,
) {
    val group = model.group
    val totalCopies = 1 + group.duplicateIds.size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            )
        } else {
            null
        },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        enabled = enabled,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(duplicateTableLabelRes(group.table)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Text(
                        text = stringResource(R.string.core_duplicates_group_badge, totalCopies),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    DuplicateRecordSummaryRow(model.keepSummary)
                }
            }

            Text(
                text = if (selected) {
                    stringResource(R.string.core_duplicates_action_selected, group.duplicateIds.size)
                } else {
                    stringResource(R.string.core_duplicates_action_skipped, totalCopies)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DuplicateRecordSummaryRow(summary: DuplicateRecordSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (summary.subtitle.isNotBlank()) {
                Text(
                    text = summary.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        summary.amountText?.let { amountText ->
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
