@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.data.repository.byCurrency
import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.LocalWindowLayout
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogActionsRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.LendBorrowRow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.utils.capitalizeFirst
import io.github.hootkit_dev.hootbook.presentation.navigation.AccountDetail
import io.github.hootkit_dev.hootbook.presentation.navigation.NotificationSettings
import io.github.hootkit_dev.hootbook.presentation.navigation.AiAssistant
import io.github.hootkit_dev.hootbook.presentation.navigation.safeNavigate
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountBalanceRow
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountSectionSummary
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountSectionToggle
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.buildAccountSections
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.listKey
import io.github.hootkit_dev.hootbook.presentation.ui.components.BudgetCarousel
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencySelectionBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.GreetingCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.HeatmapWidget
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.SubscriptionIconsStack
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance.ThemeViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Convertshape2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Gallery
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.icons.RefreshCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Setting2
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.bottomFade
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeDefaults.tint
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.presentation.ui.components.LendBorrowCard
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalHazeApi::class
)
@Composable
fun SharedTransitionScope.HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionsWithSearch: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToBudgets: (Long?) -> Unit = {},
    onNavigateToBudgetHistory: (Long) -> Unit = {},
    onNavigateToLendBorrow: (String?) -> Unit = { _ -> },
    onTransactionClick: (Long, String) -> Unit = { _, _ -> },
    animatedContentScope: AnimatedContentScope? = null,
) {

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val deletedTransaction by homeViewModel.deletedTransaction.collectAsState()
    val categoriesMap by homeViewModel.categoriesMap.collectAsStateWithLifecycle()
    val subcategoriesMap by homeViewModel.subcategoriesMap.collectAsStateWithLifecycle()
    val lookups by homeViewModel.lookups.collectAsStateWithLifecycle()
    val homeWidgets by homeViewModel.homeWidgets.collectAsStateWithLifecycle()
    val nextCardDue by homeViewModel.nextCardDue.collectAsStateWithLifecycle()
    val overviewViewModel: io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountOverviewViewModel = hiltViewModel()
    val overviewItems by overviewViewModel.items.collectAsStateWithLifecycle()
    val holdings = io.github.hootkit_dev.hootbook.data.repository.LocalAccountHoldings.current
    LaunchedEffect(uiState.accountBalances, uiState.creditCards, uiState.selectedCurrency, holdings) {
        // Every currency an account holds counts towards its category
        overviewViewModel.update(
            (uiState.accountBalances + uiState.creditCards).byCurrency(holdings),
            uiState.selectedCurrency
        )
    }
    val openCategory: (io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory) -> Unit = { category ->
        val investments = io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory.INVESTMENTS
        // Nothing to show yet: connecting or adding happens in Add account → Broker
        if (category == investments && overviewItems.any {
                it.category == investments && it.status == io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.OverviewStatus.CONNECT
            })
            navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.AddAccount(investments.name))
        else if (category == investments)
            navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.Investments)
        else navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.AccountCategoryRoute(category.name))
    }
    val activity = LocalActivity.current
    val hazeStateBanner = remember { HazeState()}
    val blurEffects = themeUiState.blurEffects

    val snackbarHostState = remember { SnackbarHostState() }
    
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backPressScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            backPressScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(context.getString(R.string.press_back_again_to_close), duration = SnackbarDuration.Short)
            }
        }
    }
    val scope = rememberCoroutineScope()

    var showEditWidgetsSheet by remember { mutableStateOf(false) }
    var showAccountBreakdown by rememberSaveable { mutableStateOf(false) }

    // Haptic feedback
    val view = LocalView.current


    LaunchedEffect(Unit) {
        // Refresh account balances to ensure proper currency conversion
        homeViewModel.refreshAccountBalances()
        // A new day since Home was last shown moves "this month" on
        homeViewModel.refreshDate()
    }

    // Handle delete undo snackbar
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            homeViewModel.clearDeletedTransaction()

            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.transaction_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    // Pass the transaction directly since state is already
                    // cleared
                    homeViewModel.undoDeleteTransaction(transaction)
                }
            }
        }
    }

    // Clear snackbar when navigating away
    DisposableEffect(Unit) { onDispose { snackbarHostState.currentSnackbarData?.dismiss() } }

    val lazyListState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                GreetingCard(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(vertical = 12.dp),
                    userName = uiState.userName,
                    profileImageUri = uiState.profileImageUri,
                    profileBackgroundColor = uiState.profileBackgroundColor,
                    onProfileClick = onNavigateToSettings,
                    onAiClick = { navController.safeNavigate(AiAssistant) }
                )
            }
        },
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
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                // Banner Image Background
                AnimatedVisibility(
                    visible = uiState.showBannerImage,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        if (uiState.bannerImageUri != null) {
                            AsyncImage(
                                model = uiState.bannerImageUri,
                                contentDescription = stringResource(R.string.banner),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(hazeStateBanner)
                                    .alpha(0.5f)
                                    .bottomFade(0.4f)
                                    .hazeSource(hazeStateBanner),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.banner_bg_image),
                                contentDescription = stringResource(R.string.banner),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.5f)
                                    .bottomFade(0.4f)
                                    .hazeSource(hazeStateBanner),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // The cards Home shows, as lazy items; the two-column layout splits them
            val widgetItems: LazyListScope.(List<HomeWidgetUiModel>) -> Unit = { widgets ->
                widgets.forEach { widgetModel ->
                    if (widgetModel.isVisible) {
                        when (widgetModel.widget) {
                            HomeWidget.NETWORTH_SUMMARY -> {
                                item(key = "net_worth") {
                                    NetworthSummaryCards(
                                        uiState = uiState,
                                        onCurrencySelected = { homeViewModel.selectCurrency(it) },
                                        onNetWorthClick = { navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.ManageAccounts) },
                                        breakdownShown = showAccountBreakdown,
                                        onToggleBreakdown = { showAccountBreakdown = !showAccountBreakdown }
                                    )
                                }
                                // The account categories, only when asked for
                                if (showAccountBreakdown) {
                                    item(key = "account_overview") {
                                        AccountCategoryList(
                                            overviewItems, openCategory, nextCardDue,
                                            Modifier.padding(horizontal = Dimensions.Padding.content).animateItem()
                                        )
                                    }
                                }
                                item(key = "this_month") {
                                    MonthCard(
                                        income = uiState.currentMonthIncome,
                                        expenses = uiState.currentMonthExpenses,
                                        lastIncome = uiState.lastMonthIncome,
                                        lastExpenses = uiState.lastMonthExpenses,
                                        currency = uiState.selectedCurrency,
                                        onClick = onNavigateToAnalytics,
                                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                    )
                                }
                            }
                            HomeWidget.LOANS -> {
                                // One row, and none while nothing is lent or borrowed, so Home's
                                // key figures fit without scrolling
                                val loans = uiState.lendBorrowSummary
                                if (loans.totalLentRemaining.signum() != 0 || loans.totalBorrowedRemaining.signum() != 0) {
                                    item(key = "loans") {
                                        LendBorrowRow(
                                            summary = loans,
                                            currency = uiState.baseCurrency,
                                            onClick = { onNavigateToLendBorrow(null) },
                                            onLentClick = { onNavigateToLendBorrow(LendBorrowFilter.YOU_GET.name) },
                                            onBorrowedClick = { onNavigateToLendBorrow(LendBorrowFilter.YOU_OWE.name) },
                                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                        )
                                    }
                                }
                            }
                            HomeWidget.TRANSACTION_HEATMAP -> {
                                item(key = "transaction_heatmap") {
                                    HeatmapWidget(
                                        data = uiState.transactionHeatmap,
                                        blurEffects = blurEffects && uiState.showBannerImage,
                                        hazeState = hazeStateBanner
                                    )
                                }
                            }
                            HomeWidget.BUDGET_CAROUSEL -> {
                                if (uiState.activeBudgets.isNotEmpty()) {
                                    item(key = "budget_carousel") {
                                        var lastBudgetClickTime by remember { mutableLongStateOf(0L) }
                                        BudgetCarousel(
                                            budgets = uiState.activeBudgets,
                                            onBudgetClick = {
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - lastBudgetClickTime > 500) {
                                                    lastBudgetClickTime = currentTime
                                                    onNavigateToBudgets(it)
                                                }
                                            },
                                            onEditClick = {
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - lastBudgetClickTime > 500) {
                                                    lastBudgetClickTime = currentTime
                                                    onNavigateToBudgets(it)
                                                }
                                            },
                                            onHistoryClick = onNavigateToBudgetHistory,
                                            animatedVisibilityScope = animatedContentScope,
                                        )
                                    }
                                }
                            }
                            // Shown under the net worth, from its 分类 button
                            HomeWidget.ACCOUNT_CAROUSEL -> Unit
                            HomeWidget.UPCOMING_SUBSCRIPTIONS -> {
                                if (uiState.upcomingSubscriptions.isNotEmpty()) {
                                    item(key = "upcoming_subscriptions") {
                                        val cardModifier = Modifier.padding(
                                            start = Dimensions.Padding.content,
                                            end = Dimensions.Padding.content,
                                        )

                                        if (animatedContentScope != null) {
                                            UpcomingSubscriptionsCard(
                                                subscriptions = uiState.upcomingSubscriptions,
                                                totalAmount = uiState.upcomingSubscriptionsTotal,
                                                currency = uiState.upcomingSubscriptionsCurrency,
                                                categoriesMap = categoriesMap,
                                                subcategoriesMap = subcategoriesMap,
                                                onClick = onNavigateToSubscriptions,
                                                blurEffects = blurEffects && uiState.showBannerImage,
                                                hazeState = hazeStateBanner,
                                                modifier = cardModifier.sharedBounds(
                                                    rememberSharedContentState(key = "upcoming_subscriptions_card"),
                                                    animatedVisibilityScope = animatedContentScope,
                                                    boundsTransform = { _, _ ->
                                                        tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
                                                    },
                                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                                        contentScale = ContentScale.Fit,
                                                        alignment = Alignment.Center
                                                    ),
                                                    renderInOverlayDuringTransition = false
                                                )
                                                    .skipToLookaheadSize()
                                            )
                                        } else {
                                            UpcomingSubscriptionsCard(
                                                subscriptions = uiState.upcomingSubscriptions,
                                                totalAmount = uiState.upcomingSubscriptionsTotal,
                                                currency = uiState.upcomingSubscriptionsCurrency,
                                                categoriesMap = categoriesMap,
                                                subcategoriesMap = subcategoriesMap,
                                                onClick = onNavigateToSubscriptions,
                                                blurEffects = blurEffects && uiState.showBannerImage,
                                                hazeState = hazeStateBanner,
                                                modifier = cardModifier
                                            )
                                        }
                                    }
                                }
                            }
                            HomeWidget.RECENT_TRANSACTIONS -> {
                                item(key = "recent_transactions") {
                                    RecentTransactionsCard(
                                        transactions = uiState.recentTransactions,
                                        decorate = { lookups.decorate(it, uiState.conversions) },
                                        targetName = { tx ->
                                            tx.toAccountId?.let { id -> lookups.accounts.values.firstOrNull { it.accountId == id }?.bankName }
                                        },
                                        // The second line is converted to the currency shown above
                                        mainCurrency = uiState.selectedCurrency,
                                        isLoading = uiState.isLoading,
                                        onTransactionClick = { onTransactionClick(it.id, "transaction_${it.id}") },
                                        onViewAll = onNavigateToTransactions,
                                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                    )
                                }
                            }
                        }
                    }
                }

            }
            val contentPadding = PaddingValues(
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 0.dp
            )
            if (LocalWindowLayout.current.widthDp < HOME_TWO_COLUMN_MIN_WIDTH_DP) {
                HomeContentList(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    widgetItems(homeWidgets)
                    item { CustomizeHomeButton { showEditWidgetsSheet = true } }
                    item {
                        Spacer(Modifier.height(120.dp)) // Clear of the add button
                    }
                }
            } else {
                // Wide window (an unfolded foldable): balances on the left, activity on the
                // right, each column in the user's order, so the key figures fit on one screen.
                val (activity, balances) = homeWidgets.partition { it.widget in HOME_ACTIVITY_WIDGETS }
                val activityListState = rememberLazyListState()
                Row(modifier = Modifier.fillMaxSize()) {
                    listOf(balances to lazyListState, activity to activityListState).forEach { (column, state) ->
                        HomeContentList(
                            state = state,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            widgetItems(column)
                            if (state === activityListState) item { CustomizeHomeButton { showEditWidgetsSheet = true } }
                            item { Spacer(Modifier.height(120.dp)) }
                        }
                    }
                }
            }

            // Edit Widgets Sheet
            if (showEditWidgetsSheet) {
                EditWidgetsSheet(
                    onDismissRequest = { showEditWidgetsSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    widgets = homeWidgets,
                    onToggleVisibility = homeViewModel::toggleHomeWidgetVisibility,
                    onReorder = homeViewModel::updateWidgetsOrder,
                    onResetLayout = homeViewModel::resetWidgetsLayout,
                    showBannerImage = uiState.showBannerImage,
                    onToggleBannerImage = homeViewModel::toggleBannerImage
                )
            }
        }
    }

}




