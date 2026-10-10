package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ACCOUNT_LIST_DETAIL_MIN_WIDTH_DP
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.PaneAccount
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountDetailPane
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.LIST_DETAIL_MIN_WIDTH_DP
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.TransactionDetailPane
import androidx.compose.material3.VerticalDivider
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.ReadableWidth
import androidx.compose.runtime.CompositionLocalProvider
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.rememberWindowLayout
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.NavigationRailWidth
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.LocalWindowLayout
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountDetailScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AddAccountScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.ManageAccountsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AddScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.analytics.AnalyticsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.BudgetDetailScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.BudgetHistoryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.BudgetsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.CategoriesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.contacts.ContactsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.home.HomeScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.PersonDetailScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding.OnBoardingScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.ProfileScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.SettingsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.AboutScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.LicensesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency.CurrencySettingsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance.AppearanceScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance.ThemeViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock.AppLockScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy.DataPrivacyScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.cloudbackup.BackupSyncScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.notifications.NotificationScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates.QuickTemplatesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.ai.AiAssistantScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions.SubscriptionsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.ExportTransactionsDialog
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.TransactionDetailScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.TransactionsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.TransactionsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ImportArrow01
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeApi::class)
@Composable
fun HootbookNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = Home,
    onEditComplete: () -> Unit = {}
) {
    // Use a stable start destination
    val stableStartDestination = remember { startDestination }

    // Get theme settings for bottom nav style
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeUiState by themeViewModel.themeUiState.collectAsState()

    // Track current destination for bottom nav visibility
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    ObserveMainTabSettled(navBackStackEntry)
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Check if current route is in bottom nav routes
    val showBottomNav = BOTTOM_NAV_ROUTES.any { qualifiedName ->
        currentRoute?.contains(qualifiedName ?: "") == true
    }

    val transactionsViewModel: TransactionsViewModel = hiltViewModel()
    val view = LocalView.current

    // State for full resync confirmation dialog
    var showExportDialog by remember { mutableStateOf(false) }

    val isHomeScreen = currentRoute?.contains(Home::class.qualifiedName ?: "") == true
    val isAnalyticsScreen = currentRoute?.contains(Analytics::class.qualifiedName ?: "") == true
    val isTransactionsScreen = currentRoute?.contains(Transactions::class.qualifiedName ?: "") == true
    val isAddTransactionScreen = currentRoute?.contains(AddTransaction::class.qualifiedName ?: "") == true
    val isSubscriptionsScreen = currentRoute?.contains(Subscriptions::class.qualifiedName ?: "") == true
    val isBudgetDetailScreen = currentRoute?.contains(BudgetDetail::class.qualifiedName ?: "") == true


    val hazeState = remember { HazeState() }

    // Wide windows (an unfolded foldable, a tablet) navigate with a rail at the start edge
    // instead of the bottom bar; lock and onboarding stay full screen.
    val windowLayout = rememberWindowLayout()
    val isFullScreenFlow = listOf(AppLock::class, OnBoarding::class).any {
        currentRoute?.contains(it.qualifiedName ?: "") == true
    }
    val showRail = windowLayout.useNavigationRail && currentRoute != null && !isFullScreenFlow
    val showBottomBar = showBottomNav && !windowLayout.useNavigationRail

    CompositionLocalProvider(LocalWindowLayout provides windowLayout) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ordinary navigation must not put every scrolling destination in a shared lookahead layout.
        Box {
            NavHost(
                navController = navController,
                startDestination = stableStartDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (showRail) NavigationRailWidth else 0.dp)
                    .hazeSource(hazeState),
            ) {
                // App Lock Screen
                composable<AppLock>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) {
                    AppLockScreen(
                        onUnlocked = {
                            // Back to what was open; on a cold start that is Home under the lock
                            if (!navController.popBackStack()) navController.safeNavigate(Home)
                        }
                    )
                }

                // Onboarding Screen
                composable<OnBoarding>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) {
                    OnBoardingScreen(
                        onOnBoardingComplete = {
                            navController.safeNavigate(Home) {
                                popUpTo(OnBoarding) { inclusive = true }
                            }
                        }
                    )
                }

                composable<Investments> {
                    ReadableWidth {
                    io.github.hootkit_dev.hootbook.presentation.ui.features.investments.InvestmentsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onManageManualAccounts = { navController.safeNavigate(AccountCategoryRoute("INVESTMENTS")) },
                        onAddInvestment = { navController.safeNavigate(AddAccount(AccountCategory.INVESTMENTS.name)) }
                    )
                    }
                }

                /* BOTTOM NAV SCREENS ---- */
                // Home Screen
                composable<Home>(
                    enterTransition = MainTabMotion.enter,
                    exitTransition = MainTabMotion.exit,
                    popEnterTransition = MainTabMotion.popEnter,
                    popExitTransition = MainTabMotion.popExit
                ) {
                    SharedTransitionLayout {
                        HomeScreen(
                            navController = navController,
                            onNavigateToSettings = { navController.safeNavigate(Settings) },
                            onNavigateToTransactions = { navController.safeNavigate(Transactions()) },
                            onNavigateToTransactionsWithSearch = {
                                navController.safeNavigate(Transactions(focusSearch = true))
                            },
                            onNavigateToSubscriptions = { navController.safeNavigate(Subscriptions) },
                            // As the Analytics tab does, so back returns Home
                            onNavigateToAnalytics = {
                                navController.safeNavigate(Analytics) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToBudgets = { id ->
                                if (id != null) {
                                    navController.safeNavigate(BudgetDetail(budgetId = id, sharedElementKey = "budget_card_$id"))
                                } else {
                                    navController.safeNavigate(Budgets())
                                }
                            },
                            onNavigateToBudgetHistory = { id ->
                                navController.safeNavigate(BudgetHistory(id))
                            },
                            onNavigateToLendBorrow = { filter -> navController.safeNavigate(LendBorrow(filter)) },
                            onTransactionClick = { transactionId, key ->
                                navController.safeNavigate(TransactionDetail(transactionId, key))
                            },
                            animatedContentScope = this@composable,
                        )
                    }
                }

                // Analytics Screen
                composable<Analytics>(
                    enterTransition = MainTabMotion.enter,
                    exitTransition = MainTabMotion.exit,
                    popEnterTransition = MainTabMotion.popEnter,
                    popExitTransition = MainTabMotion.popExit
                ) {
                    SharedTransitionLayout {
                        AnalyticsScreen(
                            onNavigateToTransactions = { category, merchant, period, currency ->
                                navController.safeNavigate(
                                    Transactions(
                                        category = category,
                                        merchant = merchant,
                                        period = period,
                                        currency = currency
                                    )
                                )
                            },
                            animatedContentScope = this@composable,
                            blurEffects = themeUiState.blurEffects,
                        )
                    }
                }

                /* SETTINGS & SUB-SCREENS ---- */
                composable<Settings>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    SettingsScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToCategories = { navController.safeNavigate(Categories) },
                        onNavigateToManageAccounts = { navController.safeNavigate(ManageAccounts) },
                        onNavigateToQuickTemplates = { navController.safeNavigate(QuickTemplates) },
                        onNavigateToAiAssistant = { navController.safeNavigate(AiAssistant) },
                        onNavigateToAppearance = { navController.safeNavigate(Appearance) },
                        onNavigateToProfile = { navController.safeNavigate(Profile) },
                        onNavigateToNotifications = { navController.safeNavigate(NotificationSettings) },
                        onNavigateToBudgets = { navController.safeNavigate(Budgets()) },
                        onNavigateToLendBorrow = { navController.safeNavigate(LendBorrow()) },
                        onNavigateToDataPrivacy = { navController.safeNavigate(DataPrivacy) },
                        onNavigateToCloudBackup = { navController.safeNavigate(CloudBackup) },
                        onNavigateToAbout = { navController.safeNavigate(About) },
                        onNavigateToCurrency = { navController.safeNavigate(CurrencySettings) },
                        blurEffects = themeUiState.blurEffects
                    )
                    }
                }

                composable<About>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    AboutScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToLicenses = { navController.safeNavigate(Licenses) },
                        blurEffects = themeUiState.blurEffects
                    )
                    }
                }

                composable<Licenses>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    LicensesScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                    }
                }

                composable<DataPrivacy>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    DataPrivacyScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToAccounts = { navController.safeNavigate(ManageAccounts) },
                        blurEffects = themeUiState.blurEffects
                    )
                    }
                }

                composable<CloudBackup>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    BackupSyncScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToAccounts = { navController.safeNavigate(ManageAccounts) },
                        onNavigateToFirebaseSync = { navController.safeNavigate(CloudSync) },
                        blurEffects = themeUiState.blurEffects
                    )
                    }
                }

                composable<CloudSync>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    SyncScreen(onNavigateBack = { navController.safePopBackStack() })
                    }
                }

                composable<CurrencySettings>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    CurrencySettingsScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                    }
                }

                composable<Profile>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    SharedTransitionLayout {
                        ProfileScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToContacts = { navController.safeNavigate(Contacts()) },
                            onNavigateToPerson = { personId ->
                                navController.safeNavigate(
                                    PersonDetail(personId, "person_avatar_$personId")
                                )
                            },
                            animatedContentScope = this@composable
                        )
                    }
                    }
                }

                composable<Contacts>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val contactsRoute = backStackEntry.toRoute<Contacts>()
                        ContactsScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToPersonDetail = { personId ->
                                navController.safeNavigate(
                                    PersonDetail(personId, "person_avatar_$personId")
                                )
                            },
                            selectedPersonId = contactsRoute.personId,
                            animatedContentScope = this@composable,
                            blurEffects = themeUiState.blurEffects
                        )
                    }
                    }
                }

                composable<Appearance>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    AppearanceScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                    }
                }

                composable<NotificationSettings>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    NotificationScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        blurEffects = themeUiState.blurEffects,
                    )
                    }
                }

                composable<Categories>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    CategoriesScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        blurEffects = themeUiState.blurEffects
                    )
                    }
                }


                composable<AccountCategoryRoute>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { entry ->
                    val selected = io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory.entries.firstOrNull {
                        it.name == entry.toRoute<AccountCategoryRoute>().category
                    } ?: io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory.WALLETS
                    AccountsListDetail(navController, this@composable) { openAccount ->
                        ManageAccountsScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToAccountDetail = openAccount,
                            onNavigateToAddAccount = { cat -> navController.safeNavigate(AddAccount(cat?.name)) },
                            blurEffects = themeUiState.blurEffects,
                            category = selected
                        )
                    }
                }

                composable<ManageAccounts>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    AccountsListDetail(navController, this@composable) { openAccount ->
                        ManageAccountsScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToAccountDetail = openAccount,
                            onNavigateToAddAccount = { cat -> navController.safeNavigate(AddAccount(cat?.name)) },
                            blurEffects = themeUiState.blurEffects
                        )
                    }
                }

                composable<AddAccount>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { entry ->
                    ReadableWidth {
                    val initialCategory = entry.toRoute<AddAccount>().category?.let { name ->
                        io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory.entries.firstOrNull { it.name == name }
                    }
                    AddAccountScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        initialCategory = initialCategory
                    )
                    }
                }

                composable<AiAssistant>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    AiAssistantScreen(onNavigateBack = { navController.safePopBackStack() })
                    }
                }

                composable<QuickTemplates>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    QuickTemplatesScreen(onNavigateBack = { navController.safePopBackStack() })
                    }
                }

                /* DETAIL SCREENS (with shared transitions) ---- */
                composable<TransactionDetail>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val transactionDetail = backStackEntry.toRoute<TransactionDetail>()
                        TransactionDetailScreen(
                            transactionId = transactionDetail.transactionId,
                            sharedElementKey = transactionDetail.sharedElementKey,
                            onNavigateBack = {
                                onEditComplete()
                                navController.safePopBackStack()
                            },
                            onNavigateToPersonDetail = { personId ->
                                navController.safeNavigate(PersonDetail(personId, "person_avatar_$personId"))
                            },
                            animatedContentScope = this@composable,
                            blurEffects = themeUiState.blurEffects,
                        )
                    }
                    }
                }

                composable<AddTransaction>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) {
                    ReadableWidth {
                    Box(Modifier.fillMaxSize())
                    }
                }

                composable<AccountDetail>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val accountDetail = backStackEntry.toRoute<AccountDetail>()
                        AccountDetailScreen(
                            navController = navController,
                            bankName = accountDetail.bankName,
                            accountLast4 = accountDetail.accountLast4,
                            animatedContentScope = this@composable
                        )
                    }
                    }
                }

                composable<Subscriptions>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) {
                    ReadableWidth {
                    SharedTransitionLayout {
                        SubscriptionsScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onEditSubscription = { id ->
                                navController.safeNavigate(AddTransaction(initialTab = 1, subscriptionId = id))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }
                    }
                }

                composable<Transactions>(
                    enterTransition = MainTabMotion.enter,
                    exitTransition = MainTabMotion.exit,
                    popEnterTransition = MainTabMotion.popEnter,
                    popExitTransition = MainTabMotion.popExit
                ) { backStackEntry ->
                    val transactions = backStackEntry.toRoute<Transactions>()
                    // Wide windows: the list on the left, the tapped transaction on the right
                    var paneTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }
                    val listDetail = LocalWindowLayout.current.widthDp >= LIST_DETAIL_MIN_WIDTH_DP
                    val list: @Composable () -> Unit = {
                        TransactionsScreen(
                            onExport = { showExportDialog = true },
                            transactionsViewModel = transactionsViewModel,
                            initialCategory = transactions.category,
                            initialMerchant = transactions.merchant,
                            initialPeriod = transactions.period,
                            initialCurrency = transactions.currency,
                            initialType = transactions.type,
                            focusSearch = transactions.focusSearch,
                            onNavigateBack = { navController.safePopBackStack() },
                            onTransactionClick = { transactionId, key ->
                                navController.safeNavigate(TransactionDetail(transactionId, key))
                            },
                            onNavigateToSettings = {
                                navController.safeNavigate(Settings)
                            },
                            animatedContentScope = this@composable,
                            blurEffects = themeUiState.blurEffects,
                            onTransactionSelected = if (listDetail) { id -> paneTransactionId = id } else null
                        )
                    }
                    if (!listDetail) {
                        list()
                    } else {
                        // Back closes the open transaction before leaving the tab
                        BackHandler(enabled = paneTransactionId != null) { paneTransactionId = null }
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) { list() }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Box(modifier = Modifier.weight(1f)) {
                                TransactionDetailPane(
                                    transactionId = paneTransactionId,
                                    onClose = { paneTransactionId = null },
                                    onNavigateToPersonDetail = { personId ->
                                        navController.safeNavigate(PersonDetail(personId, "person_avatar_$personId"))
                                    },
                                    blurEffects = themeUiState.blurEffects
                                )
                            }
                        }
                    }
                }

                composable<Budgets>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val budgets = backStackEntry.toRoute<Budgets>()
                        BudgetsScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onBudgetClick = { id, key ->
                                navController.safeNavigate(BudgetDetail(budgetId = id, sharedElementKey = key))
                            },
                            onHistoryClick = { id ->
                                navController.safeNavigate(BudgetHistory(id))
                            },
                            animatedContentScope = this@composable,
                            sharedElementPrefix = budgets.sharedElementPrefix,
                            blurEffects = themeUiState.blurEffects
                        )
                    }
                    }
                }

                composable<BudgetDetail>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val budgetDetail = backStackEntry.toRoute<BudgetDetail>()
                        BudgetDetailScreen(
                            budgetId = budgetDetail.budgetId,
                            startDate = budgetDetail.startDate,
                            endDate = budgetDetail.endDate,
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToHistory = { id -> navController.safeNavigate(BudgetHistory(id)) },
                            onTransactionClick = { transactionId, key ->
                                navController.safeNavigate(TransactionDetail(transactionId, key))
                            },
                            animatedContentScope = this@composable,
                            sharedElementKey = budgetDetail.sharedElementKey,
                            blurEffects = themeUiState.blurEffects
                        )
                    }
                    }
                }

                composable<BudgetHistory>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val budgetHistory = backStackEntry.toRoute<BudgetHistory>()
                        BudgetHistoryScreen(
                            budgetId = budgetHistory.budgetId,
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToDetail = { id, start, end ->
                                navController.safeNavigate(BudgetDetail(
                                    budgetId = id,
                                    startDate = start?.toString(),
                                    endDate = end?.toString()
                                ))
                            }
                        )
                    }
                    }
                }

                composable<LendBorrow>(
                    enterTransition = HootbookTransitions.horizontalSlideEnter,
                    exitTransition = HootbookTransitions.horizontalSlideExit,
                    popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                    popExitTransition = HootbookTransitions.horizontalSlidePopExit
                ) {
                    ReadableWidth {
                    SharedTransitionLayout {
                        LendBorrowScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onNavigateToPersonDetail = { personId ->
                                navController.safeNavigate(
                                    PersonDetail(personId, "person_avatar_$personId")
                                )
                            },
                            animatedContentScope = this@composable,
                            blurEffects = themeUiState.blurEffects
                        )
                    }
                    }
                }

                composable<PersonDetail>(
                    enterTransition = HootbookTransitions.noneEnter,
                    exitTransition = HootbookTransitions.noneExit,
                    popEnterTransition = HootbookTransitions.noneEnter,
                    popExitTransition = HootbookTransitions.noneExit
                ) { backStackEntry ->
                    ReadableWidth {
                    SharedTransitionLayout {
                        val personDetail = backStackEntry.toRoute<PersonDetail>()
                        PersonDetailScreen(
                            onNavigateBack = { navController.safePopBackStack() },
                            onTransactionClick = { transactionId, key ->
                                navController.safeNavigate(TransactionDetail(transactionId, key))
                            },
                            sharedElementKey = personDetail.sharedElementKey,
                            animatedContentScope = this@composable
                        )
                    }
                    }
                }
            }
        }

        SharedTransitionLayout {
            // Add Screen Overlay - Handled here for shared transition from FAB
            AnimatedVisibility(
                visible = isAddTransactionScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                val addTransaction = if (isAddTransactionScreen) {
                    try { navBackStackEntry?.toRoute<AddTransaction>() ?: AddTransaction() }
                    catch (_: Exception) { AddTransaction() }
                } else AddTransaction()

                AddScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    animatedVisibilityScope = this@AnimatedVisibility,
                    initialTab = addTransaction.initialTab,
                    subscriptionId = addTransaction.subscriptionId,
                    transactionType = addTransaction.type,
                    templateId = addTransaction.templateId,
                    blurEffects = themeUiState.blurEffects,
                )
            }

            // FABs Container - Shown on Home, Transactions, Subscriptions, and Budget Detail
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = isHomeScreen || isTransactionsScreen || isSubscriptionsScreen || isBudgetDetailScreen,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimensions.Padding.content)
                        // Clear of the 64 dp ShortNavigationBar when it shows
                        .padding(bottom = if (showBottomBar) 64.dp else 0.dp)
                        .navigationBarsPadding()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Add FAB
                        FloatingActionButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                val initialTab = if (isSubscriptionsScreen) 1 else 0
                                navController.safeNavigate(AddTransaction(initialTab = initialTab))
                            },
                            modifier = Modifier
                                .then(
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "fab_to_add"),
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        boundsTransform = { _, _ ->
                                            tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
                                        },
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                            contentScale = ContentScale.FillBounds,
                                            alignment = Alignment.Center
                                        )
                                    )
                                        .skipToLookaheadSize()
                                ),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.add_transaction_subscription_cd)
                            )
                        }
                    }
                }
            }



            // Export Transactions Dialog (Only when on TransactionsScreen)
            if (showExportDialog && isTransactionsScreen) {
                // Collected inside the branch so the export list is only observed while the
                // dialog is actually open.
                val transactionsUiState by transactionsViewModel.uiState.collectAsStateWithLifecycle()
                ExportTransactionsDialog(
                    transactions = transactionsUiState.transactions,
                    onDismiss = { showExportDialog = false },
                    blurEffects = themeUiState.blurEffects,
                    hazeState = hazeState
                )
            }
        }

        // Block pointer input while a navigation transition is in progress so a quick tap
        // meant for the destination screen doesn't hit the still-composed outgoing screen.
        // Placed under the navigation bar so tab taps are never swallowed: switching tabs
        // rapidly used to drop taps that landed during the previous tab's fade.
        NavigationTransitionInputBlocker(
            navController = navController,
            isAddTransactionScreen = isAddTransactionScreen
        )

        // Bottom Navigation
        HootbookBottomNavigation(
            navController = navController,
            currentDestination = currentDestination,
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Navigation rail on wide windows
        HootbookNavigationRail(
            navController = navController,
            currentDestination = currentDestination,
            visible = showRail,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
    }
}

