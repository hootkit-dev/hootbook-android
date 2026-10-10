@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings
import io.github.hootkit_dev.hootbook.data.repository.byCurrency
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountBalanceText
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountCurrenciesText
import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountSubtitle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.domain.brokerage.BrokerageAccount
import io.github.hootkit_dev.hootbook.domain.brokerage.BrokerageError
import io.github.hootkit_dev.hootbook.domain.brokerage.Holding
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.EditAccountSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ManageAccountsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.snapshotByCurrency
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.snapshotTotals
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * Read-only holdings and manual investment accounts. Adding one (manual, or a broker connection
 * under "Broker auto-sync") happens in Add account with the Broker type: [onAddInvestment].
 */
@Composable
fun InvestmentsScreen(
    onNavigateBack: () -> Unit,
    onManageManualAccounts: () -> Unit = {},
    onAddInvestment: () -> Unit = {},
    viewModel: InvestmentsViewModel = hiltViewModel()
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val manualAccounts by viewModel.manualAccounts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    InvestmentsContent(
        connections = connections,
        busy = busy,
        loaded = loaded,
        error = error,
        onBack = onNavigateBack,
        onAdd = onAddInvestment,
        onRefreshAll = viewModel::refreshAll,
        onRetry = viewModel::reload,
        manualAccounts = manualAccounts,
        onManageManualAccounts = onManageManualAccounts
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class)
@Composable
internal fun InvestmentsContent(
    connections: List<BrokerConnection>,
    busy: Boolean,
    loaded: Boolean,
    error: BrokerageError?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRefreshAll: () -> Unit,
    onRetry: () -> Unit,
    manualAccounts: List<AccountBalanceEntity> = emptyList(),
    onManageManualAccounts: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val hasHoldings = manualAccounts.isNotEmpty() || connections.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.overview_investments),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = connections.isNotEmpty(),
                navigationContent = { NavigationContent(onBack) },
                actionContent = {
                    if (connections.isNotEmpty()) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onRefreshAll,
                            enabled = !busy,
                            modifier = Modifier.testTag("refresh_holdings")
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.investments_refresh)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (loaded) {
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag("add_investment"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.investments_add))
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = busy,
            onRefresh = onRefreshAll,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(horizontal = Dimensions.Padding.content)
                    .testTag("investments_list"),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (error != null) {
                    item(key = "broker_error") { InvestmentError(error) }
                }
                if (!loaded && !busy) {
                    item(key = "retry") {
                        ListItem(
                            headline = { Text(stringResource(R.string.investments_retry), fontWeight = FontWeight.Medium) },
                            onClick = onRetry,
                            shape = ListItemPosition.Single.toShape(),
                            padding = PaddingValues(0.dp)
                        )
                    }
                }
                if (loaded && hasHoldings) {
                    item(key = "net_worth_banner") {
                        InvestmentNetWorthBanner(manualAccounts, connections)
                    }
                    item(key = "accounts_header") {
                        SectionHeader(title = stringResource(R.string.investments_accounts))
                    }
                    item(key = "account_breakdown") {
                        InvestmentAccountBreakdown(manualAccounts, connections, onManageManualAccounts)
                    }
                }
                if (loaded && !hasHoldings) {
                    item(key = "empty") {
                        ListItem(
                            headline = {
                                Text(stringResource(R.string.investments_empty_title), fontWeight = FontWeight.Medium)
                            },
                            supporting = { Text(stringResource(R.string.investments_empty_body)) },
                            shape = ListItemPosition.Single.toShape(),
                            padding = PaddingValues(0.dp)
                        )
                    }
                }
                connections.forEach { connection ->
                    connectionSection(connection)
                }
                if (manualAccounts.isNotEmpty() && connections.isNotEmpty()) {
                    item(key = "source_note") {
                        Text(
                            stringResource(R.string.overview_source_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentNetWorthBanner(
    manuals: List<AccountBalanceEntity>,
    connections: List<BrokerConnection>
) {
    val totals = mutableMapOf<String, BigDecimal>()
    // Every currency a brokerage account holds
    manuals.byCurrency(LocalAccountHoldings.current).forEach { account ->
        totals[account.currency] = (totals[account.currency] ?: BigDecimal.ZERO) + account.balance
    }
    connections.snapshotTotals().forEach { (currency, amount) ->
        totals[currency] = (totals[currency] ?: BigDecimal.ZERO) + amount
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("investment_net_worth"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                stringResource(R.string.investments_net_worth),
                style = MaterialTheme.typography.titleSmall
            )
            totals.toSortedMap().forEach { (currency, amount) ->
                Text(
                    CurrencyFormatter.formatCurrency(amount, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InvestmentAccountBreakdown(
    manuals: List<AccountBalanceEntity>,
    connections: List<BrokerConnection>,
    onManageManualAccounts: () -> Unit
) {
    val brokerRows = connections.flatMap { connection ->
        connection.accounts.map { account -> connection to account }
    }
    val count = manuals.size + brokerRows.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        manuals.forEachIndexed { index, account ->
            ListItem(
                headline = { Text(account.bankName, fontWeight = FontWeight.Medium) },
                supporting = { Text(accountSubtitle(account)) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(accountBalanceText(account), fontWeight = FontWeight.Medium)
                        accountCurrenciesText(account)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                onClick = onManageManualAccounts,
                shape = ListItemPosition.from(index, count).toShape(),
                padding = PaddingValues(0.dp)
            )
        }
        brokerRows.forEachIndexed { index, (connection, account) ->
            val snapshots = account.snapshotByCurrency().toSortedMap()
            ListItem(
                headline = { Text(connection.label, fontWeight = FontWeight.Medium) },
                supporting = { Text(account.accountId) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        snapshots.forEach { (currency, amount) ->
                            Text(
                                CurrencyFormatter.formatCurrency(amount, currency),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                shape = ListItemPosition.from(manuals.size + index, count).toShape(),
                padding = PaddingValues(0.dp)
            )
        }
    }
}

private fun LazyListScope.connectionSection(connection: BrokerConnection) {
    item(key = "connection_header_${connection.id}") {
        SectionHeader(title = connection.label)
    }
    item(key = "connection_${connection.id}") {
        ListItem(
            headline = {
                Text(stringResource(R.string.investments_synced, formattedSyncTime(connection.syncedAt)))
            },
            supporting = {
                val asOf = connection.accounts.map { it.asOf }.distinct().joinToString()
                if (asOf.isNotEmpty()) {
                    Text(stringResource(R.string.investments_as_of, asOf))
                }
            },
            shape = ListItemPosition.Single.toShape(),
            padding = PaddingValues(0.dp)
        )
    }
    connection.accounts.forEach { account ->
        item(key = "account_${connection.id}_${account.accountId}") {
            SectionHeader(title = account.accountId)
        }
        item(key = "account_totals_${connection.id}_${account.accountId}") {
            AccountTotals(account)
        }
        val holdings = account.holdings
        if (holdings.isEmpty()) {
            item(key = "no_positions_${connection.id}_${account.accountId}") {
                ListItem(
                    headline = { Text(stringResource(R.string.investments_no_positions)) },
                    shape = ListItemPosition.Single.toShape(),
                    padding = PaddingValues(0.dp)
                )
            }
        } else {
            itemsIndexed(
                holdings,
                key = { _, holding ->
                    "holding_${connection.id}_${account.accountId}_${holding.instrumentId}_${holding.currency}_${holding.model}"
                }
            ) { index, holding ->
                HoldingRow(holding, ListItemPosition.from(index, holdings.size))
            }
        }
    }
}

@Composable
private fun AccountTotals(account: BrokerageAccount) {
    val currencies = (account.holdings.map { it.currency } + account.cashBalances.map { it.currency })
        .toSortedSet()
    val cashByCurrency = account.cashBalances.associateBy { it.currency }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        if (currencies.isEmpty()) {
            ListItem(
                headline = { Text(stringResource(R.string.investments_as_of, account.asOf)) },
                supporting = { Text(stringResource(R.string.investments_no_positions)) },
                shape = ListItemPosition.Single.toShape(),
                padding = PaddingValues(0.dp)
            )
            return
        }
        currencies.forEachIndexed { index, currency ->
            val holdings = account.holdings.filter { it.currency == currency }
            val cash = cashByCurrency[currency]
            val holdingsTotal = sumDecimals(holdings.map { it.marketValue })
            val cashAmount = cash?.endingCash?.toBigDecimalOrNull()
            val snapshot = when {
                holdingsTotal != null && cashAmount != null -> holdingsTotal + cashAmount
                holdingsTotal != null && cash == null -> holdingsTotal
                else -> null
            }
            val supporting = buildString {
                append(stringResource(R.string.investments_market_total, currency, decimal(holdingsTotal?.toPlainString())))
                if (cashAmount != null) {
                    append('\n')
                    append(
                        if (cashAmount.signum() < 0) {
                            stringResource(R.string.investments_margin_debit, currency, decimal(cashAmount.abs().toPlainString()))
                        } else {
                            stringResource(R.string.investments_cash, currency, decimal(cash.endingCash))
                        }
                    )
                } else {
                    append('\n')
                    append(stringResource(R.string.investments_cash_missing))
                }
            }
            ListItem(
                headline = {
                    Text(
                        text = stringResource(
                            R.string.investments_snapshot_total,
                            currency,
                            decimal(snapshot?.toPlainString())
                        ),
                        fontWeight = FontWeight.Medium
                    )
                },
                supporting = { Text(supporting) },
                shape = ListItemPosition.from(index, currencies.size).toShape(),
                padding = PaddingValues(0.dp)
            )
        }
    }
}

@Composable
private fun HoldingRow(holding: Holding, position: ListItemPosition) {
    val dark = isAppInDarkTheme
    val pnl = holding.unrealizedPnl?.toBigDecimalOrNull()
    val pnlColor = when {
        pnl == null -> MaterialTheme.colorScheme.onSurfaceVariant
        pnl.signum() >= 0 -> if (dark) income_dark else income_light
        else -> if (dark) expense_dark else expense_light
    }
    ListItem(
        headline = { Text(holding.symbol, fontWeight = FontWeight.Medium) },
        supporting = {
            val detail = buildString {
                append(holding.description)
                if (holding.model.isNotBlank()) {
                    append(" · ")
                    append(holding.model)
                }
                append('\n')
                append(stringResource(R.string.investments_quantity, holding.quantity))
            }
            Text(detail)
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.investments_value, holding.currency, decimal(holding.marketValue)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.investments_pnl, holding.currency, decimal(holding.unrealizedPnl)),
                    style = MaterialTheme.typography.bodySmall,
                    color = pnlColor
                )
            }
        },
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
internal fun IbkrConnectDialog(
    busy: Boolean,
    error: BrokerageError?,
    onDismiss: () -> Unit,
    onConnect: (String, String, String) -> Unit
) {
    // Intentionally remember, not rememberSaveable: credentials never enter saved instance state.
    var label by remember { mutableStateOf("IBKR") }
    var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Scaffold(
            topBar = {
                CustomTitleTopAppBar(
                    title = stringResource(R.string.investments_connect_ibkr),
                    scrollBehaviorSmall = scrollBehavior,
                    scrollBehaviorLarge = scrollBehavior,
                    hazeState = hazeState,
                    hasBackButton = true,
                    navigationContent = { NavigationContent(onDismiss) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    stringResource(R.string.investments_setup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.investments_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    uriHandler.openUri(
                        "https://www.interactivebrokers.com/docs/web-api/flex-web-service/client-portal-configuration"
                    )
                }) {
                    Text(stringResource(R.string.investments_help))
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(60) },
                    label = { Text(stringResource(R.string.investments_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim().take(128) },
                    label = { Text("Flex Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("flex_token"),
                    shape = MaterialTheme.shapes.large
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.trim().take(32) },
                    label = { Text("Query ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("flex_query"),
                    shape = MaterialTheme.shapes.large
                )
                if (error != null) InvestmentError(error)
                if (busy) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.investments_syncing))
                }
                Button(
                    onClick = { onConnect(label, token, query) },
                    enabled = !busy && token.matches(Regex("[0-9]{6,128}")) && query.matches(Regex("[0-9]{1,32}")),
                    modifier = Modifier.fillMaxWidth().testTag("save_broker"),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.investments_connect_sync))
                }
            }
        }
    }
}

@Composable
internal fun InvestmentError(error: BrokerageError) {
    val resource = when (error) {
        BrokerageError.INVALID_CREDENTIALS -> R.string.investments_error_credentials
        BrokerageError.EXPIRED_CREDENTIALS -> R.string.investments_error_expired
        BrokerageError.IP_RESTRICTED -> R.string.investments_error_ip
        BrokerageError.INVALID_QUERY -> R.string.investments_error_query
        BrokerageError.REPORT_NOT_READY -> R.string.investments_error_pending
        BrokerageError.RATE_LIMITED -> R.string.investments_error_rate
        BrokerageError.NETWORK -> R.string.investments_error_network
        BrokerageError.INVALID_REPORT -> R.string.investments_error_report
        BrokerageError.UNSUPPORTED_PROVIDER -> R.string.investments_error_provider
        BrokerageError.STORAGE -> R.string.investments_error_storage
        BrokerageError.DUPLICATE_CONNECTION -> R.string.investments_error_duplicate
    }
    Text(
        stringResource(resource),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("broker_error")
    )
}

private fun formattedSyncTime(syncedAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(syncedAt))

private fun sumDecimals(values: List<String?>): BigDecimal? {
    if (values.isEmpty()) return BigDecimal.ZERO
    if (values.any { it == null }) return null
    return values.fold(BigDecimal.ZERO) { n, v -> n + v!!.toBigDecimal() }
}

private fun decimal(value: String?): String = value?.let {
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(it.toBigDecimal())
} ?: "—"
