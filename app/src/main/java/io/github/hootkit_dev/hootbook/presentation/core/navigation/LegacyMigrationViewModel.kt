package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationCoordinator
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationPhase
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationUiState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI commands never infer success or replay an import whose outcome is uncertain. */
@HiltViewModel
class LegacyMigrationViewModel @Inject constructor(
    private val coordinator: LegacyMigrationCoordinator,
) : ViewModel() {
    private val running = MutableStateFlow(false)
    private val failure = MutableStateFlow(false)
    val commandFailed = failure.asStateFlow()
    val state = combine(coordinator.state, running) { current, active ->
        current.copy(busy = current.busy || active)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LegacyMigrationUiState(busy = true),
    )

    init { reload() }

    fun reload() = command(reloadsState = true) { coordinator.load() }

    fun prepare() {
        if (failure.value || coordinator.state.value.phase !in setOf(
                LegacyMigrationPhase.IDLE, LegacyMigrationPhase.FAILED,
            )) return
        command { coordinator.prepare() }
    }

    fun migrate(acceptLosses: Boolean) {
        if (!acceptLosses || failure.value || coordinator.state.value.phase != LegacyMigrationPhase.REVIEW) return
        command { coordinator.migrate(acceptLosses = true) }
    }

    fun resumePlatform() {
        if (failure.value || coordinator.state.value.phase != LegacyMigrationPhase.PLATFORM_PENDING) return
        command { coordinator.resumePlatform() }
    }

    fun exportRecovery(destination: Uri) = command { coordinator.exportRecovery(destination) }

    private fun command(reloadsState: Boolean = false, action: suspend () -> Unit) {
        // Acquire before dispatch so repeated taps cannot queue a second native operation.
        if (coordinator.state.value.busy || !running.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                action()
                if (reloadsState) failure.value = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No exception messages: they can contain source paths or service credentials.
                // Only a journal reload may clear this guard; export cannot enable a retry.
                failure.value = true
            } finally {
                running.value = false
            }
        }
    }
}
