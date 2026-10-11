package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class CoreTransactionFullViewModelTest {
    private fun fixture(block: suspend TestScope.(FakeLedgerCore, CoreTransactionsViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val core = FakeLedgerCore()
            val model = CoreTransactionsViewModel(CoreSession(backgroundScope) { core }, ChangeBus())
            store.put("model", model); block(core, model)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun selectingAllMatchingFetchesEveryCorePageWithoutChangingFilters() = fixture { core, model ->
        val rows = (1..451).map { core.transaction.copy(id = "id/$it") }
        val offsets = mutableListOf<UInt>()
        core.onList = { filter, request ->
            assertEquals(listOf("account"), filter.accountIds)
            offsets += request.offset
            TransactionPage(rows.drop(request.offset.toInt()).take(request.limit.toInt()), rows.size.toUInt())
        }
        model.initialize("account", null); runCurrent()
        model.selectAllMatching(); model.selectAllMatching(); runCurrent()
        assertEquals(rows.map { it.id }.toSet(), model.state.value.selectedIds)
        assertTrue(offsets.containsAll(listOf(0u, 200u, 400u)))
        core.assertHandlesReleased()
    }
    @Test fun oldNonCancellableDetailNeverRefillsBehindANewDraft() = fixture { core, model ->
        val old = CompletableDeferred<Unit>()
        core.onGet = { id -> withContext(NonCancellable) { old.await(); core.transaction.copy(id = id) } }
        model.initialize(null, "old/detail"); runCurrent()
        model.newTransaction(); runCurrent()
        assertNotNull(model.state.value.form)
        assertNull(model.state.value.detailId)
        assertNull(model.state.value.detail)
        old.complete(Unit); runCurrent()
        assertNull(model.state.value.detail)
        assertNull(model.state.value.detailId)
        assertNotNull(model.state.value.form)
        core.assertHandlesReleased()
    }
    @Test fun failedWriteMessageSurvivesSuccessfulCacheRefreshCanDismissAndRetryWithoutLosingDraft() = fixture { core, model ->
        runCurrent(); model.newTransaction()
        model.updateForm(model.state.value.form!!.copy(amount = "12", categoryId = "category"))
        core.onAdd = { throw IllegalStateException("private response credential") }
        model.save(); runCurrent()
        assertNotNull(model.state.value.commandFailure)
        assertNotNull(model.state.value.error)
        assertNull(model.state.value.queryFailure)
        assertFalse(model.state.value.error!!.contains("private response"))
        assertEquals("12", model.state.value.form!!.amount)
        model.refresh(); runCurrent()
        assertNotNull(model.state.value.commandFailure)
        model.clearError(); model.refresh(); runCurrent()
        assertNull(model.state.value.commandFailure)
        assertNull(model.state.value.error)
        core.onAdd = { "created" }; model.save(); runCurrent()
        assertNull(model.state.value.form)
        assertNull(model.state.value.error)
        core.assertHandlesReleased()
    }
    @Test fun bulkPatchPassesSelectedStringIdsAndClearsSelectionOnlyAfterSuccess() = fixture { core, model ->
        runCurrent(); model.toggleSelected("id/a"); model.toggleSelected("id/b")
        model.bulkUpdate(CoreTransactionBulkForm(amount = "12.340", replaceNotes = true, notes = "")); runCurrent()
        assertEquals(listOf("id/a", "id/b"), core.lastBulk!!.first)
        assertEquals(BigDecimal("12.340"), core.lastBulk!!.second.amount)
        assertEquals("", core.lastBulk!!.second.notes)
        assertTrue(model.state.value.selectedIds.isEmpty())
        core.assertHandlesReleased()
    }
    @Test fun selectedDeleteReturnsCoreUndoTokenAndDoesNotRecalculateBalances() = fixture { core, model ->
        runCurrent(); model.toggleSelected("id/a"); model.toggleSelected("id/b")
        model.deleteSelected(); model.deleteSelected(); runCurrent()
        assertEquals(listOf("id/a", "id/b"), core.deletedIds)
        assertEquals("opaque-core-token", model.state.value.undoToken)
        model.undo(); runCurrent()
        assertEquals("opaque-core-token", core.undoneToken)
        core.assertHandlesReleased()
    }
    @Test fun purgeAllUsesTheEntireCoreRecycleBinNotCurrentPageOrFilter() = fixture { core, model ->
        val rows = (1..251).map { core.transaction.copy(id = "recycled-$it", deleted = true) }
        core.onList = { filter, request ->
            if (filter.deleted == DeletedFilter.ONLY) TransactionPage(rows.drop(request.offset.toInt()).take(request.limit.toInt()), rows.size.toUInt())
            else TransactionPage(emptyList(), 0u)
        }
        runCurrent(); model.applyQuery(CoreTransactionQuery(text = "current filter")); runCurrent()
        model.purgeSelected(allRecycleBin = true); runCurrent()
        assertEquals(rows.map { it.id }, core.purgedIds)
        core.assertHandlesReleased()
    }
    @Test fun duplicateCandidateIdsComeFromCoreAndRemainSelectableForBulkCategoryUpdate() = fixture { core, model ->
        core.duplicateIds = listOf("candidate-a", "candidate-b")
        runCurrent(); model.newTransaction(); model.updateForm(model.state.value.form!!.copy(amount = "35", categoryId = "category"))
        model.checkDuplicates(); runCurrent()
        assertEquals(2, model.state.value.duplicateCandidates.size)
        model.selectDuplicateCandidates()
        // Candidate records come from Core get(), not reconstructed from the search amount.
        assertEquals(setOf("candidate-a", "candidate-b"), model.state.value.selectedIds)
        core.assertHandlesReleased()
    }
    @Test fun repeatExpenseCallsRealCoreSubscriptionFromTransactionOnce() = fixture { core, model ->
        model.initialize(null, "tx"); runCurrent()
        model.createSubscription(Cycle(2u, CycleUnit.WEEK), LocalDate(2027, 1u, 1u))
        model.createSubscription(Cycle(2u, CycleUnit.WEEK), null); runCurrent()
        assertEquals(1, core.fromTransactionCalls)
        assertEquals(Triple("tx", Cycle(2u, CycleUnit.WEEK), LocalDate(2027, 1u, 1u)), core.createdSubscriptionFrom)
        assertEquals("subscription-created", model.state.value.createdSubscriptionId)
        assertEquals(0, core.addCalls)
        core.assertHandlesReleased()
    }
    @Test fun savedEditingDraftWinsOverReplayedTemplateAfterProcessRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val core = FakeLedgerCore()
            val draft = CoreTransactionForm(editingId = "edit-existing", amount = "12.30", notes = "unsaved")
            val state = SavedStateHandle(mapOf("transaction_draft" to saveTransactionForm(draft)))
            val model = CoreTransactionsViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), state)
            store.put("model", model); model.initialize(null, null, templateId = "template"); runCurrent()
            assertEquals(draft, model.state.value.form)
            assertEquals(0, core.prefillCalls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun inputCurrencyPreferenceDoesNotChangeCoreMainCurrencyOrOverrideUserEdits() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val prefs = CoreUiPreferencesStore(ApplicationProvider.getApplicationContext())
            prefs.update { it.copy(defaultEntryEnabled = true, defaultEntryCurrency = "GBP") }
            val core = FakeLedgerCore(); core.mainCurrency = "CNY"
            val model = CoreTransactionsViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), uiPreferences = prefs)
            store.put("model", model); runCurrent(); model.newTransaction(); runCurrent()
            assertEquals("GBP", model.state.value.form!!.currency)
            assertEquals("CNY", core.mainCurrency)
            model.newTransaction(); model.updateForm(model.state.value.form!!.copy(amount = "123", currency = "USD")); runCurrent()
            assertEquals("USD", model.state.value.form!!.currency)
            assertEquals("123", model.state.value.form!!.amount)
            core.assertHandlesReleased()
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
