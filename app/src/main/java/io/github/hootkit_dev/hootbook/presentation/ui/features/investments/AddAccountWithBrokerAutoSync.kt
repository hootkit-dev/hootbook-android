package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
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
 * Broker-enabled shared full-screen "Add account" page, laid out like the Add Transaction screen.
 *
 * The form used to live in a ModalBottomSheet. Inside a sheet the form's scroll takes part in the
 * sheet's nested-scroll and settle animation, so a fast fling that reached the end of the form
 * handed its velocity to the sheet and produced a visible jolt. A plain screen has no such outer
 * scroll participant. The form itself is the shared [EditAccountSheet] content.
 *
 * With [initialCategory] INVESTMENTS the Broker type is preselected, and the page offers "Broker
 * auto-sync" (read-only holdings) above the manual form: every "connect a broker" action in the app
 * opens this page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountWithBrokerAutoSync(
    onNavigateBack: () -> Unit,
    initialCategory: AccountCategory? = null,
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
                if (!uiState.isLoading && uiState.overview != null)
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
                            BrokerAutoSyncSection(viewModel = investmentsViewModel)
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
