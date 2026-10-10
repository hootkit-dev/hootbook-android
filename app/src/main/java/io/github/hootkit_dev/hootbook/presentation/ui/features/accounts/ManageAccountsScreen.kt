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
import io.github.hootkit_dev.hootbook.presentation.ui.components.institutionKeyOf
import io.github.hootkit_dev.hootbook.presentation.ui.components.institutionKey
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRow
import io.github.hootkit_dev.hootbook.utils.sumOfBigDecimal
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
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CardType
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
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeApi::class
)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountDetail: (String, String) -> Unit,
    onNavigateToAddAccount: (AccountCategory?) -> Unit = {},
    manageAccountsViewModel: ManageAccountsViewModel = hiltViewModel(),
    blurEffects: Boolean,
    category: AccountCategory? = null,
) {
    val uiState by manageAccountsViewModel.uiState.collectAsStateWithLifecycle()

    var walletsExpanded by rememberSaveable { mutableStateOf(false) }
    var banksExpanded by rememberSaveable { mutableStateOf(false) }
    var creditCardsExpanded by rememberSaveable { mutableStateOf(false) }
    var investmentsExpanded by rememberSaveable { mutableStateOf(false) }
    val brokerageConnections by manageAccountsViewModel.brokerageConnections.collectAsStateWithLifecycle()
    var disconnectingBrokerage by remember { mutableStateOf<io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection?>(null) }
    val categoryAccounts = remember(uiState.accounts, category) {
        uiState.accounts.filter { category == null || it.category() == category }
    }
    val holdings = io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings.current
    val sections = remember(categoryAccounts, uiState.mainAccountKey, holdings) {
        buildAccountSections(categoryAccounts, uiState.mainAccountKey, holdings)
    }
    val walletSection = sections.visible[0]
    val bankSection = sections.visible[1]
    val creditSection = sections.visible[2]
    val investmentSection = sections.visible[3]
    val investmentSummary = remember(investmentSection, brokerageConnections, category) {
        if (category == null || category == AccountCategory.INVESTMENTS) investmentSection.withBrokerSnapshots(brokerageConnections)
        else investmentSection
    }
    val wallets = walletSection.visibleAccounts(category != null || walletsExpanded)
    val visibleRegularAccounts = bankSection.visibleAccounts(category != null || banksExpanded)
    val visibleCreditCards = creditSection.visibleAccounts(category != null || creditCardsExpanded)
    val visibleInvestments = investmentSection.visibleAccounts(category != null || investmentsExpanded)
    val showBrokerageLinks = category == null || category == AccountCategory.INVESTMENTS
    val showInvestmentRows = category != null || investmentsExpanded
    // Add account with the Broker type: connect a broker, or add a manual broker account
    val addBrokerAccount = {
        manageAccountsViewModel.clearAccountSaveError()
        onNavigateToAddAccount(AccountCategory.INVESTMENTS)
    }
    val allRegularAccounts = remember(uiState.accounts) {
        uiState.accounts.filter { !it.isCreditCard && !it.isWallet }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    val showFloatingLabel by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex == 0 }
    }
    var selectedCardForLink by remember { mutableStateOf<CardEntity?>(null) }

    // Show snackbar messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                manageAccountsViewModel.clearError()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(category?.titleRes ?: R.string.title_accounts)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.account_navigate_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            val fabContainerColor =  MaterialTheme.colorScheme.primaryContainer
            val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ExtendedFloatingActionButton(
                onClick = {
                    manageAccountsViewModel.clearAccountSaveError()
                    onNavigateToAddAccount(category)
                },
                expanded = showFloatingLabel,
                icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_account_fab_desc)) },
                text = { Text(text = stringResource(R.string.add_account_fab_desc)) },
                containerColor = fabContainerColor,
                contentColor = fabContentColor
            ) },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (categoryAccounts.isEmpty() && !(showBrokerageLinks && brokerageConnections.isNotEmpty())) {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.no_accounts_yet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.add_account_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (category == null) {
                            InvestmentsEmptySection(
                                onAdd = addBrokerAccount,
                                showTitle = false,
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurEffects) Modifier.hazeSource(state = hazeState) else Modifier),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content +
                                paddingValues.calculateTopPadding(),
                        bottom = 96.dp + paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Wallets Section
                    if (walletSection.accounts.isNotEmpty()) {
                        item(key = "wallet_summary", contentType = "section_summary") {
                            AccountSectionSummary(
                                section = walletSection,
                                expanded = walletsExpanded,
                                onToggle = if (category == null) ({ walletsExpanded = !walletsExpanded }) else null
                            )
                        }
                        items(wallets, key = { it.listKey() }, contentType = { "bank_account" }) { account ->
                            AccountItem(
                                account = account,
                                linkedCards = emptyList(),
                                isMain = uiState.mainAccountKey == "${account.bankName}_${account.accountLast4}",
                                onAccountClick = {
                                    onNavigateToAccountDetail(account.bankName, account.accountLast4)
                                },
                            )
                        }
                    }



                    // Regular Bank Accounts Section (Visible Only)
                    if (bankSection.accounts.isNotEmpty()) {
                        item(key = "bank_summary", contentType = "section_summary") {
                            AccountSectionSummary(section = bankSection, expanded = banksExpanded, onToggle = if (category == null) ({ banksExpanded = !banksExpanded }) else null)
                        }
                        items(visibleRegularAccounts, key = { it.listKey() }, contentType = { "bank_account" }) { account ->
                            AccountItem(
                                account = account,
                                linkedCards = uiState.linkedCards[account.accountLast4]
                                    ?: emptyList(),
                                onUnlinkCard = { cardId -> manageAccountsViewModel.unlinkCard(cardId) },
                                isMain = uiState.mainAccountKey == "${account.bankName}_${account.accountLast4}",
                                onAccountClick = {
                                    onNavigateToAccountDetail(account.bankName, account.accountLast4)
                                },
                            )
                        }
                    }


                    // Credit Cards Section (Visible Only)
                    if (creditSection.accounts.isNotEmpty()) {
                        item(key = "credit_summary", contentType = "section_summary") {
                            AccountSectionSummary(section = creditSection, expanded = creditCardsExpanded, onToggle = if (category == null) ({ creditCardsExpanded = !creditCardsExpanded }) else null)
                        }

                        items(visibleCreditCards, key = { it.listKey() }, contentType = { "credit_account" }) { card ->
                            CreditCardItem(
                                card = card,
                                isMain = uiState.mainAccountKey == "${card.bankName}_${card.accountLast4}",
                                onAccountClick = {
                                    onNavigateToAccountDetail(card.bankName, card.accountLast4)
                                },
                            )
                        }
                    }



                    if (category == null && investmentSection.accounts.isEmpty() && brokerageConnections.isEmpty()) {
                        // Always offered in the all-accounts view, so investments stay findable
                        item(key = "investment_empty", contentType = "investment_empty") {
                            InvestmentsEmptySection(onAdd = addBrokerAccount)
                        }
                    } else if (investmentSection.accounts.isNotEmpty() || (showBrokerageLinks && brokerageConnections.isNotEmpty())) {
                        item(key = "investment_summary", contentType = "section_summary") {
                            AccountSectionSummary(
                                section = investmentSummary,
                                expanded = investmentsExpanded,
                                onToggle = if (category == null) ({ investmentsExpanded = !investmentsExpanded }) else null
                            )
                        }
                        if (showInvestmentRows) {
                            items(visibleInvestments, key = { it.listKey() }, contentType = { "investment_account" }) { account ->
                                AccountItem(
                                    account = account,
                                    linkedCards = emptyList(),
                                    isMain = uiState.mainAccountKey == "${account.bankName}_${account.accountLast4}",
                                    onAccountClick = {
                                        onNavigateToAccountDetail(account.bankName, account.accountLast4)
                                    },
                                )
                            }
                            if (showBrokerageLinks) {
                                items(brokerageConnections, key = { "broker:${it.id}" }, contentType = { "broker_connection" }) { connection ->
                                    BrokerageConnectionRow(
                                        connection = connection,
                                        onDisconnect = { disconnectingBrokerage = connection }
                                    )
                                }
                                item(key = "investment_add", contentType = "investment_empty") {
                                    InvestmentsEmptySection(onAdd = addBrokerAccount, showTitle = false)
                                }
                            }
                        }
                    }

                    // Orphaned Cards Section
                    if (category == null && uiState.orphanedCards.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(Spacing.md))

                            SectionHeader(
                                title = stringResource(R.string.section_unlinked_cards),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        items(uiState.orphanedCards, key = { "unlinked:${it.id}" }, contentType = { "unlinked_card" }) { card ->
                            OrphanedCardItem(
                                card = card,
                                accounts = allRegularAccounts,
                                onLinkToAccount = {
                                    selectedCardForLink = card
                                },
                                onDeleteCard = { cardId ->
                                    manageAccountsViewModel.deleteCard(cardId)
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }

    // Delete Account Confirmation Dialog
    disconnectingBrokerage?.let { connection ->
        AlertDialog(
            onDismissRequest = { disconnectingBrokerage = null },
            title = { Text(stringResource(R.string.investments_disconnect)) },
            text = { Text(stringResource(R.string.investments_disconnect_hint)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.investments_disconnect),
                    onClick = {
                        manageAccountsViewModel.disconnectBrokerage(connection.id)
                        disconnectingBrokerage = null
                    }
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.investments_cancel),
                    onClick = { disconnectingBrokerage = null }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }

    if (selectedCardForLink != null) {
        val card = selectedCardForLink!!
        // Same institution, so "BofA (Checking)" is offered for a card from "BofA".
        val matchingAccounts = uiState.accounts.filter { it.institutionKey() == institutionKeyOf(card.bankName) }
        LinkCardDialog(
            card = card,
            accounts = matchingAccounts,
            onDismiss = { selectedCardForLink = null },
            onConfirm = { accountLast4 ->
                scope.launch {
                    manageAccountsViewModel.linkCardToAccount(card.id, accountLast4)
                    selectedCardForLink = null
                }
            },
            hazeState = hazeState,
            blurEffects = blurEffects
        )
    }
}



@Composable
private fun CreditCardItem(
    card: AccountBalanceEntity,
    isMain: Boolean = false,
    onAccountClick: () -> Unit = {}
) {
    val available = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
    val utilization =
        if (card.creditLimit != null && card.creditLimit > BigDecimal.ZERO) {
            ((card.balance.toDouble() / card.creditLimit.toDouble()) * 100).toInt()
        } else {
            0
        }

    val utilizationColor =
        when {
            utilization > 70 -> MaterialTheme.colorScheme.error
            utilization > 30 -> warningColor
            else -> successColor
        }

    CompactAccountCard(
        account = card,
        isMain = isMain,
        onClick = onAccountClick
    ) {
        // Credit Card
        Column(
            modifier = Modifier.padding(horizontal = 16.dp,vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Available Credit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.label_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(
                        available,
                        card.currency
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Credit Limit with Utilization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.credit_limit_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(
                            card.creditLimit ?: BigDecimal.ZERO,
                            card.currency
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.utilization_used, utilization),
                        style = MaterialTheme.typography.bodySmall,
                        color = utilizationColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BrokerageConnectionRow(
    connection: io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("broker_connection_${connection.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(connection.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    connection.accounts.joinToString { it.accountId }.ifBlank { connection.providerId },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDisconnect) {
                Text(stringResource(R.string.investments_disconnect))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class)
@Composable
private fun AccountItem(
    account: AccountBalanceEntity,
    linkedCards: List<CardEntity> = emptyList(),
    isMain: Boolean = false,
    onUnlinkCard: (cardId: Long) -> Unit = {},
    onAccountClick: () -> Unit = {}
) {
    Column {
        CompactAccountCard(
            account = account,
            isMain = isMain,
            onClick = onAccountClick
        ) {

            // Linked Cards Section
            if (linkedCards.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.linked_cards_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    linkedCards.forEach { card ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.xs),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "💳",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Column {
                                        Row(modifier = Modifier.padding(start = Spacing.sm),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                        ) {
                                            Text(
                                                text = "•••• ${card.cardLast4}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (!card.isActive
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.label_inactive),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onUnlinkCard(card.id) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LinkOff,
                                        contentDescription = stringResource(R.string.unlink_card_desc),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrphanedCardItem(
    card: CardEntity,
    accounts: List<AccountBalanceEntity>,
    onLinkToAccount: (String) -> Unit,
    onDeleteCard: (Long) -> Unit
) {
    var expandedSource by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(
            onClick = { expandedSource = !expandedSource },
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("💳", style = MaterialTheme.typography.titleMedium)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.bankName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "•••• ${card.cardLast4}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.more_options_desc),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = MaterialTheme.shapes.large,
                        containerColor = Color.Transparent,
                        shadowElevation = 0.dp,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.link_to_account)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLinkToAccount("")
                            },
                            modifier = Modifier
                                .shadow(
                                    elevation = 2.dp,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 4.dp
                                    )
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 4.dp
                                    )
                                )
                        )

                        Spacer(modifier = Modifier.height(1.5.dp))
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Iconax.Bag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDeleteCard(card.id)
                            },
                            modifier = Modifier
                                .shadow(
                                    elevation = 2.dp,
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 4.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp
                                    )
                                )
                        )
                    }
                }
            }
            Text(
                text = "${if (card.cardType == CardType.CREDIT) stringResource(R.string.credit_card)
                else stringResource(R.string.debit_card)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Show last known balance if available
            if (card.lastBalance != null) {
                Text(
                    text = stringResource(R.string.last_balance, CurrencyFormatter.formatCurrency(card.lastBalance, card.currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkCardDialog(
    card: CardEntity,
    accounts: List<AccountBalanceEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    hazeState: HazeState = remember { HazeState() }
) {
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.link_card_title))
                Text(
                    text = listOfNotNull(card.bankName, maskAccountNumber(card.cardLast4)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_accounts_found_link, card.bankName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = stringResource(R.string.select_account_link_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    accounts.forEach { account ->
                        val isSelected = selectedAccount == account.accountLast4
                        AccountRow(
                            account = account,
                            selected = isSelected,
                            containerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            onClick = { selectedAccount = account.accountLast4 }
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.action_link),
                onClick = { selectedAccount?.let(onConfirm) },
                enabled = selectedAccount != null
            )
        },
        dismissButton = {
            DialogDismissButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss
            )
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}


