package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowSummary
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowTransactionItem
import io.github.hootkit_dev.hootbook.domain.model.PersonCategory
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowPersonUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowTransactionUseCase
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import io.github.hootkit_dev.hootbook.domain.usecase.GetLendBorrowPersonsUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.GetLendBorrowSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

data class LendBorrowUiState(
    val summary: LendBorrowSummary = LendBorrowSummary(),
    val persons: List<LendBorrowPerson> = emptyList(),
    val filteredPersons: List<LendBorrowPerson> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: LendBorrowFilter = LendBorrowFilter.ALL,
    val selectedCategory: PersonCategory? = null,
    val selectedTab: Int = 0, // 0 = People, 1 = All Entries
    val isLoading: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedPersonIds: Set<Long> = emptySet(),
    val showAddPersonSheet: Boolean = false,
    val showAddTransactionSheet: Boolean = false,
    val selectedPersonForTx: LendBorrowPerson? = null,
    val baseCurrency: String = "CNY",
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList()
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
    private val getSummaryUseCase: GetLendBorrowSummaryUseCase,
    private val getPersonsUseCase: GetLendBorrowPersonsUseCase,
    private val addEditPersonUseCase: AddEditLendBorrowPersonUseCase,
    private val addEditTransactionUseCase: AddEditLendBorrowTransactionUseCase,
    private val currencyRepository: CurrencyRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val categoryRepository: CategoryRepository,
    val attachmentService: AttachmentService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialFilter: LendBorrowFilter = savedStateHandle.get<String>("filter")
        ?.let { runCatching { LendBorrowFilter.valueOf(it) }.getOrNull() }
        ?: LendBorrowFilter.ALL

    private val _uiState = MutableStateFlow(LendBorrowUiState(selectedFilter = initialFilter))
    val uiState: StateFlow<LendBorrowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getSummaryUseCase(),
                getPersonsUseCase(),
                currencyRepository.effectiveBaseCurrencyCode,
                accountBalanceRepository.getAllLatestBalances(),
                categoryRepository.getAllCategories()
            ) { summary, persons, currency, accounts, categories ->
                _uiState.update { state ->
                    val filtered = applyFilterAndSearch(persons, state.selectedFilter, state.searchQuery, state.selectedCategory)
                    state.copy(
                        summary = summary,
                        persons = persons,
                        filteredPersons = filtered,
                        baseCurrency = currency,
                        accounts = accounts,
                        categories = categories,
                        isLoading = false
                    )
                }
            }.collect { }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.persons, state.selectedFilter, query, state.selectedCategory)
            state.copy(searchQuery = query, filteredPersons = filtered)
        }
    }

    fun onFilterSelected(filter: LendBorrowFilter) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.persons, filter, state.searchQuery, state.selectedCategory)
            state.copy(selectedFilter = filter, filteredPersons = filtered)
        }
    }

    fun onCategorySelected(category: PersonCategory?) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.persons, state.selectedFilter, state.searchQuery, category)
            state.copy(selectedCategory = category, filteredPersons = filtered)
        }
    }

    fun onTabSelected(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun showAddPersonSheet(show: Boolean) {
        _uiState.update { it.copy(showAddPersonSheet = show) }
    }

    fun showAddTransactionSheet(show: Boolean, person: LendBorrowPerson? = null) {
        _uiState.update { it.copy(showAddTransactionSheet = show, selectedPersonForTx = person) }
    }

    fun addPerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonCategory?) {
        viewModelScope.launch {
            addEditPersonUseCase.addPerson(name, phone, notes, color, avatar, category)
            showAddPersonSheet(false)
        }
    }

    fun addTransaction(
        personId: Long,
        type: LendBorrowType,
        amount: BigDecimal,
        title: String,
        dueDate: LocalDateTime?,
        accountId: Long? = null,
        category: String? = null,
        merchant: String? = null,
        attachments: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            addEditTransactionUseCase.addTransaction(
                personId = personId,
                type = type,
                amount = amount,
                title = title,
                dueDate = dueDate,
                accountId = accountId,
                category = category,
                merchant = merchant,
                attachments = attachments
            )
            showAddTransactionSheet(false)
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { state ->
            if (state.isSelectionMode) {
                state.copy(isSelectionMode = false, selectedPersonIds = emptySet())
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }

    fun togglePersonSelection(personId: Long) {
        _uiState.update { state ->
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
        _uiState.update { state ->
            state.copy(selectedPersonIds = state.filteredPersons.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPersonIds = emptySet()) }
    }

    fun selectPersonSet(ids: Set<Long>) {
        _uiState.update { it.copy(selectedPersonIds = ids) }
    }

    fun deleteSelectedPersons() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedPersonIds
            if (selectedIds.isEmpty()) return@launch
            selectedIds.forEach { addEditPersonUseCase.deletePerson(it) }
            _uiState.update { it.copy(isSelectionMode = false, selectedPersonIds = emptySet()) }
        }
    }

    private fun applyFilterAndSearch(
        persons: List<LendBorrowPerson>,
        filter: LendBorrowFilter,
        query: String,
        category: PersonCategory?
    ): List<LendBorrowPerson> {
        return persons.filter { person ->
            val matchesQuery = query.isBlank() ||
                    person.name.contains(query, ignoreCase = true) ||
                    (person.phoneNumber?.contains(query) == true) ||
                    (person.notes?.contains(query, ignoreCase = true) == true)

            val matchesFilter = when (filter) {
                LendBorrowFilter.ALL -> true
                LendBorrowFilter.YOU_GET -> person.netBalance > BigDecimal.ZERO
                LendBorrowFilter.YOU_OWE -> person.netBalance < BigDecimal.ZERO
                LendBorrowFilter.OVERDUE -> person.hasOverdue
                LendBorrowFilter.SETTLED -> person.netBalance.compareTo(BigDecimal.ZERO) == 0 && person.totalLent.compareTo(BigDecimal.ZERO) == 0 && person.totalBorrowed.compareTo(BigDecimal.ZERO) == 0
            }

            val matchesCategory = when (category) {
                null -> true
                PersonCategory.OTHER -> person.category == null || person.category == PersonCategory.OTHER
                else -> person.category == category
            }

            matchesQuery && matchesFilter && matchesCategory
        }
    }
}
