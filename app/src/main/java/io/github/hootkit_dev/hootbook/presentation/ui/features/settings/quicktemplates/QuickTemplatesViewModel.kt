package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

data class QuickTemplatesUiState(
    val templates: List<QuickTemplate> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val subcategories: Map<String, Subcategory> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class QuickTemplatesViewModel @Inject constructor(private val session: CoreSession, private val changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(QuickTemplatesUiState())
    val uiState = mutable.asStateFlow()
    private val order = MutableStateFlow<List<String>?>(null)
    init {
        viewModelScope.launch {
            session.observe(changes, setOf(Table.QUICK_TEMPLATES, Table.CATEGORIES, Table.SUBCATEGORIES)) { core -> coreQuery {
                val templates = core.plans().use { it.templates() }
                core.categories().use { api ->
                    val categories = api.list(null, true)
                    QuickTemplatesUiState(templates, categories.associateBy(Category::id),
                        categories.flatMap { it.subcategories }.associateBy(Subcategory::id), loading = false)
                }
            } }.collect { result -> result.fold(
                onSuccess = { snapshot -> mutable.update { snapshot.copy(saving = it.saving) } },
                onFailure = { failure -> mutable.update { it.copy(loading = false, error = failure.message) } }) }
        }
        // Drag events coalesce, then the newest order is persisted after the current command.
        viewModelScope.launch {
            order.filterNotNull().collect { ids ->
                uiState.first { !it.saving }
                if (ids != order.value) return@collect
                command { it.reorderTemplates(ids) }
                uiState.first { !it.saving }
            }
        }
    }
    fun update(row: QuickTemplate, name: String, amount: BigDecimal?, prefill: Boolean, onSaved: () -> Unit = {}) = command(onSaved) {
        it.updateTemplate(row.id, QuickTemplateDraft(name, row.kind, row.merchant, row.categoryId, row.subcategoryId,
            amount, prefill && amount != null, row.accountId, row.currency, row.notes))
    }
    fun delete(id: String) = command { it.deleteTemplate(id) }
    fun reorder(rows: List<QuickTemplate>) { order.value = rows.map(QuickTemplate::id) }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (PlansInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { session.withCore { core -> core.plans().use { block(it) } }; onSuccess() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
