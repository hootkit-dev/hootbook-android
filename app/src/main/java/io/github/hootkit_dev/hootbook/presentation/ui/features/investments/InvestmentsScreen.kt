@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountBalanceText
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountCurrenciesText
import io.github.hootkit_dev.hootbook.presentation.ui.components.accountSubtitle
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import java.math.BigDecimal

/**
 * Read-only holdings and manual investment accounts. Adding one (manual, or a broker connection
 * under "Broker auto-sync") happens in Add account with the Broker type: [onAddInvestment].
 */
@Composable
fun InvestmentsScreen(
    onNavigateBack: () -> Unit,
    onManageManualAccounts: () -> Unit = {},
    onAddInvestment: () -> Unit = {},
    viewModel: InvestmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.busy || state.selected != null || state.linkTarget != null) {
        viewModel.back()
    }
    InvestmentsContent(
        connections = state.connections,
        busy = state.busy,
        loaded = !state.loading,
        error = state.error,
        onBack = { if (!viewModel.back()) onNavigateBack() },
        onAdd = onAddInvestment,
        onRefreshAll = viewModel::refreshAll,
        onRetry = viewModel::reload,
        manualAccounts = state.investmentAccounts.filter { it.broker == null },
        onManageManualAccounts = onManageManualAccounts,
        selected = state.selected,
        onLink = viewModel::showLink,
    )
    state.linkTarget?.let { ref ->
        AlertDialog(
            onDismissRequest = { viewModel.showLink(null) },
            title = { Text(stringResource(R.string.core_invest_link_title)) },
            text = {
                Column {
                    availableLinkAccounts(ref, state.overview?.accounts.orEmpty()).forEach { account
                        ->
                        TextButton(
                            enabled = !state.busy,
                            onClick = { viewModel.link(ref, account.id) },
                        ) {
                            Text(account.name)
                        }
                    }
                    TextButton(enabled = !state.busy, onClick = { viewModel.link(ref, null) }) {
                        Text(stringResource(R.string.core_invest_unlink))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { viewModel.showLink(null) }) {
                    Text(stringResource(R.string.investments_cancel))
                }
            },
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeApi::class,
)
@Composable
internal fun InvestmentsContent(
    connections: List<BrokerageConnection>,
    busy: Boolean,
    loaded: Boolean,
    error: CoreInvestmentFailure?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRefreshAll: () -> Unit,
    onRetry: () -> Unit,
    manualAccounts: List<Account> = emptyList(),
    onManageManualAccounts: () -> Unit = {},
    selected: BrokerAccountRef? = null,
    onLink: (BrokerAccountRef?) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected, connections, loaded, error) {
        if (selected != null && loaded) {
            var index =
                (if (error != null) 1 else 0) +
                    (if (manualAccounts.isNotEmpty() || connections.isNotEmpty()) 2 else 1)
            for (connection in connections) {
                index += 2
                for (account in connection.accounts) {
                    if (selected == BrokerAccountRef(connection.id, account.accountId)) {
                        listState.scrollToItem(index)
                        return@LaunchedEffect
                    }
                    index += 2 + maxOf(1, account.holdings.size)
                }
            }
        }
    }
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
                            modifier = Modifier.testTag("refresh_holdings"),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.investments_refresh),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (loaded) {
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag("add_investment"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.investments_add),
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = busy,
            onRefresh = onRefreshAll,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier.fillMaxSize()
                        .hazeSource(state = hazeState)
                        .padding(horizontal = Dimensions.Padding.content)
                        .testTag("investments_list"),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (error != null) {
                    item(key = "broker_error") { InvestmentError(error) }
                }
                if (!loaded && !busy) {
                    item(key = "retry") {
                        ListItem(
                            headline = {
                                Text(
                                    stringResource(R.string.investments_retry),
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            onClick = onRetry,
                            shape = ListItemPosition.Single.toShape(),
                            padding = PaddingValues(0.dp),
                        )
                    }
                }
                if (loaded && hasHoldings) {
                    item(key = "accounts_header") {
                        SectionHeader(title = stringResource(R.string.investments_accounts))
                    }
                    item(key = "account_breakdown") {
                        InvestmentAccountBreakdown(
                            manualAccounts,
                            connections,
                            onManageManualAccounts,
                            onLink,
                        )
                    }
                }
                if (loaded && !hasHoldings) {
                    item(key = "empty") {
                        ListItem(
                            headline = {
                                Text(
                                    stringResource(R.string.investments_empty_title),
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            supporting = { Text(stringResource(R.string.investments_empty_body)) },
                            shape = ListItemPosition.Single.toShape(),
                            padding = PaddingValues(0.dp),
                        )
                    }
                }
                connections.forEach { connection -> connectionSection(connection, onLink) }
                if (manualAccounts.isNotEmpty() && connections.isNotEmpty()) {
                    item(key = "source_note") {
                        Text(
                            stringResource(R.string.overview_source_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentAccountBreakdown(
    manuals: List<Account>,
    connections: List<BrokerageConnection>,
    onManageManualAccounts: () -> Unit,
    onLink: (BrokerAccountRef?) -> Unit,
) {
    val brokerRows =
        connections.flatMap { connection ->
            connection.accounts.map { account -> connection to account }
        }
    val count = manuals.size + brokerRows.size
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        manuals.forEachIndexed { index, account ->
            ListItem(
                headline = { Text(account.name, fontWeight = FontWeight.Medium) },
                supporting = { Text(accountSubtitle(account)) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(accountBalanceText(account), fontWeight = FontWeight.Medium)
                        accountCurrenciesText(account)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!account.totalComplete)
                            Text(
                                stringResource(R.string.core_invest_incomplete),
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                },
                onClick = onManageManualAccounts,
                shape = ListItemPosition.from(index, count).toShape(),
                padding = PaddingValues(0.dp),
            )
        }
        brokerRows.forEachIndexed { index, (connection, account) ->
            val snapshots = account.valueByCurrency
            ListItem(
                headline = { Text(connection.label, fontWeight = FontWeight.Medium) },
                supporting = { Text(account.accountId) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        snapshots.forEach { money ->
                            Text(
                                CurrencyFormatter.formatCurrency(money.amount, money.currency),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                onClick = { onLink(BrokerAccountRef(connection.id, account.accountId)) },
                shape = ListItemPosition.from(manuals.size + index, count).toShape(),
                padding = PaddingValues(0.dp),
            )
        }
    }
}

private fun LazyListScope.connectionSection(
    connection: BrokerageConnection,
    onLink: (BrokerAccountRef?) -> Unit,
) {
    item(key = "connection_header_${connection.id}") { SectionHeader(title = connection.label) }
    item(key = "connection_${connection.id}") {
        ListItem(
            headline = {
                Text(
                    stringResource(
                        R.string.investments_synced,
                        connection.syncedAt
                            ?.coreDateText(
                                java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                                    java.time.format.FormatStyle.SHORT
                                )
                            )
                            .orEmpty(),
                    )
                )
            },
            supporting = {
                connection.lastError?.let {
                    InvestmentError(CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, it))
                }
                val asOf =
                    connection.accounts.map { it.asOf.coreDateText() }.distinct().joinToString()
                if (asOf.isNotEmpty()) {
                    Text(stringResource(R.string.investments_as_of, asOf))
                }
            },
            shape = ListItemPosition.Single.toShape(),
            padding = PaddingValues(0.dp),
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
                    padding = PaddingValues(0.dp),
                )
            }
        } else {
            itemsIndexed(
                holdings,
                key = { _, holding ->
                    "holding_${connection.id}_${account.accountId}_${holding.instrumentId}_${holding.currency}_${holding.model}"
                },
            ) { index, holding ->
                HoldingRow(holding, ListItemPosition.from(index, holdings.size))
            }
        }
    }
}

@Composable
private fun AccountTotals(account: BrokerageAccount) {
    val currencies =
        (account.valueByCurrency.map { it.currency } + account.cash.map { it.currency })
            .toSortedSet()
            .toList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
        if (!account.complete)
            Text(
                stringResource(R.string.core_invest_incomplete),
                color = MaterialTheme.colorScheme.error,
            )
        currencies.forEachIndexed { index, currency ->
            val value = account.valueByCurrency.find { it.currency == currency }
            val cash = account.cash.find { it.currency == currency }
            ListItem(
                headline = {
                    Text(
                        stringResource(
                            R.string.investments_snapshot_total,
                            currency,
                            decimal(value?.amount),
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                },
                supporting = {
                    Text(
                        if (cash != null && cash.endingCash.signum() < 0)
                            stringResource(
                                R.string.investments_margin_debit,
                                currency,
                                decimal(cash.endingCash.abs()),
                            )
                        else if (cash != null)
                            stringResource(
                                R.string.investments_cash,
                                currency,
                                decimal(cash.endingCash),
                            )
                        else stringResource(R.string.investments_cash_missing)
                    )
                },
                shape = ListItemPosition.from(index, currencies.size).toShape(),
                padding = PaddingValues(0.dp),
            )
        }
        if (currencies.isEmpty())
            ListItem(
                headline = {
                    Text(stringResource(R.string.investments_as_of, account.asOf.coreDateText()))
                },
                supporting = { Text(stringResource(R.string.investments_no_positions)) },
                shape = ListItemPosition.Single.toShape(),
                padding = PaddingValues(0.dp),
            )
    }
}

@Composable
private fun HoldingRow(holding: Holding, position: ListItemPosition) {
    val dark = isAppInDarkTheme
    val pnl = holding.unrealizedPnl
    val pnlColor =
        when {
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
                append(
                    stringResource(R.string.investments_quantity, holding.quantity.toPlainString())
                )
            }
            Text(detail)
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(
                        R.string.investments_value,
                        holding.currency,
                        decimal(holding.marketValue),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(
                        R.string.investments_pnl,
                        holding.currency,
                        decimal(holding.unrealizedPnl),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = pnlColor,
                )
            }
        },
        shape = position.toShape(),
        padding = PaddingValues(0.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
internal fun IbkrConnectDialog(
    busy: Boolean,
    error: CoreInvestmentFailure?,
    onDismiss: () -> Unit,
    onConnect: (String, String, String) -> Unit,
) {
    // Intentionally remember, not rememberSaveable: credentials never enter saved instance state.
    val window = LocalContext.current.activityWindow()
    DisposableEffect(window) {
        val release = window?.let(::protectInvestmentWindow)
        onDispose { release?.invoke() }
    }
    var label by remember { mutableStateOf("IBKR") }
    var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
    ) {
        Scaffold(
            topBar = {
                CustomTitleTopAppBar(
                    title = stringResource(R.string.investments_connect_ibkr),
                    scrollBehaviorSmall = scrollBehavior,
                    scrollBehaviorLarge = scrollBehavior,
                    hazeState = hazeState,
                    hasBackButton = true,
                    navigationContent = { NavigationContent(onDismiss) },
                )
            }
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .hazeSource(state = hazeState)
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    stringResource(R.string.investments_setup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.investments_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        uriHandler.openUri(
                            "https://www.interactivebrokers.com/docs/web-api/flex-web-service/client-portal-configuration"
                        )
                    },
                ) {
                    Text(stringResource(R.string.investments_help))
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(60) },
                    label = { Text(stringResource(R.string.investments_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
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
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.trim().take(32) },
                    label = { Text("Query ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("flex_query"),
                    shape = MaterialTheme.shapes.large,
                )
                if (error != null) InvestmentError(error)
                if (busy) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.investments_syncing))
                }
                Button(
                    onClick = {
                        onConnect(label, token, query)
                        token = ""
                        query = ""
                    },
                    enabled =
                        !busy &&
                            token.matches(Regex("[0-9]{6,128}")) &&
                            query.matches(Regex("[0-9]{1,32}")),
                    modifier = Modifier.fillMaxWidth().testTag("save_broker"),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.investments_connect_sync))
                }
            }
        }
    }
}

@Composable
internal fun InvestmentError(error: CoreInvestmentFailure) {
    Text(
        investmentFailureText(error),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("broker_error"),
    )
}

private fun decimal(value: BigDecimal?): String =
    value?.let { CurrencyFormatter.formatAmount(it) } ?: "—"

@Composable
internal fun investmentFailureText(failure: CoreInvestmentFailure): String =
    stringResource(
        when (failure.kind) {
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
            null ->
                when (failure.code) {
                    InvestmentFailureCode.INVALID -> R.string.core_invest_error_invalid
                    InvestmentFailureCode.CONFLICT -> R.string.core_invest_error_conflict
                    InvestmentFailureCode.NOT_FOUND -> R.string.core_invest_error_not_found
                    InvestmentFailureCode.NETWORK -> R.string.core_invest_error_network
                    InvestmentFailureCode.STORAGE -> R.string.core_invest_error_storage
                    InvestmentFailureCode.PLATFORM -> R.string.core_invest_error_platform
                    InvestmentFailureCode.CANCELLED -> R.string.core_invest_cancelled_notice
                    else -> R.string.core_invest_error_unknown
                }
        }
    )
