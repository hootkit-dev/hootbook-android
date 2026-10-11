@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import androidx.compose.material3.ripple
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.github.hootkit_dev.hootbook.presentation.navigation.TransactionDetail
import io.github.hootkit_dev.hootbook.presentation.navigation.safeNavigate
import io.github.hootkit_dev.hootbook.presentation.navigation.safePopBackStack
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalanceChart
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionTotalsCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AccountDetailScreen(
    accountId: String,
    onNavigateBack: () -> Unit,
    onTransaction: (String) -> Unit,
    onBrokerage: (Long, String) -> Unit,
    accountDetailViewModel: AccountDetailViewModel = hiltViewModel(),
    manageViewModel: ManageAccountsViewModel = hiltViewModel(),
    animatedContentScope: AnimatedVisibilityScope? = null,
    initialHistory: Boolean = false,
) {
    val uiState by accountDetailViewModel.uiState.collectAsStateWithLifecycle()
    val manageState by manageViewModel.uiState.collectAsStateWithLifecycle()
    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(accountId) { accountDetailViewModel.initialize(accountId) }
    val selectedDateRange = uiState.selectedDateRange
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var showCalibration by remember(accountId) { mutableStateOf(false) }
    var showEdit by remember(accountId) { mutableStateOf(false) }
    var showHistory by remember(accountId) { mutableStateOf(initialHistory) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { active = true; onDispose { active = false } }
    LaunchedEffect(uiState.error, manageState.errorMessage) {
        (uiState.error ?: manageState.errorMessage)?.let { snackbar.showSnackbar(operationFailed); accountDetailViewModel.clearError(); manageViewModel.clearError() }
    }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to lazyListState.layoutInfo.totalItemsCount }
            .collect { (last, count) -> if (last != null && last >= count - 3) accountDetailViewModel.loadMore() }
    }
    Box(Modifier.fillMaxSize().then(if (animatedContentScope != null) Modifier.sharedBounds(
        rememberSharedContentState(key = "account_$accountId"), animatedVisibilityScope = animatedContentScope,
        boundsTransform = { _, _ -> tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing) },
        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center))
        .skipToLookaheadSize() else Modifier)) {
        Scaffold(modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
            topBar = {
                CustomTitleTopAppBar(scrollBehaviorSmall = scrollBehaviorSmall, scrollBehaviorLarge = scrollBehaviorLarge,
                    title = uiState.account?.name ?: stringResource(R.string.account_details), hasBackButton = true,
                    hazeState = hazeState, navigationContent = { NavigationContent(onNavigateBack) },
                    actionContent = {
                        if (uiState.account != null) io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton(
                            Iconax.Edit2, stringResource(R.string.edit_account_title), { showEdit = true })
                    })
            }) { padding ->
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(top = Dimensions.Padding.content + padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
                item {
                    uiState.account?.let { account ->
                        Column(Modifier.padding(horizontal = Dimensions.Padding.content), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AccountCard(account)
                            if (account.kind == AccountKind.CREDIT_CARD) CardBillPanel(uiState.cardStatus, account.mainCurrency) { showEdit = true }
                            account.securities?.let { securities ->
                                Text(stringResource(R.string.core_full_securities, CoreMoneyPresentation.formatCurrency(securities.amount, securities.currency)), style = MaterialTheme.typography.bodyMedium)
                            }
                            account.broker?.let { broker ->
                                OutlinedButton(onClick = { onBrokerage(broker.connectionId, broker.brokerAccountId) }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.overview_investments) + " · " + broker.asOf.coreDateText())
                                }
                            }
                            if (account.pockets.size > 1) io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCurrencyChoice(
                                currencies = account.pockets.map { it.currency }, selected = uiState.currency ?: account.mainCurrency,
                                onSelect = accountDetailViewModel::selectCurrency)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                FilledTonalButton(onClick = { showCalibration = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1.8f)) {
                                    Text(stringResource(if (account.kind == AccountKind.CREDIT_CARD) R.string.update_outstanding_title else R.string.balance_calibration), maxLines = 1)
                                }
                                OutlinedButton(onClick = { showHistory = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.history), maxLines = 1)
                                }
                            }
                            io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch(
                                title = stringResource(R.string.default_account_title), subtitle = stringResource(R.string.default_account_hint),
                                checked = account.isDefault, onCheckedChange = { on -> manageViewModel.setDefault(if (on) account.id else null) }, isSingle = true)
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.md)) }
                item { DateRangeFilter(selectedDateRange, accountDetailViewModel::selectDateRange) }
                item { Spacer(Modifier.height(Spacing.md)) }
                if (uiState.balanceSeries.isNotEmpty()) item {
                    ExpandableBalanceChart(primaryCurrency = uiState.currency.orEmpty(), balanceHistory = uiState.balanceSeries,
                        selectedTimeframe = selectedDateRange.getLocalizedLabel(), modifier = Modifier.padding(horizontal = Dimensions.Padding.content))
                }
                item { Spacer(Modifier.height(Spacing.md)) }
                uiState.summary?.let { summary -> item {
                    TransactionTotalsCard(income = summary.income, expenses = summary.expense, netBalance = summary.net,
                        currency = summary.currency, title = selectedDateRange.getLocalizedLabel(), isEstimated = !summary.complete,
                        isLoading = uiState.isLoading, modifier = Modifier.padding(horizontal = Dimensions.Padding.content))
                } }
                item { Spacer(Modifier.height(Spacing.md)) }
                item { SectionHeader(title = stringResource(R.string.transactions_count, uiState.total.toInt()), modifier = Modifier.padding(horizontal = Dimensions.Padding.content + Spacing.sm)) }
                item { Spacer(Modifier.height(Spacing.sm)) }
                if (uiState.transactions.isEmpty() && !uiState.isLoading) item { EmptyTransactionsState(Modifier.padding(horizontal = Dimensions.Padding.content)) }
                itemsIndexed(uiState.transactions, key = { _, tx -> tx.id }) { index, transaction ->
                    val category = uiState.categories[transaction.categoryId]
                    val debt = uiState.debts[transaction.debtId]
                    val person = uiState.people[debt?.personId]
                    TransactionItem(transaction = transaction, categoryEntity = category,
                        subcategoryEntity = category?.subcategories?.firstOrNull { it.id == transaction.subcategoryId },
                        accountIconName = uiState.account?.icon, accountColorHex = uiState.account?.color,
                        mainCurrency = uiState.summary?.currency, convertedAmount = uiState.convertedAmounts[transaction.id],
                        currentAccountContext = accountId, currentBankNameContext = uiState.account?.name,
                        linkedLoanPersonName = person?.name, linkedLoanPersonColor = person?.color, linkedLoanPersonAvatar = person?.avatar,
                        onClick = { onTransaction(transaction.id) }, shape = ListItemPosition.from(index, uiState.transactions.size).toShape(),
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content), sharedTransitionScope = this@AccountDetailScreen,
                        animatedContentScope = animatedContentScope, sharedElementKey = "transaction_${transaction.id}")
                }
                if (uiState.isLoading || uiState.loadingMore) item { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { LoadingCircle() } }
                item { Spacer(Modifier.height(Spacing.md)) }
            }
        }
    }
    uiState.account?.let { account ->
        if (showHistory) io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet(
            onDismissRequest = { if (!uiState.saving) showHistory = false }, containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }) {
            HistorySheet(bankName = account.name, accountLast4 = account.last4.orEmpty(), currency = uiState.currency ?: account.mainCurrency,
                balanceHistory = uiState.anchors.sortedByDescending { it.at.coreDateText() }, saving = uiState.saving, commandRevision = uiState.commandRevision,
                onDeleteBalance = accountDetailViewModel::deleteAnchor, onUpdateBalance = accountDetailViewModel::updateAnchor)
        }
        if (showCalibration) BalanceCalibrationSheet(account, onDismiss = { if (!uiState.saving) showCalibration = false }, onSave = { amount, currency ->
            accountDetailViewModel.calibrate(amount, currency) { if (active) showCalibration = false }
        })
        if (showEdit) io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet(
            onDismissRequest = { if (!manageState.isSavingAccount) showEdit = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            EditAccountSheet(account = account, allAccounts = manageState.overview?.accounts.orEmpty(),
                institutions = manageState.institutions,
                defaultCurrency = manageState.defaultCurrency, isSaving = manageState.isSavingAccount,
                saveError = manageState.accountSaveError, onClearSaveError = manageViewModel::clearAccountSaveError,
                onDismiss = { if (!manageState.isSavingAccount) showEdit = false },
                onDelete = if (uiState.accountTransactionCount == 0u) ({ manageViewModel.deleteAccount(account.id, onDeleted = { if (active) onNavigateBack() }) }) else null,
                onSave = { input -> manageViewModel.editAccount(account, input) { if (active) showEdit = false } })
        }
    }
}
@Composable
private fun ExpandableBalanceChart(
    modifier: Modifier = Modifier,
    primaryCurrency: String,
    balanceHistory: List<BalanceDay>,
    selectedTimeframe: String
) {
    var isExpanded by remember { mutableStateOf(false) }

    HootbookCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() }
            ){ isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.balance_trend),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = selectedTimeframe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    BalanceChart(
                        primaryCurrency = primaryCurrency,
                        balanceHistory = balanceHistory,
                        height = 180
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilter(
    selectedRange: DateRange,
    onRangeSelected: (DateRange) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        item{
            Spacer(modifier = Modifier.width(Spacing.md))
        }
        items(DateRange.values().toList()) { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.getLocalizedLabel()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderWidth = 0.dp,
                    selected = selectedRange == range,
                    enabled = true
                ),
            )
        }
        item{
            Spacer(modifier = Modifier.width(Spacing.md))
        }
    }
}


