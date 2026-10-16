package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InvestmentOperationKind {
    CONNECT,
    SET_CREDENTIALS,
    REFRESH,
    REFRESH_ALL,
    DISCONNECT,
    LINK,
}

data class InvestmentOperation(val kind: InvestmentOperationKind, val connectionId: String? = null)

data class InvestmentsUiState(
    val loading: Boolean = true,
    val providers: List<BrokerageProviderInfo> = emptyList(),
    val institutions: List<Institution> = emptyList(),
    val connections: List<BrokerageConnection> = emptyList(),
    val overview: AccountsOverview? = null,
    val selected: BrokerAccountRef? = null,
    val selectedConnectionId: String? = null,
    val dailyAutoSyncEnabled: Boolean = true,
    val holding: CoreHoldingKey? = null,
    val connectProvider: String? = null,
    val credentialsTarget: BrokerageConnection? = null,
    val linkTarget: BrokerAccountRef? = null,
    val disconnectTarget: String? = null,
    val operation: InvestmentOperation? = null,
    val cancelling: Boolean = false,
    val error: CoreInvestmentFailure? = null,
    val connectionErrors: Map<String, CoreInvestmentFailure> = emptyMap(),
    val cancellationNotice: Boolean = false,
    val commandRevision: Long = 0,
) {
    val busy: Boolean
        get() = operation != null

    val account: BrokerageAccount?
        get() = selected?.let { linkedAccount(it, connections) }

    val selectedConnection: BrokerageConnection?
        get() =
            connections.find { it.id == (selected?.connectionId ?: selectedConnectionId) }
                ?: connections.firstOrNull()

    val selectedHolding: Holding?
        get() = account?.holdings?.find { CoreHoldingKey.of(it) == holding }

    val investmentAccounts: List<Account>
        get() =
            overview?.accounts.orEmpty().filter {
                it.kind == AccountKind.INVESTMENT || it.broker != null
            }
}

