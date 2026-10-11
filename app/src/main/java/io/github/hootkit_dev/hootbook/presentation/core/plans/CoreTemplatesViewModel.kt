package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import java.time.LocalDate as JavaDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal val TEMPLATE_TABLES = setOf(Table.QUICK_TEMPLATES, Table.TRANSACTIONS, Table.ACCOUNTS,
    Table.CATEGORIES, Table.SUBCATEGORIES, Table.SETTINGS)

data class CoreTemplatesUiState(
    val templates: List<QuickTemplate> = emptyList(),
    val suggestions: List<TemplateSuggestion> = emptyList(),
    val references: CorePlanReferences = CorePlanReferences(),
    val selected: QuickTemplate? = null,
    val selectedId: String? = null,
    val form: CoreTemplateForm? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val errorResource:Int?=null,
    val commandRevision: Long = 0,
)

@HiltViewModel
class CoreTemplatesViewModel private constructor(
    private val session: CoreSession?, changes: ChangeBus,
    private val testPlans: (suspend () -> PlansInterface)?,
    private val testReferences: (suspend () -> CorePlanReferences)?,
    private val today: () -> LocalDate,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, null, { JavaDate.now().corePlanDate() })
    internal constructor(changes: ChangeBus, plans: suspend () -> PlansInterface,
        references: suspend () -> CorePlanReferences = { CorePlanReferences() },
        today: () -> LocalDate = { JavaDate.now().corePlanDate() }) : this(null, changes, plans, references, today)
    private suspend fun <T> withPlans(block: suspend (PlansInterface) -> T): T {
        if (testPlans != null) return block(testPlans.invoke())
        return session!!.awaitCore().plans().use { block(it) }
    }
    private val mutable = MutableStateFlow(CoreTemplatesUiState())
    val state = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var queryVersion = 0L
    init { viewModelScope.launch { changes.observe(TEMPLATE_TABLES).collect { refresh() } } }
    fun refresh() {
        val version = ++queryVersion
        queryJob?.cancel()
        val selectedId = mutable.value.selectedId
        mutable.update { it.copy(loading = true) }
        queryJob = viewModelScope.launch {
            try {
                val (rows, suggestions) = withPlans { it.templates() to it.templateSuggestions(today()) }
                val references = testReferences?.invoke() ?: session!!.awaitCore().planReferences()
                if (version == queryVersion) mutable.update { it.copy(templates = rows, suggestions = suggestions,
                    references = references, selected = rows.find { row -> row.id == selectedId }, loading = false,
                    errorResource = if (selectedId != null && rows.none { row -> row.id == selectedId }) io.github.hootkit_dev.hootbook.R.string.core_detail_template_missing else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (version == queryVersion) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }
    fun select(id: String?) { if (blockNavigation()) return; mutable.update { it.copy(selectedId = id, selected = null, error = null) }; refresh() }
    fun create() { if (blockNavigation()) return; mutable.update { it.copy(form = CoreTemplateForm(), error = null) } }
    fun edit() { if (blockNavigation()) return; mutable.value.selected?.let { row -> mutable.update { it.copy(form = CoreTemplateForm.from(row), error = null) } } }
    fun useSuggestion(row: TemplateSuggestion) { if (blockNavigation()) return; mutable.update { it.copy(form = CoreTemplateForm.from(row), error = null) } }
    fun updateForm(form: CoreTemplateForm) { if (!mutable.value.saving) mutable.update { it.copy(form = form, error = null) } }
    fun back(): Boolean {
        if (blockNavigation()) return true
        if (mutable.value.form != null) { mutable.update { it.copy(form = null, error = null) }; return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    fun canNavigate(): Boolean = !blockNavigation()
    private fun blockNavigation(): Boolean {
        if (!mutable.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }; return true
    }
    fun clearError() { mutable.update { it.copy(error = null,errorResource=null) } }
    fun save() {
        val form = mutable.value.form ?: return
        command { api ->
            val id = if (form.id == null) api.createTemplate(form.toDraft()) else form.id.also { api.updateTemplate(it, form.toDraft()) }
            mutable.update { it.copy(form = null, selectedId = id, selected = null) }
        }
    }
    fun delete() { val row = mutable.value.selected ?: return
        command { it.deleteTemplate(row.id); mutable.update { state -> state.copy(selectedId = null, selected = null) } }
    }
    fun move(row: QuickTemplate, direction: Int) {
        val ids = moveTemplateIds(mutable.value.templates, row.id, direction) ?: return
        command { it.reorderTemplates(ids) }
    }
    fun dismissSuggestion(row: TemplateSuggestion) = command { it.dismissSuggestion(row.key) }
    private fun command(block: suspend (PlansInterface) -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try { withPlans(block); mutable.update { it.copy(commandRevision = it.commandRevision + 1) }; refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) } }
        }
    }
}
