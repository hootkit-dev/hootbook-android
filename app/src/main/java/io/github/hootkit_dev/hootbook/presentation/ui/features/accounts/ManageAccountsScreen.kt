@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.runtime.key
import io.github.hootkit_dev.hootbook.presentation.ui.theme.successColor
import io.github.hootkit_dev.hootbook.presentation.ui.theme.warningColor
import androidx.compose.material3.ripple
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.maskAccountNumber
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.EyeSlash
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.features.investments.BrokerConnectForm
import io.github.hootkit_dev.hootbook.presentation.ui.features.investments.InvestmentsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountDetail: (String) -> Unit,
    onNavigateToAddAccount: (AccountCategory?) -> Unit,
    onBrokerage: (String, String) -> Unit,
    onNavigateToLendBorrow: (String?) -> Unit = {},
    category: AccountCategory? = null,
    manageAccountsViewModel: ManageAccountsViewModel = hiltViewModel(),
    investmentsViewModel: InvestmentsViewModel = hiltViewModel(),
) {
    val uiState by manageAccountsViewModel.uiState.collectAsStateWithLifecycle()
    val investmentsState by investmentsViewModel.state.collectAsStateWithLifecycle()
    val accounts = uiState.overview?.accounts.orEmpty().filter { category == null || it.category() == category }
    val connections = uiState.connections.takeIf { category == null || category == AccountCategory.INVESTMENTS }.orEmpty()
    val sections = remember(accounts) { accounts.accountSections() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val showFloatingLabel by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 } }
    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(operationFailed); manageAccountsViewModel.clearError() }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(category?.titleRes ?: R.string.title_accounts),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddAccount(category) }, expanded = showFloatingLabel,
                icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_account_fab_desc)) },
                text = { Text(stringResource(R.string.add_account_fab_desc)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            if (accounts.isEmpty() && connections.isEmpty() && !uiState.isLoading) {
                Column(Modifier.fillMaxSize().padding(paddingValues), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AccountBalance, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(R.string.no_accounts_yet), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.add_account_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category == null) InvestmentsEmptySection(onAdd = { onNavigateToAddAccount(AccountCategory.INVESTMENTS) }, showTitle = false, modifier = Modifier.padding(horizontal = Dimensions.Padding.content))
                }
            } else {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Dimensions.Padding.content, end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                        bottom = 116.dp + paddingValues.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (category == null) {
                        uiState.netWorthHistory?.takeIf { it.points.isNotEmpty() }?.let { history ->
                            item(key = "net_worth_history") {
                                NetWorthHistoryCard(
                                    netWorth = uiState.overview?.netWorth,
                                    history = history,
                                    period = uiState.netWorthPeriod,
                                    customRange = uiState.netWorthCustomRange,
                                    step = uiState.netWorthStep,
                                    onSelectPeriod = manageAccountsViewModel::selectNetWorthPeriod,
                                    onSelectCustomRange = manageAccountsViewModel::setNetWorthCustomRange,
                                    onSelectStep = manageAccountsViewModel::selectNetWorthStep,
                                )
                            }
                        }
                        uiState.debts?.takeIf { it.receivable.signum() != 0 || it.payable.signum() != 0 }?.let { loans ->
                            item(key = "loans") {
                                io.github.hootkit_dev.hootbook.presentation.ui.components.LendBorrowRow(
                                    summary = loans,
                                    currency = loans.currency,
                                    onClick = { onNavigateToLendBorrow(null) },
                                    onLentClick = { onNavigateToLendBorrow(io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter.YOU_GET.name) },
                                    onBorrowedClick = { onNavigateToLendBorrow(io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter.YOU_OWE.name) },
                                )
                                if (!loans.complete) {
                                    Text(
                                        stringResource(R.string.core_complete_incomplete),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    val visibleAccountIds = accounts.filter { !it.archived }.mapTo(hashSetOf()) { it.id }
                    sections.forEach { (kind, members) ->
                        val standaloneSnapshotCount = if (kind == AccountCategory.INVESTMENTS) {
                            connections.sumOf { conn ->
                                if (conn.accounts.isEmpty()) 1
                                else conn.accounts.count { it.linkedAccountId == null || it.linkedAccountId !in visibleAccountIds }
                            }
                        } else 0
                        if (members.isNotEmpty() || (kind == AccountCategory.INVESTMENTS && connections.isNotEmpty())) {
                            item(key = "header_$kind") {
                                val count = members.size + standaloneSnapshotCount
                                Text(
                                    text = stringResource(category?.titleRes ?: kind.titleRes) + if (count > 0) " · $count" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(members, key = { "account_${it.id}" }) { account ->
                                CompactAccountCard(
                                    account = account,
                                    isMain = account.isDefault,
                                    onClick = { onNavigateToAccountDetail(account.id) }
                                ) {
                                    if (account.kind == AccountKind.CREDIT_CARD) {
                                        AccountCreditDetails(account)
                                    }
                                    account.broker?.let { broker ->
                                        TextButton(
                                            onClick = { onBrokerage(broker.connectionId, broker.brokerAccountId) },
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .testTag("broker_connection_${broker.connectionId}_${broker.brokerAccountId}")
                                        ) {
                                            Text(
                                                text = listOfNotNull(
                                                    stringResource(R.string.overview_investments),
                                                    broker.brokerAccountId.takeIf { it.isNotBlank() },
                                                    broker.asOf?.coreDateText(),
                                                ).joinToString(" · "),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                            if (kind == AccountCategory.INVESTMENTS) {
                                connections.forEach { connection ->
                                    connection.accounts
                                        .filter { it.linkedAccountId == null || it.linkedAccountId !in visibleAccountIds }
                                        .forEach { snapshot ->
                                            item(key = "conn_${connection.id}_${snapshot.accountId}") {
                                                io.github.hootkit_dev.hootbook.presentation.ui.features.investments.BrokerageSnapshotRow(
                                                    connection,
                                                    snapshot,
                                                    onClick = { onBrokerage(connection.id, snapshot.accountId) }
                                                )
                                            }
                                        }
                                    if (connection.accounts.isEmpty()) {
                                        item(key = "conn_${connection.id}") {
                                            val subtitle = when {
                                                !connection.hasCredentials -> stringResource(R.string.core_invest_missing_credentials_hint)
                                                connection.syncedAt == null -> stringResource(R.string.core_invest_needs_first_refresh_hint)
                                                else -> null
                                            }
                                            io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem(
                                                headline = { Text(connection.label) },
                                                supporting = subtitle?.let { { Text(it) } },
                                                leading = {
                                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Rounded.Link,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                trailing = {
                                                    if (!connection.hasCredentials) {
                                                        TextButton(
                                                            onClick = { investmentsViewModel.showSetCredentials(connection.id) },
                                                            enabled = !investmentsState.busy,
                                                            shapes = ButtonDefaults.shapes(),
                                                        ) {
                                                            Text(stringResource(R.string.core_invest_set_credentials))
                                                        }
                                                    } else {
                                                        TextButton(
                                                            onClick = { investmentsViewModel.refresh(connection.id) },
                                                            enabled = !investmentsState.busy,
                                                            shapes = ButtonDefaults.shapes(),
                                                        ) {
                                                            Text(stringResource(R.string.core_invest_refresh))
                                                        }
                                                    }
                                                },
                                                onClick = { onBrokerage(connection.id, "") },
                                                padding = PaddingValues(0.dp),
                                            )
                                        }
                                    }
                                }
                                if (connections.isNotEmpty()) {
                                    item(key = "investments_daily_auto_sync") {
                                        PreferenceSwitch(
                                            title = stringResource(R.string.core_invest_daily_auto_sync_title),
                                            subtitle = stringResource(R.string.core_invest_daily_auto_sync_subtitle),
                                            checked = investmentsState.dailyAutoSyncEnabled,
                                            onCheckedChange = investmentsViewModel::setDailyAutoSync,
                                            padding = PaddingValues(0.dp),
                                            isSingle = true,
                                        )
                                    }
                                }
                                item(key = "investments_add_broker") {
                                    InvestmentsEmptySection(
                                        onAdd = { onNavigateToAddAccount(AccountCategory.INVESTMENTS) },
                                        showTitle = false
                                    )
                                }
                            }
                        } else if (kind == AccountCategory.INVESTMENTS && category == null) {
                            item(key = "header_investments") {
                                Text(
                                    text = stringResource(R.string.overview_investments),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                                )
                            }
                            item(key = "investment_empty") {
                                InvestmentsEmptySection(
                                    onAdd = { onNavigateToAddAccount(AccountCategory.INVESTMENTS) },
                                    showTitle = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    investmentsState.credentialsTarget?.let { connection ->
        BrokerConnectForm(
            providerId = connection.providerId,
            busy = investmentsState.busy,
            error = investmentsState.error,
            onDismiss = {
                if (investmentsState.busy) investmentsViewModel.cancelOperation()
                else investmentsViewModel.showSetCredentials(null)
                investmentsViewModel.clearError()
            },
            onConnect = { _, fields -> investmentsViewModel.setCredentials(connection.id, fields) },
        )
    }
}

@Composable
private fun NetWorthHistoryCard(
    netWorth: Money?,
    history: NetWorthHistory,
    period: io.github.hootkit_dev.hootbook.presentation.common.TimePeriod,
    customRange: Pair<java.time.LocalDate, java.time.LocalDate>?,
    step: HistoryStep,
    onSelectPeriod: (io.github.hootkit_dev.hootbook.presentation.common.TimePeriod) -> Unit,
    onSelectCustomRange: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    onSelectStep: (HistoryStep) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    val blurEffects = LocalBlurEffects.current
    val latestAmount = netWorth?.amount ?: history.points.lastOrNull()?.netWorth
    val currency = netWorth?.currency ?: history.currency
    val timePeriods = remember { io.github.hootkit_dev.hootbook.presentation.common.TimePeriod.entries }
    val customRangeLabel = remember(customRange) {
        io.github.hootkit_dev.hootbook.utils.DateRangeUtils.formatDateRange(customRange)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.net_worth_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    latestAmount?.let { amount ->
                        Text(
                            CoreMoneyPresentation.formatCurrency(amount, currency),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(timePeriods) { item ->
                            val isSelected =
                                if (item == io.github.hootkit_dev.hootbook.presentation.common.TimePeriod.CUSTOM) {
                                    period == item && customRange != null
                                } else {
                                    period == item
                                }
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (item == io.github.hootkit_dev.hootbook.presentation.common.TimePeriod.CUSTOM) {
                                        showDateRangePicker = true
                                    } else {
                                        onSelectPeriod(item)
                                    }
                                },
                                label = {
                                    Text(
                                        if (item == io.github.hootkit_dev.hootbook.presentation.common.TimePeriod.CUSTOM && customRangeLabel != null) {
                                            customRangeLabel
                                        } else {
                                            stringResource(item.labelRes)
                                        }
                                    )
                                },
                                colors =
                                    androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                border =
                                    androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                        borderWidth = 0.dp,
                                        selected = isSelected,
                                        enabled = true,
                                    ),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(
                            HistoryStep.DAILY to R.string.account_net_worth_step_daily,
                            HistoryStep.MONTHLY to R.string.account_net_worth_step_monthly,
                        ).forEach { (s, labelRes) ->
                            val isSelected = step == s
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = { onSelectStep(s) },
                                label = { Text(stringResource(labelRes)) },
                                colors =
                                    androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                border =
                                    androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                        borderWidth = 0.dp,
                                        selected = isSelected,
                                        enabled = true,
                                    ),
                            )
                        }
                    }
                    io.github.hootkit_dev.hootbook.presentation.ui.features.analytics.NetWorthLineChart(
                        points = history.points,
                        currency = history.currency,
                    )
                    history.securitiesSince?.let { secSince ->
                        Text(
                            stringResource(R.string.account_net_worth_securities_since, secSince.coreDateText()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(
                            if (history.points.any { !it.complete }) {
                                R.string.account_net_worth_securities_missing
                            } else {
                                R.string.account_net_worth_current_rates_note
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showDateRangePicker) {
        io.github.hootkit_dev.hootbook.presentation.ui.components.DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                onSelectCustomRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customRange?.first,
            initialEndDate = customRange?.second,
            blurEffects = blurEffects,
        )
    }
}

@Composable
internal fun AccountCreditDetails(account: Account) {
    val pocket = account.pockets.firstOrNull { it.currency == account.mainCurrency } ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        pocket.availableCredit?.let { available ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.label_available), style = MaterialTheme.typography.bodyMedium)
                Text(CoreMoneyPresentation.formatCurrency(available, pocket.currency), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
        pocket.creditLimit?.let { limit ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.credit_limit_label), style = MaterialTheme.typography.bodyMedium)
                Text(CoreMoneyPresentation.formatCurrency(limit, pocket.currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
