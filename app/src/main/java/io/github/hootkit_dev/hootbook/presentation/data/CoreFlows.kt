package io.github.hootkit_dev.hootbook.presentation.data

import dev.hootkit.core.HootKitCoreInterface
import dev.hootkit.core.Table
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * Queries the current snapshot on subscription and after dependent table revisions change.
 * ChangeBus retains revisions while there are no collectors. A newer revision cancels an
 * in-flight query. Like StateFlow, bursts may coalesce into the latest committed snapshot.
 *
 * Close each acquired UniFFI service inside the query with `dev.hootkit.core.use`, for example
 * `core.ledger().use { ledger -> ledger.summary(filter) }`. Never close the session-owned core.
 * CoreError is generated as CoreException in Kotlin and propagates unchanged. ViewModels can
 * catch CoreException into their UiState; coroutine CancellationException must propagate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> CoreSession.observe(
    changes: ChangeBus,
    tables: Set<Table>,
    query: suspend (HootKitCoreInterface) -> T,
): Flow<T> = changes.observe(tables)
    .mapLatest { query(awaitCore()) }
    .flowOn(Dispatchers.IO)

/**
 * Runs a command off the main thread without taking ownership of the process's Core handle.
 * Acquire and release services inside [block]: `withCore { it.ledger().use { l -> l.add(draft) } }`.
 * CoreException and cancellation are deliberately not wrapped or converted to display text.
 * The calling ViewModel owns command-in-progress state and duplicate submission prevention.
 */
suspend fun <T> CoreSession.withCore(block: suspend (HootKitCoreInterface) -> T): T =
    withContext(Dispatchers.IO) { block(awaitCore()) }

/** Keep query failures in state so a later ChangeBus revision can retry the query. */
suspend fun <T> coreQuery(query: suspend () -> T): Result<T> = try {
    Result.success(query())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
