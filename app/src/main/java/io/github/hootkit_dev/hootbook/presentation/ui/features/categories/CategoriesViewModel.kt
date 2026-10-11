package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CategoriesUiState(
    val categories: Map<String, Category> = emptyMap(),
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val showAddEditDialog: Boolean = false,
    val editingCategory: Category? = null,
    val showSubcategoryDialog: Boolean = false,
    val editingSubcategory: Subcategory? = null,
    val targetCategoryId: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val categoryToDelete: Category? = null,
    val hasTransactions: Boolean = false,
    val showMigrationSheet: Boolean = false,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
) : ViewModel() {
    private val mutable = MutableStateFlow(CategoriesUiState())
    val uiState = mutable.asStateFlow()
    private var deletionQuery: Job? = null
    private var deletionGeneration = 0

    init {
        viewModelScope.launch {
            session.observe(changes, setOf(Table.CATEGORIES, Table.SUBCATEGORIES, Table.TRANSACTIONS)) { core ->
                coreQuery { core.categories().use { it.list(null, true).associateBy(Category::id) } }
            }.collect { result ->
                result.fold(
                    onSuccess = { rows -> mutable.update { it.copy(categories = rows, isLoading = false) } },
                    onFailure = { failure -> mutable.update { it.copy(isLoading = false, errorMessage = failure.message) } },
                )
            }
        }
    }

    fun updateSearchQuery(query: String) { mutable.update { it.copy(searchQuery = query) } }
    fun showAddDialog() { if (!uiState.value.saving) mutable.update { it.copy(showAddEditDialog = true, editingCategory = null) } }
    fun showEditDialog(category: Category) { if (!uiState.value.saving) mutable.update { it.copy(showAddEditDialog = true, editingCategory = category) } }
    fun hideDialog() { if (!uiState.value.saving) mutable.update { it.copy(showAddEditDialog = false, editingCategory = null) } }
    fun showAddSubcategoryDialog(categoryId: String) { if (!uiState.value.saving) mutable.update { it.copy(showSubcategoryDialog = true, editingSubcategory = null, targetCategoryId = categoryId) } }
    fun showEditSubcategoryDialog(subcategory: Subcategory) { if (!uiState.value.saving) mutable.update { it.copy(showSubcategoryDialog = true, editingSubcategory = subcategory, targetCategoryId = subcategory.categoryId) } }
    fun hideSubcategoryDialog() { if (!uiState.value.saving) mutable.update { it.copy(showSubcategoryDialog = false, editingSubcategory = null, targetCategoryId = null) } }
    fun saveCategory(name: String, color: String, iconName: String, isIncome: Boolean) {
        val editing = uiState.value.editingCategory
        command { api ->
            if (editing == null) api.create(NewCategory(if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE, name, iconName, color))
            else api.update(editing.id, CategoryPatch(name = name.takeUnless { it == editing.displayName }, color = color, icon = iconName))
            mutable.update { it.copy(showAddEditDialog = false, editingCategory = null) }
        }
    }
    fun saveSubcategory(name: String, iconResId: Int, iconName: String, color: String) {
        val state = uiState.value
        val parent = state.targetCategoryId ?: return
        command { api ->
            if (state.editingSubcategory == null) api.createSubcategory(NewSubcategory(parent, name, iconName, color))
            else api.updateSubcategory(state.editingSubcategory.id, SubcategoryPatch(name = name.takeUnless { it == state.editingSubcategory.displayName }, icon = iconName, color = color))
            mutable.update { it.copy(showSubcategoryDialog = false, editingSubcategory = null, targetCategoryId = null) }
        }
    }
    fun resetCategory(id: String) = command { api ->
        api.reset(id)
        mutable.update { it.copy(showAddEditDialog = false, editingCategory = null) }
    }
    fun resetSubcategory(id: String) = command { api ->
        api.resetSubcategory(id)
        mutable.update { it.copy(showSubcategoryDialog = false, editingSubcategory = null) }
    }
    fun deleteSubcategory(row: Subcategory) = command { api ->
        api.deleteSubcategory(row.id)
        mutable.update { it.copy(showSubcategoryDialog = false, editingSubcategory = null) }
    }
    fun deleteCategory(category: Category) {
        if (uiState.value.saving) return
        val generation = ++deletionGeneration
        deletionQuery?.cancel()
        deletionQuery = viewModelScope.launch {
            val result = coreQuery { session.withCore { core -> core.categories().use { it.transactionCount(category.id) } } }
            if (generation != deletionGeneration) return@launch
            result.fold(
                onSuccess = { count -> mutable.update { it.copy(categoryToDelete = category, hasTransactions = count > 0u, showDeleteConfirmation = true) } },
                onFailure = { failure -> mutable.update { it.copy(errorMessage = failure.message) } },
            )
        }
    }
    fun dismissDeleteConfirmation() {
        if (uiState.value.saving) return
        ++deletionGeneration; deletionQuery?.cancel()
        mutable.update { it.copy(showDeleteConfirmation = false, categoryToDelete = null) }
    }
    fun showMigrationSheet() { if (!uiState.value.saving) mutable.update { it.copy(showMigrationSheet = true, showDeleteConfirmation = false) } }
    fun hideMigrationSheet() { if (!uiState.value.saving) mutable.update { it.copy(showMigrationSheet = false, categoryToDelete = null) } }
    fun confirmDelete(moveToMiscellaneous: Boolean = true) = delete(null)
    fun confirmMigrationToCategory(category: Category, subcategory: Subcategory?) = delete(CategoryRef(category.id, subcategory?.id))
    private fun delete(destination: CategoryRef?) {
        val row = uiState.value.categoryToDelete ?: return
        command { api ->
            api.delete(row.id, destination)
            mutable.update { it.copy(showDeleteConfirmation = false, showMigrationSheet = false, categoryToDelete = null, showAddEditDialog = false, editingCategory = null) }
        }
    }
    fun reorder(ids: List<String>) = command { it.reorder(ids) }
    fun reorderSubcategories(categoryId: String, ids: List<String>) = command { it.reorderSubcategories(categoryId, ids) }
    fun clearSnackbarMessage() { mutable.update { it.copy(errorMessage = null) } }
    private fun command(block: suspend (CategoriesInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            try { session.withCore { core -> core.categories().use { block(it) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(errorMessage = failure.message) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
