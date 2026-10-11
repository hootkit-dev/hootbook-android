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

internal val SUBSCRIPTION_TABLES = setOf(Table.SUBSCRIPTIONS, Table.TRANSACTIONS, Table.ACCOUNTS,
    Table.ACCOUNT_CURRENCIES, Table.CATEGORIES, Table.SUBCATEGORIES, Table.SETTINGS, Table.EXCHANGE_RATES)

data class CoreSubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val summary: SubscriptionSummary? = null,
    val references: CorePlanReferences = CorePlanReferences(),
    val includeHidden: Boolean = false,
    val selectedId: String? = null,
    val selected: Subscription? = null,
    val form: CoreSubscriptionForm? = null,
    val undoHiddenId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val navigationBlocked: Boolean = false,
    val error: String? = null,
    val errorResource:Int?=null,
    val commandRevision: Long = 0,
)

@HiltViewModel
class CoreSubscriptionsViewModel private constructor(
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
    private val mutable = MutableStateFlow(CoreSubscriptionsUiState())
    val state = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var queryVersion = 0L
    private var initialized = false
    init { viewModelScope.launch { changes.observe(SUBSCRIPTION_TABLES).collect { refresh() } } }
    fun initialize(subscriptionId: String?) {
        if (initialized) return
        initialized = true
        if (subscriptionId != null) select(subscriptionId)
    }
    fun refresh() {
        val version = ++queryVersion
        queryJob?.cancel()
        val snapshot = mutable.value
        mutable.update { it.copy(loading = true) }
        queryJob = viewModelScope.launch {
            try {
                val date = today()
                // Query hidden rows for an explicitly opened ID, without mutating the user's list filter.
                val (rows, summary) = withPlans {
                    it.subscriptions(date, snapshot.includeHidden || snapshot.selectedId != null) to it.subscriptionSummary(date)
                }
                val references = testReferences?.invoke() ?: session!!.awaitCore().planReferences()
                if (version == queryVersion) mutable.update { it.copy(subscriptions = rows, summary = summary,
                    references = references, selected = rows.find { row -> row.id == snapshot.selectedId }, loading = false,
                    errorResource = if (snapshot.selectedId != null && rows.none { row -> row.id == snapshot.selectedId }) io.github.hootkit_dev.hootbook.R.string.core_detail_subscription_missing else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (version == queryVersion) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }
    fun includeHidden(value: Boolean) { if (blockNavigation()) return; mutable.update { it.copy(includeHidden = value) }; refresh() }
    fun select(id: String?) { if (blockNavigation()) return; mutable.update { it.copy(selectedId = id, selected = null, error = null) }; refresh() }
    fun create() { if (blockNavigation()) return; mutable.update { it.copy(form = CoreSubscriptionForm(currency = it.references.mainCurrency, nextDue = today().planDateText()), error = null) } }
    fun edit(row: Subscription) { if (!blockNavigation()) mutable.update { it.copy(form = CoreSubscriptionForm.from(row), error = null) } }
    fun edit() { if (blockNavigation()) return; mutable.value.selected?.let { row -> mutable.update { it.copy(form = CoreSubscriptionForm.from(row), error = null) } } }
    fun updateForm(form: CoreSubscriptionForm) { if (!mutable.value.saving) mutable.update { it.copy(form = form, error = null) } }
    fun back(): Boolean {
        if (blockNavigation()) return true
        if (mutable.value.form != null) { mutable.update { it.copy(form = null, error = null) }; return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    /** Outbound navigation must use this guard too, not only the toolbar/system Back. */
    fun canNavigate(): Boolean = !blockNavigation()
    private fun blockNavigation(): Boolean {
        if (!mutable.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }; return true
    }
    fun clearError() { mutable.update { it.copy(error = null,errorResource=null) } }
    fun save() {
        val form = mutable.value.form ?: return
        command { api ->
            val id = if (form.id == null) api.createSubscription(form.toDraft()) else form.id.also { api.updateSubscription(it, form.toDraft()) }
            mutable.update { it.copy(form = null, selectedId = id, selected = null) }
        }
    }
    fun toggleHidden() { mutable.value.selected?.let(::toggleHidden) }
    fun toggleHidden(row: Subscription) {
        command { it.setSubscriptionHidden(row.id, !row.hidden)
            mutable.update { state -> state.copy(undoHiddenId = if (!row.hidden) row.id else null) } }
    }
    fun undoHidden() {
        val id = mutable.value.undoHiddenId ?: return
        command { it.setSubscriptionHidden(id, false); mutable.update { state -> state.copy(undoHiddenId = null) } }
    }
    /** Changes schedule only; ledger.add(subscriptionId) is the distinct payment-with-transaction path. */
    fun markPaid() { val row = mutable.value.selected ?: return; val date = today(); command { it.markSubscriptionPaid(row.id, date) } }
    fun delete() {
        val row = mutable.value.selected ?: return
        command { it.deleteSubscription(row.id)
            mutable.update { state -> state.copy(selectedId = null, selected = null,
                undoHiddenId = state.undoHiddenId.takeUnless { id -> id == row.id }) } }
    }
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
