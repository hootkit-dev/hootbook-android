@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem as InvestmentListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.*

@Composable
fun CoreInvestmentsScreen(
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onAccountHistory: (String) -> Unit,
    viewModel: CoreInvestmentsViewModel = hiltViewModel(),
    onAddInvestment: () -> Unit = { onManageAccounts() },
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
        onAccountHistory = { if (!state.busy) onAccountHistory(it) }, onAddInvestment = { if (!state.busy) onAddInvestment() })

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
                state.error?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.showLink(null) }) { Text(stringResource(R.string.core_invest_close)) } })
    }
    state.disconnectTarget?.let { id ->
        AlertDialog(onDismissRequest = { viewModel.showDisconnect(null) },
            title = { Text(stringResource(R.string.core_invest_disconnect)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.core_invest_disconnect_warning))
                state.error?.let { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.disconnect(id) }) { Text(stringResource(R.string.core_invest_disconnect)) } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = { viewModel.showDisconnect(null) }) { Text(stringResource(R.string.core_invest_cancel)) } })
    }
}

/**
 * Investments: what each investment account is worth, the broker connections and their accounts,
 * the ledger's investment accounts, and brokers to connect; then one broker account's holdings
 * and cash, and one holding's details. Snapshots update only when the user refreshes.
 */
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
    onAddInvestment: () -> Unit = onManageAccounts,
) {
    val enabled = !state.busy
    SecondaryScreen(
        title = state.selectedHolding?.symbol ?: state.account?.accountId ?: stringResource(R.string.overview_investments), onBack = onBack,
        actions = { if (state.selected == null && state.connections.isNotEmpty())
            TooltipIconButton(Icons.Rounded.Refresh, stringResource(R.string.investments_refresh), onRefreshAll, enabled = enabled) },
        floatingActionButton = {
            if (!state.loading && state.selected == null) AddFab(stringResource(R.string.investments_add), onAddInvestment)
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.busy, onRefresh = { if (enabled) onRefreshAll() }, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                if (state.busy) item("progress") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(if (state.cancelling) R.string.core_invest_cancelling else R.string.core_invest_working), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(enabled = !state.cancelling, onClick = onCancel) { Text(stringResource(R.string.core_invest_cancel_request)) }
                    }
                }
                state.error?.let { error -> item("error") { ErrorBanner(investmentFailureText(error), onClearError) } }
                if (state.cancellationNotice) item("cancelled") { Text(stringResource(R.string.core_invest_cancelled_notice), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val holding = state.selectedHolding
                val account = state.account
                when {
                    holding != null && account != null -> item("holding_detail") { HoldingDetails(holding, account) }
                    account != null -> brokerAccountItems(account, state, enabled, onRefresh, onLink, onAccountHistory, onHolding)
                    else -> overviewItems(state, enabled, providerName, onRefresh, onDisconnect, onSelect, onAccountHistory, onManageAccounts, onConnect)
                }
            }
        }
    }
}

