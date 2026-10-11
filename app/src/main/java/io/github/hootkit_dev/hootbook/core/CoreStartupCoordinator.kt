package io.github.hootkit_dev.hootbook.core

import dev.hootkit.platform.CoreSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application-owned asynchronous startup, never an activity-owned handle.
 * Foreground state is reserved for the real v2 sync adapter; offline builds do no remote work.
 */
class CoreStartupCoordinator(
    private val scope: CoroutineScope,
    private val session: () -> CoreSession,
    private val isCrashProcess: () -> Boolean,
    private val reportFailure: (Throwable) -> Unit,
) {
    private var startup: Job? = null
    private val mutableForeground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutableForeground.asStateFlow()

    @Synchronized
    fun start(): Job? {
        if (isCrashProcess()) return null
        return startup ?: scope.launch {
            try {
                session().awaitCore()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // CoreSession publishes Failed; the UI can display it and explicitly retry.
                runCatching { reportFailure(failure) }
            }
        }.also { startup = it }
    }

    fun onForegroundChanged(foreground: Boolean) {
        if (!isCrashProcess()) mutableForeground.value = foreground
        // Backgrounding is not shutdown: workers/screens share the same process-owned Core.
    }
}
