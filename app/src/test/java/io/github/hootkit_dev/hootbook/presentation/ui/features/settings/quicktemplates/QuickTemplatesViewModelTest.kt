package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class QuickTemplatesViewModelTest : CoreViewModelTest() {
    @Test fun loadsRefreshesRecoversAndLastDragWinsDuringPendingReorder() = runTest {
        val core = FakePlansCore(); val bus = ChangeBus()
        val vm = keep(QuickTemplatesViewModel(CoreSession(backgroundScope) { core }, bus))
        awaitState { !vm.uiState.value.loading }; assertSame(core.rows, vm.uiState.value.templates)
        core.onTemplates = { error("offline") }; bus.changed(Table.QUICK_TEMPLATES)
        awaitState { vm.uiState.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }; core.onTemplates = { core.rows }; bus.changed(Table.QUICK_TEMPLATES)
        awaitState { vm.uiState.value.error == null }
        val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await() }
        vm.reorder(core.rows.reversed()); awaitState { core.reorderWrites.size == 1 }
        vm.reorder(listOf(core.rows[1], core.rows[0], core.rows[2])); vm.reorder(core.rows)
        gate.complete(Unit); awaitState { core.reorderWrites.size == 2 && !vm.uiState.value.saving }
        assertEquals(core.rows.map { it.id }, core.reorderWrites.last())
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun editsPreserveCoreReferencesAndGuardDoubleSubmitAndRetryErrors() = runTest {
        val core = FakePlansCore(); val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await(); error("bad amount") }
        val vm = keep(QuickTemplatesViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        awaitState { !vm.uiState.value.loading }; var successes = 0
        vm.update(core.template, "New", BigDecimal("34"), true) { successes++ }
        vm.update(core.template, "New", BigDecimal("34"), true) { successes++ }
        awaitState { core.commandCalls == 1 }; gate.complete(Unit); awaitState { !vm.uiState.value.saving }
        assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.error); assertEquals(0, successes)
        assertEquals(core.template.accountId, core.templateDraft!!.accountId); assertEquals(core.template.merchant, core.templateDraft!!.merchant)
        core.onCommand = {}; vm.update(core.template, "New", BigDecimal("34"), true) { successes++ }
        awaitState { !vm.uiState.value.saving }; assertEquals(1, successes)
        vm.delete(core.template.id); awaitState { !vm.uiState.value.saving }; assertEquals(3, core.commandCalls)
        assertEquals(core.opened.get(), core.closed.get())
    }
}
