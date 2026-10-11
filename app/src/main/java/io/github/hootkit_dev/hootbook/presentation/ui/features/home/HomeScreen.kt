@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetCard
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetCardCompact
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferences
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.core.home.CoreHomeUiState
import io.github.hootkit_dev.hootbook.presentation.core.analytics.javaDate
import io.github.hootkit_dev.hootbook.presentation.ui.components.GreetingCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.HeatmapWidget
import io.github.hootkit_dev.hootbook.presentation.ui.components.HomeContentList
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.*
import io.github.hootkit_dev.hootbook.utils.bottomFade

/** Original Home composition, with generated Core records as the only financial input. */
@Composable
fun HomeScreen(
    state: CoreHomeUiState, prefs: CoreUiPreferences, assets: CorePlatformAssets,
    onRefresh: () -> Unit, onSettings: () -> Unit, onAi: () -> Unit,
    onAccounts: () -> Unit, onAccountCategory: (AccountKind) -> Unit, onCurrencies: () -> Unit,
    onAnalytics: () -> Unit, onTransactions: () -> Unit, onTransaction: (String) -> Unit,
    onBudgets: () -> Unit, onBudget: (String) -> Unit, onSubscriptions: () -> Unit,
    onSubscription: (String) -> Unit, onDebts: () -> Unit,
    onWidgetVisibility: (HomeWidget, Boolean) -> Unit, onReorder: (List<HomeWidget>) -> Unit,
    onResetLayout: () -> Unit, onToggleBanner: () -> Unit,
    netWorthHistory: (@Composable () -> Unit)? = null,
) {
    var showBreakdown by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val avatar = remember(prefs.avatar) { runCatching { assets.resolve(prefs.avatar)?.toUri() }.getOrNull() }
    val banner = remember(prefs.banner) { runCatching { assets.resolve(prefs.banner) }.getOrNull() }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        GreetingCard(userName = prefs.userName.ifBlank { stringResource(R.string.core_complete_default_name) }, profileImageUri = avatar,
            profileBackgroundColor = if (prefs.profileBackground != 0) androidx.compose.ui.graphics.Color(prefs.profileBackground) else MaterialTheme.colorScheme.secondaryContainer,
            onProfileClick = onSettings, onAiClick = onAi, modifier = Modifier.padding(vertical = 12.dp))
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (prefs.showBanner) {
                val modifier = Modifier.fillMaxWidth().height(300.dp).alpha(0.5f).bottomFade(0.4f)
                if (banner != null) AsyncImage(banner, stringResource(R.string.banner), modifier, contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.banner_bg_image), stringResource(R.string.banner), modifier, contentScale = ContentScale.Crop)
            }
            val widgetItems: LazyListScope.(List<HomeWidget>) -> Unit = { widgets ->
                (state.errorResource ?: state.widgetError)?.let { message -> item(key = "error") {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(message), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.core_complete_refresh)) }
                    }
                } }
                widgets.filterNot { it in prefs.hiddenHomeWidgets }.forEach { widget -> when (widget) {
                    HomeWidget.NETWORTH_SUMMARY -> {
                        item(key = "net_worth") { state.overview?.let { NetWorthSection(it, state.customSymbols, state.displayNetWorth, onCurrencies, onAccounts, showBreakdown, { showBreakdown = !showBreakdown }, Modifier.padding(horizontal = 16.dp)) } }
                        if (netWorthHistory != null) item(key = "net_worth_history") { netWorthHistory() }
                        if (showBreakdown) item(key = "account_categories") { AccountCategoryList(state, onAccountCategory, Modifier.padding(horizontal = 16.dp)) }
                        item(key = "this_month") { state.summary?.let { MonthCard(it, state.previousSummary, state.customSymbols, onAnalytics, Modifier.padding(horizontal = 16.dp)) } }
                    }
                    HomeWidget.ACCOUNT_CAROUSEL -> Unit // Account kinds remain under the net-worth disclosure.
                    HomeWidget.LOANS -> state.debts?.takeIf { it.receivable.signum() != 0 || it.payable.signum() != 0 }?.let { debt -> item(key = "loans") {
                        Surface(onClick = onDebts, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column(Modifier.weight(1f)) { Text(stringResource(R.string.lend_borrow_you_get), style = MaterialTheme.typography.labelMedium); Text(HomeMoneyPresentation.format(debt.receivable, debt.currency, state.customSymbols), style = MaterialTheme.typography.titleLarge) }
                                Column(Modifier.weight(1f)) { Text(stringResource(R.string.lend_borrow_you_owe), style = MaterialTheme.typography.labelMedium); Text(HomeMoneyPresentation.format(debt.payable, debt.currency, state.customSymbols), style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                    } }
                    HomeWidget.TRANSACTION_HEATMAP -> item(key = "heatmap") { ActivityHeatmapCard(state.heatmap.associate { it.date.javaDate() to it.transactions.toInt() }, prefs.blur && prefs.showBanner) }
                    HomeWidget.BUDGET_CAROUSEL -> if (state.budgets.isNotEmpty()) item(key = "budgets") {
                        if (state.budgets.size == 1) {
                            val budget = state.budgets.first()
                            CoreBudgetCard(budget = budget, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                onClick = { onBudget(budget.id) }, onHistoryClick = onBudget)
                        } else {
                            val pager = rememberPagerState(pageCount = { state.budgets.size })
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
                                val budget = state.budgets[page]
                                CoreBudgetCardCompact(budget = budget, modifier = Modifier.fillMaxWidth(),
                                    onClick = { onBudget(budget.id) }, onHistoryClick = onBudget)
                            }
                        }
                    }
                    HomeWidget.UPCOMING_SUBSCRIPTIONS -> if (state.upcoming.isNotEmpty()) item(key = "subscriptions") { UpcomingSubscriptionsCard(state.upcoming, state.subscriptions, state.customSymbols, assets, onSubscriptions, Modifier.padding(horizontal = 16.dp)) }
                    HomeWidget.RECENT_TRANSACTIONS -> item(key = "recent") { RecentTransactionsCard(state.recent, state.overview?.accounts.orEmpty(), state.categories, state.recentDisplay, state.customSymbols, assets,
                        state.loading || state.widgetsLoading, onTransaction, onTransactions, Modifier.padding(horizontal = 16.dp),
                        loadFailed = !state.widgetsLoaded && (state.errorResource != null || state.widgetError != null)) }
                } }
            }
            val visibleWidgets = prefs.homeOrder
            if (maxWidth < 720.dp) {
                HomeContentList(rememberLazyListState(), Modifier.fillMaxSize().testTag("home_content"), PaddingValues(top = 16.dp, bottom = 112.dp)) {
                    widgetItems(visibleWidgets)
                    item(key = "customize") { CustomizeHomeButton { showEditor = true } }
                }
            } else {
                val activity = setOf(HomeWidget.RECENT_TRANSACTIONS, HomeWidget.BUDGET_CAROUSEL, HomeWidget.TRANSACTION_HEATMAP)
                Row(Modifier.fillMaxSize()) {
                    listOf(visibleWidgets.filterNot { it in activity }, visibleWidgets.filter { it in activity }).forEachIndexed { index, widgets ->
                        HomeContentList(rememberLazyListState(), Modifier.weight(1f).fillMaxHeight().testTag("home_content_$index"), PaddingValues(top = 16.dp, bottom = 112.dp)) {
                            widgetItems(widgets)
                            if (index == 0) item(key = "customize") { CustomizeHomeButton { showEditor = true } }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) EditWidgetsSheet({ showEditor = false }, rememberModalBottomSheetState(skipPartiallyExpanded = true), prefs.homeOrder, prefs.hiddenHomeWidgets, onWidgetVisibility, onReorder, onResetLayout, prefs.showBanner, onToggleBanner)
}

@Composable
private fun CustomizeHomeButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = onClick) { Icon(Icons.Rounded.DashboardCustomize, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.home_customize)) } }
}
