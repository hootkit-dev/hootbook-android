package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.core.home.CoreHomeUiState
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
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
        rule.onNodeWithText(CoreMoneyPresentation.format(BigDecimal("1234.56"), "CNY")).assertIsDisplayed()
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
        rule.onNodeWithText(CoreMoneyPresentation.format(BigDecimal("77.25"), "CNY")).assertIsDisplayed()
        rule.onNodeWithTag("home_month_summary").performClick()
        rule.runOnIdle { assertEquals(true, opened) }
    }
}
