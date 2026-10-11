package io.github.hootkit_dev.hootbook.presentation.core.investments

import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreInvestmentsViewModelTest {
    private fun fixture(block: suspend TestScope.(TestBroker, CoreInvestmentsViewModel, ChangeBus) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = TestBroker()
            val bus = ChangeBus()
            val model = CoreInvestmentsViewModel(bus, { api })
            store.put("fixture", model)
            block(api, model, bus)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun initialAndResumeReadOnlyCacheAndNeverFetchReport() = fixture { api, model, _ ->
        runCurrent(); model.reload(); runCurrent()
        assertEquals(2, api.reads)
        assertEquals(0, api.refreshCalls)
        assertEquals(0, api.connectCalls)
        assertFalse(model.state.value.loading)
    }
    @Test fun onlyRelevantTableRevisionsReloadBrokerData() = fixture { api, _, bus ->
        runCurrent()
        bus.onChange(ChangeSet(listOf(Table.PEOPLE), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        assertEquals(1, api.reads)
        listOf(Table.BROKERAGE, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
            Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS).forEach {
            bus.onChange(ChangeSet(listOf(it), emptyList(), ChangeOrigin.LOCAL)); runCurrent()
        }
        assertEquals(8, api.reads)
        assertEquals(0, api.refreshCalls)
    }
    @Test fun cancelledOldCacheResponseNeverOverwritesNewerSnapshot() = fixture { api, model, _ ->
        val old = CompletableDeferred<Unit>()
        api.read = {
            if (api.reads == 1) withContext(NonCancellable) { old.await(); listOf(investmentConnection(total = "100")) }
            else listOf(investmentConnection(total = "200"))
        }
        runCurrent(); model.reload(); runCurrent()
        assertEquals(BigDecimal("200"), model.state.value.connections.single().accounts.single().total!!.amount)
        old.complete(Unit); runCurrent()
        assertEquals(BigDecimal("200"), model.state.value.connections.single().accounts.single().total!!.amount)
    }
    @Test fun repeatedRefreshAndCacheReloadDoNotDuplicateOrCancelWrite() = fixture { api, model, _ ->
        runCurrent()
        val done = CompletableDeferred<Unit>()
        api.fetch = { done.await(); investmentConnection() }
        model.refresh(1); model.refresh(1); runCurrent()
        assertEquals(1, api.refreshCalls)
        model.reload(); model.selectAccount(BrokerAccountRef(1, "U1")); runCurrent()
        assertTrue(model.state.value.busy)
        assertTrue(model.back())
        assertNull(model.state.value.selected)
        done.complete(Unit); runCurrent()
        assertFalse(model.state.value.busy)
        assertEquals(1L, model.state.value.commandRevision)
    }
    @Test fun cancellationBeforeDispatchClearsBusyAndDoesNotFetch() = fixture { api, model, _ ->
        runCurrent(); model.refresh(1); model.cancelOperation(); runCurrent()
        assertEquals(0, api.refreshCalls)
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.cancellationNotice)
        assertFalse(model.state.value.cancelling)
    }
    @Test fun nativeOperationIgnoringCancellationBlocksNewWritesUntilSettled() = fixture { api, model, _ ->
        runCurrent()
        val done = CompletableDeferred<Unit>()
        api.fetch = { withContext(NonCancellable) { done.await(); investmentConnection() } }
        model.refresh(1); runCurrent(); model.cancelOperation(); runCurrent()
        assertTrue(model.state.value.busy)
        assertTrue(model.state.value.cancelling)
        model.disconnect(1); model.refresh(1); runCurrent()
        assertEquals(0, api.disconnectCalls)
        assertEquals(1, api.refreshCalls)
        done.complete(Unit); runCurrent()
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.cancellationNotice)
        assertEquals(0L, model.state.value.commandRevision)
    }
    @Test fun refreshFailureRetainsCacheAndExposesOnlyTypedFailure() = fixture { api, model, _ ->
        runCurrent()
        api.fetch = { throw CoreException.Brokerage(BrokerageErrorKind.EXPIRED_CREDENTIALS) }
        model.refresh(1); runCurrent()
        assertEquals(investmentConnection(), model.state.value.connections.single())
        assertEquals(BrokerageErrorKind.EXPIRED_CREDENTIALS, model.state.value.error!!.kind)
        assertEquals(BrokerageErrorKind.EXPIRED_CREDENTIALS, model.state.value.connectionErrors[1]!!.kind)
        assertFalse(model.state.value.busy)
    }
    @Test fun refreshAllContinuesAfterOneFailureAndNeverRunsDuplicates() = fixture { api, model, _ ->
        api.connections = listOf(investmentConnection(1), investmentConnection(2))
        runCurrent()
        val fetched = mutableListOf<Long>()
        api.fetch = { id -> fetched += id; if (id == 1L) throw CoreException.Brokerage(BrokerageErrorKind.RATE_LIMITED) else investmentConnection(id) }
        model.refreshAll(); model.refreshAll(); runCurrent()
        assertEquals(listOf(1L, 2L), fetched)
        assertEquals(setOf(1L), model.state.value.connectionErrors.keys)
        assertEquals(2, api.refreshCalls)
    }
    @Test fun credentialsAreClearedOnSuccessAndNotCopiedIntoUiState() = fixture { api, model, _ ->
        runCurrent(); model.showConnect("ibkr_flex")
        val done = CompletableDeferred<Unit>()
        api.create = { _, _, _ -> done.await(); investmentConnection() }
        model.connect("ibkr_flex", "Fixture", mapOf("token" to "987654321", "queryId" to "1"))
        model.connect("ibkr_flex", "Fixture", mapOf("token" to "987654321", "queryId" to "1")); runCurrent()
        assertEquals(1, api.connectCalls)
        assertEquals("987654321", api.submittedFields!!["token"])
        assertFalse(model.state.value.toString().contains("987654321"))
        done.complete(Unit); runCurrent()
        assertTrue(api.submittedFields!!.isEmpty())
        assertNull(model.state.value.connectProvider)
    }
    @Test fun credentialsAndBusyAreClearedOnFailureAndPreDispatchCancellation() = fixture { api, model, _ ->
        runCurrent()
        api.create = { _, _, _ -> throw CoreException.Platform("987654321") }
        model.connect("ibkr_flex", "Fixture", mapOf("token" to "987654321")); runCurrent()
        assertTrue(api.submittedFields!!.isEmpty())
        assertEquals(InvestmentFailureCode.PLATFORM, model.state.value.error!!.code)
        assertFalse(model.state.value.toString().contains("987654321"))
        model.connect("ibkr_flex", "Fixture", mapOf("token" to "987654321")); model.cancelOperation(); runCurrent()
        assertEquals(1, api.connectCalls)
        assertFalse(model.state.value.busy)
    }
    @Test fun activeConnectionCancellationReleasesCredentialReferencesAndReconcilesCache() = fixture { api, model, _ ->
        runCurrent()
        val pending = CompletableDeferred<Unit>()
        api.create = { _, _, _ -> pending.await(); investmentConnection() }
        model.connect("ibkr_flex", "Fixture", mapOf("token" to "987654321")); runCurrent()
        assertEquals("987654321", api.submittedFields!!["token"])
        model.cancelOperation(); runCurrent()
        assertTrue(api.submittedFields!!.isEmpty())
        assertTrue(model.state.value.cancellationNotice)
        assertFalse(model.state.value.busy)
        assertEquals(0L, model.state.value.commandRevision)
        assertEquals(listOf(investmentConnection()), model.state.value.connections)
    }
    @Test fun linkUnlinkAndDisconnectUseOnlyCoreCommandsWithOriginalIds() = fixture { api, model, _ ->
        runCurrent()
        val linked = mutableListOf<Triple<Long, String, String?>>()
        api.link = { id, broker, ledger -> linked += Triple(id, broker, ledger) }
        model.link(BrokerAccountRef(1, "U1"), "ledger/账户?x"); runCurrent()
        model.link(BrokerAccountRef(1, "U1"), null); runCurrent()
        assertEquals(listOf(Triple(1L, "U1", "ledger/账户?x"), Triple(1L, "U1", null)), linked)
        api.remove = { api.connections = emptyList() }
        model.selectAccount(BrokerAccountRef(1, "U1")); model.showDisconnect(1)
        model.disconnect(1); model.disconnect(1); runCurrent()
        assertEquals(1, api.disconnectCalls)
        assertTrue(model.state.value.connections.isEmpty())
        assertNull(model.state.value.selected)
        assertNull(model.state.value.disconnectTarget)
    }
    @Test fun removedOrChangedHoldingSelectionCannotDisplayStaleDetail() = fixture { api, model, _ ->
        runCurrent(); model.selectAccount(BrokerAccountRef(1, "U1"))
        model.selectHolding(CoreHoldingKey.of(investmentConnection().accounts.single().holdings.single()))
        assertNotNull(model.state.value.selectedHolding)
        api.connections = listOf(investmentConnection().copy(accounts = listOf(investmentConnection().accounts.single().copy(holdings = emptyList()))))
        model.reload(); runCurrent()
        assertNull(model.state.value.holding)
        assertNull(model.state.value.selectedHolding)
        assertNotNull(model.state.value.selected)
        api.connections = emptyList(); model.reload(); runCurrent()
        assertNull(model.state.value.selected)
    }
}

private class TestBroker : BrokerageInterface {
    var connections = listOf(investmentConnection())
    var reads = 0
    var connectCalls = 0
    var refreshCalls = 0
    var disconnectCalls = 0
    var submittedFields: Map<String, String>? = null
    var read: suspend () -> List<BrokerageConnection> = { connections }
    var create: suspend (String, String, Map<String, String>) -> BrokerageConnection = { _, _, _ -> investmentConnection() }
    var fetch: suspend (Long) -> BrokerageConnection = { investmentConnection(it) }
    var remove: suspend (Long) -> Unit = {}
    var link: suspend (Long, String, String?) -> Unit = { _, _, _ -> }
    override fun providers() = listOf(BrokerageProviderInfo("ibkr_flex", "ibkr", listOf(CredentialField("token", true), CredentialField("queryId", false))))
    override suspend fun connections(): List<BrokerageConnection> { reads++; return read() }
    override suspend fun connect(providerId: String, label: String, fields: Map<String, String>): BrokerageConnection {
        connectCalls++; submittedFields = fields; return create(providerId, label, fields)
    }
    override suspend fun refresh(connectionId: Long): BrokerageConnection { refreshCalls++; return fetch(connectionId) }
    override suspend fun disconnect(connectionId: Long) { disconnectCalls++; remove(connectionId) }
    override suspend fun linkAccount(connectionId: Long, brokerAccountId: String, accountId: String?) { link(connectionId, brokerAccountId, accountId) }
}
