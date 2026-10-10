package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import io.github.hootkit_dev.hootbook.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.LendBorrowType
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowTransactionItem
import io.github.hootkit_dev.hootbook.domain.model.PersonCategory
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowPersonUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.AddEditLendBorrowTransactionUseCase
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import io.github.hootkit_dev.hootbook.domain.usecase.GetPersonDetailUseCase
import io.github.hootkit_dev.hootbook.domain.usecase.SettleLendBorrowUseCase
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

data class PersonDetailUiState(
    val person: LendBorrowPerson? = null,
    val transactions: List<LendBorrowTransactionItem> = emptyList(),
    val isLoading: Boolean = true,
    val showEditPersonSheet: Boolean = false,
    val showAddTransactionSheet: Boolean = false,
    val showSettleSheet: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val transactionForAction: LendBorrowTransactionItem? = null,
    val showTransactionActionDialog: Boolean = false,
    val transactionToEdit: LendBorrowTransactionItem? = null,
    val showEditTransactionSheet: Boolean = false,
    val showDeleteTransactionDialog: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedRecordIds: Set<Long> = emptySet(),
    val baseCurrency: String = "CNY",
    val accounts: List<AccountBalanceEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList()
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPersonDetailUseCase: GetPersonDetailUseCase,
    private val addEditPersonUseCase: AddEditLendBorrowPersonUseCase,
    private val addEditTransactionUseCase: AddEditLendBorrowTransactionUseCase,
    private val settleLendBorrowUseCase: SettleLendBorrowUseCase,
    private val currencyRepository: CurrencyRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val categoryRepository: CategoryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    val attachmentService: AttachmentService
) : ViewModel() {

    private val personId: Long = checkNotNull(savedStateHandle["personId"])
    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getPersonDetailUseCase.getPerson(personId),
                getPersonDetailUseCase.getTransactions(personId),
                currencyRepository.effectiveBaseCurrencyCode,
                accountBalanceRepository.getAllLatestBalances(),
                categoryRepository.getAllCategories()
            ) { person: LendBorrowPerson?, transactions: List<LendBorrowTransactionItem>, currency: String, accounts: List<AccountBalanceEntity>, categories: List<CategoryEntity> ->
                PersonDetailUiState(
                    person = person,
                    transactions = transactions,
                    baseCurrency = currency,
                    accounts = accounts,
                    categories = categories,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.update { state ->
                    newState.copy(
                        showEditPersonSheet = state.showEditPersonSheet,
                        showAddTransactionSheet = state.showAddTransactionSheet,
                        showSettleSheet = state.showSettleSheet,
                        showDeleteConfirmDialog = state.showDeleteConfirmDialog,
                        transactionForAction = state.transactionForAction,
                        showTransactionActionDialog = state.showTransactionActionDialog,
                        transactionToEdit = state.transactionToEdit,
                        showEditTransactionSheet = state.showEditTransactionSheet,
                        showDeleteTransactionDialog = state.showDeleteTransactionDialog,
                        isSelectionMode = state.isSelectionMode,
                        selectedRecordIds = state.selectedRecordIds
                    )
                }
            }
        }
    }

    fun showEditPersonSheet(show: Boolean) {
        _uiState.update { it.copy(showEditPersonSheet = show) }
    }

    fun showAddTransactionSheet(show: Boolean) {
        _uiState.update { it.copy(showAddTransactionSheet = show) }
    }

    fun showSettleSheet(show: Boolean) {
        _uiState.update { it.copy(showSettleSheet = show) }
    }

    fun showDeleteConfirmDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmDialog = show) }
    }

    fun showTransactionActions(transaction: LendBorrowTransactionItem) {
        _uiState.update {
            it.copy(transactionForAction = transaction, showTransactionActionDialog = true)
        }
    }

    fun dismissTransactionActions() {
        _uiState.update { it.copy(showTransactionActionDialog = false) }
    }

    fun openTransactionEdit(transaction: LendBorrowTransactionItem) {
        _uiState.update {
            it.copy(
                transactionToEdit = transaction,
                showEditTransactionSheet = true,
                showTransactionActionDialog = false
            )
        }
    }

    fun hideEditTransactionSheet() {
        _uiState.update { it.copy(showEditTransactionSheet = false, transactionToEdit = null) }
    }

    fun deleteTransactionRequested(transaction: LendBorrowTransactionItem) {
        _uiState.update {
            it.copy(
                transactionForAction = transaction,
                showDeleteTransactionDialog = true,
                showTransactionActionDialog = false
            )
        }
    }

    fun dismissTransactionDeleteDialog() {
        _uiState.update { it.copy(showDeleteTransactionDialog = false) }
    }

    fun updatePerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonCategory?) {
        viewModelScope.launch {
            addEditPersonUseCase.updatePerson(personId, name, phone, notes, color, avatar, category)
            showEditPersonSheet(false)
        }
    }

    fun addPerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonCategory?) {
        viewModelScope.launch {
            addEditPersonUseCase.addPerson(name, phone, notes, color, avatar, category)
        }
    }

    fun deletePerson(onDeleted: () -> Unit) {
        viewModelScope.launch {
            addEditPersonUseCase.deletePerson(personId)
            showDeleteConfirmDialog(false)
            onDeleted()
        }
    }

    fun addTransaction(
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

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            addEditTransactionUseCase.deleteTransaction(transactionId)
            dismissTransactionDeleteDialog()
        }
    }

    fun enterSelectionMode(recordId: Long) {
        _uiState.update {
            it.copy(isSelectionMode = true, selectedRecordIds = setOf(recordId))
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { state ->
            if (state.isSelectionMode) {
                state.copy(isSelectionMode = false, selectedRecordIds = emptySet())
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }

    fun toggleRecordSelection(recordId: Long) {
        _uiState.update { state ->
            state.copy(
                selectedRecordIds = if (recordId in state.selectedRecordIds) {
                    state.selectedRecordIds - recordId
                } else {
                    state.selectedRecordIds + recordId
                }
            )
        }
    }

    fun selectAllRecords() {
        _uiState.update { state ->
            state.copy(selectedRecordIds = state.transactions.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedRecordIds = emptySet()) }
    }

    fun deleteSelectedRecords() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedRecordIds
            if (selectedIds.isEmpty()) return@launch
            selectedIds.forEach { addEditTransactionUseCase.deleteTransaction(it) }
            _uiState.update { it.copy(isSelectionMode = false, selectedRecordIds = emptySet()) }
        }
    }

    fun updateTransaction(
        transactionId: Long,
        type: LendBorrowType,
        amount: BigDecimal,
        title: String,
        dueDate: LocalDateTime?,
        date: LocalDateTime,
        accountId: Long? = null,
        category: String? = null,
        merchant: String? = null,
        attachments: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            addEditTransactionUseCase.updateTransaction(
                id = transactionId,
                personId = personId,
                type = type,
                amount = amount,
                title = title,
                dueDate = dueDate,
                date = date,
                accountId = accountId,
                category = category,
                merchant = merchant,
                attachments = attachments
            )
            hideEditTransactionSheet()
        }
    }

    fun settle(amount: BigDecimal, note: String, isLentSettlement: Boolean, accountId: Long? = null) {
        viewModelScope.launch {
            settleLendBorrowUseCase.settle(
                personId = personId,
                amount = amount,
                title = note.ifBlank { context.getString(R.string.settlement_title) },
                isLentSettlement = isLentSettlement,
                accountId = accountId
            )
            showSettleSheet(false)
        }
    }
}
