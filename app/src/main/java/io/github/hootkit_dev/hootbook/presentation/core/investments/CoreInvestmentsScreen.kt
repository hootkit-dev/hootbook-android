package io.github.hootkit_dev.hootbook.presentation.core.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreInvestmentsScreen(
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onAccountHistory: (String) -> Unit,
    viewModel: CoreInvestmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    fun providerName(id: String): String {
        val provider = state.providers.find { it.id == id }
        val institution = state.institutions.find { it.key == provider?.institutionKey }
        return institution?.let { if (chinese) it.chineseName else it.englishName } ?: id
    }
    BackHandler(state.busy || state.selected != null || state.connectProvider != null || state.linkTarget != null || state.disconnectTarget != null) {
        viewModel.back()
    }
    CoreInvestmentsContent(state, providerName = ::providerName,
        onBack = { if (!viewModel.back()) onBack() },
        onReload = viewModel::reload, onRefresh = viewModel::refresh, onRefreshAll = viewModel::refreshAll,
        onCancel = viewModel::cancelOperation, onClearError = viewModel::clearError,
        onConnect = viewModel::showConnect, onSelect = viewModel::selectAccount,
        onHolding = viewModel::selectHolding, onLink = viewModel::showLink,
        onDisconnect = viewModel::showDisconnect, onManageAccounts = { if (!state.busy) onManageAccounts() },
        onAccountHistory = { if (!state.busy) onAccountHistory(it) })

    state.connectProvider?.let { id -> state.providers.find { it.id == id }?.let { provider ->
        CoreBrokerConnectDialog(provider, providerName(id), state,
            onDismiss = { viewModel.showConnect(null) }, onCancel = viewModel::cancelOperation,
            onSubmit = { label, fields -> viewModel.connect(id, label, fields) })
    } }
    state.linkTarget?.let { ref ->
        AlertDialog(onDismissRequest = { viewModel.showLink(null) },
            title = { Text(stringResource(R.string.core_invest_link_title)) },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.core_invest_link_warning))
                val candidates = availableLinkAccounts(ref, state.overview?.accounts.orEmpty())
                if (candidates.isEmpty()) Text(stringResource(R.string.core_invest_no_link_accounts))
                candidates.forEach { account ->
                    TextButton(enabled = !state.busy, onClick = { viewModel.link(ref, account.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${account.name} · ${account.mainCurrency}")
                    }
                }
                if (linkedAccount(ref, state.connections)?.linkedAccountId != null) TextButton(enabled = !state.busy,
                    onClick = { viewModel.link(ref, null) }) { Text(stringResource(R.string.core_invest_unlink)) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.showLink(null) }) { Text(stringResource(R.string.core_invest_close)) } })
    }
    state.disconnectTarget?.let { id ->
        AlertDialog(onDismissRequest = { viewModel.showDisconnect(null) },
            title = { Text(stringResource(R.string.core_invest_disconnect)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.core_invest_disconnect_warning))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.disconnect(id) }) { Text(stringResource(R.string.core_invest_disconnect)) } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = { viewModel.showDisconnect(null) }) { Text(stringResource(R.string.core_invest_cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoreInvestmentsContent(
    state: CoreInvestmentsUiState,
    providerName: (String) -> String,
    onBack: () -> Unit, onReload: () -> Unit, onRefresh: (Long) -> Unit, onRefreshAll: () -> Unit,
    onCancel: () -> Unit, onClearError: () -> Unit, onConnect: (String?) -> Unit,
    onSelect: (BrokerAccountRef?) -> Unit, onHolding: (CoreHoldingKey?) -> Unit,
    onLink: (BrokerAccountRef?) -> Unit, onDisconnect: (Long?) -> Unit,
    onManageAccounts: () -> Unit, onAccountHistory: (String) -> Unit,
) {
    var filter by remember(state.selected) { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.selectedHolding?.symbol ?: state.account?.accountId ?: stringResource(R.string.core_invest_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.core_invest_back)) } },
            actions = { TextButton(enabled = !state.busy, onClick = onReload) { Text(stringResource(R.string.core_invest_read_cache)) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading || state.busy) item("progress") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.busy) {
                        Text(stringResource(if (state.cancelling) R.string.core_invest_cancelling else R.string.core_invest_working))
                        TextButton(enabled = !state.cancelling, onClick = onCancel) { Text(stringResource(R.string.core_invest_cancel_request)) }
                    }
                }
            }
            state.error?.let { error -> item("error") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(investmentFailureText(error))
                        TextButton(onClick = onClearError) { Text(stringResource(R.string.core_invest_close)) }
                    }
                }
            } }
            if (state.cancellationNotice) item("cancelled") { Text(stringResource(R.string.core_invest_cancelled_notice)) }
            val holding = state.selectedHolding
            val account = state.account
            if (holding != null && account != null) {
                item("holding_detail") { HoldingDetails(holding, account) }
            } else if (account != null) {
                val ref = state.selected!!
                item("snapshot") {
                    SnapshotCard(account)
                    TextButton(enabled = !state.busy, onClick = { onRefresh(ref.connectionId) }) { Text(stringResource(R.string.core_invest_refresh)) }
                    TextButton(enabled = !state.busy, onClick = { onLink(ref) }) { Text(stringResource(R.string.core_invest_link_title)) }
                    account.linkedAccountId?.let { id ->
                        TextButton(enabled = !state.busy, onClick = { onAccountHistory(id) }) { Text(stringResource(R.string.core_invest_cash_history)) }
                    }
                    Text(stringResource(R.string.core_invest_latest_only), style = MaterialTheme.typography.bodySmall)
                }
                item("filter") { OutlinedTextField(filter, onValueChange = { filter = it }, label = { Text(stringResource(R.string.core_invest_search_holdings)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                val holdings = account.holdings.filter { filter.isBlank() || it.symbol.contains(filter, true) || it.description.contains(filter, true) }
                if (holdings.isEmpty()) item("no_holdings") { Text(stringResource(R.string.core_invest_no_holdings)) }
                items(holdings, key = { listOf(it.instrumentId, it.currency, it.model).joinToString("|") { part -> "${part.length}:$part" } }) { value ->
                    ElevatedCard(onClick = { if (!state.busy) onHolding(CoreHoldingKey.of(value)) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${value.symbol} · ${value.currency}", style = MaterialTheme.typography.titleMedium)
                            Text(value.description)
                            Text(stringResource(R.string.core_invest_quantity, investmentDecimal(value.quantity)))
                            Text(stringResource(R.string.core_invest_market_value, "${investmentDecimal(value.marketValue)} ${value.currency}"))
                            if (value.model.isNotBlank()) Text(stringResource(R.string.core_invest_model, value.model))
                        }
                    }
                }
                item("cash_title") { Text(stringResource(R.string.core_invest_cash), style = MaterialTheme.typography.titleMedium) }
                if (account.cash.isEmpty()) item("no_cash") { Text(stringResource(R.string.core_invest_no_cash)) }
                items(account.cash, key = { "cash:${it.currency}" }) { cash ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text("${cash.currency} · ${investmentDecimal(cash.endingCash)}")
                        Text(stringResource(R.string.core_invest_settled_cash, investmentDecimal(cash.endingSettledCash)))
                    } }
                }
            } else {
                item("note") { Text(stringResource(R.string.core_invest_report_note)) }
                item("refresh_all") { Button(enabled = !state.busy && state.connections.isNotEmpty(), onClick = onRefreshAll) { Text(stringResource(R.string.core_invest_refresh_all)) } }
                if (state.connections.isEmpty() && !state.loading) item("empty") { Text(stringResource(R.string.core_invest_no_connections)) }
                state.connections.forEach { connection ->
                    item("connection:${connection.id}") {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(connection.label, style = MaterialTheme.typography.titleLarge)
                            Text(providerName(connection.providerId))
                            Text(stringResource(R.string.core_invest_synced_at, investmentTime(connection.syncedAt)))
                            val failure = state.connectionErrors[connection.id] ?: connection.lastError?.let { CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, it) }
                            failure?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(enabled = !state.busy, onClick = { onRefresh(connection.id) }) { Text(stringResource(R.string.core_invest_refresh)) }
                                TextButton(enabled = !state.busy, onClick = { onDisconnect(connection.id) }) { Text(stringResource(R.string.core_invest_disconnect)) }
                            }
                        } }
                    }
                    connection.accounts.forEach { brokerAccount ->
                        item("account:${connection.id}:${brokerAccount.accountId}") {
                            ElevatedCard(onClick = { if (!state.busy) onSelect(BrokerAccountRef(connection.id, brokerAccount.accountId)) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) { SnapshotCard(brokerAccount) }
                            }
                        }
                    }
                }
                item("manual_title") { Text(stringResource(R.string.core_invest_ledger_accounts), style = MaterialTheme.typography.titleLarge) }
                items(state.investmentAccounts, key = { "ledger:${it.id}" }) { ledger ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(ledger.name, style = MaterialTheme.typography.titleMedium)
                        Text("${investmentDecimal(ledger.total.amount)} ${ledger.total.currency}")
                        if (!ledger.totalComplete) Text(stringResource(R.string.core_invest_incomplete))
                        ledger.securities?.let { Text(stringResource(R.string.core_invest_securities, "${investmentDecimal(it.amount)} ${it.currency}")) }
                        ledger.broker?.let { Text(stringResource(R.string.core_invest_linked_broker, it.brokerAccountId, investmentDate(it.asOf))) }
                        TextButton(enabled = !state.busy, onClick = { onAccountHistory(ledger.id) }) { Text(stringResource(R.string.core_invest_cash_history)) }
                    } }
                }
                item("manual_add") { OutlinedButton(enabled = !state.busy, onClick = onManageAccounts) { Text(stringResource(R.string.core_invest_manage_accounts)) } }
                item("providers_title") { Text(stringResource(R.string.core_invest_providers), style = MaterialTheme.typography.titleLarge) }
                if (state.providers.isEmpty() && !state.loading) item("no_providers") { Text(stringResource(R.string.core_invest_no_providers)) }
                items(state.providers, key = { "provider:${it.id}" }) { provider ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(providerName(provider.id), style = MaterialTheme.typography.titleMedium)
                        Text(provider.id)
                        Button(enabled = !state.busy, onClick = { onConnect(provider.id) }) { Text(stringResource(R.string.core_invest_connect)) }
                    } }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(account: BrokerageAccount) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(account.accountId, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)))
        account.total?.let { Text(stringResource(R.string.core_invest_snapshot_total, "${investmentDecimal(it.amount)} ${it.currency}")) }
            ?: Text(stringResource(R.string.core_invest_total_unavailable))
        if (!account.complete) Text(stringResource(R.string.core_invest_incomplete), color = MaterialTheme.colorScheme.error)
        account.valueByCurrency.forEach { Text(stringResource(R.string.core_invest_original_value, "${investmentDecimal(it.amount)} ${it.currency}")) }
        account.linkedAccountId?.let { Text(stringResource(R.string.core_invest_linked_account, it)) }
    }
}

