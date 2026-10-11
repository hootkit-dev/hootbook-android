package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
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

data class PersonDetailUiState(
    val person: Person? = null,
    val transactions: List<DebtEntry> = emptyList(),
    val isLoading: Boolean = true,
    val showEditPersonSheet: Boolean = false,
    val showAddTransactionSheet: Boolean = false,
    val showSettleSheet: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val transactionForAction: DebtEntry? = null,
    val showTransactionActionDialog: Boolean = false,
    val transactionToEdit: DebtEntry? = null,
    val showEditTransactionSheet: Boolean = false,
    val showDeleteTransactionDialog: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedRecordIds: Set<String> = emptySet(),
    val baseCurrency: String = "CNY",
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val saving: Boolean = false,
    val error: Int? = null,
    val linkedTransactions: Map<String, Transaction> = emptyMap()
)


@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    val attachmentStore: CoreAttachmentStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val personId: String = checkNotNull(savedState["personId"])
    private val mutable = MutableStateFlow(PersonDetailUiState())
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            session.observe(changes, planDebtTables) { core -> coreQuery {
                core.plans().use { api ->
                    val person = api.person(personId, planToday())
                    val debts = api.debts(personId)
                    val accounts = core.accounts().use { it.overview().accounts.associateBy(Account::id) }
                    val categories = core.categories().use { it.list(null, true).associateBy(Category::id) }
                    val linked = core.ledger().use { ledger -> debts.mapNotNull { debt ->
                        debt.transactionId?.let { id -> ledger.get(id)?.let { id to it } }
                    }.toMap() }
                    PersonDetailUiState(person = person, transactions = debts, baseCurrency = person.totals.currency,
                        accounts = accounts, categories = categories, linkedTransactions = linked, isLoading = false)
                }
            } }.collect { result -> result.fold(
                onSuccess = { snapshot -> mutable.update { it.copy(person = snapshot.person, transactions = snapshot.transactions,
                    accounts = snapshot.accounts, categories = snapshot.categories, linkedTransactions = snapshot.linkedTransactions,
                    baseCurrency = snapshot.baseCurrency, isLoading = false, error = null) } },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, error = coreFailureText(failure)) } }) }
        }
    }
    fun updatePerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonRelation?) = command {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        it.updatePerson(personId, PersonDraft(name, phone?.takeIf(String::isNotBlank), notes?.takeIf(String::isNotBlank), color, avatar, category))
        showEditPersonSheet(false)
    }
    fun createPerson(draft: PersonDraft, onSaved: (String) -> Unit) = command {
        attachmentStore.retainForCore(listOfNotNull(draft.avatar))
        val id = it.createPerson(draft)
        withContext(Dispatchers.Main) { onSaved(id) }
    }
    fun addPerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonRelation?) = command {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        it.createPerson(PersonDraft(name, phone?.takeIf(String::isNotBlank), notes?.takeIf(String::isNotBlank), color, avatar, category))
    }
    fun deletePerson(onDeleted: () -> Unit) = command(onDeleted) { it.deletePerson(personId) }
    fun addTransaction(draft: DebtDraft) = command { attachmentStore.retainForCore(draft.attachments); it.addDebt(draft); showAddTransactionSheet(false) }
    fun updateTransaction(id: String, draft: DebtDraft) = command { attachmentStore.retainForCore(draft.attachments); it.updateDebt(id, draft); hideEditTransactionSheet() }
    fun deleteTransaction(id: String) = command { it.deleteDebt(id); dismissTransactionDeleteDialog() }
    fun settle(draft: SettleDraft) = command { it.settle(personId, draft); showSettleSheet(false) }
    fun deleteSelectedRecords() {
        val ids = uiState.value.selectedRecordIds
        if (ids.isEmpty()) return
        command { api -> ids.forEach { api.deleteDebt(it) }; mutable.update { it.copy(isSelectionMode = false, selectedRecordIds = emptySet()) } }
    }

    fun showEditPersonSheet(show: Boolean) {
        mutable.update { it.copy(showEditPersonSheet = show) }
    }
    fun showAddTransactionSheet(show: Boolean) {
        mutable.update { it.copy(showAddTransactionSheet = show) }
    }
    fun showSettleSheet(show: Boolean) {
        mutable.update { it.copy(showSettleSheet = show) }
    }
    fun showDeleteConfirmDialog(show: Boolean) {
        mutable.update { it.copy(showDeleteConfirmDialog = show) }
    }
    fun showTransactionActions(transaction: DebtEntry) {
        mutable.update {
            it.copy(transactionForAction = transaction, showTransactionActionDialog = true)
        }
    }
    fun dismissTransactionActions() {
        mutable.update { it.copy(showTransactionActionDialog = false) }
    }
    fun openTransactionEdit(transaction: DebtEntry) {
        mutable.update {
            it.copy(
                transactionToEdit = transaction,
                showEditTransactionSheet = true,
                showTransactionActionDialog = false
            )
        }
    }
    fun hideEditTransactionSheet() {
        mutable.update { it.copy(showEditTransactionSheet = false, transactionToEdit = null) }
    }
    fun deleteTransactionRequested(transaction: DebtEntry) {
        mutable.update {
            it.copy(
                transactionForAction = transaction,
                showDeleteTransactionDialog = true,
                showTransactionActionDialog = false
            )
        }
    }
    fun dismissTransactionDeleteDialog() {
        mutable.update { it.copy(showDeleteTransactionDialog = false) }
    }
    fun enterSelectionMode(recordId: String) {
        mutable.update {
            it.copy(isSelectionMode = true, selectedRecordIds = setOf(recordId))
        }
    }
    fun toggleSelectionMode() {
        mutable.update { state ->
            if (state.isSelectionMode) {
                state.copy(isSelectionMode = false, selectedRecordIds = emptySet())
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }
    fun toggleRecordSelection(recordId: String) {
        mutable.update { state ->
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
        mutable.update { state ->
            state.copy(selectedRecordIds = state.transactions.map { it.id }.toSet())
        }
    }
    fun clearSelection() {
        mutable.update { it.copy(selectedRecordIds = emptySet()) }
    }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (PlansInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { session.withCore { core -> core.plans().use { block(it) } }; onSuccess() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
