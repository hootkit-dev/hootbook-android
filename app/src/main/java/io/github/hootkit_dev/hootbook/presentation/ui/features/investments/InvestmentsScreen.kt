@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
)

package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.hootbookTextFieldColors
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
 * Investments: what each investment account is worth, the broker connections and their accounts,
 * the ledger's investment accounts, and brokers to connect; then one broker account's holdings
 * and cash, and one holding's details. Snapshots update only when the user refreshes.
 */
@Composable
fun InvestmentsScreen(
    onNavigateBack: () -> Unit,
    onManageManualAccounts: () -> Unit = {},
    onAddInvestment: () -> Unit = onManageManualAccounts,
    viewModel: InvestmentsViewModel = hiltViewModel(),
    onAccountHistory: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chinese = LocalConfiguration.current.locales[0].language == "zh"

    fun providerName(id: String): String {
        val provider = state.providers.find { it.id == id }
        val institution = state.institutions.find { it.key == provider?.institutionKey }
        return institution?.let { if (chinese) it.chineseName else it.englishName } ?: id
    }

    BackHandler(
        state.busy ||
            state.selected != null ||
            state.connectProvider != null ||
            state.linkTarget != null ||
            state.disconnectTarget != null ||
            state.holding != null
    ) {
        viewModel.back()
    }

    InvestmentsContent(
        state = state,
        providerName = ::providerName,
        onBack = { if (!viewModel.back()) onNavigateBack() },
        onRefresh = viewModel::refresh,
        onRefreshAll = viewModel::refreshAll,
        onCancel = viewModel::cancelOperation,
        onClearError = viewModel::clearError,
        onConnect = viewModel::showConnect,
        onSelect = viewModel::selectAccount,
        onHolding = viewModel::selectHolding,
        onLink = viewModel::showLink,
        onDisconnect = viewModel::showDisconnect,
        onManageAccounts = { if (!state.busy) onManageManualAccounts() },
        onAccountHistory = { if (!state.busy) onAccountHistory(it) },
        onAddInvestment = { if (!state.busy) onAddInvestment() },
    )

    // Connect dialog
    state.connectProvider?.let { id ->
        val provider = BrokerProviderRegistry.find(id)
        if (provider != null) {
            provider.ConnectDialog(
                busy = state.busy,
                error = state.error,
                onDismiss = { viewModel.showConnect(null) },
                onConnect = { label, fields -> viewModel.connect(id, label, fields) },
            )
        } else {
            BrokerConnectForm(
                busy = state.busy,
                error = state.error,
                providerId = id,
                onDismiss = { viewModel.showConnect(null) },
                onConnect = { label, fields -> viewModel.connect(id, label, fields) },
            )
        }
    }

    // Link dialog
    state.linkTarget?.let { ref ->
        AlertDialog(
            onDismissRequest = { viewModel.showLink(null) },
            title = { Text(stringResource(R.string.core_invest_link_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.core_invest_link_warning))
                    val candidates = availableLinkAccounts(ref, state.overview?.accounts.orEmpty())
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.core_invest_no_link_accounts))
                    }
                    candidates.forEach { account ->
                        TextButton(
                            enabled = !state.busy,
                            onClick = { viewModel.link(ref, account.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${account.name} · ${account.mainCurrency}")
                        }
                    }
                    if (linkedAccount(ref, state.connections)?.linkedAccountId != null) {
                        TextButton(
                            enabled = !state.busy,
                            onClick = { viewModel.link(ref, null) },
                        ) {
                            Text(stringResource(R.string.core_invest_unlink))
                        }
                    }
                    state.error?.let {
                        Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { viewModel.showLink(null) }) {
                    Text(stringResource(R.string.core_invest_close))
                }
            },
        )
    }

    // Disconnect dialog
    state.disconnectTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { viewModel.showDisconnect(null) },
            title = { Text(stringResource(R.string.core_invest_disconnect)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.core_invest_disconnect_warning))
                    state.error?.let {
                        Text(investmentFailureText(it), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { viewModel.disconnect(id) }) {
                    Text(
                        stringResource(R.string.core_invest_disconnect),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = { viewModel.showDisconnect(null) }) {
                    Text(stringResource(R.string.core_invest_cancel))
                }
            },
        )
    }
}

