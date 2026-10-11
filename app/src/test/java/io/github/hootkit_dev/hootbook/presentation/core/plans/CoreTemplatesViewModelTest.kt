package io.github.hootkit_dev.hootbook.presentation.core.plans

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreTemplatesViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }
    private fun keep(vm: CoreTemplatesViewModel) = vm.also { stores += ViewModelStore().also { it.put("vm", vm) } }
    private fun model(bus: ChangeBus = ChangeBus(), handler: (String, Array<out Any?>) -> Any?) = keep(
        CoreTemplatesViewModel(bus, plans = { plansProxy { method, args ->
            if (method == "templateSuggestions") emptyList<TemplateSuggestion>() else handler(method, args)
        } }, today = { PLAN_TODAY }))

    @Test fun latestRefreshWinsWhenOldQueryReturnsAfterCancellation() = runTest {
        var calls = 0
        var old: Continuation<List<QuickTemplate>>? = null
        val newer = planTemplate("new")
        val vm = model { method, args -> check(method == "templates")
            if (++calls == 1) {
                @Suppress("UNCHECKED_CAST")
                old = args.last() as Continuation<List<QuickTemplate>>; COROUTINE_SUSPENDED
            } else listOf(newer)
        }
        runCurrent(); vm.refresh(); runCurrent(); assertSame(newer, vm.state.value.templates.single())
        old!!.resume(listOf(planTemplate("old"))); runCurrent()
        assertSame(newer, vm.state.value.templates.single())
    }
    @Test fun rapidSaveBlocksBackSelectionSuggestionAndOutboundNavigation() = runTest {
        var pending: Continuation<String>? = null
        var writes = 0
        val vm = model { method, args -> when (method) {
            "templates" -> emptyList<QuickTemplate>()
            "createTemplate" -> { writes++
                @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<String>; COROUTINE_SUSPENDED }
            else -> error(method)
        } }
        runCurrent(); vm.create(); vm.updateForm(vm.state.value.form!!.copy(name = "早餐", amount = "10", prefillAmount = true))
        val draft = vm.state.value.form
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, writes); assertTrue(vm.back()); assertFalse(vm.canNavigate())
        vm.select("other"); vm.useSuggestion(planSuggestion()); vm.create()
        assertSame(draft, vm.state.value.form); assertNull(vm.state.value.selectedId)
        pending!!.resume("created"); runCurrent(); assertFalse(vm.state.value.saving); assertNull(vm.state.value.form)
        assertEquals("created", vm.state.value.selectedId)
    }
    @Test fun failedTemplateWriteKeepsDraftAndErrorForCorrection() = runTest {
        val vm = model { method, _ -> when (method) {
            "templates" -> emptyList<QuickTemplate>()
            "createTemplate" -> throw IllegalArgumentException("Core rejected template")
            else -> error(method)
        } }
        runCurrent(); vm.create(); val draft = vm.state.value.form!!.copy(name = "早餐", amount = "-1", notes = "保留")
        vm.updateForm(draft); vm.save(); runCurrent()
        assertEquals(draft, vm.state.value.form); assertNotNull(vm.state.value.error); assertFalse(vm.state.value.saving)
    }
    @Test fun editUsesUpdateAndPreservesGeneratedIdAndPrefillFlag() = runTest {
        val row = planTemplate()
        var updated: Pair<String, QuickTemplateDraft>? = null
        val vm = model { method, args -> when (method) {
            "templates" -> listOf(row)
            "updateTemplate" -> { updated = args[0] as String to args[1] as QuickTemplateDraft; Unit }
            else -> error(method)
        } }
        runCurrent(); vm.select(row.id); runCurrent(); vm.edit()
        vm.updateForm(vm.state.value.form!!.copy(prefillAmount = false)); vm.save(); runCurrent()
        assertEquals(row.id, updated!!.first); assertFalse(updated!!.second.prefillAmount)
        assertEquals(row.amount, updated!!.second.amount)
    }
    @Test fun reorderDelegatesCompleteStringIdSequenceToCore() = runTest {
        val rows = listOf(planTemplate("a"), planTemplate("b"), planTemplate("c"))
        var reordered: List<String>? = null
        val vm = model { method, args -> when (method) {
            "templates" -> rows
            "reorderTemplates" -> { @Suppress("UNCHECKED_CAST") val ids = args[0] as List<String>; reordered = ids; Unit }
            else -> error(method)
        } }
        runCurrent(); vm.move(rows[1], -1); runCurrent()
        assertEquals(listOf("b", "a", "c"), reordered)
    }
    @Test fun suggestionOnlyCreatesOnSaveAndNeverAutomaticallyDismisses() = runTest {
        val suggestions = listOf(planSuggestion())
        var creates = 0
        val vm = keep(CoreTemplatesViewModel(ChangeBus(), plans = { plansProxy { method, _ -> when (method) {
            "templates" -> emptyList<QuickTemplate>()
            "templateSuggestions" -> if (creates == 0) suggestions else emptyList<TemplateSuggestion>()
            "createTemplate" -> { creates++; "new" }
            else -> error("unexpected command $method")
        } } }, today = { PLAN_TODAY }))
        runCurrent(); vm.useSuggestion(suggestions.single()); runCurrent()
        assertEquals(0, creates); assertEquals(suggestions.single().name, vm.state.value.form!!.name)
        vm.save(); runCurrent(); assertEquals(1, creates); assertTrue(vm.state.value.suggestions.isEmpty())
    }
    @Test fun dismissalUsesOpaqueCoreSuggestionKey() = runTest {
        var dismissed: String? = null
        val vm = model { method, args -> when (method) {
            "templates" -> emptyList<QuickTemplate>()
            "dismissSuggestion" -> { dismissed = args[0] as String; Unit }
            else -> error(method)
        } }
        runCurrent(); vm.dismissSuggestion(planSuggestion()); runCurrent()
        assertEquals(planSuggestion().key, dismissed)
    }
    @Test fun deleteDelegatesTemplateOnlyAndClearsSelectedRecord() = runTest {
        var rows = listOf(planTemplate())
        var deleted: String? = null
        val vm = model { method, args -> when (method) {
            "templates" -> rows
            "deleteTemplate" -> { deleted = args[0] as String; rows = emptyList(); Unit }
            else -> error(method)
        } }
        runCurrent(); vm.select(rows.single().id); runCurrent(); vm.delete(); runCurrent()
        assertEquals("template-uuid", deleted); assertNull(vm.state.value.selectedId); assertFalse(vm.back())
    }
    @Test fun changesDoNotOverwriteUserDraftAndBackUnwindsEditorThenDetail() = runTest {
        val row = planTemplate()
        val bus = ChangeBus()
        val vm = model(bus) { method, _ -> check(method == "templates"); listOf(row) }
        runCurrent(); vm.select(row.id); runCurrent(); vm.edit()
        val draft = vm.state.value.form!!.copy(notes = "未保存")
        vm.updateForm(draft); bus.onChange(ChangeSet(listOf(Table.QUICK_TEMPLATES), emptyList(), ChangeOrigin.SYNC)); runCurrent()
        assertSame(draft, vm.state.value.form); assertTrue(vm.back()); assertEquals(row.id, vm.state.value.selectedId)
        assertTrue(vm.back()); runCurrent(); assertFalse(vm.back())
    }
    @Test fun productionTemplateServiceHandlesDestroyedOnQuerySuccessAndCommandFailure() = runTest {
        val root = FakePlansCore()
        val vm = keep(CoreTemplatesViewModel(CoreSession(backgroundScope) { root }, ChangeBus()))
        runCurrent(); root.assertReleased()
        vm.create(); vm.updateForm(vm.state.value.form!!.copy(name = "早餐", amount = "10")); root.failWrite = true
        vm.save(); runCurrent(); assertNotNull(vm.state.value.error); assertNotNull(vm.state.value.form)
        root.assertReleased()
        root.failWrite = false; vm.save(); runCurrent(); assertNull(vm.state.value.form); root.assertReleased()
    }
}
