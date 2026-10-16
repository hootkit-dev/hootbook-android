package dev.hootkit.platform

import dev.hootkit.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreSessionTest {
    @Test fun concurrentScreensShareOneOpening() = runTest {
        val ready = CompletableDeferred<Unit>()
        val core = TestCore()
        var calls = 0
        val session = CoreSession(backgroundScope) { calls++; ready.await(); core }
        val screens = List(10) { async { session.awaitCore() } }
        runCurrent()
        assertEquals(1, calls)
        ready.complete(Unit)
        screens.forEach { assertSame(core, it.await()) }
        session.shutdown()
        assertEquals(listOf("shutdown", "close"), core.events)
    }

    @Test fun cancelledScreenDoesNotCancelOpening() = runTest {
        val ready = CompletableDeferred<Unit>()
        val core = TestCore()
        var calls = 0
        val session = CoreSession(backgroundScope) { calls++; ready.await(); core }
        val screen = launch { session.awaitCore() }
        runCurrent()
        screen.cancel()
        ready.complete(Unit)
        assertSame(core, session.awaitCore())
        assertEquals(1, calls)
        session.shutdown()
    }

    @Test fun closeWaitsForOpeningAndRejectsNewConsumers() = runTest {
        val ready = CompletableDeferred<Unit>()
        val core = TestCore()
        val session = CoreSession(backgroundScope) { ready.await(); core }
        val screen = launch { session.awaitCore() }
        runCurrent()
        val closing = launch { session.shutdown() }
        runCurrent()
        assertFalse(closing.isCompleted)
        assertEquals(CoreSessionState.Closing, session.state.value)
        try { session.awaitCore(); fail("closing session accepted a consumer") } catch (_: IllegalStateException) {}
        ready.complete(Unit)
        screen.join()
        closing.join()
        session.shutdown()
        assertEquals(CoreSessionState.Closed, session.state.value)
        assertEquals(listOf("shutdown", "close"), core.events)
    }

    @Test fun failedOpeningCanBeExplicitlyRetried() = runTest {
        var calls = 0
        val core = TestCore()
        val session = CoreSession(backgroundScope) {
            if (++calls == 1) throw IllegalStateException("test opening failure")
            core
        }
        try { session.awaitCore(); fail("opening should fail") } catch (_: IllegalStateException) {}
        assertTrue(session.state.value is CoreSessionState.Failed)
        assertSame(core, session.retryOpening())
        session.shutdown()
        assertEquals(2, calls)
    }

    private class TestCore : HootKitCoreInterface, AutoCloseable {
        val events = mutableListOf<String>()
        override suspend fun shutdown() { events += "shutdown" }
        override fun close() { events += "close" }
        override fun accounts(): Accounts = error("not queried")
        override fun ai(): Ai = error("not queried")
        override fun backup(): Backup = error("not queried")
        override fun brokerage(): Brokerage = error("not queried")
        override fun budgets(): Budgets = error("not queried")
        override fun catalog(): Catalog = error("not queried")
        override fun categories(): Categories = error("not queried")
        override fun currency(): Currency = error("not queried")
        override fun ledger(): Ledger = error("not queried")
        override fun legacy(): Legacy = error("not queried")
        override fun plans(): Plans = error("not queried")
        override fun settings(): Settings = error("not queried")
        override fun sync(): Sync = error("not queried")
        override fun tags(): Tags = error("not queried")
        override fun version() = CoreVersion("test", "1.1", 2u, 1u)
    }
}
