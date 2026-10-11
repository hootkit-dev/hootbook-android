package io.github.hootkit_dev.hootbook.presentation.navigation

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.hootkit_dev.hootbook.presentation.core.investments.BrokerAccountRef
import dev.hootkit.core.Table
import dev.hootkit.core.TransactionKind
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.CoreIntentIngress
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions.SubscriptionsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates.QuickTemplatesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.contacts.ContactsScreen
import io.github.hootkit_dev.hootbook.presentation.core.ai.CoreAiScreen
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreAnalyticsScreen
import io.github.hootkit_dev.hootbook.presentation.core.appearance.CoreAppearanceScreen
import io.github.hootkit_dev.hootbook.presentation.core.applock.CoreAppLockSettingsScreen
import io.github.hootkit_dev.hootbook.presentation.core.backup.CoreBackupScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.CategoriesScreen
import io.github.hootkit_dev.hootbook.presentation.core.currencies.CoreCurrenciesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.home.HomeScreen
import io.github.hootkit_dev.hootbook.presentation.core.investments.CoreInvestmentsScreen
import io.github.hootkit_dev.hootbook.presentation.core.investments.CoreInvestmentsViewModel
import io.github.hootkit_dev.hootbook.presentation.core.navigation.CoreLocaleGate
import io.github.hootkit_dev.hootbook.presentation.core.notifications.CoreNotificationPreferencesScreen
import io.github.hootkit_dev.hootbook.presentation.core.onboarding.CoreOnboardingScreen
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIconsScreen
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreProfileScreen
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreSettingsScreen
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AddScreen
import androidx.activity.compose.BackHandler
import dev.hootkit.core.TransactionFilter
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.AboutScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.LicensesScreen
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HootbookNavHost(
    session: CoreSession,
    preferences: CoreUiPreferencesStore,
    ingress: CoreIntentIngress,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val prefs by preferences.state.collectAsStateWithLifecycle()
    val pending by ingress.pending.collectAsStateWithLifecycle()
    val shareFailure by ingress.failed.collectAsStateWithLifecycle()
    val stableStartDestination = remember { if (prefs.onboardingFinished) Home else OnBoarding }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    ObserveMainTabSettled(navBackStackEntry)
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val addEntry = visibleEntries.lastOrNull { it.destination.hasRoute<AddTransaction>() }
    val addStateHolder = rememberSaveableStateHolder()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomNav = BOTTOM_NAV_ROUTES.any { name -> currentRoute?.contains(name.orEmpty()) == true }
    val isHomeScreen = currentRoute?.contains(Home::class.qualifiedName.orEmpty()) == true
    val isTransactionsScreen = currentRoute?.contains(Transactions::class.qualifiedName.orEmpty()) == true
    val isAddTransactionScreen = currentRoute?.contains(AddTransaction::class.qualifiedName.orEmpty()) == true
    val isSubscriptionsScreen = currentRoute?.contains(Subscriptions::class.qualifiedName.orEmpty()) == true
    val isBudgetDetailScreen = currentRoute?.contains(BudgetDetail::class.qualifiedName.orEmpty()) == true
    val windowLayout = rememberWindowLayout()
    val isFullScreenFlow = !prefs.onboardingFinished
    val showRail = windowLayout.useNavigationRail && currentRoute != null && !isFullScreenFlow
    val showBottomBar = showBottomNav && !windowLayout.useNavigationRail && !isFullScreenFlow
    val hazeState = remember { HazeState() }
    val view = LocalView.current
    var schema by remember(session) { mutableStateOf<UInt?>(null) }
    var recordHint by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        try { schema = session.withCore { it.version().schema } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { /* Schema metadata is optional; startup owns Core failures. */ }
    }
    val back: () -> Unit = { navController.safePopBackStack() }
    fun go(route: Any) { navController.safeNavigate(route) { launchSingleTop = true } }
    fun coreScreen(route: String) { go(coreScreenDestination(route)) }
    fun record(table: Table, id: String) {
        val destination = when (table) {
            Table.TRANSACTIONS -> TransactionDetail(id)
            Table.ACCOUNTS -> AccountDetail(id)
            Table.BUDGETS -> BudgetDetail(id)
            Table.SUBSCRIPTIONS -> Subscriptions(id)
            Table.PEOPLE -> PersonDetail(id)
            else -> {
                recordHint = "$table · $id"
                when (table) {
                    Table.QUICK_TEMPLATES -> QuickTemplates
                    Table.CATEGORIES, Table.SUBCATEGORIES -> Categories
                    Table.DEBTS -> LendBorrow()
                    else -> ManageAccounts
                }
            }
        }
        go(destination)
    }

    CoreLocaleGate(session) {
        LaunchedEffect(pending.firstOrNull()?.token, prefs.onboardingFinished, navBackStackEntry?.id) {
            val entry = pending.firstOrNull() ?: return@LaunchedEffect
            if (navController.routeIngress(entry.target, prefs.onboardingFinished)) ingress.consumed(entry.token)
        }
        CompositionLocalProvider(LocalWindowLayout provides windowLayout) {
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.fillMaxSize()
                    .padding(start = if (showRail) NavigationRailWidth else 0.dp)
                    .hazeSource(hazeState)) {
                    if (shareFailure) {
                        TextButton(onClick = ingress::dismissFailure, modifier = Modifier.statusBarsPadding()) {
                            Text(stringResource(R.string.core_shell_share_failed))
                        }
                    }
                    recordHint?.let { hint ->
                        Row(Modifier.statusBarsPadding()) {
                            Text(hint, Modifier.weight(1f))
                            TextButton(onClick = { recordHint = null }) { Text(stringResource(R.string.core_shell_close)) }
                        }
                    }
                    // Temporary Core screens do not reserve the original shell's bottom-bar space.
                    Box(Modifier.weight(1f).padding(bottom = if (showBottomBar) 64.dp else 0.dp)
                        .safeDrawingPadding().consumeWindowInsets(WindowInsets.safeDrawing)
                        .semantics { testTagsAsResourceId = true }
                        .testTag("hootbook-screen-" + currentRoute?.substringBefore('?')?.substringBefore('/')?.substringAfterLast('.').orEmpty())) {
                        NavHost(
                            navController = navController,
                            startDestination = stableStartDestination,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = HootbookTransitions.horizontalSlideEnter,
                            exitTransition = HootbookTransitions.horizontalSlideExit,
                            popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                            popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                        ) {
                            composable<OnBoarding>(
                                enterTransition = HootbookTransitions.noneEnter,
                                exitTransition = HootbookTransitions.noneExit,
                                popEnterTransition = HootbookTransitions.noneEnter,
                                popExitTransition = HootbookTransitions.noneExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until onboarding is migrated
                                    CoreOnboardingScreen(session, preferences, ::coreScreen) {
                                        navController.navigate(Home) { popUpTo<OnBoarding> { inclusive = true } }
                                    }
                                }
                            }
                            composable<Home>(
                                enterTransition = MainTabMotion.enter,
                                exitTransition = MainTabMotion.exit,
                                popEnterTransition = MainTabMotion.popEnter,
                                popExitTransition = MainTabMotion.popExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        HomeScreen(
                                            navController = navController,
                                            onNavigateToProfile = { go(Profile) },
                                            onNavigateToTransactions = { go(Transactions()) },
                                            onNavigateToSubscriptions = { go(Subscriptions()) },
                                            onNavigateToAnalytics = { go(Analytics) },
                                            onNavigateToBudgets = { id -> if (id == null) go(Budgets()) else go(BudgetDetail(id)) },
                                            onNavigateToBudgetHistory = { go(BudgetHistory(it)) },
                                            onNavigateToLendBorrow = { go(LendBorrow(it)) },
                                            onTransactionClick = { id, key -> go(TransactionDetail(id, key)) },
                                            animatedContentScope = this@composable,
                                        )
                                    }
                                }
                            }
                            composable<Transactions>(
                                enterTransition = MainTabMotion.enter,
                                exitTransition = MainTabMotion.exit,
                                popEnterTransition = MainTabMotion.popEnter,
                                popExitTransition = MainTabMotion.popExit,
                            ) { entry ->
                                val route = entry.toRoute<Transactions>()
                                var paneTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
                                val listDetail = LocalWindowLayout.current.widthDp >= LIST_DETAIL_MIN_WIDTH_DP
                                val initialFilter = remember(route) {
                                    if (route == Transactions()) null else emptyTransactionFilter().copy(
                                        accountIds = listOfNotNull(route.accountId),
                                        categoryIds = listOfNotNull(route.categoryId),
                                        currency = route.currency,
                                        kinds = route.kinds?.split(",")?.map(TransactionKind::valueOf).orEmpty(),
                                        from = route.from?.let { java.time.LocalDate.parse(it).atStartOfDay().coreTime() },
                                        to = route.to?.let { java.time.LocalDate.parse(it).atStartOfDay().coreTime() },
                                    )
                                }
                                val filterKey = remember(route) {
                                    kotlinx.serialization.json.Json.encodeToString(Transactions.serializer(), route)
                                }
                                val list: @Composable () -> Unit = {
                                    key(filterKey) {
                                    TransactionsScreen(
                                        transactionsViewModel = hiltViewModel(key = filterKey),
                                        initialFilter = initialFilter, onNavigateBack = back,
                                        onTransactionClick = { id, key -> go(TransactionDetail(id, key)) },
                                        onNavigateToSettings = { go(Settings) },
                                        animatedContentScope = this@composable, blurEffects = prefs.blur,
                                        onTransactionSelected = if (listDetail) { id -> paneTransactionId = id } else null,
                                    )
                                    }
                                }
                                if (!listDetail) list() else {
                                    BackHandler(enabled = paneTransactionId != null) { paneTransactionId = null }
                                    Row(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(1f)) { list() }
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Box(Modifier.weight(1f)) {
                                            TransactionDetailPane(paneTransactionId, { paneTransactionId = null },
                                                { go(PersonDetail(it, "person_avatar_$it")) }, prefs.blur)
                                        }
                                    }
                                }
                            }

                            composable<TransactionDetail>(
                                enterTransition = HootbookTransitions.noneEnter,
                                exitTransition = HootbookTransitions.noneExit,
                                popEnterTransition = HootbookTransitions.noneEnter,
                                popExitTransition = HootbookTransitions.noneExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        val route = entry.toRoute<TransactionDetail>()
                                        TransactionDetailScreen(
                                            transactionId = route.transactionId, sharedElementKey = route.sharedElementKey,
                                            onNavigateBack = back, onNavigateToPersonDetail = { go(PersonDetail(it, "person_avatar_$it")) },
                                            animatedContentScope = this@composable, blurEffects = prefs.blur,
                                        )
                                    }
                                }
                            }
                            composable<AddTransaction>(
                                enterTransition = HootbookTransitions.noneEnter,
                                exitTransition = HootbookTransitions.noneExit,
                                popEnterTransition = HootbookTransitions.noneEnter,
                                popExitTransition = HootbookTransitions.noneExit,
                            ) { entry ->
                                ReadableWidth {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                            composable<Analytics>(
                                enterTransition = MainTabMotion.enter,
                                exitTransition = MainTabMotion.exit,
                                popEnterTransition = MainTabMotion.popEnter,
                                popExitTransition = MainTabMotion.popExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until analytics is migrated
                                    CoreAnalyticsScreen(
                                        onBack = back, onTransactions = { go(Transactions(categoryId = it)) },
                                        onFilterTransactions = { filter ->
                                            go(Transactions(
                                                categoryId = filter.categoryIds.singleOrNull(), from = filter.from?.coreDateText()?.substringBefore('T'),
                                                to = filter.to?.coreDateText()?.substringBefore('T'), currency = filter.currency,
                                                kinds = filter.kinds.joinToString(",") { it.name },
                                            ))
                                        },
                                    )
                                }
                            }
                            composable<ManageAccounts>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                AccountsListDetail(animatedContentScope = this@composable,
                                    onOpen = { go(AccountDetail(it)) }, onTransaction = { go(TransactionDetail(it)) },
                                    onBrokerage = { connection, id -> go(Investments(connection.toString(), id)) }) { open ->
                                    ManageAccountsScreen(onNavigateBack = back, onNavigateToAccountDetail = open,
                                        onNavigateToAddAccount = { go(AddAccount(it?.name)) },
                                        onBrokerage = { connection, id -> go(Investments(connection.toString(), id)) })
                                }
                            }
                            composable<AccountCategoryRoute>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                val route = entry.toRoute<AccountCategoryRoute>()
                                val category = AccountCategory.entries.firstOrNull { it.name == route.category }
                                AccountsListDetail(animatedContentScope = this@composable,
                                    onOpen = { go(AccountDetail(it)) }, onTransaction = { go(TransactionDetail(it)) },
                                    onBrokerage = { connection, id -> go(Investments(connection.toString(), id)) }) { open ->
                                    ManageAccountsScreen(onNavigateBack = back, onNavigateToAccountDetail = open,
                                        onNavigateToAddAccount = { go(AddAccount(it?.name)) }, category = category,
                                        onBrokerage = { connection, id -> go(Investments(connection.toString(), id)) })
                                }
                            }
                            composable<AccountDetail>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                val route = entry.toRoute<AccountDetail>()
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        AccountDetailScreen(accountId = route.accountId, initialHistory = route.history,
                                            animatedContentScope = this@composable, onNavigateBack = back,
                                            onTransaction = { go(TransactionDetail(it)) },
                                            onBrokerage = { connection, id -> go(Investments(connection.toString(), id)) })
                                    }
                                }
                            }
                            composable<AddAccount>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                val route = entry.toRoute<AddAccount>()
                                ReadableWidth {
                                    AddAccountScreen(onNavigateBack = back,
                                        initialCategory = AccountCategory.entries.firstOrNull { it.name == route.category },
                                        onInvestments = { go(Investments()) })
                                }
                            }
                            composable<Categories>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    CategoriesScreen(onNavigateBack = back, blurEffects = prefs.blur)
                                }
                            }
                            composable<Budgets>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        BudgetsScreen(onNavigateBack = back,
                                            onBudgetClick = { id, key -> go(BudgetDetail(id, sharedElementKey = key)) },
                                            onHistoryClick = { go(BudgetHistory(it)) },
                                            sharedElementPrefix = entry.toRoute<Budgets>().sharedElementPrefix,
                                            animatedContentScope = this@composable, blurEffects = prefs.blur)
                                    }
                                }
                            }
                            composable<BudgetDetail>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    val route = entry.toRoute<BudgetDetail>()
                                    SharedTransitionLayout {
                                        BudgetDetailScreen(budgetId = route.budgetId, startDate = route.startDate, endDate = route.endDate,
                                            onNavigateBack = back, onNavigateToHistory = { go(BudgetHistory(it)) },
                                            animatedContentScope = this@composable, sharedElementKey = route.sharedElementKey,
                                            blurEffects = prefs.blur)
                                    }
                                }
                            }
                            composable<BudgetHistory>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    BudgetHistoryScreen(entry.toRoute<BudgetHistory>().budgetId, back,
                                        onNavigateToDetail = { id, start, end -> go(BudgetDetail(id, startDate = start?.coreDateText(), endDate = end?.coreDateText())) })
                                }
                            }
                            composable<Subscriptions>(
                                enterTransition = HootbookTransitions.noneEnter,
                                exitTransition = HootbookTransitions.noneExit,
                                popEnterTransition = HootbookTransitions.noneEnter,
                                popExitTransition = HootbookTransitions.noneExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        SubscriptionsScreen(onNavigateBack = back,
                                            initialSubscriptionId = entry.toRoute<Subscriptions>().subscriptionId,
                                            onEditSubscription = { go(AddTransaction(initialTab = 1, subscriptionId = it)) },
                                            onRecordPayment = { go(AddTransaction(subscriptionId = it)) },
                                            sharedTransitionScope = this@SharedTransitionLayout, animatedContentScope = this@composable)
                                    }
                                }
                            }
                            composable<QuickTemplates>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    QuickTemplatesScreen(back)
                                }
                            }
                            composable<LendBorrow>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        LendBorrowScreen(back, { go(PersonDetail(it, "person_avatar_$it")) },
                                            animatedContentScope = this@composable, blurEffects = prefs.blur)
                                    }
                                }
                            }
                            composable<Contacts>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        ContactsScreen(back, { go(PersonDetail(it, "person_avatar_$it")) },
                                            selectedPersonId = entry.toRoute<Contacts>().personId,
                                            animatedContentScope = this@composable, blurEffects = prefs.blur)
                                    }
                                }
                            }
                            composable<PersonDetail>(
                                enterTransition = HootbookTransitions.noneEnter,
                                exitTransition = HootbookTransitions.noneExit,
                                popEnterTransition = HootbookTransitions.noneEnter,
                                popExitTransition = HootbookTransitions.noneExit,
                            ) { entry ->
                                ReadableWidth {
                                    SharedTransitionLayout {
                                        PersonDetailScreen(back, { id, key -> go(TransactionDetail(id, key)) },
                                            sharedElementKey = entry.toRoute<PersonDetail>().sharedElementKey,
                                            animatedContentScope = this@composable)
                                    }
                                }
                            }
                            composable<Investments> { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until investments is migrated
                                    val route = entry.toRoute<Investments>()
                                    val vm: CoreInvestmentsViewModel = hiltViewModel()
                                    val state by vm.state.collectAsStateWithLifecycle()
                                    var opened by rememberSaveable { mutableStateOf(false) }
                                    LaunchedEffect(route, state.loading) {
                                        val connection = route.connectionId?.toLongOrNull()
                                        val account = route.accountId
                                        if (!state.loading && !opened && connection != null && account != null) {
                                            opened = true
                                            vm.selectAccount(BrokerAccountRef(connection, account))
                                        }
                                    }
                                    CoreInvestmentsScreen(back, { go(ManageAccounts) }, { go(AccountDetail(it, history = true)) }, vm)
                                }
                            }
                            composable<AiAssistant>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until AI assistant is migrated
                                    CoreAiScreen(back)
                                }
                            }
                            composable<Settings>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until settings is migrated
                                    CoreSettingsScreen(back, { go(CurrencySettings()) }, { go(DataPrivacy) },
                                        onRoute = ::coreScreen, coreSchemaVersion = schema)
                                }
                            }
                            composable<CurrencySettings>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until currency settings is migrated
                                    CoreCurrenciesScreen(back, entry.toRoute<CurrencySettings>().currency)
                                }
                            }
                            composable<DataPrivacy>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until data and privacy is migrated
                                    CoreBackupScreen(back)
                                }
                            }
                            composable<CloudSync>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until sync is migrated
                                    CoreSyncScreen(back, { go(DataPrivacy) }, ::record)
                                }
                            }
                            composable<Profile>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until profile is migrated
                                    CoreProfileScreen(back, { go(Contacts()) }, { go(MerchantIcons()) })
                                }
                            }
                            composable<MerchantIcons> { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until merchant icons is migrated
                                    CoreMerchantIconsScreen(back, entry.toRoute<MerchantIcons>().name)
                                }
                            }
                            composable<Appearance>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until appearance is migrated
                                    CoreAppearanceScreen(back, { go(AppLockSettings) })
                                }
                            }
                            composable<AppLockSettings> { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until app-lock settings is migrated
                                    CoreAppLockSettingsScreen(back)
                                }
                            }
                            composable<NotificationSettings>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    // TEMP(restore-ui): mount Core screen until notification settings is migrated
                                    CoreNotificationPreferencesScreen(back)
                                }
                            }
                            composable<About>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    AboutScreen(back, { go(Licenses) }, blurEffects = prefs.blur, coreSchemaVersion = schema)
                                }
                            }
                            composable<Licenses>(
                                enterTransition = HootbookTransitions.horizontalSlideEnter,
                                exitTransition = HootbookTransitions.horizontalSlideExit,
                                popEnterTransition = HootbookTransitions.horizontalSlidePopEnter,
                                popExitTransition = HootbookTransitions.horizontalSlidePopExit,
                            ) { entry ->
                                ReadableWidth {
                                    LicensesScreen(back, coreSchemaVersion = schema)
                                }
                            }
                        }
                    }
                }
                SharedTransitionLayout {
                    AnimatedVisibility(
                        visible = isAddTransactionScreen,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        addEntry?.let { entry ->
                            entry.LocalOwnersProvider(addStateHolder) {
                                val route = entry.toRoute<AddTransaction>()
                                val formKey = remember(route) {
                                    kotlinx.serialization.json.Json.encodeToString(AddTransaction.serializer(), route)
                                }
                                key(formKey) {
                                    AddScreen(
                                        addViewModel = hiltViewModel(key = formKey),
                                        onNavigateBack = back, animatedVisibilityScope = this@AnimatedVisibility,
                                        initialTab = route.initialTab, subscriptionId = route.subscriptionId,
                                        transactionType = route.type, templateId = route.templateId, blurEffects = prefs.blur,
                                    )
                                }
                            }
                        }
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

/** Original wide-window account list/detail split, keyed by the Core account id. */
@Composable
private fun AccountsListDetail(
    animatedContentScope: AnimatedVisibilityScope,
    onOpen: (String) -> Unit,
    onTransaction: (String) -> Unit,
    onBrokerage: (Long, String) -> Unit,
    list: @Composable (openAccount: (String) -> Unit) -> Unit,
) {
    if (LocalWindowLayout.current.widthDp < ACCOUNT_LIST_DETAIL_MIN_WIDTH_DP) {
        ReadableWidth { list(onOpen) }
        return
    }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = accountId != null) { accountId = null }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { list { accountId = it } }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(Modifier.weight(1f)) {
            AccountDetailPane(accountId = accountId, animatedContentScope = animatedContentScope,
                onTransaction = onTransaction, onBrokerage = onBrokerage, onClose = { accountId = null })
        }
    }
}
