package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.EditAccountSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ManageAccountsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Wallet3

/**
 * Broker-enabled shared full-screen "Add account" page.
 *
 * Structured into two clear categories:
 * 1. "Connect a broker" - connects supported brokers (IBKR Flex) for auto-syncing read-only holdings,
 *    extensible for future broker integrations via [BrokerProvider].
 * 2. "Add manually" - a dedicated entry point that opens the manual account creation form,
 *    supporting bank accounts, credit cards, cash wallets, and custom investment accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountWithBrokerAutoSync(
    onNavigateBack: () -> Unit,
    initialCategory: AccountCategory? = null,
    onOpenConnection: (dev.hootkit.core.BrokerageConnection) -> Unit = {},
    investmentsViewModel: InvestmentsViewModel = hiltViewModel(),
    manageAccountsViewModel: ManageAccountsViewModel = hiltViewModel(),
) {
    val uiState by manageAccountsViewModel.uiState.collectAsStateWithLifecycle()
    val brokerState by investmentsViewModel.state.collectAsStateWithLifecycle()
    val defaultCurrency = uiState.defaultCurrency
    var isScreenActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        isScreenActive = true
        onDispose { isScreenActive = false }
    }

    var showManualForm by rememberSaveable {
        mutableStateOf(initialCategory != null && initialCategory != AccountCategory.INVESTMENTS)
    }

    val goBack: () -> Unit = {
        if (brokerState.busy) {
            investmentsViewModel.cancelOperation()
        } else if (showManualForm && (initialCategory == null || initialCategory == AccountCategory.INVESTMENTS)) {
            showManualForm = false
        } else {
            onNavigateBack()
        }
    }
    BackHandler(enabled = true, onBack = goBack)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    val screenTitle = if (showManualForm) {
        stringResource(R.string.add_manually_title)
    } else {
        stringResource(R.string.add_account_title)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehavior,
                    title = screenTitle,
                    hazeState = hazeState,
                    hasBackButton = true,
                    navigationContent = {
                        NavigationContent(onNavigateBack = goBack)
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())
            ) {
                if (!uiState.isLoading && uiState.overview != null) {
                    if (showManualForm) {
                        EditAccountSheet(
                            allAccounts = uiState.overview?.accounts.orEmpty(),
                            defaultCurrency = defaultCurrency,
                            initialCategory = initialCategory,
                            isSaving = uiState.isSavingAccount || brokerState.busy,
                            saveError = uiState.accountSaveError,
                            onClearSaveError = manageAccountsViewModel::clearAccountSaveError,
                            showHeading = false, // the top bar already shows the title
                            institutions = uiState.institutions,
                            brokerAutoSync = null, // Manual creation form does not mix auto-sync inline
                            onDismiss = goBack,
                            onSave = { input ->
                                manageAccountsViewModel.addAccount(
                                    input,
                                    onSaved = { if (isScreenActive) onNavigateBack() },
                                )
                            },
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            // Category 1: Connect a broker
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.connect_broker_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.broker_auto_sync_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Broker auto-sync card / section (IBKR)
                                BrokerAutoSyncSection(
                                    viewModel = investmentsViewModel,
                                    showTitle = false,
                                    onOpenConnection = onOpenConnection,
                                )
                            }

                            // Category 2: Add manually
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.add_manually_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.add_manually_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Dedicated entry card for manual account creation
                                Surface(
                                    onClick = { showManualForm = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Iconax.Wallet3,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.add_manually_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = stringResource(R.string.add_manually_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
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
}
