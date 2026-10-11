package io.github.hootkit_dev.hootbook.presentation.core.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InvestmentOperationKind { CONNECT, REFRESH, REFRESH_ALL, DISCONNECT, LINK }
data class InvestmentOperation(val kind: InvestmentOperationKind, val connectionId: Long? = null)
data class CoreInvestmentsUiState(
    val loading: Boolean = true,
    val providers: List<BrokerageProviderInfo> = emptyList(),
    val institutions: List<Institution> = emptyList(),
    val connections: List<BrokerageConnection> = emptyList(),
    val overview: AccountsOverview? = null,
    val selected: BrokerAccountRef? = null,
    val holding: CoreHoldingKey? = null,
    val connectProvider: String? = null,
    val linkTarget: BrokerAccountRef? = null,
    val disconnectTarget: Long? = null,
    val operation: InvestmentOperation? = null,
    val cancelling: Boolean = false,
    val error: CoreInvestmentFailure? = null,
    val connectionErrors: Map<Long, CoreInvestmentFailure> = emptyMap(),
    val cancellationNotice: Boolean = false,
    val commandRevision: Long = 0,
) {
    val busy: Boolean get() = operation != null
    val account: BrokerageAccount? get() = selected?.let { linkedAccount(it, connections) }
    val selectedHolding: Holding? get() = account?.holdings?.find { CoreHoldingKey.of(it) == holding }
    val investmentAccounts: List<Account> get() = overview?.accounts.orEmpty().filter { it.kind == AccountKind.INVESTMENT || it.broker != null }
}

