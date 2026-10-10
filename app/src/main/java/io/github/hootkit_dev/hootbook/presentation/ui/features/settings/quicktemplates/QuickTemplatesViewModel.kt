package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.repository.QuickTemplateRepository
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class QuickTemplatesViewModel @Inject constructor(
    private val repository: QuickTemplateRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    /** For the category icon and colour on each row. */
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.categories

    val templates: StateFlow<List<QuickTemplateEntity>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun update(template: QuickTemplateEntity, name: String, amount: BigDecimal?, prefillAmount: Boolean) {
        viewModelScope.launch {
            repository.update(
                template.copy(
                    name = name.trim().ifBlank { template.merchantName },
                    amount = amount,
                    prefillAmount = prefillAmount && amount != null
                )
            )
        }
    }

    /** Persist a new order after a drag; [ordered] is the full list in its new order. */
    fun reorder(ordered: List<QuickTemplateEntity>) {
        viewModelScope.launch { repository.reorder(ordered) }
    }
}
