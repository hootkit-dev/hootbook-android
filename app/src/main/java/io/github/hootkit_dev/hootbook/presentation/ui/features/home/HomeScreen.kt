@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import io.github.hootkit_dev.hootbook.presentation.data.ConvertedMoney

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.LocalWindowLayout
import io.github.hootkit_dev.hootbook.presentation.ui.components.LendBorrowRow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.navigation.AiAssistant
import io.github.hootkit_dev.hootbook.presentation.navigation.safeNavigate
import io.github.hootkit_dev.hootbook.presentation.ui.components.BudgetCarousel
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencySelectionBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.GreetingCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.HeatmapWidget
import io.github.hootkit_dev.hootbook.presentation.ui.components.SubscriptionIconsStack
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Convertshape2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.category
import io.github.hootkit_dev.hootbook.presentation.ui.components.HomeContentList
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
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowFilter

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalHazeApi::class
)
@Composable
fun SharedTransitionScope.HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToBudgets: (String?) -> Unit = {},
    onNavigateToBudgetHistory: (String) -> Unit = {},
    onNavigateToLendBorrow: (String?) -> Unit = { _ -> },
    onTransactionClick: (String, String) -> Unit = { _, _ -> },
    animatedContentScope: AnimatedContentScope? = null,
) {

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val prefs by homeViewModel.preferences.state.collectAsStateWithLifecycle()
    val homeWidgets = prefs.homeOrder.map { HomeWidgetUiModel(it, it !in prefs.hiddenHomeWidgets) }
    val categoriesMap = uiState.categories
    val subcategoriesMap = uiState.subcategories
    val openCategory: (io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory) -> Unit = {
        val investments = io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory.INVESTMENTS
        if (it == investments && uiState.connections.isEmpty() && uiState.overview?.accounts.orEmpty().none { a -> a.category() == investments })
            navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.AddAccount(investments.name))
        else
            navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.AccountCategoryRoute(it.name))
    }
    val hazeStateBanner = remember { HazeState()}
    val blurEffects = prefs.blur

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

    var showEditWidgetsSheet by remember { mutableStateOf(false) }
    var showAccountBreakdown by rememberSaveable { mutableStateOf(false) }


    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(Unit) {
        homeViewModel.refreshDate()
        homeViewModel.refresh()
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(operationFailed) }
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
                        .padding(vertical = 6.dp),
                    userName = prefs.userName,
                    profileImageUri = homeViewModel.assets.resolve(prefs.avatar)?.let { android.net.Uri.fromFile(it) },
                    profileBackgroundColor = Color(prefs.profileBackground),
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
                    visible = prefs.showBanner,
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
                        if (homeViewModel.assets.resolve(prefs.banner) != null) {
                            AsyncImage(
                                model = homeViewModel.assets.resolve(prefs.banner),
                                contentDescription = stringResource(R.string.banner),
                                modifier = Modifier
                                    .fillMaxSize()
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
                                        showLoans = HomeWidget.LOANS !in prefs.hiddenHomeWidgets,
                                        onCurrencySelected = { homeViewModel.selectCurrency(it) },
                                        onNetWorthClick = { navController.safeNavigate(io.github.hootkit_dev.hootbook.presentation.navigation.ManageAccounts) },
                                        onOpenLendBorrow = onNavigateToLendBorrow,
                                        breakdownShown = showAccountBreakdown,
                                        onToggleBreakdown = { showAccountBreakdown = !showAccountBreakdown }
                                    )
                                }
                                // The account categories, only when asked for
                                if (showAccountBreakdown) {
                                    item(key = "account_overview") {
                                        AccountCategoryList(
                                            uiState.overview, openCategory, connections = uiState.connections,
                                            cardStatuses = uiState.cardStatuses,
                                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content).animateItem()
                                        )
                                    }
                                }
                                val cardReminders = buildCreditCardReminders(
                                    accounts = uiState.overview?.accounts.orEmpty(),
                                    cardStatuses = uiState.cardStatuses,
                                )
                                val visibleUpcoming = if (HomeWidget.UPCOMING_SUBSCRIPTIONS !in prefs.hiddenHomeWidgets) {
                                    uiState.upcoming
                                } else {
                                    emptyList()
                                }
                                val hasUrgentNotifications = uiState.pendingConflictCount > 0 ||
                                    uiState.pendingAiReview != null ||
                                    cardReminders.isNotEmpty() ||
                                    visibleUpcoming.isNotEmpty()
                                if (hasUrgentNotifications) {
                                    item(key = "urgent_notifications") {
                                        val subscriptionsModifier = if (animatedContentScope != null) {
                                            Modifier.sharedBounds(
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
                                            ).skipToLookaheadSize()
                                        } else {
                                            Modifier
                                        }
                                        UrgentNotificationsSection(
                                            pendingConflictCount = uiState.pendingConflictCount,
                                            pendingAiReview = uiState.pendingAiReview,
                                            cardReminders = cardReminders,
                                            upcomingSubscriptions = visibleUpcoming,
                                            subscriptionSummary = uiState.subscriptions,
                                            monthlySubscriptions = uiState.displayTotals["monthly"],
                                            customSymbols = uiState.customSymbols,
                                            categoriesMap = categoriesMap,
                                            subcategoriesMap = subcategoriesMap,
                                            onConflictClick = {
                                                navController.safeNavigate(
                                                    io.github.hootkit_dev.hootbook.presentation.navigation.CloudSync
                                                )
                                            },
                                            onAiReviewClick = { navController.safeNavigate(AiAssistant) },
                                            onAccountClick = { id ->
                                                navController.safeNavigate(
                                                    io.github.hootkit_dev.hootbook.presentation.navigation.AccountDetail(id)
                                                )
                                            },
                                            onSubscriptionsClick = onNavigateToSubscriptions,
                                            subscriptionsModifier = subscriptionsModifier,
                                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content).animateItem(),
                                        )
                                    }
                                }
                                item(key = "this_month") {
                                    uiState.summary?.let { summary -> MonthCard(
                                        summary = summary,
                                        lastSummary = uiState.lastSummary,
                                        display = uiState.displayTotals,
                                        customSymbols = uiState.customSymbols,
                                        onClick = onNavigateToAnalytics,
                                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                    ) }

                                }
                            }
                            HomeWidget.LOANS -> Unit
                            HomeWidget.TRANSACTION_HEATMAP -> {
                                item(key = "transaction_heatmap") {
                                    HeatmapWidget(
                                        data = uiState.heatmap.map { (day, count) -> java.time.LocalDate.of(day.year, day.month.toInt(), day.day.toInt()) to count.toInt() }.toMap(),
                                        blurEffects = blurEffects && prefs.showBanner,
                                        hazeState = hazeStateBanner
                                    )
                                }
                            }
                            HomeWidget.BUDGET_CAROUSEL -> {
                                if (uiState.budgets.isNotEmpty()) {
                                    item(key = "budget_carousel") {
                                        var lastBudgetClickTime by remember { mutableLongStateOf(0L) }
                                        BudgetCarousel(
                                            budgets = uiState.budgets,
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
                            // Rendered inside the urgent_notifications section above MonthCard
                            HomeWidget.UPCOMING_SUBSCRIPTIONS -> Unit
                            HomeWidget.RECENT_TRANSACTIONS -> {
                                item(key = "recent_transactions") {
                                    RecentTransactionsCard(
                                        transactions = uiState.recent,
                                        categories = uiState.categories,
                                        subcategories = uiState.subcategories,
                                        accounts = uiState.accounts,
                                        conversions = uiState.recentConversions,
                                        customSymbols = uiState.customSymbols,
                                        mainCurrency = uiState.displayCurrency,
                                        isLoading = uiState.loading,
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
                top = 4.dp + paddingValues.calculateTopPadding(),
                bottom = 0.dp
            )
            if (LocalWindowLayout.current.widthDp < HOME_TWO_COLUMN_MIN_WIDTH_DP) {
                HomeContentList(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    showBannerImage = prefs.showBanner,
                    onToggleBannerImage = homeViewModel::toggleBannerImage
                )
            }
        }
    }

}




@Composable
private fun NetworthSummaryCards(
    uiState: HomeUiState,
    showLoans: Boolean = true,
    onCurrencySelected: (String) -> Unit,
    onNetWorthClick: () -> Unit,
    onOpenLendBorrow: (String?) -> Unit = {},
    breakdownShown: Boolean,
    onToggleBreakdown: () -> Unit,
) {
    var showCurrencySheet by remember { mutableStateOf(false) }

    if (showCurrencySheet) {
        CurrencySelectionBottomSheet(
            selectedCurrency = uiState.displayCurrency,
            availableCurrencies = uiState.availableCurrencies,
            onCurrencySelected = {
                onCurrencySelected(it)
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false }
        )
    }

    NetWorthSection(
        netWorth = uiState.displayNetWorth?.amount ?: uiState.overview?.netWorth?.amount.takeIf { uiState.displayNetWorth == null },
        currency = uiState.displayCurrency,
        customSymbols = uiState.customSymbols,
        complete = uiState.overview?.netWorthComplete != false && uiState.displayNetWorth?.complete != false,
        canChangeCurrency = uiState.availableCurrencies.size > 1,
        onCurrencyClick = { showCurrencySheet = true },
        onOpenAccounts = onNetWorthClick,
        breakdownShown = breakdownShown,
        onToggleBreakdown = onToggleBreakdown,
        debts = uiState.debts.takeIf { showLoans },
        displayTotals = uiState.displayTotals,
        onOpenLendBorrow = onOpenLendBorrow,
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
