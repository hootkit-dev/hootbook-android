package io.github.hootkit_dev.hootbook.presentation.core.sync

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.core.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreSyncViewModelTest {
    private fun runModel(block: suspend TestScope.(ViewModelStore, CoroutineScope) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore(); val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try { block(store, app) } finally { store.clear(); app.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
    private fun model(store: ViewModelStore, app: CoroutineScope, native: SyncTestHandle, changes: ChangeBus = ChangeBus(),
        decision: CoreIdentityDecision = CoreIdentityDecision.ACCEPTED): CoreSyncViewModel {
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null); accepting = { decision } }
        val coordinator = CoreSyncCoordinator(changes, platform, gate, SyncTestScheduler(), app) { native }
        return CoreSyncViewModel(changes, coordinator) { native }.also { store.put("vm", it) }
    }
    @Test fun loadsAllStatusNoticesConflictsAndPolicyFromCoreWithoutNetworkV1Guess() = runModel { store, app ->
        val native = SyncTestHandle().apply { conflictValues = listOf(conflict()); noticeValues = listOf(notice()) }; val vm = model(store, app, native); runCurrent()
        assertEquals(native.status, vm.state.value.status); assertEquals(native.conflictValues, vm.state.value.conflicts); assertEquals(native.noticeValues, vm.state.value.notices)
        assertEquals(native.policy, vm.state.value.policy); assertNull(vm.state.value.hasV1)
    }
    @Test fun allCorePhasesRemainLiteralUiStateRatherThanFakeIdleOrSignedOut() = runModel { store, app ->
        val native = SyncTestHandle(); val vm = model(store, app, native); runCurrent()
        for (phase in SyncPhase.entries) { native.status = syncTestStatus(phase); vm.refresh(); runCurrent(); assertEquals(phase, vm.state.value.status!!.phase) }
    }
    @Test fun passphraseNeverStoredInUiStateAndDuplicateCommandsAreBlocked() = runModel { store, app ->
        val release = CompletableDeferred<Unit>(); val native = SyncTestHandle().apply { onCommand = { release.await() } }; val vm = model(store, app, native); runCurrent()
        vm.passphrase("private-passphrase"); vm.passphrase("second-key"); runCurrent(); assertTrue(vm.back()); assertEquals(listOf("passphrase"), native.commands)
        assertFalse(vm.state.value.toString().contains("private-passphrase")); release.complete(Unit); runCurrent(); assertFalse(vm.state.value.busy); assertFalse(vm.back())
    }
    @Test fun wrongPassphraseReportsCoreErrorAndDoesNotPretendToUnlock() = runModel { store, app ->
        val native = SyncTestHandle().apply { status = syncTestStatus(SyncPhase.NEEDS_PASSPHRASE); onCommand = { throw CoreException.Sync(SyncErrorKind.WRONG_PASSPHRASE) } }; val vm = model(store, app, native); runCurrent()
        vm.passphrase("bad"); runCurrent(); assertTrue(vm.state.value.error!!.contains("WRONG_PASSPHRASE")); assertEquals(SyncPhase.NEEDS_PASSPHRASE, vm.state.value.status!!.phase)
    }
    @Test fun destructiveResetReplaceAndV1DeletionRequireExplicitConfirmation() = runModel { store, app ->
        val native = SyncTestHandle(); val vm = model(store, app, native); runCurrent()
        vm.resetCloud("new", false); vm.replaceWithCloud(false); vm.deleteV1(false); runCurrent(); assertTrue(native.commands.isEmpty())
        vm.resetCloud("new", true); runCurrent(); vm.replaceWithCloud(true); runCurrent(); vm.deleteV1(true); runCurrent()
        assertEquals(listOf("reset", "replace", "delete-v1"), native.commands)
    }
    @Test fun checkAndDeleteLegacyDataUseActualBooleanAndFailureDoesNotClaimDeletion() = runModel { store, app ->
        val native = SyncTestHandle().apply { v1 = true }; val vm = model(store, app, native); runCurrent(); vm.checkV1(); runCurrent(); assertEquals(true, vm.state.value.hasV1)
        native.onCommand = { throw CoreException.Sync(SyncErrorKind.OFFLINE) }; vm.deleteV1(true); runCurrent()
        assertEquals(true, vm.state.value.hasV1); assertNotNull(vm.state.value.error)
    }
    @Test fun currentCoreCrossAccountChoiceIsExplicitlyBlockedWithNoNativeMutation() = runModel { store, app ->
        val native = SyncTestHandle().apply { status = syncTestStatus(SyncPhase.NEEDS_ACCOUNT_CHOICE) }; val vm = model(store, app, native); runCurrent()
        vm.accountChoice(AccountSwitchChoice.MERGE); vm.accountChoice(AccountSwitchChoice.REPLACE_LOCAL); runCurrent()
        assertTrue(native.commands.isEmpty()); assertTrue(vm.state.value.error!!.contains("#70"))
    }
    @Test fun resolveUsesLocalIntegerConflictIdAndRawCoreChoiceExactlyOnce() = runModel { store, app ->
        val native = SyncTestHandle(); val vm = model(store, app, native); runCurrent()
        val choice = ConflictChoice.PerField(mapOf("amount" to ConflictSide.THEIRS, "currency" to ConflictSide.THEIRS)); vm.resolve(72, choice); runCurrent()
        assertEquals(listOf("resolve:72"), native.commands); assertEquals(choice, native.choice)
    }
    @Test fun staleConflictFailurePreservesReviewStateAndAllowsRefresh() = runModel { store, app ->
        val native = SyncTestHandle().apply { conflictValues = listOf(conflict()); onCommand = { throw CoreException.NotFound(Entity.SYNC_CONFLICT, "72") } }
        val vm = model(store, app, native); runCurrent(); vm.resolve(72, ConflictChoice.Mine); runCurrent()
        assertEquals(native.conflictValues, vm.state.value.conflicts); assertTrue(vm.state.value.error!!.contains("Refresh"))
    }
    @Test fun conflictPolicyAndNoticeDismissGoDirectlyToCoreNotUiDatabase() = runModel { store, app ->
        val native = SyncTestHandle(); val vm = model(store, app, native); runCurrent()
        val policy = ConflictPolicy(BothEditedPolicy.ASK, DeletePolicy.ASK); vm.policy(policy); runCurrent(); vm.dismissConflicts(listOf(72)); runCurrent(); vm.dismissNotices(listOf(8)); runCurrent()
        assertEquals(policy, native.policy); assertEquals(listOf("policy", "dismiss-conflicts", "dismiss-notices"), native.commands)
    }
    @Test fun identityRestrictionStopsRemoteMutationsButLeavesLocalStatusReadable() = runModel { store, app ->
        val native = SyncTestHandle(); val vm = model(store, app, native, decision = CoreIdentityDecision.MISSING_IDENTITY_BLOCKED); runCurrent()
        vm.resetCloud("new", true); runCurrent(); assertTrue(native.commands.isEmpty()); assertNotNull(vm.state.value.status); assertNotNull(vm.state.value.error)
    }
    @Test fun manualRunShowsLiteralCoreFailureOutcomeWithoutFakeSuccess() = runModel { store, app ->
        val native = SyncTestHandle().apply { onRun = { SyncOutcome(false, SyncErrorKind.CORRUPT, 4u, 5u, null) } }; val vm = model(store, app, native); runCurrent()
        vm.syncNow(); runCurrent(); assertEquals(false, vm.state.value.outcome!!.ok); assertEquals(SyncErrorKind.CORRUPT, vm.state.value.outcome!!.error)
        assertEquals(4u, vm.state.value.outcome!!.pushed); assertEquals(5u, vm.state.value.outcome!!.pulled)
    }
    @Test fun clearingViewModelDoesNotCancelAnAlreadyDispatchedAtomicCoreCommand() = runModel { store, app ->
        val release = CompletableDeferred<Unit>(); val native = SyncTestHandle().apply { onCommand = { release.await() } }; val vm = model(store, app, native); runCurrent()
        vm.resetCloud("not-persisted", true); runCurrent(); store.clear(); release.complete(Unit); runCurrent()
        assertEquals(listOf("reset"), native.commands)
    }
    @Test fun unrelatedTableChangesDoNotRefreshSyncAndRelevantChangesReadNewCoreState() = runModel { store, app ->
        val native = SyncTestHandle(); val changes = ChangeBus(); val vm = model(store, app, native, changes); runCurrent(); val original = vm.state.value.status
        native.status = native.status.copy(outboxCount = 42u); changes.onChange(ChangeSet(listOf(Table.PEOPLE), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(original, vm.state.value.status)
        changes.onChange(ChangeSet(listOf(Table.SYNC_STATE), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(42u, vm.state.value.status!!.outboxCount)
    }
    companion object {
        fun conflict() = SyncConflict(72, LocalDateTime(2026, 10u, 11u, 0u, 0u, 0u, 0u), Table.TRANSACTIONS, "transaction-uuid", ConflictKind.BOTH_EDITED,
            true, null, listOf(ConflictField("amount", "1", "2", "3")), "{\"amount\":\"2\"}", "{\"amount\":\"3\"}")
        fun notice() = SyncNotice(8, LocalDateTime(2026, 10u, 11u, 0u, 0u, 0u, 0u), Table.ACCOUNTS, "account-uuid", "Bank", "1234", null,
            listOf(NoticeReference(Table.TRANSACTIONS, listOf("transaction-uuid"))))
    }
}