@Composable
internal fun InvestmentsContent(
    state: InvestmentsUiState,
    providerName: (String) -> String,
    onBack: () -> Unit,
    onRefresh: (Long) -> Unit,
    onRefreshAll: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit,
    onConnect: (String?) -> Unit,
    onSelect: (BrokerAccountRef?) -> Unit,
    onHolding: (CoreHoldingKey?) -> Unit,
    onLink: (BrokerAccountRef?) -> Unit,
    onDisconnect: (Long?) -> Unit,
    onManageAccounts: () -> Unit,
    onAccountHistory: (String) -> Unit,
    onAddInvestment: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val holding = state.selectedHolding
    val account = state.account

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title =
                    when {
                        holding != null -> holding.symbol
                        account != null -> account.accountId
                        else -> stringResource(R.string.core_invest_title)
                    },
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = state.selected == null && state.connections.isNotEmpty(),
                navigationContent = { NavigationContent(onBack) },
                actionContent = {
                    if (state.selected == null && state.connections.isNotEmpty()) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = onRefreshAll,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("refresh_holdings"),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.core_invest_refresh_all),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.loading && state.selected == null) {
                FloatingActionButton(
                    onClick = onAddInvestment,
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
            isRefreshing = state.busy,
            onRefresh = { if (!state.busy) onRefreshAll() },
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
                if (state.busy) {
                    item(key = "progress") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (state.cancelling) R.string.core_invest_cancelling
                                        else R.string.core_invest_working
                                    ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(enabled = !state.cancelling, onClick = onCancel) {
                                Text(stringResource(R.string.core_invest_cancel_request))
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    item(key = "error") {
                        InvestmentError(error)
                    }
                }
                if (state.cancellationNotice) {
                    item(key = "cancelled") {
                        Text(
                            text = stringResource(R.string.core_invest_cancelled_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    holding != null && account != null -> {
                        item(key = "holding_detail") {
                            HoldingDetails(holding, account)
                        }
                    }
                    account != null -> {
                        brokerAccountItems(
                            account = account,
                            state = state,
                            enabled = !state.busy,
                            onRefresh = onRefresh,
                            onLink = onLink,
                            onAccountHistory = onAccountHistory,
                            onHolding = onHolding,
                        )
                    }
                    else -> {
                        overviewItems(
                            state = state,
                            enabled = !state.busy,
                            providerName = providerName,
                            onRefresh = onRefresh,
                            onDisconnect = onDisconnect,
                            onSelect = onSelect,
                            onAccountHistory = onAccountHistory,
                            onManageAccounts = onManageAccounts,
                            onConnect = onConnect,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.overviewItems(
    state: InvestmentsUiState,
    enabled: Boolean,
    providerName: (String) -> String,
    onRefresh: (Long) -> Unit,
    onDisconnect: (Long?) -> Unit,
    onSelect: (BrokerAccountRef?) -> Unit,
    onAccountHistory: (String) -> Unit,
    onManageAccounts: () -> Unit,
    onConnect: (String?) -> Unit,
) {
    // 1. Net worth card (if accounts exist)
    if (state.connections.isNotEmpty() || state.investmentAccounts.isNotEmpty()) {
        item(key = "net_worth") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.investments_net_worth),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    state.investmentAccounts.forEach { ledger ->
                        InvestmentValue(
                            label = ledger.name,
                            value = CurrencyFormatter.format(ledger.total.amount, ledger.total.currency),
                        )
                    }
                    state.connections.forEach { connection ->
                        connection.accounts.filter { it.linkedAccountId == null }.forEach { account ->
                            InvestmentValue(
                                label = "${connection.label} · ${account.accountId}",
                                value =
                                    account.total?.let { CurrencyFormatter.format(it.amount, it.currency) }
                                        ?: stringResource(R.string.core_invest_total_unavailable),
                            )
                        }
                    }
                    if (state.investmentAccounts.size + state.connections.sumOf { c -> c.accounts.count { it.linkedAccountId == null } } > 1) {
                        Text(
                            text = stringResource(R.string.core_parity_investment_total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }

    // 2. Guide Header (when empty)
    if (state.connections.isEmpty() && state.investmentAccounts.isEmpty() && !state.loading) {
        item(key = "empty_guide") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.investments_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.investments_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 3. Broker Connections section (for each connected broker, if any)
    state.connections.forEach { connection ->
        item(key = "connection_${connection.id}") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = connection.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = buildString {
                                append(providerName(connection.providerId))
                                connection.syncedAt?.let {
                                    append(" · ")
                                    append(stringResource(R.string.core_invest_synced_at, investmentTime(it)))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BrokerOverflowMenu(
                        enabled = enabled,
                        onRefresh = { onRefresh(connection.id) },
                        onDisconnect = { onDisconnect(connection.id) },
                    )
                }

                val failure =
                    state.connectionErrors[connection.id]
                        ?: connection.lastError?.let {
                            CoreInvestmentFailure(InvestmentFailureCode.BROKERAGE, it)
                        }
                if (failure != null) {
                    InvestmentError(failure)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp),
                ) {
                    connection.accounts.forEachIndexed { index, account ->
                        ListItem(
                            headline = { Text(account.accountId, fontWeight = FontWeight.Medium) },
                            supporting = {
                                Text(stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)))
                            },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text =
                                            account.total?.let { CurrencyFormatter.format(it.amount, it.currency) }
                                                ?: "—",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            onClick = {
                                if (enabled) onSelect(BrokerAccountRef(connection.id, account.accountId))
                            },
                            shape = ListItemPosition.from(index, connection.accounts.size).toShape(),
                            padding = PaddingValues(0.dp),
                        )
                    }
                }
            }
        }
    }

    // 4. Investment ledger accounts section
    item(key = "ledger_accounts") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(title = stringResource(R.string.core_invest_ledger_accounts))
            val totalCount = state.investmentAccounts.size + 1
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                state.investmentAccounts.forEachIndexed { index, ledger ->
                    ListItem(
                        headline = { Text(ledger.name, fontWeight = FontWeight.Medium) },
                        supporting = {
                            val details =
                                listOfNotNull(
                                    ledger.securities?.let {
                                        stringResource(
                                            R.string.core_invest_securities,
                                            CurrencyFormatter.format(it.amount, it.currency),
                                        )
                                    },
                                    ledger.broker?.let {
                                        stringResource(
                                            R.string.core_invest_linked_broker,
                                            it.brokerAccountId,
                                            investmentDate(it.asOf),
                                        )
                                    },
                                    if (!ledger.totalComplete)
                                        stringResource(R.string.core_invest_incomplete)
                                    else null,
                                ).joinToString(" · ").ifBlank {
                                    stringResource(R.string.core_invest_cash_history)
                                }
                            Text(details)
                        },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text =
                                        CurrencyFormatter.format(
                                            ledger.total.amount,
                                            ledger.total.currency,
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = { if (enabled) onAccountHistory(ledger.id) },
                        shape = ListItemPosition.from(index, totalCount).toShape(),
                        padding = PaddingValues(0.dp),
                    )
                }

                // Entry row: [🏛️ AccountBalance Icon with agy rounded badge] "Add or manage investment accounts"
                ListItem(
                    leading = {
                        Box(
                            modifier =
                                Modifier.size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                    headline = {
                        Text(
                            text = stringResource(R.string.core_invest_manage_accounts),
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onManageAccounts,
                    shape = ListItemPosition.from(state.investmentAccounts.size, totalCount).toShape(),
                    padding = PaddingValues(0.dp),
                )
            }
        }
    }

    // 5. Connect a broker section
    item(key = "connect_broker_section") {
        val providers = BrokerProviderRegistry.getProviders()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(title = stringResource(R.string.core_invest_connect_broker))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                providers.forEachIndexed { index, provider ->
                    ListItem(
                        leading = {
                            Box(
                                modifier =
                                    Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        },
                        headline = {
                            Text(
                                text = stringResource(provider.nameRes),
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        supporting = {
                            Text(stringResource(provider.descriptionRes))
                        },
                        trailing = {
                            Text(
                                text = stringResource(R.string.core_invest_connect) + " >",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { if (enabled) onConnect(provider.id) },
                        shape = ListItemPosition.from(index, providers.size).toShape(),
                        padding = PaddingValues(0.dp),
                    )
                }
            }
        }
    }

    // 6. Footer note
    item(key = "footer_note") {
        Text(
            text = stringResource(R.string.core_invest_snapshot_note),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InvestmentValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BrokerOverflowMenu(
    enabled: Boolean,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_invest_refresh)) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.core_invest_disconnect),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.LinkOff,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDisconnect()
                },
            )
        }
    }
}

private fun LazyListScope.brokerAccountItems(
    account: BrokerageAccount,
    state: InvestmentsUiState,
    enabled: Boolean,
    onRefresh: (Long) -> Unit,
    onLink: (BrokerAccountRef?) -> Unit,
    onAccountHistory: (String) -> Unit,
    onHolding: (CoreHoldingKey?) -> Unit,
) {
    val ref = state.selected ?: return
    item("snapshot") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SnapshotCard(account)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onRefresh(ref.connectionId) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.core_invest_refresh)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
                AssistChip(
                    onClick = { onLink(ref) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.core_invest_link_title)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, null, Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
                account.linkedAccountId?.let { id ->
                    AssistChip(
                        onClick = { onAccountHistory(id) },
                        enabled = enabled,
                        label = { Text(stringResource(R.string.core_invest_cash_history)) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.ShowChart,
                                null,
                                Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }

    item("holdings_header") {
        SectionHeader(title = stringResource(R.string.investments_accounts))
    }

    item("holdings_search") {
        var filter by rememberSaveable(state.selected) { mutableStateOf("") }
        val holdings =
            account.holdings.filter {
                filter.isBlank() ||
                    it.symbol.contains(filter, true) ||
                    it.description.contains(filter, true)
            }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBarBox(
                searchQuery = TextFieldValue(filter),
                onSearchQueryChange = { filter = it.text },
                label = { Text(stringResource(R.string.core_invest_search_holdings)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
            )
            if (holdings.isEmpty()) {
                Text(
                    text = stringResource(R.string.core_invest_no_holdings),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                holdings.forEachIndexed { index, holding ->
                    HoldingRow(
                        holding = holding,
                        position = ListItemPosition.from(index, holdings.size),
                        onClick = { if (enabled) onHolding(CoreHoldingKey.of(holding)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(account: BrokerageAccount) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    account.total?.let { CurrencyFormatter.format(it.amount, it.currency) }
                        ?: stringResource(R.string.core_invest_total_unavailable),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!account.complete) {
                Text(
                    text = stringResource(R.string.core_invest_incomplete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            account.valueByCurrency.forEach {
                Text(
                    text =
                        stringResource(
                            R.string.core_invest_original_value,
                            CurrencyFormatter.format(it.amount, it.currency),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HoldingDetails(holding: Holding, account: BrokerageAccount) {
    val money = { value: BigDecimal? ->
        value?.let { CurrencyFormatter.format(it, holding.currency) } ?: "—"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = holding.description,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        val details =
            listOf(
                stringResource(R.string.core_invest_as_of, investmentDate(account.asOf)),
                stringResource(R.string.core_invest_quantity, investmentDecimal(holding.quantity)),
                stringResource(R.string.core_invest_market_value, money(holding.marketValue)),
                stringResource(R.string.core_invest_cost_basis, money(holding.costBasis)),
                stringResource(R.string.core_invest_pnl, money(holding.unrealizedPnl)),
                stringResource(R.string.core_invest_asset_class, holding.assetClass.ifEmpty { "—" }),
                stringResource(R.string.core_invest_model, holding.model.ifEmpty { "—" }),
                stringResource(R.string.core_invest_instrument_id, holding.instrumentId),
            )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            details.forEachIndexed { index, line ->
                ListItem(
                    headline = { Text(line, fontWeight = FontWeight.Normal) },
                    shape = ListItemPosition.from(index, details.size).toShape(),
                    padding = PaddingValues(0.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.core_invest_snapshot_note),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HoldingRow(
    holding: Holding,
    position: ListItemPosition,
    onClick: () -> Unit = {},
) {
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
                    stringResource(
                        R.string.investments_quantity,
                        investmentDecimal(holding.quantity),
                    )
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
                    text =
                        holding.marketValue?.let {
                            CurrencyFormatter.format(it, holding.currency)
                        } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        holding.unrealizedPnl?.let {
                            (if (it.signum() > 0) "+" else "") +
                                CurrencyFormatter.format(it, holding.currency)
                        } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = pnlColor,
                )
            }
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp),
    )
}

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
                TextField(
                    value = label,
                    onValueChange = { label = it.take(60) },
                    label = { Text(stringResource(R.string.investments_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = hootbookTextFieldColors(),
                )
                TextField(
                    value = token,
                    onValueChange = { token = it.trim().take(128) },
                    label = { Text("Flex Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("flex_token"),
                    shape = MaterialTheme.shapes.large,
                    colors = hootbookTextFieldColors(),
                )
                TextField(
                    value = query,
                    onValueChange = { query = it.trim().take(32) },
                    label = { Text("Query ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("flex_query"),
                    shape = MaterialTheme.shapes.large,
                    colors = hootbookTextFieldColors(),
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
