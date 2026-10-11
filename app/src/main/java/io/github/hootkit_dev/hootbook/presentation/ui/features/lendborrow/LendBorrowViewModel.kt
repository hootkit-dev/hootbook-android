package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

data class LendBorrowUiState(
    val summary: DebtSummary? = null,
    val persons: List<Person> = emptyList(),
    val filteredPersons: List<Person> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: LendBorrowFilter = LendBorrowFilter.ALL,
    val selectedCategory: PersonRelation? = null,
    val selectedTab: Int = 0, // 0 = People, 1 = All Entries
    val isLoading: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedPersonIds: Set<String> = emptySet(),
    val showAddPersonSheet: Boolean = false,
    val showAddTransactionSheet: Boolean = false,
    val selectedPersonForTx: Person? = null,
    val baseCurrency: String = "CNY",
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val linkedTransactions: Map<String, Transaction> = emptyMap()
)

enum class LendBorrowFilter {
    ALL,
    YOU_GET,    // People who owe you (totalLent > totalBorrowed)
    YOU_OWE,    // People you owe (totalBorrowed > totalLent)
    OVERDUE,
    SETTLED
}


@HiltViewModel
class LendBorrowViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    val attachmentStore: CoreAttachmentStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(LendBorrowUiState(
        selectedFilter = (savedState.get<String>("selectedFilter") ?: savedState.get<String>("filter"))
            ?.let { runCatching { LendBorrowFilter.valueOf(it) }.getOrNull() } ?: LendBorrowFilter.ALL,
        searchQuery = savedState["searchQuery"] ?: "",
        selectedCategory = savedState.get<String>("selectedCategory")?.let { runCatching { PersonRelation.valueOf(it) }.getOrNull() }))
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            session.observe(changes, planDebtTables) { core -> coreQuery {
                core.plans().use { api ->
                    val today = planToday()
                    val persons = api.people(false, today)
                    val summary = api.debtSummary(today)
                    val accounts = core.accounts().use { it.overview().accounts.associateBy(Account::id) }
                    LendBorrowUiState(summary = summary, persons = persons, baseCurrency = summary.currency,
                        accounts = accounts, isLoading = false)
                }
            } }.collect { result -> result.fold(
                onSuccess = { snapshot -> mutable.update { current -> current.copy(summary = snapshot.summary,
                    persons = snapshot.persons, filteredPersons = applyFilterAndSearch(snapshot.persons, current.selectedFilter,
                        current.searchQuery, current.selectedCategory), baseCurrency = snapshot.baseCurrency,
                    accounts = snapshot.accounts, isLoading = false, error = null) } },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, error = failure.message) } }) }
        }
    }
    fun createPerson(draft: PersonDraft, onSaved: (String) -> Unit) = command {
        attachmentStore.retainForCore(listOfNotNull(draft.avatar))
        val id = it.createPerson(draft)
        withContext(Dispatchers.Main) { onSaved(id) }
    }
    fun addPerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonRelation?) = command {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        it.createPerson(PersonDraft(name, phone?.takeIf(String::isNotBlank), notes?.takeIf(String::isNotBlank), color, avatar, category))
        showAddPersonSheet(false)
    }
    fun addTransaction(draft: DebtDraft) = command { attachmentStore.retainForCore(draft.attachments); it.addDebt(draft); showAddTransactionSheet(false) }
    fun deleteSelectedPersons() {
        val ids = uiState.value.selectedPersonIds
        if (ids.isEmpty()) return
        command { api -> ids.forEach { api.deletePerson(it) }; mutable.update { it.copy(isSelectionMode = false, selectedPersonIds = emptySet()) } }
    }

    fun onSearchQueryChanged(query: String) {
        savedState["searchQuery"] = query
        mutable.update { state ->
            val filtered = applyFilterAndSearch(state.persons, state.selectedFilter, query, state.selectedCategory)
            state.copy(searchQuery = query, filteredPersons = filtered)
        }
    }
    fun onFilterSelected(filter: LendBorrowFilter) {
        savedState["selectedFilter"] = filter.name
        mutable.update { state ->
            val filtered = applyFilterAndSearch(state.persons, filter, state.searchQuery, state.selectedCategory)
            state.copy(selectedFilter = filter, filteredPersons = filtered)
        }
    }
    fun onCategorySelected(category: PersonRelation?) {
        savedState["selectedCategory"] = category?.name
        mutable.update { state ->
            val filtered = applyFilterAndSearch(state.persons, state.selectedFilter, state.searchQuery, category)
            state.copy(selectedCategory = category, filteredPersons = filtered)
        }
    }
    fun onTabSelected(tabIndex: Int) {
        mutable.update { it.copy(selectedTab = tabIndex) }
    }
    fun showAddPersonSheet(show: Boolean) {
        mutable.update { it.copy(showAddPersonSheet = show) }
    }
    fun showAddTransactionSheet(show: Boolean, person: Person? = null) {
        mutable.update { it.copy(showAddTransactionSheet = show, selectedPersonForTx = person) }
    }
    fun toggleSelectionMode() {
        mutable.update { state ->
            if (state.isSelectionMode) {
                state.copy(isSelectionMode = false, selectedPersonIds = emptySet())
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }
    fun togglePersonSelection(personId: String) {
        mutable.update { state ->
            state.copy(
                selectedPersonIds = if (personId in state.selectedPersonIds) {
                    state.selectedPersonIds - personId
                } else {
                    state.selectedPersonIds + personId
                }
            )
        }
    }
    fun selectAllPersons() {
        mutable.update { state ->
            state.copy(selectedPersonIds = state.filteredPersons.map { it.id }.toSet())
        }
    }
    fun clearSelection() {
        mutable.update { it.copy(selectedPersonIds = emptySet()) }
    }
    fun selectPersonSet(ids: Set<String>) {
        mutable.update { it.copy(selectedPersonIds = ids) }
    }
    private fun applyFilterAndSearch(
        persons: List<Person>,
        filter: LendBorrowFilter,
        query: String,
        category: PersonRelation?
    ): List<Person> {
        return persons.filter { person ->
            val matchesQuery = query.isBlank() ||
                    person.name.contains(query, ignoreCase = true) ||
                    (person.phone?.contains(query) == true) ||
                    (person.notes?.contains(query, ignoreCase = true) == true)

            val matchesFilter = when (filter) {
                LendBorrowFilter.ALL -> true
                LendBorrowFilter.YOU_GET -> person.totals.net > BigDecimal.ZERO
                LendBorrowFilter.YOU_OWE -> person.totals.net < BigDecimal.ZERO
                LendBorrowFilter.OVERDUE -> person.totals.overdue
                LendBorrowFilter.SETTLED -> person.totals.net.compareTo(BigDecimal.ZERO) == 0 && person.totals.receivable.compareTo(BigDecimal.ZERO) == 0 && person.totals.payable.compareTo(BigDecimal.ZERO) == 0
            }

            val matchesCategory = when (category) {
                null -> true
                PersonRelation.OTHER -> person.relation == null || person.relation == PersonRelation.OTHER
                else -> person.relation == category
            }

            matchesQuery && matchesFilter && matchesCategory
        }
    }
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
