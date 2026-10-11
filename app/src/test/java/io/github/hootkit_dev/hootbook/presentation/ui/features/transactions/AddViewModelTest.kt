package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AddViewModel
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AddViewModelTest : CoreViewModelTest() {
    @Test
    fun inlinePersonUsesRgbPaletteKeepsFailureAndClosesOnlyAfterSuccess() = runTest {
        val core = FakeLedgerCore()
        val vm = keep(AddViewModel(session(core), ChangeBus(), SavedStateHandle(), files, preferences))
        awaitState { !vm.uiState.value.loading }
        val gate = CompletableDeferred<Unit>()
        core.onCreatePerson = {
            gate.await()
            throw CoreException.Invalid("color", InvalidReason.BAD_FORMAT)
        }
        var closed = 0
        val color = io.github.hootkit_dev.hootbook.presentation.ui.components.AVATAR_COLORS.first()
        assertTrue(color.matches(Regex("#[0-9a-fA-F]{6}")))
        vm.addPerson("Alice", null, null, color, null, PersonRelation.FRIEND) { closed++ }
        vm.addPerson("Alice", null, null, color, null, PersonRelation.FRIEND) { closed++ }
        awaitState { core.createPersonCalls == 1 }
        assertEquals(0, closed)
        gate.complete(Unit)
        awaitState { !vm.uiState.value.writing }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.error)
        assertNull(vm.uiState.value.personId)
        assertEquals(0, closed)
        core.onCreatePerson = { "alice" }
        vm.addPerson("Alice", "123", "Friend", color, null, PersonRelation.FRIEND) { closed++ }
        awaitState { !vm.uiState.value.writing }
        assertEquals(1, closed)
        assertEquals("alice", vm.uiState.value.personId)
        assertEquals(color, core.createdPerson!!.color)
        assertEquals(PersonRelation.FRIEND, core.createdPerson!!.relation)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun addInitialDefaultsPaymentPrefillAndSingleLedgerSubmission() = runTest {
        val core = FakeLedgerCore()
        val vm =
            keep(AddViewModel(session(core), ChangeBus(), SavedStateHandle(), files, preferences))
        vm.initialize(0, core.subscription.id, null, null)
        awaitState { !vm.uiState.value.loadingSource && !vm.uiState.value.loading }
        assertEquals("1500", vm.uiState.value.amount)
        assertEquals("JPY", vm.uiState.value.transaction.currency)
        assertNull(vm.uiState.value.transaction.accountId)
        var closed = 0
        vm.saveTransaction { closed++ }
        vm.saveTransaction { closed++ }
        awaitState { vm.uiState.value.saved }
        assertEquals(1, closed)
        assertEquals(1, core.addCalls)
        assertEquals(core.subscription.id, core.lastDraft!!.subscriptionId)
        assertEquals(0, core.paidCalls)
    }

    @Test
    fun addDraftSurvivesRecreationAndLateTemplateCannotReplaceTyping() = runTest {
        val core = FakeLedgerCore()
        val saved = SavedStateHandle()
        val vm = keep(AddViewModel(session(core), ChangeBus(), saved, files, preferences))
        vm.initialize(0, null, null, null)
        awaitState { !vm.uiState.value.loadingSource }
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        core.onTemplatePrefill = {
            withContext(NonCancellable) {
                started.complete(Unit)
                gate.await()
            }
            TemplatePrefill(TransactionKind.EXPENSE, "stale", null, null, null, null, null, null)
        }
        vm.applyQuickTemplateById("old")
        awaitState { started.isCompleted }
        vm.updateTransactionMerchant("typed")
        vm.updateTransactionAmount("12.")
        gate.complete(Unit)
        awaitState { !vm.uiState.value.loadingSource }
        assertEquals("typed", vm.uiState.value.transaction.merchant)
        val restored = keep(AddViewModel(session(core), ChangeBus(), saved, files, preferences))
        restored.initialize(0, null, "TRANSFER", null)
        assertEquals("12.", restored.uiState.value.amount)
        assertEquals("typed", restored.uiState.value.transaction.merchant)
        assertEquals(TransactionKind.EXPENSE, restored.uiState.value.transaction.kind)
    }

    @Test
    fun addRecoversLookupsAfterChangeAndRetryOfFollowupCannotDuplicateTransaction() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        core.onTemplates = { error("lookup failed") }
        val vm = keep(AddViewModel(session(core), changes, SavedStateHandle(), files, preferences))
        awaitState { vm.uiState.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        core.onTemplates = { emptyList() }
        changes.changed(Table.QUICK_TEMPLATES)
        awaitState { !vm.uiState.value.loading }
        vm.updateTransactionAmount("25")
        vm.updateSaveAsQuickTemplate(true)
        core.onCreateTemplate = { error("template failed") }
        vm.saveTransaction {}
        vm.saveTransaction {}
        awaitState { vm.uiState.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        assertEquals(1, core.addCalls)
        assertEquals("tx", vm.uiState.value.createdTransactionId)
        core.onCreateTemplate = { "new-template" }
        vm.saveTransaction {}
        awaitState { vm.uiState.value.saved }
        assertEquals(1, core.addCalls)
    }

    @Test
    fun subscriptionCreateAndEditPassCoreDraftAndGuardSubmission() = runTest {
        val core = FakeLedgerCore()
        val vm =
            keep(AddViewModel(session(core), ChangeBus(), SavedStateHandle(), files, preferences))
        vm.initialize(1, core.subscription.id, null, null)
        awaitState { !vm.uiState.value.loadingSource }
        vm.updateSubscriptionAmount("42")
        vm.updateSubscriptionBillingCycle("Annual")
        vm.saveSubscription {}
        vm.saveSubscription {}
        awaitState { vm.uiState.value.saved }
        assertEquals(BigDecimal("42"), core.updatedSubscription!!.amount.amount)
        assertEquals(Cycle(1u, CycleUnit.YEAR), core.updatedSubscription!!.cycle)
        assertNull(core.updatedSubscription!!.nextDue)
        assertEquals(0, core.addCalls)
    }

    @Test
    fun transferSuggestionUsesCoreResultAndNewSubscriptionUsesPlansOnly() = runTest {
        val core = FakeLedgerCore()
        core.onConvert = { _, _, _ -> BigDecimal("718.39") }
        val vm =
            keep(AddViewModel(session(core), ChangeBus(), SavedStateHandle(), files, preferences))
        vm.initialize(0, null, "TRANSFER", null)
        awaitState { !vm.uiState.value.loadingSource }
        vm.updateTransactionAmount("100")
        vm.updateTransactionCurrency("USD")
        vm.updateTransactionTargetCurrency("CNY")
        awaitState { vm.uiState.value.targetAmount == "718.39" }
        assertEquals(BigDecimal("718.39"), vm.uiState.value.transaction.toAmount)
        vm.updateSubscriptionService("Service")
        vm.updateSubscriptionAmount("9.9")
        vm.saveSubscription {}
        vm.saveSubscription {}
        awaitState { vm.uiState.value.saved }
        assertEquals(1, core.createSubscriptionCalls)
        assertEquals(0, core.addCalls)
    }
}
