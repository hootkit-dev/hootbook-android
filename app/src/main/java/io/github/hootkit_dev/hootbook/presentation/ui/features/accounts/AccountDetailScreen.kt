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
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
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
import io.github.hootkit_dev.hootbook.presentation.ui.components.BalancePoint
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AccountDetailScreen(
    navController: NavController,
    bankName: String = "",
    accountLast4: String = "",
    accountDetailViewModel: AccountDetailViewModel = hiltViewModel(),
    animatedContentScope: AnimatedVisibilityScope? = null,
    // In a detail pane, back closes the pane instead of leaving the screen
    onNavigateBack: (() -> Unit)? = null,
    // After a rename the page shows the account under its new name; by default it reopens itself
    onRenamed: ((String) -> Unit)? = null
) {
    val uiState by accountDetailViewModel.uiState.collectAsState()
    val selectedDateRange by accountDetailViewModel.selectedDateRange.collectAsState()
    val lookups by accountDetailViewModel.lookups.collectAsStateWithLifecycle()
    
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    var showCalibration by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    // Edits, history, delete and the default account go through the account list's model
    val manageViewModel: ManageAccountsViewModel = hiltViewModel()
    val manageState by manageViewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (animatedContentScope != null) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = "account_${bankName}_${accountLast4}"),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = { _, _ ->
                            tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
                        },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center)
                    )
                        .skipToLookaheadSize()
                } else Modifier
            )
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = uiState.bankName.ifEmpty { stringResource(R.string.account_details) },
                hasBackButton = true,
                hazeState = hazeState,
                navigationContent = { NavigationContent { onNavigateBack?.invoke() ?: navController.safePopBackStack() } },
                actionContent = {
                    if (uiState.currentBalance != null) {
                        io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton(
                            Iconax.Edit2, stringResource(R.string.edit_account_title), { showEdit = true }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
            ),
        ) {
            // Account Card
            item {
                uiState.currentBalance?.let { balance ->
                    Column(
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        AccountCard(account = balance)
                        if (balance.isCreditCard) {
                            CardBillPanel(uiState.cardStatus, balance.currency, onSetDates = { showEdit = true })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            // Set the balance to what the bank shows
                            FilledTonalButton(
                                onClick = { showCalibration = true },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(if (balance.isCreditCard) R.string.update_outstanding_title else R.string.balance_calibration), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    manageViewModel.loadBalanceHistory(balance.bankName, balance.accountLast4)
                                    showHistory = true
                                },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.history), maxLines = 1)
                            }
                        }
                        // Preselected when adding a transaction
                        val isDefault = manageState.mainAccountKey == "${balance.bankName}_${balance.accountLast4}"
                        io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch(
                            title = stringResource(R.string.default_account_title),
                            subtitle = stringResource(R.string.default_account_hint),
                            checked = isDefault,
                            onCheckedChange = { on ->
                                if (on) manageViewModel.setAsMainAccount(balance.bankName, balance.accountLast4)
                                else manageViewModel.clearMainAccount()
                            },
                            isSingle = true
                        )
                    }
                    if (showHistory) {
                        io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet(
                            onDismissRequest = {
                                showHistory = false
                                manageViewModel.clearBalanceHistory()
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
                        ) {
                            HistorySheet(
                                bankName = balance.bankName,
                                accountLast4 = balance.accountLast4,
                                balanceHistory = manageState.balanceHistory,
                                onDeleteBalance = { id -> manageViewModel.deleteBalanceRecord(id, balance.bankName, balance.accountLast4) },
                                onUpdateBalance = { id, value -> manageViewModel.updateBalanceRecord(id, value, balance.bankName, balance.accountLast4) }
                            )
                        }
                    }
                    if (showEdit) {
                        AccountEditSheet(
                            account = balance,
                            cardDates = uiState.cardDates,
                            viewModel = manageViewModel,
                            onDismiss = { showEdit = false },
                            onGone = { onNavigateBack?.invoke() ?: navController.safePopBackStack() },
                            // The page is the account's by name: a renamed account opens again under the new one
                            onRenamed = { newName ->
                                onRenamed?.invoke(newName) ?: navController.safeNavigate(
                                    io.github.hootkit_dev.hootbook.presentation.navigation.AccountDetail(newName, balance.accountLast4)
                                ) {
                                    popUpTo<io.github.hootkit_dev.hootbook.presentation.navigation.AccountDetail> { inclusive = true }
                                }
                            }
                        )
                    }
                    if (showCalibration) {
                        BalanceCalibrationSheet(
                            account = balance,
                            onDismiss = { showCalibration = false },
                            onSave = accountDetailViewModel::calibrate
                        )
                    }
                }
            }
            item{
                Spacer(Modifier.height(Spacing.md))
            }

            // Date Range Filter
            item {
                DateRangeFilter(
                    selectedRange = selectedDateRange,
                    onRangeSelected = accountDetailViewModel::selectDateRange
                )
            }
            item{
                Spacer(Modifier.height(Spacing.md))
            }

            // Balance Chart
            if (uiState.balanceChartData.isNotEmpty()) {
                item {
                    ExpandableBalanceChart(
                        primaryCurrency = uiState.primaryCurrency,
                        balanceHistory = uiState.balanceChartData,
                        selectedTimeframe = selectedDateRange.getLocalizedLabel(),
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }
            item{
                Spacer(Modifier.height(Spacing.md))
            }
            // Summary Statistics
            item {
                TransactionTotalsCard(
                    income = uiState.totalIncome,
                    expenses = uiState.totalExpenses,
                    netBalance = uiState.netBalance,
                    currency = uiState.primaryCurrency,
                    title = selectedDateRange.getLocalizedLabel(),
                    isEstimated = uiState.hasMultipleCurrencies,
                    isLoading = uiState.isLoading,
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                )
            }
            item{
                Spacer(Modifier.height(Spacing.md))
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.transactions_count, uiState.transactions.size),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content + Spacing.sm)
                )
            }

            item{
                Spacer(Modifier.height(Spacing.sm))
            }
            // Transaction List
            if (uiState.transactions.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyTransactionsState(
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.transactions,
                    key = { _, it -> it.id }
                ) { index, transaction ->
                    val decoration = remember(transaction, lookups, uiState.conversions) {
                        lookups.decorate(transaction, uiState.conversions)
                    }

                    val position = remember(index, uiState.transactions.size) {
                        ListItemPosition.from(index, uiState.transactions.size)
                    }
                    val shape = position.toShape()

                    TransactionItem(
                        transaction = transaction,
                        decoration = decoration,
                        balanceAfter = transaction.balanceAfter,
                        balanceCurrency = uiState.primaryCurrency,
                        accountIconResId = uiState.currentBalance?.iconResId ?: 0,
                        accountIconName = uiState.currentBalance?.iconName,
                        accountColorHex = uiState.currentBalance?.color,
                        mainCurrency = uiState.baseCurrency,
                        currentAccountContext = uiState.currentBalance?.accountLast4,
                        currentBankNameContext = bankName,
                        onClick = {
                            navController.safeNavigate(
                                TransactionDetail(
                                    transactionId = transaction.id,
                                    sharedElementKey = "transaction_${transaction.id}"
                                )
                            )
                        },
                        shape = shape,
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        animatedContentScope = animatedContentScope,
                        sharedElementKey = "transaction_${transaction.id}",
                    )
                }
            }
            item{
                Spacer(Modifier.height(Spacing.md))
            }
            // Loading State
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingCircle()
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ExpandableBalanceChart(
    modifier: Modifier = Modifier,
    primaryCurrency: String,
    balanceHistory: List<BalancePoint>,
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
/** The account list's edit sheet, for the account shown on this page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditSheet(
    account: AccountBalanceEntity,
    cardDates: io.github.hootkit_dev.hootbook.domain.model.CardDates,
    viewModel: ManageAccountsViewModel,
    onDismiss: () -> Unit,
    onGone: () -> Unit,
    onRenamed: (String) -> Unit
) {
    val accounts by viewModel.uiState.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrencyForNewAccounts.collectAsStateWithLifecycle()
    io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet(
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        EditAccountSheet(
            account = account,
            allAccounts = accounts.accounts,
            defaultCurrency = defaultCurrency,
            initialCategory = account.category(),
            initialCardDates = cardDates,
            onDismiss = onDismiss,
            onDelete = {
                viewModel.deleteAccount(account.bankName, account.accountLast4)
                onDismiss()
                onGone()
            },
            onSave = { bankName, balance, last4, iconResId, iconName, color, isCC, isWallet, limit, currency, addedCurrencies, cardDates ->
                viewModel.editAccount(
                    oldBankName = account.bankName,
                    accountLast4 = account.accountLast4,
                    newBankName = bankName,
                    newBalance = balance,
                    newCreditLimit = limit,
                    isCreditCard = isCC,
                    isWallet = isWallet,
                    newIconResId = iconResId,
                    newIconName = iconName,
                    newColorHex = color,
                    newCurrency = currency,
                    addedCurrencies = addedCurrencies,
                    cardDates = cardDates,
                    onSaved = { if (bankName != account.bankName) onRenamed(bankName) }
                )
                onDismiss()
            }
        )
    }
}

/**
 * A credit card's bill: the open statement (what it closed at, what is still owed, when it is
 * due), or the next closing and due dates once it is paid past; without dates, a way to set them.
 */
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
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), status.due)
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
                        io.github.hootkit_dev.hootbook.utils.CurrencyFormatter.formatCurrency(status.statementAmount, currency),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val remaining = status.remaining
                    Text(
                        if (remaining == null || remaining.signum() == 0) stringResource(R.string.card_paid)
                        else stringResource(R.string.card_remaining, io.github.hootkit_dev.hootbook.utils.CurrencyFormatter.formatCurrency(remaining, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remaining == null || remaining.signum() == 0) io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors.income
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (status.closing != null) {
                    Text(stringResource(R.string.card_next_statement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.closing.format(dateFormat), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(stringResource(R.string.card_due_day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status.due.format(dateFormat), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.card_due_on, status.due.format(dateFormat)), style = MaterialTheme.typography.labelLarge)
                Text(
                    dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