/**
 * Overlays a transparent full-screen surface that consumes all pointer input while the current
 * destination is not yet RESUMED (i.e. a navigation transition is animating). Shared element
 * transitions hold the outgoing screen composed during the transition, which would otherwise
 * let a quick tap meant for the list hit a clickable element on the screen being popped.
 */
private const val TransitionBlockGraceMillis = 600L

@Composable
private fun NavigationTransitionInputBlocker(
    navController: NavHostController,
    isAddTransactionScreen: Boolean
) {
    val entry by navController.currentBackStackEntryAsState()
    val topState = entry?.lifecycle?.let { lifecycle ->
        lifecycle.currentStateFlow.collectAsStateWithLifecycle(
            initialValue = lifecycle.currentState,
            lifecycle = lifecycle
        ).value
    }
    val inTransition = !isAddTransactionScreen &&
        topState != null && topState != Lifecycle.State.RESUMED
    // Safety valve: a transition interrupted by rapid tab taps can leave the top entry STARTED
    // without ever reaching RESUMED. Blocking must never outlive a real transition, so it ends
    // after a short grace period regardless of lifecycle state.
    var timedOut by remember(entry?.id) { mutableStateOf(false) }
    LaunchedEffect(entry?.id, inTransition) {
        timedOut = false
        if (inTransition) {
            kotlinx.coroutines.delay(TransitionBlockGraceMillis)
            timedOut = true
        }
    }
    val shouldBlock = inTransition && !timedOut
    if (shouldBlock) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        )
    }
}

