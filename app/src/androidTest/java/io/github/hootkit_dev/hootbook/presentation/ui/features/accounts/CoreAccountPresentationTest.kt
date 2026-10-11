package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoreAccountPresentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun multiCurrencyCardUsesNativeAppCurrencyTotalAndStringId() {
        val account = fixtureAccount()
        var selected: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) {
            ListSection { accountRow(account, emptyMap(), null) { selected = account.id } }
        } }
        rule.onNodeWithText(CoreMoneyPresentation.format(BigDecimal("735.00"), "CNY")).assertIsDisplayed()
        rule.onNodeWithText("USD/HKD", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("account_${account.id}").performClick()
        rule.runOnIdle { assertEquals("account-core-uuid", selected) }
    }

    @Test fun investmentEmptyRowKeepsConnectionEntry() {
        var opened = false
        rule.setContent { HootbookTheme(dynamicColor = false) { InvestmentsEmptySection({ opened = true }) } }
        rule.onNodeWithTag("investments_empty_add").performClick()
        rule.runOnIdle { assertEquals(true, opened) }
    }
}

internal fun fixtureAccount() = Account(
    id = "account-core-uuid", name = "Cross-border wallet", last4 = null, kind = AccountKind.WALLET,
    mainCurrency = "USD", pockets = listOf(Pocket("USD", BigDecimal("100"), null, null), Pocket("HKD", BigDecimal("50"), null, null)),
    total = Money(BigDecimal("735.00"), "CNY"), totalComplete = true, securities = null, broker = null,
    creditLimit = null, statementDay = null, dueDay = null, institutionKey = null, icon = "", color = "#336699",
    archived = false, isDefault = true, sortOrder = 0, createdAt = LocalDateTime(2026, 10u, 11u, 0u, 0u, 0u, 0u),
)
