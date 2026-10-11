package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BudgetHistoryUiState(
    val isLoading: Boolean = true,
    val budget: Budget? = null,
    val periods: List<BudgetPeriodSummary> = emptyList(),
    val cursor: LocalDate? = null,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetHistoryViewModel @Inject constructor(private val session: CoreSession, private val changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(BudgetHistoryUiState())
    val uiState = mutable.asStateFlow()
    private val selected = MutableStateFlow<String?>(null)
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private var page: Job? = null
    init {
        viewModelScope.launch {
            selected.filterNotNull().flatMapLatest { id ->
                session.observe(changes, budgetTables) { core ->
                    generation.incrementAndGet(); page?.cancel()
                    coreQuery { core.budgets().use { api ->
                        val today = budgetToday()
                        val budget = api.get(id, today, null)
                        val periods = api.history(id, today, 24u)
                        appendBudgetHistory(emptyList(), periods, budget.anchor, today).let { window -> BudgetHistoryUiState(false, budget, window.rows, window.nextCursor) }
                    } }
                }
            }.collect { result -> result.fold(
                onSuccess = { mutable.value = it },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, loadingMore = false, error = failure.message) } }) }
        }
    }
    fun loadBudgetHistory(id: String) {
        if (selected.value == id) return
        generation.incrementAndGet(); page?.cancel(); mutable.value = BudgetHistoryUiState(); selected.value = id
    }
    fun loadMore() {
        val before = uiState.value
        val budget = before.budget ?: return
        val cursor = before.cursor ?: return
        if (before.loadingMore || before.isLoading) return
        val mine = generation.get()
        mutable.update { it.copy(loadingMore = true, error = null) }
        page = viewModelScope.launch {
            try {
                val rows = session.withCore { core -> core.budgets().use { it.history(budget.id, cursor, 24u) } }
                val window = appendBudgetHistory(before.periods, rows, budget.anchor, cursor)
                if (mine == generation.get()) mutable.update { it.copy(loadingMore = false, periods = window.rows, cursor = window.nextCursor) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (mine == generation.get()) mutable.update { it.copy(loadingMore = false, error = failure.message) } }
        }
    }
}
