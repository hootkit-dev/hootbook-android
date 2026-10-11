package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.use
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Lifecycle/restore requests are asynchronous; Core owns expiry and retry throttling. */
@Singleton
class CoreRateRefreshCoordinator internal constructor(
    scope: CoroutineScope,
    private val refresh: suspend () -> Unit,
) {
    @Inject constructor(session: CoreSession, @ApplicationScope scope: CoroutineScope) : this(
        scope, { session.withCore { core -> core.currency().use { it.refresh(false) }; Unit } },
    )

    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) {
                try { refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The rates sheet exposes sync status; never interrupt UI. */ }
            }
        }
    }

    // Retain one follow-up if an import commits while a startup refresh is still in flight.
    fun requestRefresh() { requests.trySend(Unit) }
}