@Composable
private fun HoldingDetails(holding: Holding, account: BrokerageAccount) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(holding.symbol, style = MaterialTheme.typography.headlineMedium)
        Text(holding.description)
        Text(stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)))
        Text(stringResource(R.string.core_invest_instrument_id, holding.instrumentId))
        Text(stringResource(R.string.core_invest_quantity, investmentDecimal(holding.quantity)))
        Text(stringResource(R.string.core_invest_market_value, "${investmentDecimal(holding.marketValue)} ${holding.currency}"))
        Text(stringResource(R.string.core_invest_cost_basis, "${investmentDecimal(holding.costBasis)} ${holding.currency}"))
        Text(stringResource(R.string.core_invest_pnl, "${investmentDecimal(holding.unrealizedPnl)} ${holding.currency}"))
        Text(stringResource(R.string.core_invest_asset_class, holding.assetClass.ifEmpty { "—" }))
        Text(stringResource(R.string.core_invest_model, holding.model.ifEmpty { "—" }))
        Text(stringResource(R.string.core_invest_report_note), style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
internal fun investmentFailureText(failure: CoreInvestmentFailure): String = stringResource(when (failure.kind) {
    BrokerageErrorKind.INVALID_CREDENTIALS -> R.string.core_invest_error_credentials
    BrokerageErrorKind.EXPIRED_CREDENTIALS -> R.string.core_invest_error_expired
    BrokerageErrorKind.IP_RESTRICTED -> R.string.core_invest_error_ip
    BrokerageErrorKind.INVALID_QUERY -> R.string.core_invest_error_query
    BrokerageErrorKind.REPORT_NOT_READY -> R.string.core_invest_error_not_ready
    BrokerageErrorKind.RATE_LIMITED -> R.string.core_invest_error_rate
    BrokerageErrorKind.NETWORK -> R.string.core_invest_error_network
    BrokerageErrorKind.INVALID_REPORT -> R.string.core_invest_error_report
    BrokerageErrorKind.UNSUPPORTED_PROVIDER -> R.string.core_invest_error_provider
    BrokerageErrorKind.DUPLICATE_CONNECTION -> R.string.core_invest_error_duplicate
    null -> when (failure.code) {
        InvestmentFailureCode.INVALID -> R.string.core_invest_error_invalid
        InvestmentFailureCode.CONFLICT -> R.string.core_invest_error_conflict
        InvestmentFailureCode.NOT_FOUND -> R.string.core_invest_error_not_found
        InvestmentFailureCode.NETWORK -> R.string.core_invest_error_network
        InvestmentFailureCode.STORAGE -> R.string.core_invest_error_storage
        InvestmentFailureCode.PLATFORM -> R.string.core_invest_error_platform
        InvestmentFailureCode.CANCELLED -> R.string.core_invest_cancelled_notice
        else -> R.string.core_invest_error_unknown
    }
})