/**
 * An accounts list, with the chosen account beside it on wide windows (an unfolded foldable)
 * and as its own screen otherwise. [list] receives what opening an account should do.
 */
@Composable
private fun AccountsListDetail(
    navController: NavHostController,
    animatedContentScope: AnimatedVisibilityScope,
    list: @Composable (openAccount: (bankName: String, accountLast4: String) -> Unit) -> Unit
) {
    if (LocalWindowLayout.current.widthDp < ACCOUNT_LIST_DETAIL_MIN_WIDTH_DP) {
        ReadableWidth {
            list { bankName, last4 -> navController.safeNavigate(AccountDetail(bankName, last4)) }
        }
        return
    }
    var paneAccount by rememberSaveable(stateSaver = PaneAccountSaver) { mutableStateOf<PaneAccount?>(null) }
    BackHandler(enabled = paneAccount != null) { paneAccount = null }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            list { bankName, last4 -> paneAccount = PaneAccount(bankName, last4) }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(modifier = Modifier.weight(1f)) {
            AccountDetailPane(
                account = paneAccount,
                navController = navController,
                animatedContentScope = animatedContentScope,
                onClose = { paneAccount = null },
                onRenamed = { paneAccount = it }
            )
        }
    }
}

private val PaneAccountSaver = androidx.compose.runtime.saveable.listSaver<PaneAccount?, String>(
    save = { account -> account?.let { listOf(it.bankName, it.accountLast4) } ?: emptyList() },
    restore = { values -> if (values.size == 2) PaneAccount(values[0], values[1]) else null }
)
