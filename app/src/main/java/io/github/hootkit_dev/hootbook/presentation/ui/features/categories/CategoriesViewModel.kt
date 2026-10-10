package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import io.github.hootkit_dev.hootbook.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class CategoriesViewModel
@Inject
constructor(
        private val categoryRepository: CategoryRepository,
        private val subcategoryRepository: SubcategoryRepository,
        private val transactionRepository: TransactionRepository,
        private val categoryRenamer: io.github.hootkit_dev.hootbook.data.repository.CategoryRenamer,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    // Categories list
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.categories

    // Subcategories map (category ID -> list of subcategories)
    val subcategories: StateFlow<Map<Long, List<SubcategoryEntity>>> = 
        subcategoryRepository.subcategoriesMap

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Deletion Flow State
    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _categoryToDelete = MutableStateFlow<CategoryEntity?>(null)
    val categoryToDelete: StateFlow<CategoryEntity?> = _categoryToDelete.asStateFlow()

    private val _hasTransactions = MutableStateFlow(false)
    val hasTransactions: StateFlow<Boolean> = _hasTransactions.asStateFlow()

    private val _showMigrationSheet = MutableStateFlow(false)
    val showMigrationSheet: StateFlow<Boolean> = _showMigrationSheet.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Filtered categories
    val filteredCategories: StateFlow<List<CategoryEntity>> = combine(
        categories,
        subcategories,
        _searchQuery
    ) { categories, subcategoriesMap, query ->
        if (query.isBlank()) {
            categories
        } else {
            categories.filter { category ->
                val categoryMatches = io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(category.name, query)
                val subcategoriesMatch = subcategoriesMap[category.id]?.any {
                    io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(it.name, query)
                } == true
                categoryMatches || subcategoriesMatch
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dialog states
    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()

    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory: StateFlow<CategoryEntity?> = _editingCategory.asStateFlow()

    // Snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Subcategory Dialog states
    private val _showSubcategoryDialog = MutableStateFlow(false)
    val showSubcategoryDialog: StateFlow<Boolean> = _showSubcategoryDialog.asStateFlow()

    private val _editingSubcategory = MutableStateFlow<SubcategoryEntity?>(null)
    val editingSubcategory: StateFlow<SubcategoryEntity?> = _editingSubcategory.asStateFlow()

    private val _targetCategoryId = MutableStateFlow<Long?>(null)
    val targetCategoryId: StateFlow<Long?> = _targetCategoryId.asStateFlow()

    fun showAddDialog() {
        _editingCategory.value = null
        _showAddEditDialog.value = true
    }

    fun showEditDialog(category: CategoryEntity) {
        // Allow editing system categories now (they can be edited but not deleted)
        _editingCategory.value = category
        _showAddEditDialog.value = true
    }

    fun hideDialog() {
        _showAddEditDialog.value = false
        _editingCategory.value = null
    }

    fun showAddSubcategoryDialog(categoryId: Long) {
        _targetCategoryId.value = categoryId
        _editingSubcategory.value = null
        _showSubcategoryDialog.value = true
    }

    fun showEditSubcategoryDialog(subcategory: SubcategoryEntity) {
        _targetCategoryId.value = subcategory.categoryId
        _editingSubcategory.value = subcategory
        _showSubcategoryDialog.value = true
    }

    fun hideSubcategoryDialog() {
        _showSubcategoryDialog.value = false
        _editingSubcategory.value = null
        _targetCategoryId.value = null
    }

    fun saveCategory(name: String, description: String, color: String, iconResId: Int, iconName: String, isIncome: Boolean) {
        viewModelScope.launch {
            try {
                val editingCat = _editingCategory.value

                if (editingCat != null) {
                    if (name != editingCat.name && categoryRepository.categoryExists(name)) {
                        _snackbarMessage.value = context.getString(R.string.cat_msg_exists, name)
                        return@launch
                    }
                    // The new name reaches every transaction, budget, subscription and template
                    categoryRenamer.renameCategory(editingCat.name, name) {
                        categoryRepository.updateCategory(
                            editingCat.copy(
                                name = name,
                                description = description,
                                color = color,
                                iconResId = iconResId,
                                iconName = iconName,
                                isIncome = isIncome
                            )
                        )
                    }
                    _snackbarMessage.value = context.getString(R.string.cat_msg_updated)
                } else {
                    // Check if category already exists
                    if (categoryRepository.categoryExists(name)) {
                        _snackbarMessage.value = context.getString(R.string.cat_msg_exists, name)
                        return@launch
                    }

                    // Create new category
                    categoryRepository.createCategory(
                        name = name,
                        description = description,
                        color = color,
                        iconResId = iconResId,
                        iconName = iconName,
                        isIncome = isIncome
                    )
                    _snackbarMessage.value = context.getString(R.string.cat_msg_created)
                }

                hideDialog()
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.cat_msg_save_error, e.message.orEmpty())
            }
        }
    }

    fun resetCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                val category = categoryRepository.getCategoryById(categoryId)
                val defaultName = category?.defaultName ?: category?.name
                if (category != null && defaultName != null) {
                    categoryRenamer.renameCategory(category.name, defaultName) {
                        categoryRepository.resetCategoryToDefault(categoryId)
                    }
                }
                _snackbarMessage.value = context.getString(R.string.cat_msg_reset)
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.cat_msg_reset_error, e.message.orEmpty())
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (category.isSystem) {
            _snackbarMessage.value = context.getString(R.string.cat_msg_system)
            return
        }

        viewModelScope.launch {
            try {
                _categoryToDelete.value = category
                val count = transactionRepository.getTransactionCountByCategory(category.name)
                _hasTransactions.value = count > 0
                _showDeleteConfirmation.value = true
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.cat_msg_check_error, e.message.orEmpty())
            }
        }
    }

    fun dismissDeleteConfirmation() {
        _showDeleteConfirmation.value = false
        _categoryToDelete.value = null
        _hasTransactions.value = false
    }

    fun showMigrationSheet() {
        _showDeleteConfirmation.value = false
        _showMigrationSheet.value = true
    }

    fun hideMigrationSheet() {
        _showMigrationSheet.value = false
        _categoryToDelete.value = null
        _hasTransactions.value = false
    }

    fun confirmDelete(moveToMiscellaneous: Boolean = true) {
        val category = _categoryToDelete.value ?: return
        
        viewModelScope.launch {
            try {
                if (moveToMiscellaneous && _hasTransactions.value) {
                    // Move transactions to "Miscellaneous"
                    transactionRepository.updateTransactionsCategory(
                        oldCategory = category.name,
                        newCategory = "Miscellaneous",
                        newSubcategory = null
                    )
                }
                performDelete(category)
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.cat_msg_delete_error, e.message.orEmpty())
            }
        }
    }

    fun confirmMigrationToCategory(newCategory: CategoryEntity, newSubcategory: SubcategoryEntity?) {
        val oldCategory = _categoryToDelete.value ?: return
        
        viewModelScope.launch {
            try {
                transactionRepository.updateTransactionsCategory(
                    oldCategory = oldCategory.name,
                    newCategory = newCategory.name,
                    newSubcategory = newSubcategory?.name
                )
                performDelete(oldCategory)
                hideMigrationSheet()
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.cat_msg_move_error, e.message.orEmpty())
                hideMigrationSheet()
            }
        }
    }

    private suspend fun performDelete(category: CategoryEntity) {
        val deleted = categoryRepository.deleteCategory(category.id)
        if (deleted) {
            _snackbarMessage.value = context.getString(R.string.cat_msg_deleted)
        } else {
            _snackbarMessage.value = context.getString(R.string.cat_msg_cannot_delete)
        }
        _showDeleteConfirmation.value = false
        _categoryToDelete.value = null
        _hasTransactions.value = false
    }

    fun saveSubcategory(name: String, iconResId: Int, iconName: String, color: String) {
        val categoryId = _targetCategoryId.value ?: return
        val editingSub = _editingSubcategory.value

        viewModelScope.launch {
            try {
                if (editingSub != null) {
                    val parent = categoryRepository.getCategoryById(editingSub.categoryId)?.name
                    val update: suspend () -> Unit = {
                        subcategoryRepository.updateSubcategory(
                            editingSub.copy(name = name, iconResId = iconResId, iconName = iconName, color = color)
                        )
                    }
                    if (parent != null) categoryRenamer.renameSubcategory(parent, editingSub.name, name, update) else update()
                    _snackbarMessage.value = context.getString(R.string.sub_msg_updated)
                } else {
                    subcategoryRepository.createSubcategory(
                        categoryId = categoryId,
                        name = name,
                        iconResId = iconResId,
                        iconName = iconName,
                        color = color
                    )
                    _snackbarMessage.value = context.getString(R.string.sub_msg_added)
                }
                hideSubcategoryDialog()
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.sub_msg_save_error, e.message.orEmpty())
            }
        }
    }

    fun resetSubcategory(subcategoryId: Long) {
        viewModelScope.launch {
            try {
                val sub = subcategoryRepository.getSubcategoryById(subcategoryId)
                val parent = sub?.let { categoryRepository.getCategoryById(it.categoryId)?.name }
                val defaultName = sub?.defaultName ?: sub?.name
                if (sub != null && parent != null && defaultName != null) {
                    categoryRenamer.renameSubcategory(parent, sub.name, defaultName) {
                        subcategoryRepository.resetSubcategoryToDefault(subcategoryId)
                    }
                }
                _snackbarMessage.value = context.getString(R.string.sub_msg_reset)
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.sub_msg_reset_error, e.message.orEmpty())
            }
        }
    }

    fun deleteSubcategory(subcategory: SubcategoryEntity) {
        if (subcategory.isSystem) {
            _snackbarMessage.value = context.getString(R.string.sub_msg_system)
            return
        }

        viewModelScope.launch {
            try {
                val deleted = subcategoryRepository.deleteSubcategory(subcategory)
                if (deleted) {
                    _snackbarMessage.value = context.getString(R.string.sub_msg_deleted)
                } else {
                    _snackbarMessage.value = context.getString(R.string.sub_msg_cannot_delete)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.sub_msg_delete_error, e.message.orEmpty())
            }
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}

data class CategoriesUiState(val isLoading: Boolean = false, val errorMessage: String? = null)