@Composable
private fun UpcomingSubscriptionsCard(
    modifier: Modifier = Modifier,
    subscriptions: List<SubscriptionEntity>,
    totalAmount: BigDecimal,
    currency: String,
    categoriesMap: Map<String, CategoryEntity> = emptyMap(),
    subcategoriesMap: Map<String, SubcategoryEntity> = emptyMap(),
    onClick: () -> Unit = {},
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState() }
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.Radius.xxl))
            .then(
                if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        inputScale = HazeInputScale.Auto
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = HazeDefaults.tint(containerColor),
                            blurRadius = 20.dp,
                            noiseFactor = -1f,
                        )
                        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                    }
                ) else Modifier
            ),
        shape = RoundedCornerShape(Dimensions.Radius.xxl),
        colors = CardDefaults.cardColors(
            containerColor = if (blurEffects) MaterialTheme.colorScheme.surfaceContainerLow.copy(0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.subscriptions_count_format, subscriptions.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = Dimensions.Alpha.subtitle
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text =
                            CurrencyFormatter.formatCurrency(
                                totalAmount,
                                currency
                            ).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text =
                            stringResource(R.string.per_month).uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = Dimensions.Alpha.subtitle
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

            }

            SubscriptionIconsStack(
                subscriptions = subscriptions,
                iconSize = 38.dp,
                modifier = Modifier.padding(end = Spacing.sm),
                borderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                categoriesMap = categoriesMap,
                subcategoriesMap = subcategoriesMap
            )
        }
    }
}

