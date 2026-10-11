package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreRateRefreshCoordinatorTest {
    @Test fun requestsDoNotBlockAndAnImportDuringRefreshRetainsOneFollowUp() = runTest {
        val first = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        val coordinator = CoreRateRefreshCoordinator(backgroundScope) {
            assertEquals(1, ++active)
            calls++
            if (calls == 1) first.await()
            active--
        }
        coordinator.requestRefresh()
        runCurrent()
        assertEquals(1, calls)
        repeat(3) { coordinator.requestRefresh() }
        runCurrent()
        assertEquals(1, calls)
        first.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(0, active)
    }

    @Test fun failuresStaySilentAndLaterForegroundRequestsRetry() = runTest {
        var calls = 0
        val coordinator = CoreRateRefreshCoordinator(backgroundScope) {
            if (++calls == 1) error("offline")
        }
        coordinator.requestRefresh(); runCurrent()
        coordinator.requestRefresh(); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun productionRequestUsesNonForcedCoreRefreshAndClosesService() = runTest {
        val called = CompletableDeferred<Boolean>()
        val closed = CompletableDeferred<Unit>()
        val core = object : HootKitCore(NoPointer) {
            override fun currency() = object : Currency(NoPointer) {
                override suspend fun refresh(force: Boolean): RefreshOutcome {
                    called.complete(force)
                    return RefreshOutcome.NotNeeded
                }
                override fun destroy() { closed.complete(Unit); super.destroy() }
            }
        }
        val coordinator = CoreRateRefreshCoordinator(CoreSession(backgroundScope) { core }, backgroundScope)
        coordinator.requestRefresh()
        assertFalse(called.await())
        closed.await()
    }
}
