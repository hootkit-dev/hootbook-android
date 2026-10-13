package io.github.hootkit_dev.hootbook.presentation.data

import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class CoreFlowsTest {
    private class FakeCore : HootKitCore(NoPointer) {
        var destroyed = false
        override fun destroy() { destroyed = true }
    }

    @Test fun subscriptionRefreshFilteringAndResubscription() = runBlocking {
        withTimeout(5_000) {
            val core = FakeCore()
            val session = CoreSession(CoroutineScope(Dispatchers.IO)) { core }
            val changes = ChangeBus()
            val values = Channel<Int>(Channel.UNLIMITED)
            var queries = 0
            val flow = session.observe(changes, setOf(Table.TRANSACTIONS)) {
                assertSame(core, it)
                ++queries
            }
            val job = launch { flow.collect { values.send(it) } }
            assertEquals(1, values.receive())
            changes.onChange(ChangeSet(listOf(Table.ACCOUNTS), emptyList(), ChangeOrigin.LOCAL))
            changes.onChange(ChangeSet(listOf(Table.TRANSACTIONS), emptyList(), ChangeOrigin.LOCAL))
            assertEquals(2, values.receive())
            job.cancelAndJoin()
            changes.onChange(ChangeSet(listOf(Table.TRANSACTIONS), emptyList(), ChangeOrigin.LOCAL))
            assertEquals(3, flow.first())
            assertFalse(core.destroyed)
        }
    }

    @Test fun latestRevisionCancelsStaleQueryAndClosesItsService() = runBlocking {
        withTimeout(5_000) {
            val session = CoreSession(CoroutineScope(Dispatchers.IO)) { FakeCore() }
            val changes = ChangeBus()
            val started = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            var calls = 0
            val result = async {
                session.observe(changes, setOf(Table.TRANSACTIONS)) {
                    if (++calls == 1) {
                        val ledger = object : Ledger(NoPointer) {
                            override fun destroy() { closed.complete(Unit) }
                        }
                        ledger.use {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    }
                    "latest"
                }.first()
            }
            started.await()
            changes.onChange(ChangeSet(listOf(Table.TRANSACTIONS), emptyList(), ChangeOrigin.LOCAL))
            assertEquals("latest", result.await())
            closed.await()
        }
    }

    @Test fun commandsRunOffCallerThreadAndPreserveCoreErrorsAndServiceOwnership() = runBlocking {
        val caller = Thread.currentThread()
        val core = FakeCore()
        val session = CoreSession(CoroutineScope(Dispatchers.IO)) { core }
        var serviceClosed = false
        val failure = CoreException.NotFound(Entity.TRANSACTION, "opaque/id")
        try {
            session.withCore {
                assertSame(core, it)
                assertNotSame(caller, Thread.currentThread())
                object : Ledger(NoPointer) {
                    override fun destroy() { serviceClosed = true }
                }.use { throw failure }
            }
            fail("Expected CoreException")
        } catch (actual: CoreException) {
            assertSame(failure, actual)
        }
        assertTrue(serviceClosed)
        assertFalse(core.destroyed)
    }

    @Test fun observeRunsOffCallerThreadAndPropagatesCoreError() = runBlocking {
        val caller = Thread.currentThread()
        val session = CoreSession(CoroutineScope(Dispatchers.IO)) { FakeCore() }
        val failure = CoreException.Platform("fixture")
        try {
            session.observe(ChangeBus(), setOf(Table.ACCOUNTS)) {
                assertNotSame(caller, Thread.currentThread())
                throw failure
            }.first()
            fail("Expected CoreException")
        } catch (actual: CoreException) {
            assertSame(failure, actual)
        }
    }

    @Test fun commandCancellationIsNotTurnedIntoAnErrorResult() = runBlocking {
        withTimeout(5_000) {
            val session = CoreSession(CoroutineScope(Dispatchers.IO)) { FakeCore() }
            val started = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            val job = launch {
                session.withCore {
                    object : Ledger(NoPointer) {
                        override fun destroy() { closed.complete(Unit) }
                    }.use {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                }
            }
            started.await()
            job.cancelAndJoin()
            closed.await()
            assertTrue(job.isCancelled)
        }
    }
}
