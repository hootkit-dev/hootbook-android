package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.RateProvider
import dev.hootkit.core.RefreshOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreCurrencyRefreshCoordinatorTest {
    @Test fun constructionForegroundAndImportCannotOpenCoreBeforeBootstrapIsReady() = runTest {
        var calls = 0
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { calls++; RefreshOutcome.NotNeeded }, { false }, {})
        coordinator.foregrounded()
        coordinator.importCommitted()
        runCurrent()
        assertEquals(0, calls)
        coordinator.coreReady()
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun crashProcessNeverStartsAnyRefresh() = runTest {
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { error("Crash process must not access native Core") }, { true }, {})
        coordinator.coreReady()
        coordinator.foregrounded()
        coordinator.importCommitted()
        runCurrent()
    }

    @Test fun startupAndRapidForegroundEventsShareOneNonBlockingFlight() = runTest {
        val reply = CompletableDeferred<RefreshOutcome>()
        val forces = mutableListOf<Boolean>()
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { force -> forces += force; reply.await() }, { false }, {})
        coordinator.coreReady()
        coordinator.foregrounded()
        runCurrent()
        repeat(20) { coordinator.foregrounded() }
        runCurrent()
        assertEquals(listOf(false), forces)
        assertFalse(reply.isCompleted)
        reply.complete(RefreshOutcome.Updated(RateProvider.ER_API, 2u))
        runCurrent()
        assertEquals(listOf(false), forces)
    }

    @Test fun laterForegroundStillAsksCoreToCheckExpiryWithoutForcingOrReadingRates() = runTest {
        val forces = mutableListOf<Boolean>()
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { force -> forces += force; RefreshOutcome.NotNeeded }, { false }, {})
        coordinator.coreReady()
        runCurrent()
        coordinator.foregrounded()
        runCurrent()
        assertEquals(listOf(false, false), forces)
    }

    @Test fun failedOutcomeAndThrownFailureDoNotPreventLaterForegroundRetry() = runTest {
        var calls = 0
        var reports = 0
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope, {
            when (++calls) {
                1 -> RefreshOutcome.Failed(503u)
                2 -> throw java.io.IOException("Synthetic network failure")
                else -> RefreshOutcome.NotNeeded
            }
        }, { false }, { reports++ })
        coordinator.coreReady()
        runCurrent()
        coordinator.foregrounded()
        runCurrent()
        coordinator.foregrounded()
        runCurrent()
        assertEquals(3, calls)
        assertEquals(2, reports)
    }

    @Test fun diagnosticsFailureCannotEscapeApplicationJob() = runTest {
        var calls = 0
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { calls++; RefreshOutcome.Failed(null) }, { false }, { error("Reporter unavailable") })
        coordinator.coreReady()
        runCurrent()
        coordinator.foregrounded()
        runCurrent()
        assertEquals(2, calls)
    }

    @Test fun committedRestoreQueuesExactlyOneRecheckBehindAnOlderFlight() = runTest {
        val first = CompletableDeferred<RefreshOutcome>()
        var calls = 0
        var active = 0
        var maximumActive = 0
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope, { force ->
            assertFalse(force)
            calls++
            active++
            maximumActive = maxOf(active, maximumActive)
            try {
                if (calls == 1) first.await() else RefreshOutcome.NotNeeded
            } finally { active-- }
        }, { false }, {})
        coordinator.coreReady()
        runCurrent()
        repeat(10) { coordinator.importCommitted(); coordinator.foregrounded() }
        runCurrent()
        assertEquals(1, calls)
        first.complete(RefreshOutcome.Updated(RateProvider.FRANKFURTER, 2u))
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, maximumActive)
        coordinator.importCommitted()
        runCurrent()
        assertEquals(3, calls)
    }

    @Test fun cancellationPropagatesWithoutReportingOrStartingQueuedImportRefresh() = runTest {
        val ownedScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val never = CompletableDeferred<RefreshOutcome>()
        var calls = 0
        var reports = 0
        var cancelled = false
        val coordinator = CoreCurrencyRefreshCoordinator(ownedScope, {
            calls++
            try { never.await() } catch (failure: CancellationException) { cancelled = true; throw failure }
        }, { false }, { reports++ })
        try {
            coordinator.coreReady()
            runCurrent()
            coordinator.importCommitted()
            ownedScope.cancel()
            runCurrent()
            assertTrue(cancelled)
            assertEquals(1, calls)
            assertEquals(0, reports)
        } finally { ownedScope.cancel() }
    }

    @Test fun cancelledRequestCanBeRetriedByANewerForeground() = runTest {
        var calls = 0
        var reports = 0
        val coordinator = CoreCurrencyRefreshCoordinator(backgroundScope,
            { if (++calls == 1) throw CancellationException("Synthetic interruption") else RefreshOutcome.NotNeeded },
            { false }, { reports++ })
        coordinator.coreReady()
        runCurrent()
        coordinator.foregrounded()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(0, reports)
    }
}
