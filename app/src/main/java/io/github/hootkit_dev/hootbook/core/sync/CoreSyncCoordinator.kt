package io.github.hootkit_dev.hootbook.core.sync

import android.content.Context
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CoreSyncControlState(val available: Boolean, val initialized: Boolean = false, val authChanging: Boolean = false,
    val identityDecision: CoreIdentityDecision? = null, val error: String? = null)

/** App-owned lifecycle/identity coordinator. No native open or Firebase auth at construction. */
@Singleton
class CoreSyncCoordinator private constructor(private val session: (() -> CoreSession)?, private val changes: ChangeBus,
    private val platform: CoreSyncPlatform, private val gate: CoreSyncGate, private val scheduler: CoreSyncScheduler,
    private val scope: CoroutineScope, private val testSync: (suspend () -> SyncInterface)?) {
    @Inject constructor(session: Provider<CoreSession>, changes: ChangeBus, platform: CoreSyncPlatform, gate: CoreSyncGate,
        scheduler: CoreSyncWorkScheduler, @ApplicationScope scope: CoroutineScope) :
        this(session::get, changes, platform, gate, scheduler, scope, null)
    internal constructor(changes: ChangeBus, platform: CoreSyncPlatform, gate: CoreSyncGate, scheduler: CoreSyncScheduler,
        scope: CoroutineScope, sync: suspend () -> SyncInterface) : this(null, changes, platform, gate, scheduler, scope, sync)
    private val operations = Mutex()
    private val started = AtomicBoolean()
    private val authReady = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow(CoreSyncControlState(platform.available, initialized = !platform.available))
    val state = mutable.asStateFlow()
    @Volatile private var foreground = false
    private suspend fun <T> withSync(block: suspend (SyncInterface) -> T): T {
        val handle = testSync?.invoke() ?: session!!().awaitCore().sync()
        return if (handle is Disposable) handle.use { block(handle) } else block(handle)
    }
    fun start() {
        if (!platform.available || !started.compareAndSet(false, true)) return
        scope.launch {
            try { platform.events.collect { handoff(it.account) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { mutable.update { it.copy(error = "Authentication observation failed; remote gate remains closed.") }; gate.close() }
        }
        scope.launch {
            changes.observe(setOf(Table.SYNC_STATE)).collect {
                try {
                    if (state.value.initialized && !state.value.authChanging && state.value.identityDecision == CoreIdentityDecision.ACCEPTED)
                        withSync { sync -> sync.status().retryAfterMs?.let { scheduler.schedule(it.toLong()) } }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Throwable) { mutable.update { it.copy(error = "Sync status refresh failed.") } }
            }
        }
        try { platform.start() }
        catch (_: Throwable) {
            gate.close(); mutable.update { it.copy(initialized = true, error = "Sync platform initialization failed.") }
            if (!authReady.isCompleted) authReady.complete(Unit)
        }
    }
    private suspend fun handoff(candidate: SyncAccount?) = withContext(NonCancellable) {
        operations.withLock {
            gate.close(); platform.stopRemoteListening(); scheduler.cancel()
            mutable.update { it.copy(authChanging = true, error = null) }
            try {
                val decision = candidate?.let { platform.acceptIdentity(it) }
                val accepted = candidate != null && decision == CoreIdentityDecision.ACCEPTED
                // A rejected external UID is represented as signed out, never published to Core.
                gate.publish(if (accepted) candidate else null)
                withSync { it.authChanged() } // Native authChanged awaits its serialized round lock.
                if (accepted) gate.open()
                mutable.update { it.copy(initialized = true, authChanging = false, identityDecision = decision,
                    error = when (decision) {
                        CoreIdentityDecision.DIFFERENT_UID_BLOCKED -> "This installation is bound to another Google account. Sign in with the original account; cross-account switching is unavailable with current Core (#70)."
                        CoreIdentityDecision.MISSING_IDENTITY_BLOCKED -> "The installation identity is missing for an existing Core ledger. Sync stays closed: public Core APIs do not attest first-sync ownership (#70). Offline use and backup export remain available."
                        CoreIdentityDecision.STORAGE_FAILED -> "Installation identity storage failed. Sync stays closed; retry after storage is restored."
                        else -> null
                    }) }
                if (!authReady.isCompleted) authReady.complete(Unit)
                if (accepted && foreground) runLocked(SyncReason.FOREGROUND)
            } catch (_: Throwable) {
                gate.close(); platform.stopRemoteListening()
                mutable.update { it.copy(authChanging = false, initialized = true, error = "Account handoff failed. Remote access remains closed; retry same-account sign-in.") }
                if (!authReady.isCompleted) authReady.complete(Unit)
            }
        }
    }
    suspend fun awaitReady() { if (platform.available) { start(); authReady.await() } }
    fun foregrounded() {
        foreground = true; start()
        // Initial/changed auth handoff runs foreground once after it opens the gate.
        if (!platform.available || !state.value.initialized || state.value.authChanging) return
        scope.launch { operations.withLock { if (canAccess()) runLocked(SyncReason.FOREGROUND) } }
    }
    fun backgrounded() {
        foreground = false
        if (!platform.available) return
        scope.launch { operations.withLock {
            if (state.value.initialized) withSync { if (it.appBackgrounded()) scheduler.schedule(1_000) }
        } }
    }
    suspend fun backgroundRound() { if (!platform.available) return; awaitReady()
        operations.withLock { if (canAccess()) runLocked(SyncReason.BACKGROUND) } }
    suspend fun runManual(): SyncOutcome? { if (!platform.available) return null; awaitReady()
        return operations.withLock { if (!canAccess()) return@withLock null; runLocked(SyncReason.MANUAL) } }
    private fun canAccess() = state.value.identityDecision == CoreIdentityDecision.ACCEPTED && !state.value.authChanging && runCatching { gate.lease() }.isSuccess
    private suspend fun runLocked(reason: SyncReason): SyncOutcome? {
        return try { withSync { it.run(reason) }.also { outcome -> outcome.retryAfterMs?.let { scheduler.schedule(it.toLong()) } } }
        catch (failure: CoreException.Sync) {
            if (failure.kind !in setOf(SyncErrorKind.NOT_SIGNED_IN, SyncErrorKind.NEEDS_PASSPHRASE, SyncErrorKind.PAUSED, SyncErrorKind.NEEDS_ACCOUNT_CHOICE, SyncErrorKind.UNAVAILABLE))
                mutable.update { it.copy(error = syncFailureText(failure)) }
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { mutable.update { it.copy(error = "Sync run failed; no automatic retry without a Core retry recommendation.") }; null }
    }
    suspend fun command(block: suspend (SyncInterface) -> Unit) {
        if (!platform.available) return
        awaitReady()
        operations.withLock {
            check(canAccess()) { "Sync identity is not accepted" }
            withContext(NonCancellable) { withSync(block) }
        }
    }
    suspend fun signIn(activity: Context) { start(); platform.signIn(activity) }
    suspend fun signOut() {
        gate.close(); platform.stopRemoteListening(); scheduler.cancel()
        platform.signOut() // SDK event drives serialized authChanged; identity binding is retained.
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
}
fun syncFailureText(error: Throwable): String = when (error) {
    is CoreException.Sync -> "Sync: ${error.kind}."
    is CoreException.Invalid -> "Check ${error.field}: ${error.reason}."
    is CoreException.NotFound -> "This conflict or record has changed. Refresh before deciding."
    else -> "Sync operation failed. Private remote data and credentials are not included in diagnostics."
}
