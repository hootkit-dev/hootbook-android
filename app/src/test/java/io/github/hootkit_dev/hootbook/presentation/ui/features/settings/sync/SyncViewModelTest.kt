package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.core.sync.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest : CoreViewModelTest() {
    private fun TestScope.model(native: SyncTestHandle, changes: ChangeBus,
        read: suspend () -> SyncStatus = { native.status }): SyncViewModel {
        val core = object : HootKitCore(NoPointer) {
            override fun sync() = object : Sync(NoPointer) { override suspend fun status() = read() }
        }
        val gate = CoreSyncGate()
        val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }
        val coordinator = CoreSyncCoordinator(changes, platform, gate, SyncTestScheduler(), backgroundScope) { native }
        return keep(SyncViewModel(CoreSession(backgroundScope) { core }, changes, coordinator))
    }
    @Test fun loadsLiteralPhasesRefreshesOnChangesAndLatestRefreshWins() = runTest {
        val native = SyncTestHandle(); val bus = ChangeBus(); val gate = CompletableDeferred<Unit>(); var slow = false; var entered = false
        val vm = model(native, bus) {
            val snapshot = native.status
            if (slow) { slow = false; entered = true; withContext(NonCancellable) { gate.await() } }
            snapshot
        }
        awaitState { !vm.state.value.loading }; assertSame(native.status, vm.state.value.status)
        native.status = syncTestStatus(SyncPhase.PAUSED); bus.changed(Table.SYNC_STATE)
        awaitState { vm.state.value.status?.phase == SyncPhase.PAUSED }
        slow = true; vm.refresh(); awaitState { entered }
        native.status = syncTestStatus(SyncPhase.UPGRADE_REQUIRED); vm.refresh(); gate.complete(Unit)
        awaitState { vm.state.value.status?.phase == SyncPhase.UPGRADE_REQUIRED }
    }
    @Test fun passphraseCommandsGuardDuplicatesAndErrorDoesNotPretendToUnlock() = runTest {
        val native = SyncTestHandle(); val vm = model(native, ChangeBus()); awaitState { !vm.state.value.loading && vm.state.value.control?.initialized == true }
        val gate = CompletableDeferred<Unit>(); native.onCommand = { gate.await(); throw CoreException.Sync(SyncErrorKind.WRONG_PASSPHRASE) }
        vm.submitPassphrase("private"); vm.submitPassphrase("duplicate"); awaitState { native.commands.size == 1 }
        assertFalse(vm.state.value.toString().contains("private")); gate.complete(Unit); awaitState { !vm.state.value.busy }
        assertTrue(vm.wrongPassphrase.value); assertNotNull(vm.state.value.error)
        native.onCommand = {}; vm.setEnabled(false); awaitState { !vm.state.value.busy }; assertEquals(listOf("passphrase", "paused:true"), native.commands)
        native.onRun = { SyncOutcome(false, SyncErrorKind.CORRUPT, 4u, 5u, null) }
        vm.syncNow(); awaitState { !vm.state.value.busy }; assertEquals(false, vm.state.value.outcome?.ok)
    }
    @Test fun failedInitialReadRecoversOnChange() = runTest {
        val native = SyncTestHandle(); val bus = ChangeBus(); var failing = true
        val vm = model(native, bus) { if (failing) error("load"); native.status }
        awaitState { !vm.state.value.loading }; assertNotNull(vm.state.value.error)
        failing = false; bus.changed(Table.SYNC_STATE); awaitState { vm.state.value.status != null }
    }
}
