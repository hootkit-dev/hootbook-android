package io.github.hootkit_dev.hootbook.presentation.core.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoreCategoryDraft(
    val id: String? = null,
    val parentId: String? = null,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val name: String = "",
    val icon: String = "type_food_fork_knife",
    val color: String = "#33B5E5",
) {
    fun newCategory() = NewCategory(kind, name.trim(), icon.trim(), color.trim())
    fun categoryPatch() = CategoryPatch(name = name.trim(), icon = icon.trim(), color = color.trim())
    fun newSubcategory() = NewSubcategory(requireNotNull(parentId), name.trim(), icon.trim(), color.trim())
    fun subcategoryPatch() = SubcategoryPatch(name = name.trim(), icon = icon.trim(), color = color.trim())
}

data class CoreCategoriesUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val kind: CategoryKind? = null,
    val selectedId: String? = null,
    val selected: Category? = null,
    val transactionCount: UInt = 0u,
    val draft: CoreCategoryDraft? = null,
    val commandRevision: Long = 0,
)

@HiltViewModel
class CoreCategoriesViewModel private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val testCategories: (suspend () -> CategoriesInterface)?,
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, savedState: SavedStateHandle? = null) : this(session, changes, null, savedState)
    internal constructor(changes: ChangeBus, categories: suspend () -> CategoriesInterface) : this(null, changes, categories)
    private suspend fun <T> withCategories(block: suspend (CategoriesInterface) -> T): T {
        if (testCategories != null) return block(testCategories.invoke())
        return session!!.awaitCore().categories().use { block(it) }
    }
    private val mutable = MutableStateFlow(CoreCategoriesUiState(draft = restoreCategoryDraft(savedState?.get("category_draft"))))
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    init {
        viewModelScope.launch {
            changes.observe(setOf(Table.CATEGORIES, Table.SUBCATEGORIES, Table.TRANSACTIONS, Table.SETTINGS)).collect { refresh() }
        }
    }
    fun refresh() {
        val revision = ++queryRevision
        query?.cancel()
        val selectedId = mutable.value.selectedId
        val kind = mutable.value.kind
        mutable.update { it.copy(loading = true) }
        query = viewModelScope.launch {
            try {
                val (rows, count) = withCategories { api ->
                    val rows = api.list(kind, true)
                    rows to if (rows.any { it.id == selectedId }) api.transactionCount(selectedId!!) else 0u
                }
                if (revision == queryRevision) mutable.update { it.copy(loading = false, categories = rows,
                    selected = rows.find { row -> row.id == selectedId }, selectedId = rows.find { row -> row.id == selectedId }?.id,
                    transactionCount = count) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (revision == queryRevision) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }
    fun select(id: String?) { if (rejectNavigationWhileSaving()) return; mutable.update { it.copy(selectedId = id, selected = null) }; refresh() }
    fun filter(kind: CategoryKind?) { if (rejectNavigationWhileSaving()) return; mutable.update { it.copy(kind = kind, selectedId = null, selected = null) }; refresh() }
    /** true consumes back locally; false lets the host pop this screen. */
    fun back(): Boolean {
        if (rejectNavigationWhileSaving()) return true
        if (mutable.value.draft != null) { dismissEditor(); return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    private fun rejectNavigationWhileSaving(): Boolean {
        if (!mutable.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }
        return true
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun createCategory() { if (!mutable.value.saving) { mutable.update { it.copy(draft = CoreCategoryDraft(kind = it.kind ?: CategoryKind.EXPENSE)) }; persistDraft() } }
    fun editCategory(category: Category) { if (!mutable.value.saving) mutable.update { it.copy(draft = CoreCategoryDraft(category.id, kind = category.kind,
        name = category.displayName, icon = category.icon, color = category.color)) }; persistDraft() }
    fun createSubcategory(category: Category) { if (!mutable.value.saving) mutable.update { it.copy(draft = CoreCategoryDraft(parentId = category.id,
        kind = category.kind, icon = category.icon, color = category.color)) }; persistDraft() }
    fun editSubcategory(category: Category, row: Subcategory) { if (!mutable.value.saving) mutable.update { it.copy(draft = CoreCategoryDraft(row.id,
        row.categoryId, category.kind, row.displayName, row.icon, row.color)) }; persistDraft() }
    fun draftChanged(draft: CoreCategoryDraft) { if (!mutable.value.saving) { mutable.update { it.copy(draft = draft) }; persistDraft() } }
    fun dismissEditor() { if (!mutable.value.saving) { mutable.update { it.copy(draft = null) }; persistDraft() } }
    fun save() {
        val draft = mutable.value.draft ?: return
        command { api ->
            when {
                draft.parentId == null && draft.id == null -> api.create(draft.newCategory())
                draft.parentId == null -> api.update(draft.id!!, draft.categoryPatch())
                draft.id == null -> api.createSubcategory(draft.newSubcategory())
                else -> api.updateSubcategory(draft.id, draft.subcategoryPatch())
            }
            mutable.update { it.copy(draft = null) }; persistDraft()
        }
    }
    fun hide(category: Category) = command { it.update(category.id, CategoryPatch(hidden = !category.hidden)) }
    fun hideSubcategory(row: Subcategory) = command { it.updateSubcategory(row.id, SubcategoryPatch(hidden = !row.hidden)) }
    fun reset(category: Category) = command { it.reset(category.id) }
    fun resetSubcategory(row: Subcategory) = command { it.resetSubcategory(row.id) }
    fun delete(category: Category, destination: CategoryRef?) = command {
        it.delete(category.id, destination)
        mutable.update { state -> state.copy(selectedId = null, selected = null) }
    }
    fun deleteSubcategory(row: Subcategory) = command { it.deleteSubcategory(row.id) }
    fun move(category: Category, direction: Int) {
        val ids = mutable.value.categories.map { it.id }.toMutableList()
        if (moveId(ids, category.id, direction)) command { it.reorder(ids) }
    }
    fun moveSubcategory(category: Category, row: Subcategory, direction: Int) {
        val ids = category.subcategories.map { it.id }.toMutableList()
        if (moveId(ids, row.id, direction)) command { it.reorderSubcategories(category.id, ids) }
    }
    private fun persistDraft() { savedState?.set("category_draft", mutable.value.draft?.let(::saveCategoryDraft)) }
    private fun command(block: suspend (CategoriesInterface) -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try {
                withCategories(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) } }
        }
    }
}

internal fun moveId(ids: MutableList<String>, id: String, direction: Int): Boolean {
    val index = ids.indexOf(id)
    val destination = index + direction
    if (index < 0 || destination !in ids.indices) return false
    val other = ids[destination]
    ids[destination] = id
    ids[index] = other
    return true
}
internal fun canHide(category: Category): Boolean = category.builtinKey !in setOf("other_expense", "other_income")

internal fun saveCategoryDraft(value: CoreCategoryDraft) = Bundle().apply {
    putString("id", value.id); putString("parent", value.parentId); putString("kind", value.kind.name)
    putString("name", value.name); putString("icon", value.icon); putString("color", value.color)
}
internal fun restoreCategoryDraft(value: Bundle?): CoreCategoryDraft? = value?.let {
    runCatching { CoreCategoryDraft(it.getString("id"), it.getString("parent"), CategoryKind.valueOf(it.getString("kind") ?: "EXPENSE"),
        it.getString("name").orEmpty(), it.getString("icon").orEmpty(), it.getString("color").orEmpty()) }.getOrNull()
}
