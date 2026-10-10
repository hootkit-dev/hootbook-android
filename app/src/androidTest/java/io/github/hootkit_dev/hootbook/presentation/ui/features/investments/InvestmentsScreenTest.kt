package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerConnection
import io.github.hootkit_dev.hootbook.domain.brokerage.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvestmentsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val connection = BrokerConnection("demo", "ibkr_flex", "IBKR · Demo", listOf(
        BrokerageAccount(
            "DEMO_ACCOUNT",
            "2026-09-04",
            listOf(
                Holding("1", "AAPL", "Apple Inc. · Demo", "USD", "10.5", "2100.00", "1800.00", "300.00"),
                Holding("2", "0700", "Tencent · Demo", "HKD", "100", "40000.00")
            ),
            listOf(
                CashBalance("USD", "-150.00", "-150.00"),
                CashBalance("HKD", "500.00")
            )
        )), 1788508800000L)
    @Composable private fun Content(connections: List<BrokerConnection> = listOf(connection), busy: Boolean = false,
        error: BrokerageError? = null, onAdd: () -> Unit = {}, onRefreshAll: () -> Unit = {}) {
        InvestmentsContent(connections, busy, true, error, {}, onAdd, onRefreshAll, {})
    }
    @Test fun emptyStateOffersWorkingConnectionAction() {
        var clicked = false
        rule.setContent { MaterialTheme { Content(emptyList(), onAdd = { clicked = true }) } }
        rule.onNodeWithTag("add_investment").performClick()
        rule.runOnIdle { assertTrue(clicked) }
        rule.onNodeWithText("DEMO_ACCOUNT").assertDoesNotExist()
    }
    @Test fun holdingsKeepCurrenciesAndReportDateVisible() {
        var date = ""; var usd = ""; var hkd = ""; var margin = ""; var cash = ""
        rule.setContent { MaterialTheme {
            date = stringResource(R.string.investments_as_of, "2026-09-04")
            usd = stringResource(R.string.investments_market_total, "USD", "2,100.00")
            hkd = stringResource(R.string.investments_market_total, "HKD", "40,000.00")
            margin = stringResource(R.string.investments_margin_debit, "USD", "150.00")
            cash = stringResource(R.string.investments_cash, "HKD", "500.00")
            Content()
        } }
        rule.onNodeWithText(date).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(usd, substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(hkd, substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(margin, substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(cash, substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("AAPL").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("0700").performScrollTo().assertIsDisplayed()
    }
    @Test fun refreshTargetsHoldingsFromTheToolbar() {
        var refreshed = false
        var refresh = ""
        var disconnect = ""
        rule.setContent { MaterialTheme {
            refresh = stringResource(R.string.investments_refresh)
            disconnect = stringResource(R.string.investments_disconnect)
            Content(onRefreshAll = { refreshed = true })
        } }
        rule.onNodeWithTag("refresh_holdings").assertContentDescriptionEquals(refresh).performClick()
        rule.runOnIdle { assertTrue(refreshed) }
        rule.onNodeWithText(disconnect).assertDoesNotExist()
    }
    @Test fun failedRefreshKeepsCachedHoldingsAndError() {
        rule.setContent { MaterialTheme { Content(error = BrokerageError.NETWORK) } }
        rule.onNodeWithTag("broker_error").assertIsDisplayed()
        rule.onNodeWithText("AAPL").performScrollTo().assertIsDisplayed()
    }
    @Test fun busyStateKeepsAddActionAvailable() {
        rule.setContent { MaterialTheme { Content(emptyList(), busy = true) } }
        rule.onNodeWithTag("add_investment").assertIsDisplayed().assertHasClickAction()
    }
    @Test fun tokenIsMaskedAndConnectionRequiresBothFields() {
        var submitted = ""
        rule.setContent { MaterialTheme { IbkrConnectDialog(false, null, {}, { _, token, query -> submitted = "$token:$query" }) } }
        rule.onNodeWithTag("save_broker").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("flex_token").performScrollTo().performTextInput("1234567890")
        rule.onNodeWithTag("flex_token").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        rule.onNodeWithTag("flex_query").performScrollTo().performTextInput("42")
        rule.onNodeWithTag("save_broker").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("1234567890:42", submitted) }
    }
    @Test fun largeTextAndDarkThemeKeepAmountsUnclipped() {
        var amount = ""
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    amount = stringResource(R.string.investments_value, "USD", "2,100.00")
                    Content()
                }
            }
        }
        val node = rule.onNodeWithText(amount)
        node.performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty()); assertFalse(layouts.joinToString { "size=${it.size}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, lines=${it.lineCount}, maxWidth=${it.layoutInput.constraints.maxWidth}, lastRight=${it.getLineRight(it.lineCount - 1)}" }, layouts.any { it.hasVisualOverflow })
    }
}
