package io.github.hootkit_dev.hootbook.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreLocaleReconcilerTest {
    @Test fun reusedSessionReceivesChangedUiLocaleWithoutReopening() = runTest {
        val applied = mutableListOf<String>()
        val gate = CoreLocaleReconciler { applied += it }
        gate.reconcile { "en" }; gate.reconcile { "zh-CN" }
        assertEquals(listOf("en", "zh-CN"), applied)
        assertEquals(CoreLocaleGateState.Ready("zh-CN"), gate.state.value)
    }
    @Test fun commandFailureBlocksUiAndExplicitRetryAppliesAgain() = runTest {
        var calls = 0
        val gate = CoreLocaleReconciler { calls++; if (calls == 1) error("fixture") }
        gate.reconcile { "zh-CN" }
        assertEquals(CoreLocaleGateState.Failed, gate.state.value)
        gate.reconcile { "zh-CN" }
        assertEquals(2, calls); assertEquals(CoreLocaleGateState.Ready("zh-CN"), gate.state.value)
    }
    @Test fun unchangedKeyDoesNotEmitRepeatedCoreCommands() = runTest {
        var calls = 0
        val gate = CoreLocaleReconciler { calls++ }
        repeat(10) { gate.reconcile { "en" } }
        assertEquals(1, calls)
    }
    @Test fun preferenceReadFailureAlsoBlocksUntilRetry() = runTest {
        val applied = mutableListOf<String>()
        val gate = CoreLocaleReconciler { applied += it }
        gate.reconcile { error("preference unavailable") }
        assertEquals(CoreLocaleGateState.Failed, gate.state.value); assertTrue(applied.isEmpty())
        gate.reconcile { "en" }
        assertEquals(listOf("en"), applied)
    }
}
