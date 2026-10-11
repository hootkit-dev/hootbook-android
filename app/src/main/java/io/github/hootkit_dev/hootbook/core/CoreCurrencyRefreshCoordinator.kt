package io.github.hootkit_dev.hootbook.core

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.RefreshOutcome
import dev.hootkit.core.use
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** App lifecycle scheduling only. Core owns expiry checks, providers and every rate calculation. */
@Singleton
class CoreCurrencyRefreshCoordinator internal constructor(
    private val scope: CoroutineScope,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val isCrashProcess: () -> Boolean,
    private val reportFailure: () -> Unit,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        session: Provider<CoreSession>,
    ) : this(
        scope,
        { force -> session.get().awaitCore().currency().use { it.refresh(force) } },
        { CoreProcess.isCrashProcess(context) },
        { Log.w("CoreCurrency", "Automatic rate refresh deferred; cached rates remain available") },
    )

    private var ready = false
    private var job: Job? = null
    private var refreshAfterImport = false

    /** Called only after bootstrap's legacy-upgrade gate and native opening have succeeded. */
    @Synchronized
    fun coreReady() {
        if (isCrashProcess()) return
        ready = true
        request()
    }

    @Synchronized
    fun foregrounded() = request()

    /** A restore may replace the rate table while an earlier request is still in flight. */
    @Synchronized
    fun importCommitted() = request(afterImport = true)

    private fun request(afterImport: Boolean = false) {
        if (!ready || isCrashProcess()) return
        if (job?.isCompleted == false) {
            if (afterImport) refreshAfterImport = true
            return
        }
        refreshAfterImport = false
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val running = coroutineContext[Job]
            try {
                do {
                    coroutineContext.ensureActive()
                    try {
                        if (refresh(false) is RefreshOutcome.Failed) runCatching(reportFailure)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Do not turn a network failure into a bootstrap/import failure or log data.
                        runCatching(reportFailure)
                    }
                    val again = synchronized(this@CoreCurrencyRefreshCoordinator) {
                        if (refreshAfterImport) {
                            refreshAfterImport = false
                            true
                        } else {
                            job = null
                            false
                        }
                    }
                } while (again)
            } finally {
                synchronized(this@CoreCurrencyRefreshCoordinator) {
                    if (job === running) {
                        job = null
                        refreshAfterImport = false
                    }
                }
            }
        }
        job = next
        next.start()
    }
}
