package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.HootKitCoreInterface
import dev.hootkit.platform.CoreSession
import dev.hootkit.platform.CoreSessionState
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreStartupCoordinatorTest {
    @Test fun constructingHostNeverOpensCore() = runTest {
        var accesses = 0
        CoreStartupCoordinator(backgroundScope, { accesses++; error("early opening") }, { false }, {})
        runCurrent()
        assertEquals(0, accesses)
    }

    @Test fun repeatedStartsShareOneApplicationOwnedOpening() = runTest {
        val ready = CompletableDeferred<Unit>()
        var accesses = 0
        var opens = 0
        val core = fakeCore()
        val session = CoreSession(backgroundScope) { opens++; ready.await(); core }
        val startup = CoreStartupCoordinator(backgroundScope, { accesses++; session }, { false }, {})
        val first = startup.start()
        assertSame(first, startup.start())
        runCurrent()
        assertEquals(1, accesses)
        assertEquals(1, opens)
        ready.complete(Unit)
        runCurrent()
        assertTrue(session.state.value is CoreSessionState.Ready)
        startup.onForegroundChanged(false)
        startup.onForegroundChanged(true)
        assertSame(core, session.awaitCore())
        assertEquals(1, opens)
        session.shutdown()
    }

    @Test fun crashProcessDoesNotConstructSessionOrOpenNativeCode() = runTest {
        var accesses = 0
        val startup = CoreStartupCoordinator(backgroundScope, { accesses++; error("crash opening") }, { true }, {})
        assertNull(startup.start())
        startup.onForegroundChanged(true)
        runCurrent()
        assertEquals(0, accesses)
        assertFalse(startup.foreground.value)
    }

    @Test fun foregroundChangesNeverOpenOrCloseCore() = runTest {
        var accesses = 0
        val startup = CoreStartupCoordinator(backgroundScope, { accesses++; error("unexpected access") }, { false }, {})
        startup.onForegroundChanged(true)
        assertTrue(startup.foreground.value)
        startup.onForegroundChanged(false)
        runCurrent()
        assertFalse(startup.foreground.value)
        assertEquals(0, accesses)
    }

    @Test fun openingFailureIsPublishedAndReportedWithoutCrashingApplicationScope() = runTest {
        val failure = IllegalStateException("opening fixture")
        val failures = mutableListOf<Throwable>()
        var opens = 0
        val core = fakeCore()
        val session = CoreSession(backgroundScope) { if (++opens == 1) throw failure else core }
        val startup = CoreStartupCoordinator(backgroundScope, { session }, { false }, { failures += it })
        startup.start()
        runCurrent()
        assertSame(failure, (session.state.value as CoreSessionState.Failed).cause)
        // Coroutine stack-trace recovery may copy a throwable while retaining its type/message.
        assertEquals(1, failures.size)
        assertEquals(failure.javaClass, failures.single().javaClass)
        assertEquals(failure.message, failures.single().message)
        assertSame(core, session.retryOpening())
        assertEquals(2, opens)
        session.shutdown()
    }

    @Test fun failureReporterDoesNotEscapeStartupJob() = runTest {
        val session = CoreSession(backgroundScope) { error("opening fixture") }
        val startup = CoreStartupCoordinator(backgroundScope, { session }, { false }, { error("report fixture") })
        startup.start()
        runCurrent()
        assertTrue(session.state.value is CoreSessionState.Failed)
        session.shutdown()
    }

    private fun fakeCore(): HootKitCoreInterface = Proxy.newProxyInstance(
        HootKitCoreInterface::class.java.classLoader,
        arrayOf(HootKitCoreInterface::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "shutdown" -> Unit
            "toString" -> "fixtureCore"
            else -> error("Unexpected Core call: ${method.name}")
        }
    } as HootKitCoreInterface
}
