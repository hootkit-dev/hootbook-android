package io.github.hootkit_dev.hootbook.core.sync

import dev.hootkit.core.*
import org.junit.Assert.*
import org.junit.Test

class CoreSyncGateTest {
    @Test fun signedOutAndClosedGateNeverGrantRemoteLease() {
        val gate = CoreSyncGate(); assertNull(gate.currentAccount()); assertThrows(PlatformException.Unauthorized::class.java) { gate.lease() }
        gate.publish(SyncAccount("uid-A", "a@example.test")); assertThrows(PlatformException.Unauthorized::class.java) { gate.lease() }
    }
    @Test fun leaseCapturesUidAndEpochAndIsInvalidatedBeforeSnapshotSwitch() {
        val gate = CoreSyncGate(); gate.publish(SyncAccount("uid-A", null)); gate.open(); val lease = gate.lease()
        gate.close(); assertFalse(gate.accepts(lease)); assertEquals("uid-A", lease.uid)
        gate.publish(SyncAccount("uid-B", null)); gate.open(); assertEquals("uid-A", lease.uid); assertEquals("uid-B", gate.lease().uid)
    }
    @Test fun openSnapshotCannotBeReplacedWithoutClosing() {
        val gate = CoreSyncGate(); gate.publish(SyncAccount("uid-A", null)); gate.open()
        assertThrows(IllegalStateException::class.java) { gate.publish(SyncAccount("uid-B", null)) }
        assertEquals("uid-A", gate.currentAccount()!!.uid)
    }
    @Test fun pathInjectionAndEmptyUidAreRejectedBeforePublication() {
        val gate = CoreSyncGate()
        assertThrows(IllegalArgumentException::class.java) { gate.publish(SyncAccount("x/other-user", null)) }
        assertThrows(IllegalArgumentException::class.java) { gate.publish(SyncAccount("", null)) }
        assertNull(gate.currentAccount())
    }
}