/** Core owns connections, credentials, parsing, valuation, calibration and persistence. */
@HiltViewModel
class InvestmentsViewModel
private constructor(
    private val session: CoreSession?,
    private val changes: ChangeBus,
    private val dailySyncManager: BrokerDailySyncManager?,
    private val testBrokerage: (suspend () -> BrokerageInterface)?,
    private val testAccounts: (suspend () -> AccountsOverview)?,
    private val testCatalog: (() -> List<Institution>)?,
    private val testCreateAccount: (suspend (NewAccount) -> String)? = null,
) : ViewModel() {
    @Inject
    constructor(
        session: CoreSession,
        changes: ChangeBus,
        dailySyncManager: BrokerDailySyncManager,
    ) : this(session, changes, dailySyncManager, null, null, null, null)

    internal constructor(
        session: CoreSession,
        changes: ChangeBus,
    ) : this(session, changes, null, null, null, null, null)

    internal constructor(
        changes: ChangeBus,
        brokerage: suspend () -> BrokerageInterface,
        accounts: suspend () -> AccountsOverview = {
            AccountsOverview(emptyList(), Money(java.math.BigDecimal.ZERO, "CNY"), true, null, null)
        },
        catalog: () -> List<Institution> = { emptyList() },
        createAccount: (suspend (NewAccount) -> String)? = null,
    ) : this(null, changes, null, brokerage, accounts, catalog, createAccount)

    private val mutable = MutableStateFlow(
        InvestmentsUiState(dailyAutoSyncEnabled = dailySyncManager?.enabled?.value ?: true)
    )
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var command: Job? = null

    private suspend fun <T> withBrokerage(block: suspend (BrokerageInterface) -> T): T {
        if (testBrokerage != null) return block(testBrokerage())
        return session!!.withCore { core -> core.brokerage().use { block(it) } }
    }

    init {
        dailySyncManager?.let { manager ->
            viewModelScope.launch {
                manager.enabled.collect { enabled ->
                    mutable.update { it.copy(dailyAutoSyncEnabled = enabled) }
                }
            }
        }
        reload()
    }

    fun setDailyAutoSync(enabled: Boolean) {
        dailySyncManager?.setEnabled(enabled)
        mutable.update { it.copy(dailyAutoSyncEnabled = enabled) }
        if (enabled) maybeAutoRefresh(state.value.connections)
    }

    private fun maybeAutoRefresh(connections: List<BrokerageConnection>) {
        val manager = dailySyncManager ?: return
        if (state.value.busy) return
        val prioritized = buildList {
            val currentId = state.value.selected?.connectionId ?: state.value.selectedConnectionId
            connections.find { it.id == currentId }?.let(::add)
            addAll(connections.filter { it.id != currentId })
        }
        val due = prioritized.firstOrNull { manager.shouldAutoRefreshNow(it) } ?: return
        manager.markAttempted(due.id)
        refresh(due.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reload() {
        val revision = ++queryRevision
        query?.cancel()
        mutable.update { it.copy(loading = true) }
        query =
            viewModelScope.launch {
                val tables =
                    setOf(
                        Table.BROKERAGE,
                        Table.BROKERAGE_CONNECTIONS,
                        Table.BROKERAGE_LINKS,
                        Table.ACCOUNTS,
                        Table.ACCOUNT_CURRENCIES,
                        Table.BALANCE_ANCHORS,
                        Table.TRANSACTIONS,
                        Table.EXCHANGE_RATES,
                        Table.SETTINGS,
                    )
                val snapshots =
                    if (session != null)
                        session.observe(changes, tables) { core ->
                            coreQuery {
                                val data =
                                    core.brokerage().use { it.providers() to it.connections() }
                                val overview = core.accounts().use { it.overview() }
                                val institutions = core.catalog().use { it.all() }
                                Triple(data, overview, institutions)
                            }
                        }
                    else
                        changes.observe(tables).mapLatest {
                            coreQuery {
                                Triple(
                                    withBrokerage { it.providers() to it.connections() },
                                    testAccounts!!.invoke(),
                                    testCatalog!!.invoke(),
                                )
                            }
                        }
                snapshots.collect { result ->
                    if (revision == queryRevision)
                        result.fold(
                            onSuccess = { (data, overview, institutions) ->
                                val connections = data.second
                                mutable.update { old ->
                                    val validConnectionId =
                                        old.selectedConnectionId?.takeIf { id ->
                                            connections.any { it.id == id }
                                        }
                                    val existingSelected =
                                        old.selected?.takeIf {
                                            linkedAccount(it, connections) != null
                                        }
                                    val autoSelectedFromConnection =
                                        if (existingSelected == null && validConnectionId != null) {
                                            val conn = connections.find { it.id == validConnectionId }
                                            conn?.accounts?.firstOrNull()?.let {
                                                BrokerAccountRef(conn.id, it.accountId)
                                            }
                                        } else null
                                    val selected = existingSelected ?: autoSelectedFromConnection
                                    old.copy(
                                        loading = false,
                                        providers = data.first,
                                        connections = connections,
                                        overview = overview,
                                        institutions = institutions,
                                        selected = selected,
                                        selectedConnectionId = selected?.connectionId ?: validConnectionId,
                                        holding =
                                            old.holding?.takeIf { key ->
                                                selected
                                                    ?.let { linkedAccount(it, connections) }
                                                    ?.holdings
                                                    ?.any { CoreHoldingKey.of(it) == key } == true
                                            },
                                        credentialsTarget =
                                            old.credentialsTarget?.let { target ->
                                                connections.find { it.id == target.id }
                                            },
                                        linkTarget =
                                            old.linkTarget?.takeIf {
                                                linkedAccount(it, connections) != null
                                            },
                                        disconnectTarget =
                                            old.disconnectTarget?.takeIf { id ->
                                                connections.any { it.id == id }
                                            },
                                    )
                                }
                                maybeAutoRefresh(connections)
                            },
                            onFailure = { failure ->
                                mutable.update {
                                    it.copy(loading = false, error = investmentFailure(failure))
                                }
                            },
                        )
                }
            }
    }

    fun selectAccount(ref: BrokerAccountRef?) {
        if (state.value.busy) return
        val connId = ref?.connectionId?.takeIf { id -> state.value.connections.any { it.id == id } }
        mutable.update {
            it.copy(
                selected = ref?.takeIf { value -> linkedAccount(value, it.connections) != null },
                selectedConnectionId = connId,
                holding = null,
            )
        }
        maybeAutoRefresh(state.value.connections)
    }

    fun selectConnection(connectionId: String?) {
        if (state.value.busy) return
        val conn = connectionId?.let { id -> state.value.connections.find { it.id == id } }
        val firstAccount = conn?.accounts?.firstOrNull()?.let { BrokerAccountRef(conn.id, it.accountId) }
        mutable.update {
            it.copy(
                selected = firstAccount,
                selectedConnectionId = conn?.id,
                holding = null,
            )
        }
        if (conn != null) {
            maybeAutoRefresh(listOf(conn))
        }
    }

    fun selectHolding(key: CoreHoldingKey?) {
        if (state.value.busy) return
        mutable.update {
            it.copy(
                holding =
                    key?.takeIf { value ->
                        it.account?.holdings?.any { h -> CoreHoldingKey.of(h) == value } == true
                    }
            )
        }
    }

    fun showConnect(providerId: String?) {
        if (state.value.busy) return
        mutable.update {
            it.copy(
                connectProvider = providerId?.takeIf { id -> it.providers.any { p -> p.id == id } },
                error = null,
            )
        }
    }

    fun showSetCredentials(id: String?) {
        if (state.value.busy) return
        mutable.update {
            it.copy(
                credentialsTarget = id?.let { value -> it.connections.find { c -> c.id == value } },
                error = null,
            )
        }
    }

    fun showLink(ref: BrokerAccountRef?) {
        if (state.value.busy) return
        mutable.update {
            it.copy(
                linkTarget = ref?.takeIf { value -> linkedAccount(value, it.connections) != null }
            )
        }
    }

    fun showDisconnect(id: String?) {
        if (state.value.busy) return
        mutable.update {
            it.copy(
                disconnectTarget = id?.takeIf { value -> it.connections.any { c -> c.id == value } }
            )
        }
    }

    fun clearError() {
        mutable.update { it.copy(error = null, cancellationNotice = false) }
    }

    /** true consumes navigation; credential entry/selection never survives in SavedState. */
    fun back(): Boolean {
        if (state.value.busy) return true
        return when {
            state.value.connectProvider != null -> {
                showConnect(null)
                true
            }
            state.value.credentialsTarget != null -> {
                showSetCredentials(null)
                true
            }
            state.value.linkTarget != null -> {
                showLink(null)
                true
            }
            state.value.disconnectTarget != null -> {
                showDisconnect(null)
                true
            }
            state.value.holding != null -> {
                selectHolding(null)
                true
            }
            state.value.selected != null -> {
                selectAccount(null)
                true
            }
            else -> false
        }
    }

    fun connect(providerId: String, label: String, fields: Map<String, String>) {
        if (state.value.busy) return
        val submission = InvestmentCredentialSubmission(fields)
        operate(
            InvestmentOperation(InvestmentOperationKind.CONNECT),
            onFinally = submission::close,
        ) { api ->
            val connection = api.connect(providerId, label, submission.fields())
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(connectProvider = null) }
            try {
                autoLinkBrokerAccounts(api, connection)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Connection succeeded; manual link remains available if auto-link fails.
            }
        }
    }

    fun setCredentials(id: String, fields: Map<String, String>) {
        if (state.value.busy) return
        val submission = InvestmentCredentialSubmission(fields)
        operate(
            InvestmentOperation(InvestmentOperationKind.SET_CREDENTIALS, id),
            onFinally = submission::close,
        ) { api ->
            api.setCredentials(id, submission.fields())
            currentCoroutineContext().ensureActive()
            mutable.update {
                it.copy(credentialsTarget = null, connectionErrors = it.connectionErrors - id)
            }
        }
    }

    fun refresh(id: String) =
        operate(InvestmentOperation(InvestmentOperationKind.REFRESH, id)) { api ->
            try {
                api.refresh(id)
                mutable.update { it.copy(connectionErrors = it.connectionErrors - id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update {
                    it.copy(
                        connectionErrors = it.connectionErrors + (id to investmentFailure(failure))
                    )
                }
                throw failure
            }
        }

    fun refreshAll() {
        val ids = state.value.connections.map { it.id }
        if (ids.isEmpty()) {
            reload()
            return
        }
        operate(InvestmentOperation(InvestmentOperationKind.REFRESH_ALL)) { api ->
            for (id in ids) {
                currentCoroutineContext().ensureActive()
                try {
                    api.refresh(id)
                    mutable.update { it.copy(connectionErrors = it.connectionErrors - id) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val sanitized = investmentFailure(failure)
                    mutable.update {
                        it.copy(
                            connectionErrors = it.connectionErrors + (id to sanitized),
                            error = sanitized,
                        )
                    }
                }
            }
        }
    }

    fun disconnect(id: String) =
        operate(InvestmentOperation(InvestmentOperationKind.DISCONNECT, id)) {
            it.disconnect(id)
            currentCoroutineContext().ensureActive()
            mutable.update { value ->
                value.copy(disconnectTarget = null, connectionErrors = value.connectionErrors - id)
            }
        }

    fun link(ref: BrokerAccountRef, accountId: String?) =
        operate(InvestmentOperation(InvestmentOperationKind.LINK, ref.connectionId)) {
            it.linkAccount(ref.connectionId, ref.brokerAccountId, accountId)
            currentCoroutineContext().ensureActive()
            mutable.update { value -> value.copy(linkTarget = null) }
        }

    fun createAndLink(ref: BrokerAccountRef) =
        operate(InvestmentOperation(InvestmentOperationKind.LINK, ref.connectionId)) { api ->
            val connection =
                api.connections().find { it.id == ref.connectionId }
                    ?: state.value.connections.find { it.id == ref.connectionId }
                    ?: throw CoreException.NotFound(Entity.BROKERAGE_CONNECTION, ref.connectionId)
            autoLinkBrokerAccounts(api, connection, ref.brokerAccountId)
            currentCoroutineContext().ensureActive()
            mutable.update { value -> value.copy(linkTarget = null) }
        }

    private suspend fun autoLinkBrokerAccounts(
        api: BrokerageInterface,
        connection: BrokerageConnection,
        targetBrokerAccountId: String? = null,
    ) {
        val unlinked =
            connection.accounts.filter {
                it.linkedAccountId == null &&
                    (targetBrokerAccountId == null || it.accountId == targetBrokerAccountId)
            }
        if (unlinked.isEmpty()) return
        val providers = api.providers()
        val (overview, institutions, createAccount) =
            if (session != null) {
                session.withCore { core ->
                    val ov = core.accounts().use { it.overview() }
                    val inst = core.catalog().use { it.all() }
                    val creator: suspend (NewAccount) -> String = { input ->
                        session.withCore { c -> c.accounts().use { it.create(input) } }
                    }
                    Triple(ov, inst, creator)
                }
            } else {
                val creator = testCreateAccount ?: return
                Triple(
                    testAccounts?.invoke()
                        ?: AccountsOverview(
                            emptyList(),
                            Money(java.math.BigDecimal.ZERO, "CNY"),
                            true,
                            null,
                            null,
                        ),
                    testCatalog?.invoke().orEmpty(),
                    creator,
                )
            }
        val existing = overview.accounts.toMutableList()
        for (brokerAccount in unlinked) {
            currentCoroutineContext().ensureActive()
            if (
                existing.any {
                    it.broker?.connectionId == connection.id &&
                        it.broker?.brokerAccountId == brokerAccount.accountId
                }
            ) {
                continue
            }
            val draft =
                buildBrokerLedgerAccount(
                    connection = connection,
                    brokerAccount = brokerAccount,
                    providers = providers,
                    institutions = institutions,
                    existingAccounts = existing,
                    defaultCurrency = overview.netWorth.currency,
                )
            val createdId = createAccount(draft)
            api.linkAccount(connection.id, brokerAccount.accountId, createdId)
            existing +=
                Account(
                    id = createdId,
                    name = draft.name,
                    last4 = draft.last4,
                    kind = draft.kind,
                    mainCurrency = draft.mainCurrency,
                    pockets = emptyList(),
                    total = Money(java.math.BigDecimal.ZERO, overview.netWorth.currency),
                    totalComplete = true,
                    securities = null,
                    broker = null,
                    creditLimit = null,
                    statementDay = null,
                    dueDay = null,
                    institutionKey = draft.institutionKey,
                    icon = draft.icon ?: "type_finance_chart_increasing",
                    color = draft.color ?: "#33B5E5",
                    archived = false,
                    isDefault = false,
                    sortOrder = existing.size,
                    createdAt = LocalDateTime(2026, 1u, 1u, 0u, 0u, 0u, 0u),
                )
        }
    }

    fun cancelOperation() {
        if (!state.value.busy) return
        mutable.update { it.copy(cancelling = true) }
        command?.cancel()
    }

    private fun operate(
        operation: InvestmentOperation,
        onFinally: () -> Unit = {},
        action: suspend (BrokerageInterface) -> Unit,
    ) {
        if (state.value.busy) {
            onFinally()
            return
        }
        mutable.update {
            it.copy(
                operation = operation,
                cancelling = false,
                error = null,
                cancellationNotice = false,
            )
        }
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    withBrokerage { action(it) }
                    currentCoroutineContext().ensureActive()
                    mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                } catch (cancelled: CancellationException) {
                    mutable.update { it.copy(cancellationNotice = true) }
                    throw cancelled
                } catch (failure: Exception) {
                    mutable.update { it.copy(error = investmentFailure(failure)) }
                }
            }
        command = job
        job.invokeOnCompletion { cause ->
            onFinally()
            mutable.update {
                it.copy(
                    operation = null,
                    cancelling = false,
                    cancellationNotice = it.cancellationNotice || cause is CancellationException,
                )
            }
            // Runs even when cancelled before dispatch. Cancellation is not a rollback promise.
            reload()
        }
        job.start()
    }
}
