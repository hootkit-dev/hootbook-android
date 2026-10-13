package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(private val session: CoreSession, private val changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(BudgetUiState())
    val uiState = mutable.asStateFlow()
    private val edit = MutableStateFlow(EditBudgetState())
    val editBudgetState = edit.asStateFlow()
    private val selection = MutableStateFlow<Triple<String, LocalDate?, LocalDate?>?>(null)
    init {
        viewModelScope.launch {
            selection.flatMapLatest { selected ->
                session.observe(changes, budgetTables) { core -> coreQuery {
                    val currency = core.settings().use { it.mainCurrency() }
                    val accounts = core.accounts().use { it.overview().accounts.associateBy(Account::id) }
                    val categories = core.categories().use { it.list(null, true).associateBy(Category::id) }
                    core.budgets().use { api ->
                        val rows = api.list(budgetToday(), false, currency)
                        val detail = selected?.let { api.get(it.first, it.second ?: budgetToday(), currency) }
                        if (selected?.second != null) require(detail?.current?.start == selected.second)
                        if (selected?.third != null) require(detail?.current?.end == selected.third)
                        BudgetUiState(false, rows, detail, accounts, categories, currency)
                    }
                } }
            }.collect { result -> result.fold(
                onSuccess = { snapshot -> mutable.update { snapshot.copy(saving = it.saving) } },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, error = coreFailureText(failure)) } }) }
        }
    }
    fun selectBudget(id: String, start: String? = null, end: String? = null) {
        val next = runCatching { Triple(id,
            start?.let { java.time.LocalDate.parse(it).coreBudgetDate() },
            end?.let { java.time.LocalDate.parse(it).coreBudgetDate() }) }.getOrElse { failure ->
            mutable.update { it.copy(isLoading = false, error = coreFailureText(failure)) }; return
        }
        if (selection.value == next) return
        mutable.update { it.copy(isLoading = true, selectedBudget = null, error = null) }
        selection.value = next
    }
    fun clearSelectedBudget() { selection.value = null }
    fun initNewBudget() { if (!uiState.value.saving) edit.value = EditBudgetState(currency = uiState.value.baseCurrency) }
    fun initEditBudget(row: Budget) { if (!uiState.value.saving) edit.value = EditBudgetState(row.id, row.name,
        row.amount.amount, row.anchor.javaBudgetDate().atStartOfDay(), (row.end ?: row.current.end).javaBudgetDate().atStartOfDay(),
        row.anchor, row.period, row.kind, row.accountIds, row.color, row.amount.currency,
        row.current.limits.map { NewBudgetLimit(it.categoryId, it.amount) }) }
    fun clearEditState() { if (!uiState.value.saving) initNewBudget() }
    private fun draft(block: (EditBudgetState) -> EditBudgetState) { if (!uiState.value.saving) edit.update(block) }
    fun updateBudgetAmount(amount: BigDecimal) = draft { it.copy(amount = amount) }
    fun updateBudgetName(name: String) = draft { it.copy(name = name) }
    fun updateStartDate(date: LocalDateTime) = draft { it.copy(startDate = date) }
    fun updateEndDate(date: LocalDateTime) = draft { it.copy(endDate = date) }
    fun updatePeriodType(period: BudgetPeriod) = draft { it.copy(periodType = period) }
    fun updateBudgetType(kind: BudgetKind) = draft { it.copy(budgetType = kind) }
    fun updateAccountIds(ids: List<String>?) = draft { it.copy(accountIds = ids) }
    fun updateColor(color: String) = draft { it.copy(color = color) }
    fun addCategoryLimit(id: String, amount: BigDecimal) {
        val row = edit.value
        if (row.budgetId == null) draft { it.copy(categoryLimits = it.categoryLimits.filterNot { l -> l.categoryId == id } + NewBudgetLimit(id, amount)) }
        else command { core -> core.budgets().use { it.setLimit(row.budgetId, id, amount) }; edit.update { it.copy(categoryLimits = it.categoryLimits.filterNot { l -> l.categoryId == id } + NewBudgetLimit(id, amount)) } }
    }
    fun removeCategoryLimit(id: String) {
        val row = edit.value
        if (row.budgetId == null) draft { it.copy(categoryLimits = it.categoryLimits.filterNot { l -> l.categoryId == id }) }
        else command { core -> core.budgets().use { it.setLimit(row.budgetId, id, null) }; edit.update { it.copy(categoryLimits = it.categoryLimits.filterNot { l -> l.categoryId == id }) } }
    }
    fun saveBudget(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        val row = edit.value
        command(onSuccess, onError) { core -> core.budgets().use { api ->
            val start = row.startDate.toLocalDate().coreBudgetDate()
            val end = row.endDate.toLocalDate().coreBudgetDate().takeIf { row.periodType == BudgetPeriod.CUSTOM }
            val money = Money(row.amount, row.currency)
            val name = row.name.ifBlank { row.getDefaultName() }
            if (row.budgetId == null) api.create(NewBudget(name, row.budgetType, row.periodType, money, start, end,
                row.accountIds, row.color, row.categoryLimits))
            else api.update(row.budgetId, BudgetPatch(name, row.budgetType, row.periodType, money,
                start.takeUnless { it == row.originalStart }, end, row.accountIds, row.accountIds == null, color = row.color))
        } }
    }
    fun deleteBudget(budgetId: String, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) = command(onSuccess, onError) {
        it.budgets().use { api -> api.delete(budgetId) }
    }
    private fun command(onSuccess: () -> Unit = {}, onError: () -> Unit = {}, block: suspend (HootKitCoreInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { session.withCore(block); onSuccess() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) }; onError() }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
