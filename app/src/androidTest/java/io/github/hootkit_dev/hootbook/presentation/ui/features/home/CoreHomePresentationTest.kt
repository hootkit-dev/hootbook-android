package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.core.home.CoreHomeUiState
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferences
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.fixtureAccount
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoreHomePresentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun netWorthUsesNativeOverviewAndCategoryDisclosureCallback() {
        val overview = AccountsOverview(listOf(fixtureAccount()), Money(BigDecimal("1234.56"), "CNY"), true, null, null)
        var toggled = false
        rule.setContent { HootbookTheme(dynamicColor = false) {
            NetWorthSection(overview, emptyMap(), null, {}, {}, false, { toggled = true })
        } }
        rule.onNodeWithText(HomeMoneyPresentation.format(BigDecimal("1234.56"), "CNY")).assertIsDisplayed()
        rule.onNodeWithTag("home_account_categories").performClick()
        rule.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun categoriesRouteWithCoreEnumAndKeepEmptyInvestmentEntry() {
        val overview = AccountsOverview(listOf(fixtureAccount()), Money(BigDecimal("735.00"), "CNY"), true, null, null)
        var selected: AccountKind? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { AccountCategoryList(CoreHomeUiState(overview = overview, loading = false), { selected = it }) } }
        rule.onNodeWithTag("home_kind_WALLET").performClick()
        rule.runOnIdle { assertEquals(AccountKind.WALLET, selected) }
        rule.onNodeWithTag("home_kind_INVESTMENT").performClick()
        rule.runOnIdle { assertEquals(AccountKind.INVESTMENT, selected) }
    }

    @Test fun monthCardDisplaysCoreNetRatherThanRecalculatingIt() {
        // An intentionally distinct native net proves the UI does not implement ledger arithmetic.
        val summary = LedgerSummary("CNY", BigDecimal("200"), BigDecimal("100"), BigDecimal("77.25"), true, emptyList(), emptyList(), emptyList(), emptyList())
        var opened = false
        rule.setContent { HootbookTheme(dynamicColor = false) { MonthCard(summary, null, emptyMap(), { opened = true }) } }
        rule.onNodeWithText(HomeMoneyPresentation.format(BigDecimal("77.25"), "CNY")).assertIsDisplayed()
        rule.onNodeWithTag("home_month_summary").performClick()
        rule.runOnIdle { assertEquals(true, opened) }
    }

    @Test fun refreshDoesNotMoveScrolledContentOrReplaceRows() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val assets = CorePlatformAssets(context)
        val at = LocalDateTime(2026, 10u, 11u, 9u, 30u, 0u, 0u)
        val rows = (0..5).map { index -> Transaction("tx-$index", TransactionKind.EXPENSE,
            BigDecimal("12.30"), "CNY", at, null, null, null, null, null, null,
            "Merchant $index", null, null, null, emptyList(), false, at, at) }
        val state = mutableStateOf(CoreHomeUiState(
            overview = AccountsOverview(emptyList(), Money(BigDecimal("1234.56"), "CNY"), true, null, null),
            summary = LedgerSummary("CNY", BigDecimal("100"), BigDecimal("25"), BigDecimal("75"), true,
                emptyList(), emptyList(), emptyList(), emptyList()),
            recent = rows, loading = false, widgetsLoaded = true))
        val prefs = CoreUiPreferences(hiddenHomeWidgets = HomeWidget.entries.toSet() -
            setOf(HomeWidget.NETWORTH_SUMMARY, HomeWidget.RECENT_TRANSACTIONS))
        rule.setContent { HootbookTheme(dynamicColor = false) {
            HomeScreen(state = state.value, prefs = prefs, assets = assets, onRefresh = {}, onSettings = {},
                onAi = {}, onAccounts = {}, onAccountCategory = {}, onCurrencies = {}, onAnalytics = {},
                onTransactions = {}, onTransaction = {}, onBudgets = {}, onBudget = {}, onSubscriptions = {},
                onSubscription = {}, onDebts = {}, onWidgetVisibility = { _, _ -> }, onReorder = {},
                onResetLayout = {}, onToggleBanner = {})
        } }
        rule.onNodeWithTag("transaction_tx-3").performScrollTo().assertIsDisplayed()
        val before = rule.onNodeWithTag("transaction_tx-3").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { state.value = state.value.copy(refreshing = true, widgetsLoading = true) }
        rule.onNodeWithText(context.getString(R.string.home_recent_empty)).assertDoesNotExist()
        assertEquals(before, rule.onNodeWithTag("transaction_tx-3").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { state.value = state.value.copy(refreshing = false, widgetsLoading = false, heatmapLoading = true) }
        assertEquals(before, rule.onNodeWithTag("transaction_tx-3").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { state.value = state.value.copy(heatmapLoading = false) }
        assertEquals(before, rule.onNodeWithTag("transaction_tx-3").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun recentEmptyMessageWaitsForSuccessfulLoad() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val loading = mutableStateOf(true)
        val failed = mutableStateOf(false)
        val assets = CorePlatformAssets(context)
        rule.setContent { HootbookTheme(dynamicColor = false) {
            RecentTransactionsCard(emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap(), assets,
                loading.value, {}, {}, loadFailed = failed.value)
        } }
        val emptyMessage = context.getString(R.string.home_recent_empty)
        rule.onNodeWithText(emptyMessage).assertDoesNotExist()
        rule.runOnIdle { loading.value = false; failed.value = true }
        rule.onNodeWithText(emptyMessage).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.home_recent_unavailable)).assertIsDisplayed()
        rule.runOnIdle { failed.value = false }
        rule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }
}