/** Core owns connections, credentials, parsing, valuation, calibration and persistence. */
@HiltViewModel
class CoreInvestmentsViewModel private constructor(
    private val session: CoreSession?,
    changes: ChangeBus,
    private val testBrokerage: (suspend () -> BrokerageInterface)?,
    private val testAccounts: (suspend () -> AccountsOverview)?,
    private val testCatalog: (() -> List<Institution>)?,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, null, null)
    internal constructor(changes: ChangeBus, brokerage: suspend () -> BrokerageInterface,
        accounts: suspend () -> AccountsOverview = { AccountsOverview(emptyList(), Money(java.math.BigDecimal.ZERO, "CNY"), true, null, null) },
        catalog: () -> List<Institution> = { emptyList() },
    ) : this(null, changes, brokerage, accounts, catalog)

    private val mutable = MutableStateFlow(CoreInvestmentsUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var command: Job? = null

    private suspend fun <T> withBrokerage(block: suspend (BrokerageInterface) -> T): T {
        if (testBrokerage != null) return block(testBrokerage())
        return session!!.awaitCore().brokerage().use { block(it) }
    }

    init {
        viewModelScope.launch {
            changes.observe(setOf(Table.BROKERAGE, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES,
                Table.BALANCE_ANCHORS, Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS)).collect { reload() }
        }
    }

    /** Reads cached snapshots only. It never invokes refresh/connect or starts a timer. */
    fun reload() {
        val revision = ++queryRevision
        query?.cancel()
        mutable.update { it.copy(loading = true) }
        query = viewModelScope.launch {
            try {
                val data = withBrokerage { it.providers() to it.connections() }
                val overview = testAccounts?.invoke() ?: session!!.awaitCore().accounts().use { it.overview() }
                val institutions = testCatalog?.invoke() ?: session!!.awaitCore().catalog().use { it.all() }
                currentCoroutineContext().ensureActive()
                if (revision == queryRevision) mutable.update { old ->
                    val selected = old.selected?.takeIf { linkedAccount(it, data.second) != null }
                    val holding = old.holding?.takeIf { key -> selected?.let { linkedAccount(it, data.second) }?.holdings?.any { CoreHoldingKey.of(it) == key } == true }
                    old.copy(loading = false, providers = data.first, connections = data.second,
                        overview = overview, institutions = institutions, selected = selected, holding = holding,
                        linkTarget = old.linkTarget?.takeIf { linkedAccount(it, data.second) != null },
                        disconnectTarget = old.disconnectTarget?.takeIf { id -> data.second.any { it.id == id } })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == queryRevision) mutable.update { it.copy(loading = false, error = investmentFailure(failure)) } }
        }
    }

    fun selectAccount(ref: BrokerAccountRef?) {
        if (state.value.busy) return
        mutable.update { it.copy(selected = ref?.takeIf { value -> linkedAccount(value, it.connections) != null }, holding = null) }
    }
    fun selectHolding(key: CoreHoldingKey?) {
        if (state.value.busy) return
        mutable.update { it.copy(holding = key?.takeIf { value -> it.account?.holdings?.any { h -> CoreHoldingKey.of(h) == value } == true }) }
    }
    fun showConnect(providerId: String?) {
        if (state.value.busy) return
        mutable.update { it.copy(connectProvider = providerId?.takeIf { id -> it.providers.any { p -> p.id == id } }, error = null) }
    }
    fun showLink(ref: BrokerAccountRef?) {
        if (state.value.busy) return
        mutable.update { it.copy(linkTarget = ref?.takeIf { value -> linkedAccount(value, it.connections) != null }) }
    }
    fun showDisconnect(id: Long?) {
        if (state.value.busy) return
        mutable.update { it.copy(disconnectTarget = id?.takeIf { value -> it.connections.any { c -> c.id == value } }) }
    }
    fun clearError() { mutable.update { it.copy(error = null, cancellationNotice = false) } }

    /** true consumes navigation; credential entry/selection never survives in SavedState. */
    fun back(): Boolean {
        if (state.value.busy) return true
        return when {
            state.value.connectProvider != null -> { showConnect(null); true }
            state.value.linkTarget != null -> { showLink(null); true }
            state.value.disconnectTarget != null -> { showDisconnect(null); true }
            state.value.holding != null -> { selectHolding(null); true }
            state.value.selected != null -> { selectAccount(null); true }
            else -> false
        }
    }

    fun connect(providerId: String, label: String, fields: Map<String, String>) {
        if (state.value.busy) return
        val submission = InvestmentCredentialSubmission(fields)
        operate(InvestmentOperation(InvestmentOperationKind.CONNECT), onFinally = submission::close) { api ->
            api.connect(providerId, label, submission.fields())
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(connectProvider = null) }
        }
    }
    fun refresh(id: Long) = operate(InvestmentOperation(InvestmentOperationKind.REFRESH, id)) { api ->
        try { api.refresh(id); mutable.update { it.copy(connectionErrors = it.connectionErrors - id) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(connectionErrors = it.connectionErrors + (id to investmentFailure(failure))) }; throw failure }
    }
    fun refreshAll() {
        val ids = state.value.connections.map { it.id }
        if (ids.isEmpty()) { reload(); return }
        operate(InvestmentOperation(InvestmentOperationKind.REFRESH_ALL)) { api ->
            for (id in ids) {
                currentCoroutineContext().ensureActive()
                try { api.refresh(id); mutable.update { it.copy(connectionErrors = it.connectionErrors - id) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    val sanitized = investmentFailure(failure)
                    mutable.update { it.copy(connectionErrors = it.connectionErrors + (id to sanitized), error = sanitized) }
                }
            }
        }
    }
    fun disconnect(id: Long) = operate(InvestmentOperation(InvestmentOperationKind.DISCONNECT, id)) {
        it.disconnect(id)
        currentCoroutineContext().ensureActive()
        mutable.update { value -> value.copy(disconnectTarget = null, connectionErrors = value.connectionErrors - id) }
    }
    fun link(ref: BrokerAccountRef, accountId: String?) = operate(InvestmentOperation(InvestmentOperationKind.LINK, ref.connectionId)) {
        it.linkAccount(ref.connectionId, ref.brokerAccountId, accountId)
        currentCoroutineContext().ensureActive()
        mutable.update { value -> value.copy(linkTarget = null) }
    }
    fun cancelOperation() {
        if (!state.value.busy) return
        mutable.update { it.copy(cancelling = true) }
        command?.cancel()
    }

    private fun operate(operation: InvestmentOperation, onFinally: () -> Unit = {}, action: suspend (BrokerageInterface) -> Unit) {
        if (state.value.busy) { onFinally(); return }
        mutable.update { it.copy(operation = operation, cancelling = false, error = null, cancellationNotice = false) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withBrokerage { action(it) }
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(cancellationNotice = true) }
                throw cancelled
            } catch (failure: Exception) { mutable.update { it.copy(error = investmentFailure(failure)) } }
        }
        command = job
        job.invokeOnCompletion { cause ->
            onFinally()
            mutable.update { it.copy(operation = null, cancelling = false,
                cancellationNotice = it.cancellationNotice || cause is CancellationException) }
            // Runs even when cancelled before dispatch. Cancellation is not a rollback promise.
            reload()
        }
        job.start()
    }
}
