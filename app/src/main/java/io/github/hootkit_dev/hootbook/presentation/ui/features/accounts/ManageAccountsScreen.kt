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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountDetail: (String) -> Unit,
    onNavigateToAddAccount: (AccountCategory?) -> Unit,
    onBrokerage: (Long, String) -> Unit,
    category: AccountCategory? = null,
    manageAccountsViewModel: ManageAccountsViewModel = hiltViewModel(),
) {
    val uiState by manageAccountsViewModel.uiState.collectAsStateWithLifecycle()
    val accounts = uiState.overview?.accounts.orEmpty().filter { category == null || it.category() == category }
    val sections = remember(accounts) { accounts.accountSections() }
    var expanded by remember { mutableStateOf(emptySet<AccountCategory>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val showFloatingLabel by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); manageAccountsViewModel.clearError() }
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
                }, scrollBehavior = scrollBehavior
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
            if (accounts.isEmpty() && !uiState.isLoading) {
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
                        bottom = 96.dp + paddingValues.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { (kind, members) ->
                        if (members.isNotEmpty()) {
                            item(key = "summary_$kind", contentType = "section_summary") {
                                AccountSectionSummary(kind = kind, accounts = members, expanded = kind in expanded,
                                    onToggle = if (category == null) ({ expanded = if (kind in expanded) expanded - kind else expanded + kind }) else null)
                            }
                            if (category != null || kind in expanded) items(members, key = { it.id }, contentType = { "account" }) { account ->
                                CompactAccountCard(account, account.isDefault, { onNavigateToAccountDetail(account.id) }) {
                                    if (account.kind == AccountKind.CREDIT_CARD) AccountCreditDetails(account)
                                    account.broker?.let { broker ->
                                        TextButton(onClick = { onBrokerage(broker.connectionId, broker.brokerAccountId) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(stringResource(R.string.overview_investments) + " · " + broker.asOf.coreDateText())
                                        }
                                    }
                                }
                            }
                        } else if (kind == AccountCategory.INVESTMENTS && category == null) {
                            item(key = "investment_empty") { InvestmentsEmptySection(onAdd = { onNavigateToAddAccount(AccountCategory.INVESTMENTS) }) }
                        }
                    }
                }
            }
        }
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