@Composable
private fun EmptyTransactionsState(
    modifier: Modifier = Modifier
) {
    HootbookCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Iconax.ReceiptItem,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.no_transactions_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.transactions_appear_here),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DateRange.getLocalizedLabel(): String {
    return when (this) {
        DateRange.LAST_7_DAYS -> stringResource(R.string.range_last_7_days)
        DateRange.LAST_30_DAYS -> stringResource(R.string.range_last_30_days)
        DateRange.LAST_3_MONTHS -> stringResource(R.string.range_last_3_months)
        DateRange.LAST_6_MONTHS -> stringResource(R.string.range_last_6_months)
        DateRange.LAST_YEAR -> stringResource(R.string.range_last_year)
        DateRange.ALL_TIME -> stringResource(R.string.range_all_time)
    }
}
@Composable
private fun CardBillPanel(status: CardStatus?, currency: String, onSetDates: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        java.time.format.DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)
    }
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (status == null) {
            androidx.compose.material3.TextButton(onClick = onSetDates, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.card_set_dates))
            }
            return@Surface
        }
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(status.dueDate.coreDateText()))
        val dueText = when {
            status.overdue -> stringResource(R.string.card_overdue, (-daysLeft).toInt())
            daysLeft == 0L -> stringResource(R.string.card_due_today)
            else -> stringResource(R.string.card_due_in, daysLeft.toInt())
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (status.statementAmount != null) {
                    Text(stringResource(R.string.card_statement_amount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CoreMoneyPresentation.formatCurrency(status.statementAmount!!, currency),
                        style = MaterialTheme.typography.titleMedium
                    )
                    status.outstanding?.let { remaining ->
                        Text(
                            if (remaining.signum() == 0) stringResource(R.string.card_paid)
                            else stringResource(R.string.card_remaining, CoreMoneyPresentation.formatCurrency(remaining, status.currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remaining.signum() == 0) io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors.income
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (status.statementDate != null) {
                    Text(stringResource(R.string.card_next_statement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.statementDate!!.coreDateText(dateFormat), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(stringResource(R.string.card_due_day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.dueDate.coreDateText(dateFormat), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.card_due_on, status.dueDate.coreDateText(dateFormat)), style = MaterialTheme.typography.labelLarge)
                Text(
                    dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
