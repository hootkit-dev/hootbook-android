package dev.hootkit.platform

import dev.hootkit.core.HootKitCoreInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CoreSessionState {
    data object Starting : CoreSessionState
    data object Opening : CoreSessionState
    data class Ready(val core: HootKitCoreInterface) : CoreSessionState
    data class Failed(val cause: Throwable) : CoreSessionState
    data object Closing : CoreSessionState
    data object Closed : CoreSessionState
}

/** One instance per app process; initialization belongs to the application scope, not a screen. */
class CoreSession(
    scope: CoroutineScope,
    private val opener: suspend () -> HootKitCoreInterface,
) {
    // An opening failure must not cancel the app or prevent an explicit retry.
    private val openingScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val gate = Mutex()
    private val shutdownGate = Mutex()
    private var opening: Deferred<HootKitCoreInterface>? = null
    private var closing = false
    private var closed = false
    private val mutableState = MutableStateFlow<CoreSessionState>(CoreSessionState.Starting)
    val state: StateFlow<CoreSessionState> = mutableState.asStateFlow()

    suspend fun awaitCore(): HootKitCoreInterface {
        val shared = gate.withLock {
            check(!closing && !closed) { "CoreSession is closing or closed" }
            opening ?: openingScope.async {
                try {
                    val core = opener()
                    gate.withLock { if (!closing) mutableState.value = CoreSessionState.Ready(core) }
                    core
                } catch (failure: Throwable) {
                    gate.withLock { if (!closing) mutableState.value = CoreSessionState.Failed(failure) }
                    throw failure
                }
            }.also {
                mutableState.value = CoreSessionState.Opening
                opening = it
            }
        }
        // Cancelling a screen's await does not cancel application-owned initialization.
        return shared.await()
    }

    suspend fun retryOpening(): HootKitCoreInterface {
        gate.withLock {
            check(!closing && !closed)
            if (mutableState.value is CoreSessionState.Failed) {
                opening = null
            }
        }
        return awaitCore()
    }

    /** Waits for initialization and core writes; releases the UniFFI handle only after shutdown. */
    suspend fun shutdown() = withContext(NonCancellable) {
        shutdownGate.withLock {
            val shared = gate.withLock {
                if (closed) return@withLock null
                closing = true
                mutableState.value = CoreSessionState.Closing
                opening
            }
            val core = try {
                shared?.await()
            } catch (_: CancellationException) {
                null
            } catch (_: Throwable) {
                null // Opening failed: no live database handle to shut down.
            }
            core?.shutdown()
            (core as? AutoCloseable)?.close()
            gate.withLock {
                closed = true
                mutableState.value = CoreSessionState.Closed
            }
            openingScope.cancel()
        }
    }
}