@Composable
private fun NetworthSummaryCards(
    uiState: HomeUiState,
    onCurrencySelected: (String) -> Unit,
    onNetWorthClick: () -> Unit,
    breakdownShown: Boolean,
    onToggleBreakdown: () -> Unit,
) {
    var showCurrencySheet by remember { mutableStateOf(false) }

    if (showCurrencySheet) {
        CurrencySelectionBottomSheet(
            selectedCurrency = uiState.selectedCurrency,
            availableCurrencies = uiState.availableCurrencies,
            onCurrencySelected = {
                onCurrencySelected(it)
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false }
        )
    }

    NetWorthSection(
        netWorth = uiState.totalBalance,
        currency = uiState.selectedCurrency,
        history = uiState.balanceHistory,
        canChangeCurrency = uiState.availableCurrencies.size > 1,
        onCurrencyClick = { showCurrencySheet = true },
        onOpenAccounts = onNetWorthClick,
        breakdownShown = breakdownShown,
        onToggleBreakdown = onToggleBreakdown,
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
    )
}

/** Where Home's cards are chosen and ordered (the header no longer has a menu for it). */
@Composable
private fun CustomizeHomeButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick) {
            Icon(Iconax.Convertshape2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_customize))
        }
    }
}

/** Home splits into two columns from this window width (an unfolded foldable). */
private const val HOME_TWO_COLUMN_MIN_WIDTH_DP = 720

/** What the right-hand column shows on wide windows; the rest are balances, on the left. */
private val HOME_ACTIVITY_WIDGETS = setOf(
    HomeWidget.RECENT_TRANSACTIONS, HomeWidget.BUDGET_CAROUSEL, HomeWidget.TRANSACTION_HEATMAP
)
