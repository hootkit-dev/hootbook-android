package io.github.hootkit_dev.hootbook.core.sync

import android.app.Application
import android.content.Context
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreSyncCoordinatorTest {
    private fun runCoordinator(block: suspend TestScope.(CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try { block(scope) } finally { scope.cancel(); runCurrent() }
    }
    @Test fun constructionDoesNotOpenCoreOrInitializeFirebaseAndStartIsIdempotent() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate); val native = SyncTestHandle(); var requested = 0
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { requested++; native }
        assertEquals(0, requested); assertEquals(0, platform.starts); coordinator.start(); coordinator.start(); runCurrent()
        assertEquals(1, platform.starts); assertTrue(coordinator.state.value.initialized)
    }
    @Test fun durableIdentityIsAcceptedBeforeSnapshotAndAuthChangedBeforeRemoteGateOpens() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle()
        val order = mutableListOf<String>(); platform.accepting = { order += "durable"; assertNull(gate.currentAccount()); CoreIdentityDecision.ACCEPTED }
        native.onAuth = { order += "authChanged"; assertEquals("uid-A", gate.currentAccount()!!.uid)
            assertThrows(PlatformException.Unauthorized::class.java) { gate.lease() } }
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }
        coordinator.start(); runCurrent(); assertEquals(listOf("durable", "authChanged"), order); assertEquals("uid-A", gate.lease().uid)
    }
    @Test fun authHandoffWaitsForCurrentPlatformRunAndOldRemoteLeaseNeverRebinds() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle()
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.start(); runCurrent()
        val old = gate.lease(); val release = CompletableDeferred<Unit>(); native.onRun = { release.await(); SyncOutcome(true, null, 1u, 0u, null) }
        val round = async { coordinator.runManual() }; runCurrent(); platform.accepting = { CoreIdentityDecision.DIFFERENT_UID_BLOCKED }; platform.emit(SyncAccount("uid-B", null)); runCurrent()
        assertFalse(gate.accepts(old)); assertEquals(1, native.authChanges); assertEquals("uid-A", gate.currentAccount()!!.uid)
        release.complete(Unit); round.await(); runCurrent(); assertEquals(2, native.authChanges); assertNull(gate.currentAccount())
        assertEquals(CoreIdentityDecision.DIFFERENT_UID_BLOCKED, coordinator.state.value.identityDecision); assertEquals("uid-A", old.uid)
    }
    @Test fun differentUidNeverReachesCoreAndLeavesOriginalIdentityPolicyIntact() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-B", null); accepting = { CoreIdentityDecision.DIFFERENT_UID_BLOCKED } }
        val native = SyncTestHandle(); native.onAuth = { assertNull(gate.currentAccount()) }
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.foregrounded(); runCurrent()
        assertEquals(0, native.runs); assertNull(gate.currentAccount()); assertTrue(coordinator.state.value.error!!.contains("#70"))
        assertThrows(PlatformException.Unauthorized::class.java) { gate.lease() }
    }
    @Test fun missingExistingLedgerIdentityAndStorageFailureBothKeepRemoteClosed() = runCoordinator { scope ->
        for (decision in listOf(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, CoreIdentityDecision.STORAGE_FAILED)) {
            val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null); accepting = { decision } }; val native = SyncTestHandle()
            val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.start(); runCurrent()
            assertNull(gate.currentAccount()); assertEquals(0, native.runs); assertEquals(decision, coordinator.state.value.identityDecision)
        }
    }
    @Test fun signOutKeepsBindingAndSameUidCanReauthenticateWithoutPublishingOtherUid() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle()
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.start(); runCurrent()
        coordinator.signOut(); runCurrent(); assertNull(gate.currentAccount()); assertEquals(1, platform.signouts)
        platform.emit(SyncAccount("uid-A", null)); runCurrent(); assertEquals("uid-A", gate.lease().uid)
    }
    @Test fun foregroundBackgroundAndManualHaveCorrectReasonsAndBackgroundBoolControlsWork() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle(); val scheduler = SyncTestScheduler()
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, scheduler, scope) { native }; coordinator.start(); runCurrent()
        coordinator.foregrounded(); runCurrent(); assertEquals(listOf(SyncReason.FOREGROUND), native.reasons)
        coordinator.backgrounded(); runCurrent(); assertTrue(scheduler.delays.isEmpty())
        native.backgroundNeeded = true; coordinator.backgrounded(); runCurrent(); assertEquals(listOf(1000L), scheduler.delays)
        coordinator.backgroundRound(); coordinator.runManual(); assertEquals(listOf(SyncReason.FOREGROUND, SyncReason.BACKGROUND, SyncReason.MANUAL), native.reasons)
    }
    @Test fun retryRecommendationsFromRunAndSyncStateAreScheduledNotBusyRetried() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle(); val scheduler = SyncTestScheduler(); val changes = ChangeBus()
        native.onRun = { SyncOutcome(false, SyncErrorKind.OFFLINE, 0u, 0u, 30000u) }
        val coordinator = CoreSyncCoordinator(changes, platform, gate, scheduler, scope) { native }; coordinator.start(); runCurrent(); coordinator.runManual()
        assertEquals(1, native.runs); assertTrue(30000L in scheduler.delays)
        native.status = native.status.copy(retryAfterMs = 60000u); changes.onChange(ChangeSet(listOf(Table.SYNC_STATE), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        assertTrue(60000L in scheduler.delays); assertEquals(1, native.runs)
    }
    @Test fun prerequisiteErrorHasNoInventedRetryAndCorruptHasNoRetry() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle(); val scheduler = SyncTestScheduler()
        native.onRun = { throw CoreException.Sync(SyncErrorKind.NEEDS_PASSPHRASE) }
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, scheduler, scope) { native }; coordinator.start(); runCurrent(); assertNull(coordinator.runManual()); assertTrue(scheduler.delays.isEmpty())
        native.onRun = { SyncOutcome(false, SyncErrorKind.CORRUPT, 0u, 0u, null) }; coordinator.runManual(); assertTrue(scheduler.delays.isEmpty())
    }
    @Test fun offlineFlavorNeverStartsAuthOpensCoreOrSchedulesRemoteWork() = runCoordinator { scope ->
        var opened = 0; val scheduler = SyncTestScheduler(); val coordinator = CoreSyncCoordinator(ChangeBus(), CoreOfflineSyncPlatform(), CoreSyncGate(), scheduler, scope) { opened++; SyncTestHandle() }
        coordinator.start(); coordinator.foregrounded(); coordinator.backgrounded(); coordinator.backgroundRound(); coordinator.runManual(); runCurrent()
        assertEquals(0, opened); assertTrue(scheduler.delays.isEmpty()); assertFalse(coordinator.state.value.available)
    }
    @Test fun failedAuthChangedLeavesRemoteClosedAndErrorHasNoRawDetails() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null) }; val native = SyncTestHandle().apply { onAuth = { throw IllegalStateException("private credentials") } }
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.start(); runCurrent()
        assertThrows(PlatformException.Unauthorized::class.java) { gate.lease() }; assertFalse(coordinator.state.value.error!!.contains("private credentials"))
    }
    @Test fun accountBindingFailurePreventsAllMutatingSyncCommands() = runCoordinator { scope ->
        val gate = CoreSyncGate(); val platform = SyncTestPlatform(gate).apply { initial = SyncAccount("uid-A", null); accepting = { CoreIdentityDecision.MISSING_IDENTITY_BLOCKED } }; val native = SyncTestHandle()
        val coordinator = CoreSyncCoordinator(ChangeBus(), platform, gate, SyncTestScheduler(), scope) { native }; coordinator.start(); runCurrent()
        try { coordinator.command { it.resetCloud("new") }; fail("Expected identity block") } catch (_: IllegalStateException) { }
        assertTrue(native.commands.isEmpty()); assertNull(coordinator.runManual())
    }
}
internal class SyncTestScheduler : CoreSyncScheduler {
    val delays = mutableListOf<Long>(); var cancellations = 0
    override fun schedule(afterMs: Long) { delays += afterMs }; override fun cancel() { cancellations++ }; override fun started() = Unit
}
internal class SyncTestPlatform(private val gate: CoreSyncGate) : CoreSyncPlatform {
    override val available = true; override val events = MutableSharedFlow<CoreAuthEvent>(replay = 1)
    var initial: SyncAccount? = null; var starts = 0; var signouts = 0
    var accepting: suspend (SyncAccount) -> CoreIdentityDecision = { CoreIdentityDecision.ACCEPTED }
    override fun start() { starts++; events.tryEmit(CoreAuthEvent(initial)) }
    override fun stopRemoteListening() = Unit
    override suspend fun acceptIdentity(account: SyncAccount) = accepting(account)
    override suspend fun signIn(activity: Context) = Unit
    override suspend fun signOut() { signouts++; emit(null) }
    suspend fun emit(account: SyncAccount?) { gate.close(); events.emit(CoreAuthEvent(account)) }
}
internal class SyncTestHandle : Sync(NoPointer) {
    var status = syncTestStatus(); var authChanges = 0; var runs = 0; val reasons = mutableListOf<SyncReason>(); val commands = mutableListOf<String>(); var backgroundNeeded = false
    var onAuth: suspend () -> Unit = {}; var onRun: suspend () -> SyncOutcome = { SyncOutcome(true, null, 0u, 0u, null) }
    var onCommand: suspend () -> Unit = {}; var conflictValues = emptyList<SyncConflict>(); var noticeValues = emptyList<SyncNotice>(); var policy = ConflictPolicy(BothEditedPolicy.MINE, DeletePolicy.DELETE)
    var v1 = false; var choice: ConflictChoice? = null
    override suspend fun status() = status
    override suspend fun authChanged() { authChanges++; onAuth() }
    override suspend fun run(reason: SyncReason): SyncOutcome { runs++; reasons += reason; return onRun() }
    override suspend fun appBackgrounded() = backgroundNeeded
    override suspend fun setPassphrase(passphrase: String) { commands += "passphrase"; onCommand() }
    override suspend fun setPaused(paused: Boolean) { commands += "paused:$paused"; onCommand() }
    override suspend fun resetCloud(newPassphrase: String) { commands += "reset"; onCommand() }
    override suspend fun replaceWithCloud() { commands += "replace"; onCommand() }
    override suspend fun chooseAccountSwitch(choice: AccountSwitchChoice) { commands += "choose"; onCommand() }
    override suspend fun conflicts() = conflictValues
    override suspend fun notices() = noticeValues
    override suspend fun conflictPolicy() = policy
    override suspend fun setConflictPolicy(policy: ConflictPolicy) { commands += "policy"; this.policy = policy; onCommand() }
    override suspend fun resolveConflict(conflictId: Long, choice: ConflictChoice) { commands += "resolve:$conflictId"; this.choice = choice; onCommand() }
    override suspend fun dismissConflicts(conflictIds: List<Long>) { commands += "dismiss-conflicts"; onCommand() }
    override suspend fun dismissNotices(ids: List<Long>) { commands += "dismiss-notices"; onCommand() }
    override suspend fun hasV1Data() = v1
    override suspend fun deleteV1Data() { commands += "delete-v1"; v1 = false; onCommand() }
}
internal fun syncTestStatus(phase: SyncPhase = SyncPhase.IDLE) = SyncStatus(phase, "user@example.test", "uid-A", "d".repeat(32), null, null, 0u, 0u, 0u, null, 2u, null, null)
