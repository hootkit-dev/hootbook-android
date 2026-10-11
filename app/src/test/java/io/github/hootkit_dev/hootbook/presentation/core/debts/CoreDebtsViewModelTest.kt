package io.github.hootkit_dev.hootbook.presentation.core.debts

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreDebtsViewModelTest {
    private val at = LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u)
    private fun person(id: String = "person-uuid") = Person(id, "人员", "123", "备注", "#001122", "avatar.png", PersonRelation.FRIEND,
        false, PersonTotals("CNY", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, false, true, emptyList()), at)
    private fun debt() = DebtEntry("debt-uuid", "person-uuid", DebtKind.LENT, Money(BigDecimal.TEN, "CNY"), at, null, "借出", "tx-uuid", "account-uuid", listOf("file.png"))
    private fun summary() = DebtSummary("CNY", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 1u, 1u, 0u, 0u, true)
    private fun api(handler: (String, Array<out Any?>) -> Any?): PlansInterface = Proxy.newProxyInstance(
        PlansInterface::class.java.classLoader, arrayOf(PlansInterface::class.java, Disposable::class.java),
    ) { _, method, arguments ->
        try {
            handler(method.name.substringBefore('-'), arguments ?: emptyArray())
        } catch (failure: CoreException) {
            // Generated suspend APIs declare no Java throws. Throwing a checked CoreException
            // from InvocationHandler would wrap it in UndeclaredThrowableException.
            // Resume the suspend completion with the real typed failure, as generated code does.
            @Suppress("UNCHECKED_CAST")
            val completion = arguments?.lastOrNull() as? Continuation<Any?> ?: throw failure
            completion.resumeWith(Result.failure(failure))
            COROUTINE_SUSPENDED
        }
    } as PlansInterface
    private fun query(method: String): Any? = when (method) {
        "people" -> listOf(person()); "person" -> person(); "debts" -> listOf(debt()); "debtSummary" -> summary(); "destroy" -> Unit
        else -> error(method)
    }
    private suspend fun TestScope.withModel(handler: (String, Array<out Any?>) -> Any? = { method, _ -> query(method) },
        test: suspend (CoreDebtsViewModel, ChangeBus) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val changes = ChangeBus()
            val model = CoreDebtsViewModel(changes, { api(handler) })
            store.put("test", model); runCurrent(); test(model, changes)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun initialQueryRelevantChangesAndResumeRefresh() = runTest {
        var calls = 0; var releases = 0
        withModel({ method, _ -> if (method == "people") calls++; if (method == "destroy") releases++; query(method) }) { model, changes ->
            assertEquals(1, calls); assertFalse(model.state.value.loading)
            changes.onChange(ChangeSet(listOf(Table.BUDGETS), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(1, calls)
            DEBT_TABLES.forEach { changes.onChange(ChangeSet(listOf(it), emptyList(), ChangeOrigin.LOCAL)); runCurrent() }
            assertEquals(8, calls)
            model.refresh(); runCurrent(); assertEquals(9, calls)
            assertEquals(calls, releases)
        }
    }
    @Test fun stalePersonQueryCannotReplaceNewSelection() = runTest {
        var stale: Continuation<Person>? = null
        withModel({ method, args -> when (method) {
            "person" -> if (args[0] == "old-uuid") {
                @Suppress("UNCHECKED_CAST")
                stale = args.last() as Continuation<Person>; COROUTINE_SUSPENDED
            } else person(args[0] as String)
            else -> query(method)
        } }) { model, _ ->
            model.select("old-uuid"); runCurrent()
            model.select("new-uuid"); runCurrent()
            assertEquals("new-uuid", model.state.value.person!!.id)
            stale!!.resume(person("old-uuid")); runCurrent()
            assertEquals("new-uuid", model.state.value.person!!.id)
        }
    }
    @Test fun savingGuardsDoubleSubmitBackFilterDraftAndTargetChanges() = runTest {
        var writes = 0; var pending: Continuation<String>? = null
        withModel({ method, args -> when (method) {
            "addDebt" -> { writes++; @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<String>; COROUTINE_SUSPENDED }
            else -> query(method)
        } }) { model, changes ->
            model.select("person-uuid"); runCurrent(); model.editDebt()
            model.debtChanged(model.state.value.debtForm!!.copy(amount = "10", title = "借款"))
            model.saveDebt(); model.saveDebt(); runCurrent()
            assertEquals(1, writes); assertTrue(model.state.value.saving)
            assertTrue(model.back()); model.select("another-uuid"); model.includeArchived(true)
            model.dismissEditor(); model.editPerson(); model.editDebt(debt())
            assertNull(model.transactionTarget(debt()))
            assertEquals("person-uuid", model.state.value.selectedId)
            assertFalse(model.state.value.includeArchived); assertNotNull(model.state.value.debtForm)
            assertTrue(model.state.value.navigationBlocked)
            changes.onChange(ChangeSet(listOf(Table.DEBTS), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
            pending!!.resume("new-debt-uuid"); runCurrent()
            assertFalse(model.state.value.saving); assertNull(model.state.value.debtForm)
            assertEquals(1L, model.state.value.commandRevision)
        }
    }
    @Test fun failedCommandPreservesDraftReleasesHandleAndCanRetry() = runTest {
        var releases = 0; var writes = 0
        withModel({ method, _ -> when (method) {
            "createPerson" -> if (++writes == 1) throw CoreException.Invalid("name", InvalidReason.EMPTY) else "new-person-uuid"
            "destroy" -> { releases++; Unit }
            else -> query(method)
        } }) { model, _ ->
            model.editPerson(); model.personChanged(CorePersonForm(name = "保留", avatar = "existing.png"))
            model.savePerson(); runCurrent()
            assertFalse(model.state.value.saving); assertNotNull(model.state.value.error)
            assertEquals("existing.png", model.state.value.personForm!!.avatar)
            assertEquals(0L, model.state.value.commandRevision)
            assertEquals(3, releases) // initial query, failed command, retryable refresh
            model.savePerson(); runCurrent(); assertNull(model.state.value.personForm)
            assertEquals(2, writes); assertEquals(1L, model.state.value.commandRevision)
        }
    }
    @Test fun backClosesDraftThenDetailsThenHost() = runTest {
        withModel { model, _ ->
            model.select("person-uuid"); runCurrent(); model.editPerson(model.state.value.person)
            assertTrue(model.back()); assertNull(model.state.value.personForm)
            assertEquals("person-uuid", model.state.value.selectedId)
            assertTrue(model.back()); assertNull(model.state.value.selectedId)
            assertFalse(model.back())
        }
    }
    @Test fun settlementDoubleTapAndFailureDoNotLoseExplicitDraft() = runTest {
        var writes = 0; var pending: Continuation<String>? = null; var submitted: SettleDraft? = null
        withModel({ method, args -> when (method) {
            "settle" -> {
                writes++; submitted = args[1] as SettleDraft
                @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<String>; COROUTINE_SUSPENDED
            }
            else -> query(method)
        } }) { model, _ ->
            model.select("person-uuid"); runCurrent(); model.settle("结清")
            model.saveSettlement(); model.saveSettlement(); runCurrent()
            assertEquals(1, writes); assertNull(submitted!!.amount)
            assertTrue(model.back()); model.settlementChanged(model.state.value.settlement!!.copy(title = "ignored"))
            assertEquals("结清", model.state.value.settlement!!.title)
            pending!!.resume("repayment-uuid"); runCurrent()
            assertNull(model.state.value.settlement); assertEquals(1, writes)
        }
    }
    @Test fun explicitPartialSettlementPassedToCoreUnchanged() = runTest {
        var submitted: SettleDraft? = null
        withModel({ method, args -> when (method) {
            "settle" -> { submitted = args[1] as SettleDraft; "repaid-uuid" }
            else -> query(method)
        } }) { model, _ ->
            model.select("person-uuid"); runCurrent(); model.settle("还款")
            model.settlementChanged(model.state.value.settlement!!.copy(fullAmount = false, amount = "2.30", currency = "USD", accountId = "account-uuid"))
            model.saveSettlement(); runCurrent()
            assertEquals(Money(BigDecimal("2.30"), "USD"), submitted!!.amount)
            assertEquals("account-uuid", submitted!!.accountId)
        }
    }
    @Test fun debtUpdateRetainsExistingLinksAndFiles() = runTest {
        var submitted: DebtDraft? = null; var id: String? = null
        withModel({ method, args -> when (method) {
            "updateDebt" -> { id = args[0] as String; submitted = args[1] as DebtDraft; Unit }
            else -> query(method)
        } }) { model, _ ->
            model.select("person-uuid"); runCurrent(); model.editDebt(debt())
            model.debtChanged(model.state.value.debtForm!!.copy(title = "改名")); model.saveDebt(); runCurrent()
            assertEquals("debt-uuid", id); assertEquals("account-uuid", submitted!!.accountId)
            assertEquals(listOf("file.png"), submitted!!.attachments)
            assertEquals("person-uuid", submitted!!.personId)
        }
    }
    @Test fun deleteAndUnlinkHaveDifferentCoreCommandsAndReturnStringNavigation() = runTest {
        val commands = mutableListOf<String>()
        withModel({ method, _ -> when (method) {
            "deleteDebt", "unlinkDebt", "deletePerson", "setPersonArchived" -> { commands += method; Unit }
            else -> query(method)
        } }) { model, _ ->
            assertEquals("tx-uuid", model.transactionTarget(debt()))
            model.deleteDebt(debt()); runCurrent(); model.unlinkDebt(debt()); runCurrent()
            model.archive(person()); runCurrent(); model.select("person-uuid"); runCurrent()
            model.deletePerson(person()); runCurrent()
            assertEquals(listOf("deleteDebt", "unlinkDebt", "setPersonArchived", "deletePerson"), commands)
            assertNull(model.state.value.selectedId)
        }
    }
    @Test fun deletedPersonNeverLeavesStaleDetailVisible() = runTest {
        withModel({ method, args -> when (method) {
            "person" -> throw CoreException.NotFound(Entity.PERSON, args[0] as String)
            "debts" -> emptyList<DebtEntry>()
            else -> query(method)
        } }) { model, _ ->
            model.select("deleted-uuid"); runCurrent()
            assertNull(model.state.value.selectedId); assertNull(model.state.value.person)
            assertTrue(model.state.value.debts.isEmpty()); assertNotNull(model.state.value.error)
            assertEquals("记录已删除，请刷新", model.state.value.error)
            assertFalse(model.state.value.loading)
        }
    }
    @Test fun orphanDebtRemainsVisibleAndCanBeReassignedWithoutDiscard() = runTest {
        var submitted: DebtDraft? = null
        withModel({ method, args -> when (method) {
            "person" -> throw CoreException.NotFound(Entity.PERSON, args[0] as String)
            "updateDebt" -> { submitted = args[1] as DebtDraft; Unit }
            else -> query(method)
        } }) { model, _ ->
            model.select("deleted-person-uuid"); runCurrent()
            assertEquals("deleted-person-uuid", model.state.value.selectedId)
            assertNull(model.state.value.person); assertEquals(listOf(debt()), model.state.value.debts)
            assertNull(model.state.value.error); assertFalse(model.state.value.loading)
            model.editDebt(debt()); model.debtChanged(model.state.value.debtForm!!.copy(personId = "surviving-person-uuid"))
            model.saveDebt(); runCurrent()
            assertEquals("surviving-person-uuid", submitted!!.personId)
            assertEquals(listOf("file.png"), submitted!!.attachments)
        }
    }
    @Test fun initializeKeepsStringIdAndDoesNotResetReentry() = runTest {
        withModel { model, _ ->
            model.initialize("person-uuid"); runCurrent()
            assertEquals("person-uuid", model.state.value.selectedId)
            model.select(null); model.initialize("person-uuid"); runCurrent()
            assertNull(model.state.value.selectedId)
        }
    }
}
