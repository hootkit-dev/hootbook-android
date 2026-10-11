package io.github.hootkit_dev.hootbook.presentation.core.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoreBudgetsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val errorResource:Int?=null,
    val rows: List<Budget> = emptyList(),
    val selectedId: String? = null,
    val selected: Budget? = null,
    val history: List<BudgetPeriodSummary> = emptyList(),
    val showHistory: Boolean = false,
    val historyCursor: LocalDate? = null,
    val historyLoading: Boolean = false,
    val historyDetail: Budget? = null,
    val historyDetailLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val displayCurrency: String = "",
    val draft: CoreBudgetDraft? = null,
    val limitDraft: CoreBudgetLimitDraft? = null,
    val commandRevision: Long = 0,
)

data class CoreBudgetLimitDraft(val budgetId: String, val categoryId: String, val name: String, val amount: String, val existing: Boolean)

/** A test seam of generated service interfaces, not a production repository or native test. */
internal data class BudgetQueryContext(val accounts: List<Account>, val categories: List<Category>, val currency: String)

@HiltViewModel
class CoreBudgetsViewModel private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val testBudgets: (suspend () -> BudgetsInterface)?,
    private val testContext: (suspend () -> BudgetQueryContext)?,
    private val today: () -> LocalDate,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, null, ::budgetToday)
    internal constructor(changes: ChangeBus, today: () -> LocalDate = ::budgetToday,
        context: suspend () -> BudgetQueryContext = { BudgetQueryContext(emptyList(), emptyList(), "CNY") },
        budgets: suspend () -> BudgetsInterface) : this(null, changes, budgets, context, today)
    private val mutable = MutableStateFlow(CoreBudgetsUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var historyQuery:Job?=null
    private var detailQuery:Job?=null
    private var detailRevision=0L
    private suspend fun <T> withBudgets(block: suspend (BudgetsInterface) -> T): T {
        if (testBudgets != null) {
            val handle = testBudgets.invoke()
            return if (handle is Disposable) handle.use { block(handle) } else block(handle)
        }
        return session!!.awaitCore().budgets().use { block(it) }
    }
    private suspend fun queryContext(): BudgetQueryContext {
        testContext?.let { return it() }
        val core = session!!.awaitCore()
        val accounts = core.accounts().use { it.overview().accounts }
        val categories = core.categories().use { it.list(null, true) }
        val currency = core.settings().use { it.mainCurrency() }
        return BudgetQueryContext(accounts, categories, currency)
    }
    init {
        viewModelScope.launch {
            changes.observe(setOf(Table.BUDGETS, Table.BUDGET_ACCOUNTS, Table.BUDGET_LIMITS,
                Table.TRANSACTIONS, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.CATEGORIES,
                Table.SUBCATEGORIES, Table.EXCHANGE_RATES, Table.SETTINGS)).collect { refresh() }
        }
    }
    fun refresh() {
        val revision = ++queryRevision
        query?.cancel();historyQuery?.cancel();detailQuery?.cancel();++detailRevision
        val selectedId = mutable.value.selectedId
        mutable.update { it.copy(loading = true, historyLoading=false, historyDetailLoading=false,historyDetail=null) }
        query = viewModelScope.launch {
            try {
                val context = queryContext()
                val date = today()
                val result = withBudgets { api ->
                    val rows = api.list(date, false, context.currency)
                    val row = if (rows.any { it.id == selectedId }) api.get(selectedId!!, date, context.currency) else null
                    Triple(rows, row, if (row != null) api.history(row.id, date, 24u) else emptyList())
                }
                val historyWindow=result.second?.let{appendBudgetHistory(emptyList(),result.third,it.anchor,date)}
                if (revision == queryRevision) mutable.update { it.copy(loading = false, rows = result.first,
                    selected = result.second, selectedId = result.second?.id, history = historyWindow?.rows.orEmpty(),historyCursor=historyWindow?.nextCursor,
                    accounts = context.accounts, categories = context.categories, displayCurrency = context.currency) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (revision == queryRevision) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }
    fun select(id: String?) {
        if (blocked()) return
        mutable.update { it.copy(selectedId = id, selected = null, history = emptyList(), showHistory = false) }; refresh()
    }
    fun history(show: Boolean) { if (!blocked()) {if(!show){++detailRevision;detailQuery?.cancel()};mutable.update { it.copy(showHistory = show,historyDetail=null,historyDetailLoading=false) }} }
    fun loadMoreHistory(){
        val before=mutable.value;val row=before.selected?:return;val cursor=before.historyCursor?:return
        if(before.saving||before.loading||before.historyLoading)return
        val mine=queryRevision;mutable.update{it.copy(historyLoading=true,error=null)}
        historyQuery=viewModelScope.launch{try{
            val page=withBudgets{it.history(row.id,cursor,24u)}
            val window=appendBudgetHistory(before.history,page,row.anchor,cursor)
            if(mine==queryRevision&&mutable.value.selectedId==row.id)mutable.update{it.copy(history=window.rows,historyCursor=window.nextCursor,historyLoading=false)}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(mine==queryRevision)mutable.update{it.copy(historyLoading=false,error=null,errorResource=io.github.hootkit_dev.hootbook.R.string.core_history_query_failed)}}}
    }
    fun historyPeriod(period:BudgetPeriodSummary){
        val before=mutable.value;val id=before.selectedId?:return;if(before.saving)return
        detailQuery?.cancel();val mine=++detailRevision;val current=queryRevision
        mutable.update{it.copy(historyDetail=null,historyDetailLoading=true)}
        detailQuery=viewModelScope.launch{try{
            val row=withBudgets{it.get(id,period.start,before.displayCurrency)}
            require(row.current.start==period.start&&row.current.end==period.end){"Core returned a different period"}
            if(mine==detailRevision&&current==queryRevision)mutable.update{it.copy(historyDetail=row,historyDetailLoading=false)}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(mine==detailRevision&&current==queryRevision)mutable.update{it.copy(historyDetailLoading=false,error=null,errorResource=io.github.hootkit_dev.hootbook.R.string.core_history_query_failed)}}}
    }
    fun edit(row: Budget? = null) {
        if (blocked()) return
        mutable.update { it.copy(error = null, draft = row?.let(CoreBudgetDraft::from)
            ?: CoreBudgetDraft(currency = it.displayCurrency.ifBlank { "CNY" }, start = today().budgetDateText())) }
    }
    fun draftChanged(draft: CoreBudgetDraft) { if (!mutable.value.saving) mutable.update { it.copy(draft = draft) } }
    fun dismissEditor() { if (!blocked()) mutable.update { it.copy(draft = null) } }
    fun back(): Boolean {
        if (blocked()) return true
        if (mutable.value.draft != null) { dismissEditor(); return true }
        if (mutable.value.limitDraft != null) { dismissLimit(); return true }
        if(mutable.value.historyDetail!=null){++detailRevision;detailQuery?.cancel();mutable.update{it.copy(historyDetail=null)};return true}
        if (mutable.value.showHistory) { history(false); return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    fun editLimit(row: Budget, category: Category) {
        if (blocked()) return
        val status = row.current.limits.find { it.categoryId == category.id }
        mutable.update { it.copy(error = null, limitDraft = CoreBudgetLimitDraft(row.id, category.id, category.displayName,
            status?.amount?.toPlainString().orEmpty(), status != null)) }
    }
    fun limitAmountChanged(amount: String) { if (!mutable.value.saving) mutable.update { it.copy(limitDraft = it.limitDraft?.copy(amount = amount)) } }
    fun dismissLimit() { if (!blocked()) mutable.update { it.copy(limitDraft = null) } }
    fun saveLimit(remove: Boolean = false) {
        val draft = mutable.value.limitDraft ?: return
        command {
            it.setLimit(draft.budgetId, draft.categoryId, if (remove) null else budgetAmount(draft.amount))
            mutable.update { state -> state.copy(limitDraft = null) }
        }
    }
    fun clearError() { mutable.update { it.copy(error = null,errorResource=null) } }
    private fun blocked(): Boolean {
        if (!mutable.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }; return true
    }
    fun save(defaultName: String) {
        val draft = mutable.value.draft ?: return
        command { api ->
            val id = draft.id ?: api.create(draft.newBudget(defaultName))
            if (draft.id != null) api.update(id, draft.patch(defaultName))
            mutable.update { it.copy(draft = null, selectedId = id, selected = null) }
        }
    }
    fun delete(row: Budget) = command { api ->
        api.delete(row.id)
        mutable.update { it.copy(selectedId = null, selected = null, showHistory = false, history = emptyList()) }
    }
    fun active(row: Budget) = command { it.update(row.id, BudgetPatch(active = !row.active)) }
    /** Each limit is its own Core command; no non-atomic bulk update disguised as a single save. */
    fun setLimit(row: Budget, categoryId: String, amount: String?) = command {
        it.setLimit(row.id, categoryId, amount?.let(::budgetAmount))
    }
    private fun command(block: suspend (BudgetsInterface) -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try {
                withBudgets(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutable.update { it.copy(error = if(failure is CoreBudgetInputException)null else coreFailureText(failure),errorResource=(failure as? CoreBudgetInputException)?.resource) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) } }
        }
    }
}
