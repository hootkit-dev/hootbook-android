package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

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
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.investments.BrokerAutoSyncSection
import dev.chrisbanes.haze.HazeState

/**
 * Full-screen "Add account" page, laid out like the Add Transaction screen.
 *
 * The form used to live in a ModalBottomSheet. Inside a sheet the form's scroll takes part in
 * the sheet's nested-scroll and settle animation, so a fast fling that reached the end of the
 * form handed its velocity to the sheet and produced a visible jolt. A plain screen has no such
 * outer scroll participant. The form itself is the shared [EditAccountSheet] content.
 *
 * With [initialCategory] INVESTMENTS the Broker type is preselected, and the page offers
 * "Broker auto-sync" (read-only holdings) above the manual form: every "connect a broker"
 * action in the app opens this page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    initialCategory: AccountCategory? = null,
    manageAccountsViewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val uiState by manageAccountsViewModel.uiState.collectAsStateWithLifecycle()
    val defaultCurrency by manageAccountsViewModel.defaultCurrencyForNewAccounts.collectAsStateWithLifecycle()
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
                    navigationContent = { NavigationContent(onNavigateBack) }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                EditAccountSheet(
                    allAccounts = uiState.accounts,
                    defaultCurrency = defaultCurrency,
                    initialCategory = initialCategory,
                    isSaving = uiState.isSavingAccount,
                    saveError = uiState.accountSaveError,
                    onClearSaveError = manageAccountsViewModel::clearAccountSaveError,
                    showHeading = false, // the top bar already shows the title
                    brokerAutoSync = { BrokerAutoSyncSection() },
                    onDismiss = { if (!uiState.isSavingAccount) onNavigateBack() },
                    onSave = { bankName, balance, last4, iconResId, iconName, color, isCC, isWallet, limit, currency, addedCurrencies, cardDates ->
                        manageAccountsViewModel.addAccount(
                            bankName = bankName,
                            balance = balance,
                            accountLast4 = last4,
                            iconResId = iconResId,
                            iconName = iconName,
                            colorHex = color,
                            isCreditCard = isCC,
                            isWallet = isWallet,
                            creditLimit = limit,
                            currency = currency,
                            addedCurrencies = addedCurrencies,
                            cardDates = cardDates,
                            onSaved = { if (isScreenActive) onNavigateBack() }
                        )
                    }
                )
            }
        }
    }
}
