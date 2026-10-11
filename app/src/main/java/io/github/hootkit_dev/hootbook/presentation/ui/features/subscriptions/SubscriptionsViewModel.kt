package io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(private val session: CoreSession, private val changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(SubscriptionsUiState())
    val uiState = mutable.asStateFlow()
    private var selectedId: String? = null
    init {
        viewModelScope.launch {
            session.observe(changes, setOf(Table.SUBSCRIPTIONS, Table.CATEGORIES, Table.SUBCATEGORIES,
                Table.EXCHANGE_RATES, Table.SETTINGS, Table.TRANSACTIONS)) { core -> coreQuery {
                val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val (rows, summary) = core.plans().use { it.subscriptions(today, false) to it.subscriptionSummary(today) }
                val categories = core.categories().use { it.list(null, true) }
                val subcategories = core.categories().use { api -> categories.flatMap { it.subcategories } }
                val converted = core.currency().use { api -> rows.mapNotNull { row ->
                    api.convert(row.amount.amount, row.amount.currency, summary.currency)?.let { row.id to it }
                }.toMap() }
                val convertedMonthly = core.currency().use { api -> rows.mapNotNull { row ->
                    row.monthlyEquivalent?.let { monthly -> api.convert(monthly, row.amount.currency, summary.currency) }
                        ?.let { row.id to it }
                }.toMap() }
                SubscriptionsUiState(rows, summary, categories.associateBy(Category::id), subcategories.associateBy(Subcategory::id),
                    isLoading = false, convertedAmounts = converted, convertedMonthlyAmounts = convertedMonthly)
            } }.collect { result -> result.fold(
                onSuccess = { snapshot -> mutable.update { snapshot.copy(saving = it.saving, lastHiddenSubscription = it.lastHiddenSubscription,
                    selectedSubscription = snapshot.activeSubscriptions.find { row -> row.id == selectedId }) } },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, error = failure.message) } }) }
        }
    }
    fun selectId(id: String?) { selectedId = id; mutable.update { it.copy(selectedSubscription = it.activeSubscriptions.find { row -> row.id == id }) } }
    fun selectSubscription(row: Subscription?) = selectId(row?.id)
    fun hideSubscription(id: String) {
        val row = uiState.value.activeSubscriptions.find { it.id == id } ?: return
        command { it.setSubscriptionHidden(id, true); mutable.update { state -> state.copy(lastHiddenSubscription = row) } }
    }
    fun undoHide() {
        val row = uiState.value.lastHiddenSubscription ?: return
        command { it.setSubscriptionHidden(row.id, false); mutable.update { state -> state.copy(lastHiddenSubscription = null) } }
    }
    fun markAsPaid(row: Subscription) = command {
        val date = java.time.LocalDate.now()
        it.markSubscriptionPaid(row.id, LocalDate(date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte()))
        selectSubscription(null)
    }
    private fun command(block: suspend (PlansInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { session.withCore { core -> core.plans().use { block(it) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
