package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.EditAccountSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ManageAccountsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent

/**
 * Broker-enabled shared full-screen "Add account" page.
 * Directly renders [EditAccountSheet] with tabs [Bank, Card, Wallet, Broker].
 * When the Broker tab is selected, the broker auto-sync section smoothly animates into view.
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
    BackHandler(enabled = brokerState.busy) { investmentsViewModel.cancelOperation() }
    val defaultCurrency = uiState.defaultCurrency
    var isScreenActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        isScreenActive = true
        onDispose { isScreenActive = false }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehavior,
                    title = stringResource(R.string.add_account_title),
                    hazeState = hazeState,
                    hasBackButton = true,
                    navigationContent = {
                        NavigationContent {
                            if (brokerState.busy) investmentsViewModel.cancelOperation()
                            else onNavigateBack()
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())
            ) {
                if (!uiState.isLoading && uiState.overview != null) {
                    EditAccountSheet(
                        allAccounts = uiState.overview?.accounts.orEmpty(),
                        defaultCurrency = defaultCurrency,
                        initialCategory = initialCategory,
                        isSaving = uiState.isSavingAccount || brokerState.busy,
                        saveError = uiState.accountSaveError,
                        onClearSaveError = manageAccountsViewModel::clearAccountSaveError,
                        showHeading = false, // the top bar already shows the title
                        institutions = uiState.institutions,
                        brokerAutoSync = {
                            BrokerAutoSyncSection(
                                viewModel = investmentsViewModel,
                                onOpenConnection = onOpenConnection,
                            )
                        },
                        onDismiss = {
                            if (!uiState.isSavingAccount && !brokerState.busy) onNavigateBack()
                        },
                        onSave = { input ->
                            manageAccountsViewModel.addAccount(
                                input,
                                onSaved = { if (isScreenActive) onNavigateBack() },
                            )
                        },
                    )
                }
            }
        }
    }
}
