package io.github.hootkit_dev.hootbook.presentation.core.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.sync.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CoreSyncUiState(val loading: Boolean = true, val busy: Boolean = false, val status: SyncStatus? = null,
    val control: CoreSyncControlState? = null, val conflicts: List<SyncConflict> = emptyList(), val notices: List<SyncNotice> = emptyList(),
    val policy: ConflictPolicy? = null, val hasV1: Boolean? = null, val outcome: SyncOutcome? = null,
    val error: String? = null, val navigationBlocked: Boolean = false, val commandRevision: Long = 0)
@HiltViewModel
class CoreSyncViewModel private constructor(private val session: CoreSession?, private val changes: ChangeBus,
    private val coordinator: CoreSyncCoordinator, private val testSync: (suspend () -> SyncInterface)?) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, coordinator: CoreSyncCoordinator) : this(session, changes, coordinator, null)
    internal constructor(changes: ChangeBus, coordinator: CoreSyncCoordinator, sync: suspend () -> SyncInterface) : this(null, changes, coordinator, sync)
    private val mutable = MutableStateFlow(CoreSyncUiState()); val state = mutable.asStateFlow()
    private var query: Job? = null; private var revision = 0L
    private suspend fun <T> withSync(block: suspend (SyncInterface) -> T): T {
        val handle = testSync?.invoke() ?: session!!.awaitCore().sync()
        return if (handle is Disposable) handle.use { block(handle) } else block(handle)
    }
    init {
        coordinator.start()
        viewModelScope.launch { coordinator.state.collect { control -> mutable.update { it.copy(control = control) }; refresh() } }
        viewModelScope.launch { changes.observe(setOf(Table.SYNC_STATE, Table.SETTINGS)).collect { refresh() } }
    }
    fun refresh() {
        if (state.value.busy) return
        val request = ++revision; query?.cancel()
        query = viewModelScope.launch {
            try { val values = withSync { sync ->
                val status = sync.status()
                if (status.phase == SyncPhase.UNAVAILABLE) CoreSyncUiState(loading = false, status = status, control = state.value.control)
                else CoreSyncUiState(loading = false, status = status, control = state.value.control, conflicts = sync.conflicts(), notices = sync.notices(), policy = sync.conflictPolicy())
            }
                if (request == revision) mutable.update { values.copy(error = it.error, hasV1 = it.hasV1, outcome = it.outcome, commandRevision = it.commandRevision) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { if (request == revision) mutable.update { it.copy(loading = false, error = syncFailureText(error)) } }
        }
    }
    private fun command(block: suspend () -> Unit) {
        if (state.value.busy || state.value.loading || state.value.status?.phase == SyncPhase.UNAVAILABLE) return
        ++revision; query?.cancel(); mutable.update { it.copy(busy = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try { withContext(NonCancellable) { block() }; mutable.update { it.copy(commandRevision = it.commandRevision + 1) } }
            catch (_: CoreSignInCancelled) { }
            catch (error: Throwable) { mutable.update { it.copy(error = syncFailureText(error)) } }
            finally { mutable.update { it.copy(busy = false, navigationBlocked = false) }; refresh() }
        }
    }
    fun signIn(activity: Context) = command { coordinator.signIn(activity) }
    fun signOut() = command { coordinator.signOut() }
    fun passphrase(passphrase: String) = command { coordinator.command { it.setPassphrase(passphrase) } }
    fun paused(paused: Boolean) = command { coordinator.command { it.setPaused(paused) } }
    fun syncNow() = command { val outcome = coordinator.runManual(); mutable.update { it.copy(outcome = outcome) } }
    fun resetCloud(newPassphrase: String, confirmed: Boolean) {
        if (!confirmed) { mutable.update { it.copy(error = "Confirm cloud deletion and the safety backup before resetting.") }; return }
        command { coordinator.command { it.resetCloud(newPassphrase) } }
    }
    fun replaceWithCloud(confirmed: Boolean) {
        if (!confirmed) { mutable.update { it.copy(error = "Confirm replacement of local sync data after the Core safety backup.") }; return }
        command { coordinator.command { it.replaceWithCloud() } }
    }
    fun accountChoice(@Suppress("UNUSED_PARAMETER") choice: AccountSwitchChoice) {
        // Current Core cannot attest the interrupted-first-sync ownership window (#70).
        mutable.update { it.copy(error = "Cross-account switching is not enabled with current Core (#70). Use the original installation account or continue offline and export a backup.") }
    }
    fun checkV1() = command { coordinator.command { sync -> val exists = sync.hasV1Data(); mutable.update { it.copy(hasV1 = exists) } } }
    fun deleteV1(confirmed: Boolean) {
        if (!confirmed) { mutable.update { it.copy(error = "Confirm all devices upgraded before deleting legacy protocol data.") }; return }
        command { coordinator.command { it.deleteV1Data() }; mutable.update { it.copy(hasV1 = false) } }
    }
    fun resolve(id: Long, choice: ConflictChoice) = command { coordinator.command { it.resolveConflict(id, choice) } }
    fun dismissConflicts(ids: List<Long>) = command { coordinator.command { it.dismissConflicts(ids) } }
    fun dismissNotices(ids: List<Long>) = command { coordinator.command { it.dismissNotices(ids) } }
    fun policy(policy: ConflictPolicy) = command { coordinator.command { it.setConflictPolicy(policy) } }
    fun clearError() { coordinator.clearError(); mutable.update { it.copy(error = null) } }
    fun back(): Boolean { if (!state.value.busy) return false; mutable.update { it.copy(navigationBlocked = true) }; return true }
}
