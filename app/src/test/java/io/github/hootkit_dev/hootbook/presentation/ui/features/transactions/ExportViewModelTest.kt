package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import android.app.Application
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
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
class ExportViewModelTest : CoreViewModelTest() {
    @Test
    fun exportPagesEverythingPreventsDoubleStartAndReportsFailure() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val offsets = java.util.Collections.synchronizedList(mutableListOf<UInt>())
        core.onList = { _, p ->
            offsets += p.offset
            TransactionPage(
                (p.offset.toInt() until minOf(201, p.offset.toInt() + p.limit.toInt())).map {
                    core.transaction.copy(id = "$it")
                },
                201u,
            )
        }
        val vm = keep(ExportViewModel(session(core), changes, files, context))
        assertEquals(ExportState.Ready, vm.state.value)
        vm.exportTransactions(emptyTransactionFilter())
        vm.exportTransactions(emptyTransactionFilter())
        awaitState { vm.state.value !is ExportState.Exporting }
        assertTrue(vm.state.value.toString(), vm.state.value is ExportState.Success)
        assertEquals(listOf(0u, 200u), offsets.toList())
        vm.reset()
        core.onList = { _, _ -> error("export failed") }
        vm.exportTransactions(emptyTransactionFilter())
        awaitState { vm.state.value is ExportState.Error }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, (vm.state.value as ExportState.Error).message)
    }

    @Test
    fun exportPreviewTracksLatestFilterAndRecoversAfterChange() = runTest {
        val core = FakeLedgerCore()
        val changes = ChangeBus()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        core.onList = { filter, _ ->
            if (filter.text == "slow")
                withContext(NonCancellable) {
                    started.complete(Unit)
                    gate.await()
                }
            TransactionPage(listOf(core.transaction.copy(merchant = filter.text.orEmpty())), 1u)
        }
        val vm = keep(ExportViewModel(session(core), changes, files, context))
        vm.observeFilter(emptyTransactionFilter().copy(text = "slow"))
        awaitState { started.isCompleted }
        vm.observeFilter(emptyTransactionFilter().copy(text = "latest"))
        awaitState { vm.preview.value.newest?.merchant == "latest" }
        gate.complete(Unit)
        awaitState { core.openedHandles == core.closedHandles }
        assertEquals("latest", vm.preview.value.oldest!!.merchant)
        core.onList = { _, _ -> error("preview failed") }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.preview.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        core.onList = { _, _ -> TransactionPage(listOf(core.transaction), 1u) }
        changes.changed(Table.TRANSACTIONS)
        awaitState { vm.preview.value.newest?.id == "tx" }
        assertNull(vm.preview.value.error)
    }
}
