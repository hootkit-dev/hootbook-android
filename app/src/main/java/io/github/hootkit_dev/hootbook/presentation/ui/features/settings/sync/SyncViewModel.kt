package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync

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
import android.content.Context
import io.github.hootkit_dev.hootbook.core.sync.*

data class SyncUiState(
    val status: SyncStatus? = null,
    val control: CoreSyncControlState? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val outcome: SyncOutcome? = null,
)
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val coordinator: CoreSyncCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(SyncUiState())
    val state = mutable.asStateFlow()
    val available get() = coordinator.state.value.available
    private val wrong = MutableStateFlow(false)
    val wrongPassphrase = wrong.asStateFlow()
    private var query: Job? = null
    private var generation = 0L
    init {
        coordinator.start()
        refresh()
        viewModelScope.launch { coordinator.state.collect { control ->
            mutable.update { it.copy(control = control) }; refresh()
        } }
    }
    fun refresh() {
        val mine = ++generation
        query?.cancel()
        query = viewModelScope.launch { session.observe(changes, setOf(Table.SYNC_STATE, Table.SETTINGS)) { core ->
            coreQuery { core.sync().use { it.status() } }
        }.collect { result ->
            if (mine != generation) return@collect
            result.fold(onSuccess = { status -> mutable.update { it.copy(status = status, loading = false) } },
                onFailure = { failure -> mutable.update { it.copy(loading = false, error = syncFailureText(failure)) } })
        } }
    }
    fun signIn(activity: Context) = command { coordinator.signIn(activity) }
    fun signOut() = command { coordinator.signOut() }
    fun submitPassphrase(value: String) = command {
        wrong.value = false
        coordinator.command { it.setPassphrase(value) }
        mutable.update { it.copy(outcome = coordinator.runManual()) }
    }
    fun clearWrongPassphrase() { wrong.value = false }
    fun setEnabled(value: Boolean) = command { coordinator.command { it.setPaused(!value) } }
    fun syncNow() = command { mutable.update { it.copy(outcome = coordinator.runManual()) } }
    fun clearError() { mutable.update { it.copy(error = null) }; coordinator.clearError() }
    private fun command(block: suspend () -> Unit) {
        if (state.value.busy || state.value.loading || !available) return
        mutable.update { it.copy(busy = true, error = null, outcome = null) }
        viewModelScope.launch {
            try { withContext(NonCancellable) { block() } }
            catch (_: CoreSignInCancelled) { }
            catch (failure: Exception) {
                if (failure is CoreException.Sync && failure.kind == SyncErrorKind.WRONG_PASSPHRASE) wrong.value = true
                mutable.update { it.copy(error = syncFailureText(failure)) }
            }
            finally { mutable.update { it.copy(busy = false) }; refresh() }
        }
    }
}
