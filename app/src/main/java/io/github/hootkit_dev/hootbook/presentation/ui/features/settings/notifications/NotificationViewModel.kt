package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import io.github.hootkit_dev.hootbook.core.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import io.github.hootkit_dev.hootbook.core.system.CoreSystemCoordinator
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.planToday

data class NotificationUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val subcategories: Map<String, Subcategory> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)
@HiltViewModel
class NotificationViewModel internal constructor(
    private val session: CoreSession,
    changes: ChangeBus,
    val preferences: CoreUiPreferencesStore,
    private val schedule: () -> Unit,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, preferences: CoreUiPreferencesStore,
        coordinator: CoreSystemCoordinator) : this(session, changes, preferences, coordinator::refresh)
    private val mutable = MutableStateFlow(NotificationUiState())
    val uiState = mutable.asStateFlow()
    init {
        command { preferences.ready() }
        viewModelScope.launch { session.observe(changes, setOf(Table.SUBSCRIPTIONS, Table.CATEGORIES, Table.SUBCATEGORIES)) { core ->
            coreQuery {
                val categories = core.categories().use { it.list(null, true).associateBy(Category::id) }
                val subcategories = categories.values.flatMap { it.subcategories }.associateBy(Subcategory::id)
                NotificationUiState(subscriptions = core.plans().use { it.subscriptions(planToday(), false).filterNot { row -> row.ended } },
                    categories = categories, subcategories = subcategories, loading = false)
            }
        }.collect { result -> result.fold(onSuccess = { snapshot -> mutable.update { it.copy(
            subscriptions = snapshot.subscriptions, categories = snapshot.categories, subcategories = snapshot.subcategories,
            loading = false, error = null) } }, onFailure = { failure -> mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }) } }
    }
    fun setUpcomingNotificationsEnabled(value: Boolean) = command {
        preferences.update { it.copy(notificationsEnabled = value) }; schedule()
    }
    fun toggleSubscriptionNotification(id: String, enabled: Boolean) = command {
        preferences.update { it.copy(mutedSubscriptionIds = if (enabled) it.mutedSubscriptionIds - id else it.mutedSubscriptionIds + id) }
        schedule()
    }
    fun refreshPlatform() { schedule() }
    private fun command(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