private fun LazyListScope.overviewItems(state: CoreInvestmentsUiState, enabled: Boolean, providerName: (String) -> String,
    onRefresh: (Long) -> Unit, onDisconnect: (Long?) -> Unit, onSelect: (BrokerAccountRef?) -> Unit,
    onAccountHistory: (String) -> Unit, onManageAccounts: () -> Unit, onConnect: (String?) -> Unit) {
    if (state.connections.isNotEmpty() || state.investmentAccounts.isNotEmpty()) item("net_worth") {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.investments_net_worth), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                state.investmentAccounts.forEach { ledger -> InvestmentValue(ledger.name, CoreMoneyPresentation.format(ledger.total.amount, ledger.total.currency)) }
                state.connections.forEach { connection -> connection.accounts.filter { it.linkedAccountId == null }.forEach { account ->
                    InvestmentValue("${connection.label} · ${account.accountId}", account.total?.let { CoreMoneyPresentation.format(it.amount, it.currency) }
                        ?: stringResource(R.string.core_invest_total_unavailable))
                } }
                if (state.investmentAccounts.size + state.connections.sumOf { c -> c.accounts.count { it.linkedAccountId == null } } > 1)
                    Text(stringResource(R.string.core_parity_investment_total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
    if (state.connections.isEmpty() && state.investmentAccounts.isEmpty() && !state.loading) item("empty") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.investments_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.investments_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    state.connections.forEach { connection ->
        item("connection:${connection.id}") {
            ListSection(title = connection.label, action = { OverflowMenu(enabled = enabled, actions = listOf(
                MenuAction(stringResource(R.string.core_invest_refresh), Icons.Rounded.Refresh) { onRefresh(connection.id) },
                MenuAction(stringResource(R.string.core_invest_disconnect), Icons.Rounded.LinkOff, destructive = true) { onDisconnect(connection.id) },
            )) }) {
                val failure = state.connectionErrors[connection.id] ?: connection.lastError?.let { CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, it) }
                item(headline = { Text(providerName(connection.providerId)) },
                    supporting = { Text(stringResource(R.string.core_invest_synced_at, investmentTime(connection.syncedAt))) })
                failure?.let { item(headline = { Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error) }) }
                connection.accounts.forEach { brokerAccount ->
                    item(headline = { Text(brokerAccount.accountId) },
                        supporting = { Text(stringResource(R.string.core_invest_as_of, investmentDate(brokerAccount.asOf))) },
                        trailing = { Text(brokerAccount.total?.let { CoreMoneyPresentation.format(it.amount, it.currency) } ?: "—", style = MaterialTheme.typography.titleMedium) },
                        chevron = true, onClick = { if (enabled) onSelect(BrokerAccountRef(connection.id, brokerAccount.accountId)) })
                }
            }
        }
    }
    item("ledger") {
        ListSection(title = stringResource(R.string.core_invest_ledger_accounts)) {
            state.investmentAccounts.forEach { ledger ->
                item(headline = { Text(ledger.name) },
                    supporting = { Text(listOfNotNull(
                        ledger.securities?.let { stringResource(R.string.core_invest_securities, CoreMoneyPresentation.format(it.amount, it.currency)) },
                        ledger.broker?.let { stringResource(R.string.core_invest_linked_broker, it.brokerAccountId, investmentDate(it.asOf)) },
                        if (!ledger.totalComplete) stringResource(R.string.core_invest_incomplete) else null).joinToString(" · ").ifBlank { stringResource(R.string.core_invest_cash_history) }) },
                    trailing = { Text(CoreMoneyPresentation.format(ledger.total.amount, ledger.total.currency), style = MaterialTheme.typography.titleMedium) },
                    chevron = true, onClick = { if (enabled) onAccountHistory(ledger.id) })
            }
            item(headline = { Text(stringResource(R.string.core_invest_manage_accounts)) }, leading = { Icon(Icons.Rounded.AccountBalance, null) },
                chevron = true, onClick = { if (enabled) onManageAccounts() })
        }
    }
    item("providers") {
        ListSection(title = stringResource(R.string.core_invest_providers)) {
            if (state.providers.isEmpty() && !state.loading) item(headline = { Text(stringResource(R.string.core_invest_no_providers)) })
            state.providers.forEach { provider ->
                item(headline = { Text(providerName(provider.id)) }, leading = { Icon(Icons.Rounded.Link, null) },
                    trailing = { Text(stringResource(R.string.core_invest_connect), color = MaterialTheme.colorScheme.primary) },
                    chevron = true, onClick = { if (enabled) onConnect(provider.id) })
            }
        }
    }
    item("note") { Text(stringResource(R.string.core_invest_report_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun InvestmentValue(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

private fun LazyListScope.brokerAccountItems(account: BrokerageAccount, state: CoreInvestmentsUiState, enabled: Boolean, onRefresh: (Long) -> Unit,
    onLink: (BrokerAccountRef?) -> Unit, onAccountHistory: (String) -> Unit, onHolding: (CoreHoldingKey?) -> Unit) {
    val ref = state.selected!!
    item("snapshot") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SnapshotCard(account)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onRefresh(ref.connectionId) }, enabled = enabled, label = { Text(stringResource(R.string.core_invest_refresh)) },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(AssistChipDefaults.IconSize)) })
                AssistChip(onClick = { onLink(ref) }, enabled = enabled, label = { Text(stringResource(R.string.core_invest_link_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(AssistChipDefaults.IconSize)) })
                account.linkedAccountId?.let { id -> AssistChip(onClick = { onAccountHistory(id) }, enabled = enabled,
                    label = { Text(stringResource(R.string.core_invest_cash_history)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null, Modifier.size(AssistChipDefaults.IconSize)) }) }
            }
        }
    }
    item("holdings") {
        var filter by rememberSaveable(state.selected) { mutableStateOf("") }
        val holdings = account.holdings.filter { filter.isBlank() || it.symbol.contains(filter, true) || it.description.contains(filter, true) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.invest_holdings))
            SearchBarBox(searchQuery = androidx.compose.ui.text.input.TextFieldValue(filter), onSearchQueryChange = { filter = it.text },
                label = { Text(stringResource(R.string.core_invest_search_holdings)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            if (holdings.isEmpty()) Text(stringResource(R.string.core_invest_no_holdings), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                holdings.forEachIndexed { index, value ->
                    CoreHoldingRow(value, ListItemPosition.from(index, holdings.size)) { if (enabled) onHolding(CoreHoldingKey.of(value)) }
                }
            }
        }
    }
    item("cash") {
        ListSection(title = stringResource(R.string.core_invest_cash)) {
            if (account.cash.isEmpty()) item(headline = { Text(stringResource(R.string.core_invest_no_cash)) })
            account.cash.forEach { cash ->
                item(headline = { Text(cash.currency) },
                    supporting = { Text(stringResource(R.string.core_invest_settled_cash, CoreMoneyPresentation.format(cash.endingSettledCash, cash.currency))) },
                    trailing = { Text(CoreMoneyPresentation.format(cash.endingCash, cash.currency), style = MaterialTheme.typography.titleMedium) })
            }
        }
        Text(stringResource(R.string.core_invest_latest_only), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A broker account's report date, converted total and each currency's value. */
@Composable
private fun SnapshotCard(account: BrokerageAccount) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(account.total?.let { CoreMoneyPresentation.format(it.amount, it.currency) } ?: stringResource(R.string.core_invest_total_unavailable),
                style = MaterialTheme.typography.headlineMedium)
            if (!account.complete) Text(stringResource(R.string.core_invest_incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            account.valueByCurrency.forEach { Text(stringResource(R.string.core_invest_original_value, CoreMoneyPresentation.format(it.amount, it.currency)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** One holding from the report, as label / value rows. */
@Composable
private fun HoldingDetails(holding: Holding, account: BrokerageAccount) {
    val money = { value: java.math.BigDecimal? -> value?.let { CoreMoneyPresentation.format(it, holding.currency) } ?: "—" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(holding.description, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        ListSection {
            listOf(
                stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)),
                stringResource(R.string.core_invest_quantity, investmentDecimal(holding.quantity)),
                stringResource(R.string.core_invest_market_value, money(holding.marketValue)),
                stringResource(R.string.core_invest_cost_basis, money(holding.costBasis)),
                stringResource(R.string.core_invest_pnl, money(holding.unrealizedPnl)),
                stringResource(R.string.core_invest_asset_class, holding.assetClass.ifEmpty { "—" }),
                stringResource(R.string.core_invest_model, holding.model.ifEmpty { "—" }),
                stringResource(R.string.core_invest_instrument_id, holding.instrumentId),
            ).forEach { line -> item(headline = { Text(line) }) }
        }
        Text(stringResource(R.string.core_invest_report_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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


/** The original grouped holding row. Quantity, value and P&L remain Core-provided decimals. */
@Composable
internal fun CoreHoldingRow(holding: Holding, position: ListItemPosition, onClick: () -> Unit = {}) {
    val pnlColor = when {
        holding.unrealizedPnl == null -> MaterialTheme.colorScheme.onSurfaceVariant
        holding.unrealizedPnl!!.signum() >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    InvestmentListItem(headline = { Text(holding.symbol) }, supporting = {
        Text(buildString {
            append(holding.description)
            if (holding.model.isNotBlank()) append(" · ").append(holding.model)
            append("\n").append(stringResource(R.string.investments_quantity, investmentDecimal(holding.quantity)))
        })
    }, trailing = {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(holding.marketValue?.let { CoreMoneyPresentation.format(it, holding.currency) } ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(holding.unrealizedPnl?.let { (if (it.signum() > 0) "+" else "") + CoreMoneyPresentation.format(it, holding.currency) } ?: "—", style = MaterialTheme.typography.labelMedium, color = pnlColor)
        }
    }, onClick = onClick, shape = position.toShape(), padding = PaddingValues(0.dp))
}
