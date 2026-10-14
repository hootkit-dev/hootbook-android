package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.core.ai.*
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class AiAssistantViewModelTest {
    private fun runModel(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            block(store)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun model(
        store: ViewModelStore,
        ai: FakeAi = FakeAi(),
        inbox: FakeInbox = FakeInbox(),
        prepare: suspend (List<CoreAiInboxFile>, Map<String, String>) -> List<AiAttachment> =
            { _, _ ->
                emptyList()
            },
        provider: suspend () -> AiInterface = { ai },
        nativeDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) =
        AiAssistantViewModel(
                ChangeBus(),
                inbox,
                prepare,
                nativeDispatcher = nativeDispatcher,
                ai = provider,
            )
            .also { store.put("vm", it) }

    private fun TestScope.review(vm: AiAssistantViewModel) {
        runCurrent()
        vm.request("coffee 12 GBP")
        vm.analyze()
        runCurrent()
        assertEquals(AiPhase.REVIEW, vm.state.value.phase)
    }

    @Test
    fun settingsComeFromCoreAndFreshDraftHasNoSecret() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        assertEquals("actual-model", vm.state.value.config.model)
        assertEquals("", vm.state.value.config.apiKey)
        assertTrue(vm.state.value.settings!!.hasKey)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun protocolDefaultsComeFromCoreAndKeyIsPreservedOnlyInDraft() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        vm.configChanged { it.copy(apiKey = "private-secret") }
        vm.protocol(AiApiProtocol.OPEN_AI_COMPATIBLE)
        runCurrent()
        assertEquals("core-default-url", vm.state.value.config.baseUrl)
        assertEquals("core-default-model", vm.state.value.config.model)
        assertEquals(AiApiProtocol.OPEN_AI_COMPATIBLE, vm.state.value.config.protocol)
        assertFalse(vm.state.value.toString().contains("private-secret"))
        assertEquals("private-secret", vm.state.value.config.apiKey)
    }

    @Test
    fun configSavesCoreSettingsThenKeyAndNeverReadsStoredKey() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        vm.configChanged { it.copy(apiKey = "replacement-key", model = "selected") }
        vm.saveConfig()
        vm.saveConfig()
        runCurrent()
        assertEquals(listOf("settings", "key"), ai.writes)
        assertEquals("replacement-key", ai.writtenKey)
        assertEquals("", vm.state.value.config.apiKey)
        assertFalse(vm.state.value.configDirty)
        assertEquals("selected", vm.state.value.settings!!.model)
    }

    @Test
    fun keyFailureIsSanitizedAndNoPlaintextFallbackOrSuccessfulState() = runModel { store ->
        val ai = FakeAi().apply { keyFailure = CoreException.Platform("failure: secret-key") }
        val vm = model(store, ai)
        runCurrent()
        vm.configChanged { it.copy(apiKey = "secret-key") }
        vm.saveConfig()
        runCurrent()
        assertTrue(vm.state.value.configDirty)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("secret-key"))
        assertEquals("", vm.state.value.config.apiKey)
        assertEquals(1, ai.keyCalls)
    }

    @Test
    fun blankKeyKeepsExistingAndExplicitDeleteUsesNull() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        vm.configChanged { it.copy(model = "changed") }
        vm.saveConfig()
        runCurrent()
        assertEquals(0, ai.keyCalls)
        vm.configChanged { it.copy(removeKey = true) }
        vm.saveConfig()
        runCurrent()
        assertEquals(1, ai.keyCalls)
        assertNull(ai.writtenKey)
    }

    @Test
    fun modelsUseUnsavedFormAndSavedKeyFallbackWithoutSaving() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        vm.configChanged { it.copy(baseUrl = "https://unsaved", apiKey = "unsaved-key") }
        vm.loadModels()
        runCurrent()
        assertEquals("https://unsaved", ai.modelUrl)
        assertEquals("unsaved-key", ai.modelKey)
        assertEquals(0, ai.keyCalls)
        assertEquals(1, vm.state.value.models.size)
        assertTrue(vm.state.value.configDirty)
    }

    @Test
    fun staleModelResponseCannotOverwriteNewFormOrNewModels() = runModel { store ->
        var pending: Continuation<List<AiModel>>? = null
        var count = 0
        val ai =
            FakeAi().apply {
                onModels = {
                    if (++count == 1) suspendCoroutine { pending = it }
                    else listOf(AiModel("new", "New", false, AiReasoningSupport(false, emptyList())))
                }
            }
        val vm = model(store, ai)
        runCurrent()
        vm.loadModels()
        runCurrent()
        vm.configChanged { it.copy(baseUrl = "https://new") }
        vm.loadModels()
        runCurrent()
        pending!!.resume(listOf(AiModel("stale", "Stale", false, AiReasoningSupport(false, emptyList()))))
        runCurrent()
        assertEquals(listOf("new"), vm.state.value.models.map { it.id })
        assertFalse(vm.state.value.modelLoading)
    }

    @Test
    fun lateProtocolDefaultsCannotReplaceLaterUserSelection() = runModel { store ->
        val ai = FakeAi()
        var pending: Continuation<AiInterface>? = null
        var wait = false
        val vm =
            model(
                store,
                ai,
                provider = {
                    if (wait) {
                        wait = false
                        suspendCoroutine { pending = it }
                    } else ai
                },
            )
        runCurrent()
        wait = true
        vm.protocol(AiApiProtocol.CLAUDE)
        runCurrent()
        vm.protocol(AiApiProtocol.OPEN_AI_COMPATIBLE)
        runCurrent()
        pending!!.resume(ai)
        runCurrent()
        assertEquals(AiApiProtocol.OPEN_AI_COMPATIBLE, vm.state.value.config.protocol)
    }

    @Test
    fun duplicateRunIsBlockedAndProposalDefaultsUseActualNonContiguousIndexes() =
        runModel { store ->
            val ai =
                FakeAi().apply {
                    native.review =
                        AiReview(
                            listOf(
                                AiProposal(2u, AiChange.AddTransaction(draft(), "tx-string-id")),
                                AiProposal(8u, AiChange.AddTransaction(draft(), null)),
                            ),
                            "summary",
                            true,
                        )
                }
            val vm = model(store, ai)
            runCurrent()
            vm.request("request")
            vm.analyze()
            vm.analyze()
            runCurrent()
            assertEquals(1, ai.starts)
            assertEquals(1, ai.native.runs)
            assertEquals(
                listOf(false, true),
                vm.state.value.items.map { vm.state.value.decisions.getValue(it.index).keep },
            )
            assertEquals(listOf(2u, 8u), vm.state.value.items.map { it.index })
            assertTrue(vm.state.value.review!!.stoppedAtLimit)
        }

    @Test
    fun callbackTimelineUsesCoreStepsAndIsNeverLoggedOrPersisted() = runModel { store ->
        val ai =
            FakeAi().apply {
                native.onRun = {
                    val sent = AiStep.Sent("private ledger prompt", "private user input")
                    listener!!.onStep(sent)
                    native.history += sent
                    native.review
                }
            }
        val vm = model(store, ai)
        review(vm)
        assertEquals(
            "private ledger prompt",
            (vm.state.value.steps.single() as AiStep.Sent).systemPrompt,
        )
    }

    @Test
    fun cancelCallsNativeAndIgnoresLateRunAndListenerResults() = runModel { store ->
        val result = CompletableDeferred<AiReview>()
        val ai = FakeAi().apply { native.onRun = { result.await() } }
        val vm = model(store, ai)
        runCurrent()
        vm.request("x")
        vm.analyze()
        runCurrent()
        assertEquals(AiPhase.RUNNING, vm.state.value.phase)
        vm.cancel()
        ai.listener!!.onStep(AiStep.Asking(99u))
        result.complete(ai.native.review)
        runCurrent()
        assertTrue(ai.native.cancels > 0)
        assertEquals(AiPhase.COMPOSE, vm.state.value.phase)
        assertTrue(vm.state.value.steps.isEmpty())
        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.review)
    }

    @Test
    fun cancellationDuringFilePreparationNeverStartsCoreSession() = runModel { store ->
        val hold = CompletableDeferred<Unit>()
        val ai = FakeAi()
        val vm =
            model(
                store,
                ai,
                prepare = { _, _ ->
                    hold.await()
                    emptyList()
                },
            )
        runCurrent()
        vm.request("x")
        vm.analyze()
        runCurrent()
        vm.cancel()
        hold.complete(Unit)
        runCurrent()
        assertEquals(0, ai.starts)
        assertEquals(AiPhase.COMPOSE, vm.state.value.phase)
    }

    @Test
    fun pdfPasswordRemainsInMemoryAndFailedPreparationDoesNotSend() = runModel { store ->
        val file = CoreAiInboxFile("file-id", "locked.pdf", "application/pdf", 12)
        val inbox = FakeInbox().apply { files.value = listOf(file) }
        val ai = FakeAi()
        var seen: Map<String, String>? = null
        val vm =
            model(
                store,
                ai,
                inbox,
                prepare = { _, keys ->
                    seen = keys
                    if (keys[file.id] == null) throw CoreAiPdfPasswordRequired(file.id, file.name)
                    else emptyList()
                },
            )
        runCurrent()
        vm.analyze()
        runCurrent()
        assertEquals(0, ai.starts)
        assertEquals(file, vm.state.value.passwordFile)
        vm.password("pdf-password")
        runCurrent()
        assertEquals(AiPhase.REVIEW, vm.state.value.phase)
        assertEquals("pdf-password", seen!![file.id])
        assertFalse(vm.state.value.toString().contains("pdf-password"))
        vm.restart()
        runCurrent()
        assertEquals(1, inbox.clears)
    }

    @Test
    fun editsPreserveCoreReferencesAndDecisionsIncludeKeepAndEditedByActualIndex() =
        runModel { store ->
            val ai =
                FakeAi().apply {
                    native.review =
                        AiReview(
                            listOf(AiProposal(7u, AiChange.AddTransaction(draft(), null))),
                            null,
                            false,
                        )
                }
            val vm = model(store, ai)
            review(vm)
            val form =
                AiDraftForm.from(draft()).copy(amount = "45.600", account = AiAccountRef.New("N7"))
            assertTrue(vm.edit(7u, form))
            vm.keep(7u, false)
            vm.apply()
            runCurrent()
            val decision = ai.native.decisions.single()
            assertEquals(7u, decision.index)
            assertFalse(decision.keep)
            assertEquals(AiAccountRef.New("N7"), decision.edited!!.account)
            assertEquals(BigDecimal("45.600"), decision.edited!!.amount)
        }

    @Test
    fun allSixGeneratedProposalVariantsKeepActualIndexesAndApplyAsOneBatch() = runModel { store ->
        val date = draft().dateTime
        val before =
            Transaction(
                "tx-uuid",
                TransactionKind.EXPENSE,
                BigDecimal.TEN,
                "GBP",
                date,
                "account-string",
                null,
                null,
                null,
                "category-string",
                null,
                "Cafe",
                null,
                null,
                null,
                null,
                emptyList(),
                null,
                emptyList(),
                false,
                date,
                date,
            )
        val changes: List<AiChange> =
            listOf(
                AiChange.CreateAccount(
                    "N1",
                    "Bank",
                    null,
                    AccountKind.BANK,
                    "GBP",
                    BigDecimal.ZERO,
                    null,
                    null,
                    "bank",
                    "blue",
                ),
                AiChange.SetBalance("account-string", "GBP", BigDecimal.TEN, null),
                AiChange.UpdateAccount("account-string", "New name", null, null, listOf("USD")),
                AiChange.AddTransaction(draft(), null),
                AiChange.UpdateTransaction(
                    "tx-uuid",
                    before,
                    AiTransactionEdit("Shop", null, null, "edited"),
                ),
                AiChange.DeleteTransaction("tx-uuid", before, "user requested"),
            )
        val ai =
            FakeAi().apply {
                native.review =
                    AiReview(
                        changes.mapIndexed { index, change ->
                            AiProposal((index * 3 + 1).toUInt(), change)
                        },
                        null,
                        false,
                    )
            }
        val vm = model(store, ai)
        review(vm)
        assertEquals(6, vm.state.value.items.size)
        vm.keep(16u, false)
        vm.apply()
        runCurrent()
        assertEquals(1, ai.native.applies)
        assertEquals(listOf(1u, 4u, 7u, 10u, 13u, 16u), ai.native.decisions.map { it.index })
        assertFalse(ai.native.decisions.last().keep)
        assertTrue(ai.native.decisions.all { it.edited == null })
    }

    @Test
    fun nonAddProposalAndUnknownIndexNeverAcceptEditedDraft() = runModel { store ->
        val ai =
            FakeAi().apply {
                native.review =
                    AiReview(
                        listOf(
                            AiProposal(
                                5u,
                                AiChange.SetBalance("account-string", "GBP", BigDecimal.TEN, null),
                            )
                        ),
                        null,
                        false,
                    )
            }
        val vm = model(store, ai)
        review(vm)
        assertFalse(vm.edit(5u, AiDraftForm.from(draft())))
        assertFalse(vm.edit(99u, AiDraftForm.from(draft())))
        vm.apply()
        runCurrent()
        assertNull(ai.native.decisions.single().edited)
    }

    @Test
    fun applyAndBackAreGuardedAndOnlyOneAtomicCoreCallOccurs() = runModel { store ->
        val done = CompletableDeferred<Unit>()
        val ai =
            FakeAi().apply {
                native.onApply = {
                    done.await()
                    AiApplied("opaque-token", 4u)
                }
            }
        val vm = model(store, ai)
        review(vm)
        vm.apply()
        vm.apply()
        runCurrent()
        assertTrue(vm.back())
        vm.restart()
        vm.undo()
        assertEquals(1, ai.native.applies)
        assertEquals(AiPhase.REVIEW, vm.state.value.phase)
        done.complete(Unit)
        runCurrent()
        assertEquals(AiPhase.SAVED, vm.state.value.phase)
        assertEquals(4u, vm.state.value.applied!!.saved)
        vm.apply()
        runCurrent()
        assertEquals(1, ai.native.applies)
    }

    @Test
    fun rejectedApplyRetainsSelectionsAndEditsAndHasNoUndoSuccess() = runModel { store ->
        val ai =
            FakeAi().apply {
                native.onApply = {
                    throw CoreException.Invalid("decisions[1].amount", InvalidReason.BAD_FORMAT)
                }
            }
        val vm = model(store, ai)
        review(vm)
        vm.keep(1u, false)
        vm.apply()
        runCurrent()
        assertEquals(AiPhase.REVIEW, vm.state.value.phase)
        assertFalse(vm.state.value.decisions.getValue(vm.state.value.items.single().index).keep)
        assertNull(vm.state.value.applied)
        assertEquals("Something went wrong. Please try again.", vm.state.value.error)
        vm.undo()
        runCurrent()
        assertEquals(0, ai.undos)
    }

    @Test
    fun undoIsSingleCallAndReturnsReviewForExplicitReapply() = runModel { store ->
        val done = CompletableDeferred<Unit>()
        val ai = FakeAi().apply { onUndo = { done.await() } }
        val vm = model(store, ai)
        review(vm)
        vm.apply()
        runCurrent()
        vm.undo()
        vm.undo()
        runCurrent()
        assertEquals(1, ai.undos)
        assertTrue(vm.state.value.saving)
        done.complete(Unit)
        runCurrent()
        assertEquals(AiPhase.REVIEW, vm.state.value.phase)
        assertNull(vm.state.value.applied)
        vm.apply()
        runCurrent()
        assertEquals(2, ai.native.applies)
        assertEquals(AiPhase.SAVED, vm.state.value.phase)
    }

    @Test
    fun failedUndoKeepsSavedStateAndShowsNoFalseRollback() = runModel { store ->
        val ai =
            FakeAi().apply {
                onUndo = { throw CoreException.NotFound(Entity.UNDO_TOKEN, "opaque-token") }
            }
        val vm = model(store, ai)
        review(vm)
        vm.apply()
        runCurrent()
        vm.undo()
        runCurrent()
        assertEquals(AiPhase.SAVED, vm.state.value.phase)
        assertNotNull(vm.state.value.applied)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun clearedVmWaitsForInFlightAtomicSaveBeforeReleasingSession() = runModel { store ->
        val done = CompletableDeferred<Unit>()
        val ai =
            FakeAi().apply {
                native.onApply = {
                    done.await()
                    AiApplied("token", 1u)
                }
            }
        val vm = model(store, ai)
        review(vm)
        vm.apply()
        runCurrent()
        store.clear()
        assertEquals(0, ai.native.destroys)
        done.complete(Unit)
        runCurrent()
        assertEquals(1, ai.native.destroys)
        assertEquals(1, ai.native.applies)
    }

    @Test
    fun processReplacementRetainsInboxButNoReviewUndoOrPasswordState() = runModel { store ->
        val inbox =
            FakeInbox().apply {
                files.value = listOf(CoreAiInboxFile("x", "file.csv", "text/csv", 5))
            }
        val ai = FakeAi()
        val first = model(store, ai, inbox)
        review(first)
        first.apply()
        runCurrent()
        store.clear()
        val second = model(store, FakeAi(), inbox)
        runCurrent()
        assertEquals(AiPhase.COMPOSE, second.state.value.phase)
        assertNull(second.state.value.review)
        assertNull(second.state.value.applied)
        assertNull(second.state.value.passwordFile)
        assertEquals(1, second.state.value.files.size)
    }

    @Test
    fun dirtyConfigCannotSendAndEmptyRequestCannotStart() = runModel { store ->
        val ai = FakeAi()
        val vm = model(store, ai)
        runCurrent()
        vm.analyze()
        runCurrent()
        assertEquals(0, ai.starts)
        vm.configChanged { it.copy(model = "draft") }
        vm.request("send")
        vm.analyze()
        runCurrent()
        assertEquals(0, ai.starts)
    }

    @Test
    fun runFailureIsSanitizedAndUserCanExplicitlyRetryNewSession() = runModel { store ->
        val ai = FakeAi().apply { native.onRun = { throw CoreException.Network(401.toUShort()) } }
        val vm = model(store, ai)
        runCurrent()
        vm.request("x")
        vm.analyze()
        runCurrent()
        assertEquals(AiPhase.COMPOSE, vm.state.value.phase)
        assertTrue(vm.state.value.error!!.contains("authentication"))
        assertEquals(1, ai.native.destroys)
    }

    @Test
    fun nativeWakerContinuationIsAlwaysQueuedRatherThanInlineUnderItsLock() = runBlocking {
        val ready = CompletableDeferred<CancellableContinuation<Unit>>()
        val monitor = Any()
        val reentrant = AtomicBoolean(false)
        val inWaker = AtomicBoolean(false)
        val poll =
            async(CoreAiNativeDispatcher) {
                suspendCancellableCoroutine<Unit> { ready.complete(it) }
                synchronized(monitor) { reentrant.set(inWaker.get()) }
            }
        val continuation = withTimeout(5_000) { ready.await() }
        withContext(CoreAiNativeDispatcher) {
            synchronized(monitor) {
                inWaker.set(true)
                assertTrue(CoreAiNativeDispatcher.isDispatchNeeded(coroutineContext))
                continuation.resume(Unit)
                inWaker.set(false)
            }
        }
        withTimeout(5_000) { poll.await() }
        assertFalse("Rust poll must not reenter a cancel-held mutex", reentrant.get())
    }

    @Test
    fun cancelReturnsWhileNativeCleanupWaitsOffMainAndRunUnwindsFirst() = runModel { store ->
        val main = Thread.currentThread()
        val started = CountDownLatch(1)
        val cancelling = CountDownLatch(1)
        val release = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val unwound = AtomicBoolean(false)
        val offMain = AtomicBoolean(true)
        val ai =
            FakeAi().apply {
                native.onRun = {
                    offMain.set(Thread.currentThread() !== main)
                    started.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        unwound.set(true)
                    }
                }
                native.onCancel = {
                    offMain.set(offMain.get() && Thread.currentThread() !== main && unwound.get())
                    cancelling.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                native.onDestroy = {
                    offMain.set(offMain.get() && Thread.currentThread() !== main)
                    destroyed.countDown()
                }
            }
        val vm = model(store, ai, nativeDispatcher = CoreAiNativeDispatcher)
        try {
            drainUntil { !vm.state.value.loading }
            vm.request("cancel")
            vm.analyze()
            runCurrent()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            vm.cancel()
            runCurrent()
            assertEquals(AiPhase.COMPOSE, vm.state.value.phase)
            vm.apply()
            vm.keep(1u, true)
            assertEquals(0, ai.native.applies)
            // The outer Main finally dispatches cleanup; driving Main must remain responsive.
            drainUntil { cancelling.count == 0L }
            assertEquals(0, ai.native.destroys)
            assertTrue(vm.back().not())
            ai.listener!!.onStep(AiStep.Asking(99u))
            assertTrue(vm.state.value.steps.isEmpty())
            store.clear()
            runCurrent()
            assertEquals(0, ai.native.destroys)
        } finally {
            release.countDown()
        }
        assertTrue(destroyed.await(5, TimeUnit.SECONDS))
        drainUntil { ai.native.destroys == 1 }
        assertTrue(offMain.get())
        assertEquals(1, ai.native.cancels)
        assertEquals(1, ai.native.destroys)
    }

    @Test
    fun cancellationAtStartReturnBoundaryStillReleasesTheAllocatedPointerOnce() =
        runModel { store ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val ai =
                FakeAi().apply {
                    onStart = {
                        started.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        native
                    }
                }
            val vm = model(store, ai, nativeDispatcher = CoreAiNativeDispatcher)
            drainUntil { !vm.state.value.loading }
            vm.request("start")
            vm.analyze()
            runCurrent()
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                vm.cancel()
                store.clear()
                runCurrent()
            } finally {
                release.countDown()
            }
            drainUntil { ai.native.destroys == 1 }
            assertEquals(0, ai.native.runs)
            assertEquals(1, ai.native.cancels)
            assertEquals(1, ai.native.destroys)
            assertEquals(AiPhase.COMPOSE, vm.state.value.phase)
            assertNull(vm.state.value.review)
        }

    @Test
    fun clearedReviewReleasesOnlyOnBackgroundEvenAfterViewModelScopeCancellation() =
        runModel { store ->
            val main = Thread.currentThread()
            val ai = FakeAi()
            val offMain = AtomicBoolean(true)
            ai.native.onCancel = { offMain.set(Thread.currentThread() !== main) }
            ai.native.onDestroy = { offMain.set(offMain.get() && Thread.currentThread() !== main) }
            val vm = model(store, ai, nativeDispatcher = CoreAiNativeDispatcher)
            drainUntil { !vm.state.value.loading }
            vm.request("review")
            vm.analyze()
            runCurrent()
            drainUntil { vm.state.value.phase == AiPhase.REVIEW }
            assertEquals(0, ai.native.destroys)
            store.clear()
            runCurrent()
            drainUntil { ai.native.destroys == 1 }
            assertTrue(offMain.get())
            assertEquals(1, ai.native.cancels)
            assertEquals(1, ai.native.destroys)
        }

    @Test
    fun duplicatePreviewKeepsTheExactCoreTransactionAndResetsWithTheSession() = runModel { store ->
        val ai =
            FakeAi().apply {
                native.review =
                    AiReview(
                        listOf(AiProposal(2u, AiChange.AddTransaction(draft(), "duplicate-id"))),
                        null,
                        false,
                    )
            }
        val time = LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u)
        val transaction =
            Transaction(
                "duplicate-id",
                TransactionKind.EXPENSE,
                BigDecimal("12.30"),
                "GBP",
                time,
                null,
                null,
                null,
                null,
                null,
                null,
                "Cafe",
                null,
                null,
                null,
                null,
                emptyList(),
                null,
                emptyList(),
                false,
                time,
                time,
            )
        val vm =
            AiAssistantViewModel(
                ChangeBus(),
                FakeInbox(),
                nativeDispatcher = Dispatchers.Main,
                duplicates = { mapOf(transaction.id to transaction) },
                ai = { ai },
            )
        store.put("vm", vm)
        review(vm)
        assertSame(transaction, vm.state.value.duplicates["duplicate-id"])
        assertFalse(vm.state.value.decisions.getValue(2u).keep)
        vm.restart()
        runCurrent()
        assertTrue(vm.state.value.duplicates.isEmpty())
    }

    @Test
    fun settingsChangeRefreshesLookupsWithoutDiscardingTheRequest() = runModel { store ->
        val bus = ChangeBus()
        val ai = FakeAi()
        val vm =
            AiAssistantViewModel(bus, FakeInbox(), nativeDispatcher = Dispatchers.Main, ai = { ai })
        store.put("vm", vm)
        runCurrent()
        vm.request("keep my draft")
        ai.settings = ai.settings.copy(model = "changed")
        bus.onChange(ChangeSet(listOf(Table.SETTINGS), emptyList(), ChangeOrigin.LOCAL))
        runCurrent()
        assertEquals("changed", vm.state.value.settings!!.model)
        assertEquals("keep my draft", vm.state.value.request)
    }

    @Test
    fun restartCompletesPersistentInboxClearAfterImmediateNavigation() = runModel { store ->
        val inbox = FakeInbox()
        val finish = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        inbox.onClear = {
            entered.complete(Unit)
            finish.await()
        }
        val vm = model(store, inbox = inbox)
        runCurrent()
        vm.restart()
        runCurrent()
        assertTrue(entered.isCompleted)
        store.clear()
        finish.complete(Unit)
        runCurrent()
        assertEquals(1, inbox.clears)
        assertTrue(inbox.files.value.isEmpty())
    }

    private fun TestScope.drainUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            runCurrent()
            Thread.sleep(5)
        }
        runCurrent()
        assertTrue("Background native work did not reach the expected boundary", done())
    }

    private class FakeInbox : CoreAiInboxPort {
        override val files = MutableStateFlow<List<CoreAiInboxFile>>(emptyList())
        var clears = 0
        var onClear: suspend () -> Unit = {}

        override suspend fun reload() = Unit

        override suspend fun remove(id: String) {
            files.value = files.value.filterNot { it.id == id }
        }

        override suspend fun clear() {
            onClear()
            clears++
            files.value = emptyList()
        }

        override suspend fun bytes(id: String) = ByteArray(0)
    }

    private class FakeAi : AiInterface {
        var settings =
            AiSettings(
                AiApiProtocol.CLAUDE,
                "https://actual",
                "actual-model",
                AiReasoning.PROVIDER_DEFAULT,
                true,
                true,
            )
        val native = FakeSession()
        var starts = 0
        var undos = 0
        var keyCalls = 0
        var writtenKey: String? = "initial"
        var keyFailure: Throwable? = null
        val writes = mutableListOf<String>()
        var listener: AiStepListener? = null
        var modelUrl = ""
        var modelKey: String? = null
        var onModels: suspend () -> List<AiModel> = {
            listOf(AiModel("model-id", "Display name", true, AiReasoningSupport(false, emptyList())))
        }
        var onUndo: suspend () -> Unit = {}
        var onStart: suspend () -> AiSession = { native }
        var pending: AiPendingReview? = null

        override suspend fun settings() = settings

        override fun protocolDefaults(protocol: AiApiProtocol) =
            AiApiProtocolDefaults("core-default-url", "core-default-model")

        override fun reasoningLevels(protocol: AiApiProtocol, baseUrl: String) =
            listOf(
                AiReasoning.PROVIDER_DEFAULT,
                AiReasoning.OFF,
                AiReasoning.LOW,
                AiReasoning.MEDIUM,
                AiReasoning.HIGH,
            )

        override suspend fun setSettings(
            protocol: AiApiProtocol,
            baseUrl: String,
            model: String,
        ) {
            writes += "settings"
            settings = settings.copy(protocol = protocol, baseUrl = baseUrl, model = model)
        }

        override suspend fun setReasoning(reasoning: AiReasoning) {
            settings = settings.copy(reasoning = reasoning)
        }

        override suspend fun setApiKey(apiKey: String?) {
            writes += "key"
            keyCalls++
            keyFailure?.let { throw it }
            writtenKey = apiKey
        }

        override suspend fun listModels(
            protocol: AiApiProtocol,
            baseUrl: String,
            apiKey: String?,
        ): List<AiModel> {
            modelUrl = baseUrl
            modelKey = apiKey
            return onModels()
        }

        override suspend fun start(request: AiRequest, listener: AiStepListener): AiSession {
            starts++
            this.listener = listener
            return onStart()
        }

        override suspend fun pendingReview() = pending

        override suspend fun resumePending() = onStart()

        override suspend fun discardPending() {
            pending = null
        }

        override suspend fun undo(undoToken: String) {
            undos++
            assertEquals("opaque-token", undoToken)
            onUndo()
        }
    }

    private class FakeSession : AiSession(NoPointer) {
        var runs = 0
        var applies = 0
        var cancels = 0
        var destroys = 0
        var decisions = emptyList<AiDecision>()
        val history = mutableListOf<AiStep>()
        var review =
            AiReview(
                listOf(AiProposal(1u, AiChange.AddTransaction(draft(), null))),
                "Core summary",
                false,
            )
        var onRun: suspend () -> AiReview = { review }
        var onApply: suspend () -> AiApplied = {
            AiApplied("opaque-token", decisions.count { it.keep }.toUInt())
        }

        override suspend fun run(): AiReview {
            runs++
            return onRun()
        }

        override suspend fun apply(decisions: List<AiDecision>): AiApplied {
            applies++
            this.decisions = decisions
            return onApply()
        }

        var onCancel: () -> Unit = {}
        var onDestroy: () -> Unit = {}

        override fun cancel() {
            cancels++
            onCancel()
        }

        override fun steps() = history.toList()

        override fun destroy() {
            destroys++
            onDestroy()
            super.destroy()
        }
    }

    companion object {
        internal fun draft() =
            AiTransactionDraft(
                TransactionKind.EXPENSE,
                BigDecimal("12.30"),
                "GBP",
                LocalDateTime(2026, 10u, 10u, 12u, 5u, 0u, 0u),
                "Cafe",
                "category-string",
                null,
                "note",
                AiAccountRef.Existing("account-string"),
                null,
                null,
                null,
            )
    }
}
