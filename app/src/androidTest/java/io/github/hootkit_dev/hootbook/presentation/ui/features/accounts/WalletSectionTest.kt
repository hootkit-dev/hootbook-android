package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletSectionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var expand: String
    private lateinit var collapse: String
    private lateinit var footer: String
    private fun render(names: List<String>, kind: AccountSectionKind = AccountSectionKind.WALLETS) {
        val group = buildAccountSections(names.map { name ->
            AccountBalanceEntity(bankName = name, accountLast4 = "wallet", balance = BigDecimal.TEN, timestamp = LocalDateTime.of(2026, 9, 7, 12, 0), isWallet = kind == AccountSectionKind.WALLETS, isCreditCard = kind == AccountSectionKind.CREDIT_CARDS, currency = "CNY")
        }).visible.first { it.kind == kind }
        rule.setContent {
            val context = LocalContext.current
            val title = context.getString(when (kind) {
                AccountSectionKind.WALLETS -> R.string.section_wallets
                AccountSectionKind.BANKS -> R.string.section_bank_accounts
                AccountSectionKind.CREDIT_CARDS -> R.string.section_credit_cards
                AccountSectionKind.INVESTMENTS -> R.string.overview_investments
            })
            expand = context.getString(R.string.account_section_expand_all, title)
            collapse = context.getString(R.string.account_section_collapse_all, title)
            footer = context.getString(R.string.account_show_less)
            var expanded by remember { mutableStateOf(false) }
            HootbookTheme(dynamicColor = false) {
                Column {
                    AccountSectionSummary(group, expanded = expanded, onToggle = { expanded = !expanded })
                    group.visibleAccounts(expanded).forEach { Text(it.bankName) }

                }
            }
        }
        rule.waitForIdle()
    }
    @Test fun summaryArrowExpandsAllWalletsAndCollapsesAgain() {
        val names = listOf("WeChat Pay", "Cash", "Alipay")
        render(names)
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithContentDescription(expand).assertIsDisplayed().performClick()
        names.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription(collapse).performClick()
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithContentDescription(expand).assertIsDisplayed()
    }
    @Test fun oneWalletUsesOneHeaderActionToCollapse() {
        render(listOf("Cash"))
        rule.onNodeWithText("Cash").assertDoesNotExist()
        rule.onNodeWithContentDescription(expand).performClick()
        rule.onNodeWithText("Cash").assertIsDisplayed()
        rule.onNodeWithContentDescription(collapse).performClick()
        rule.onNodeWithText("Cash").assertDoesNotExist()
    }
    @Test fun bankArrowExpandsEveryAccountAndCollapses() {
        val names = listOf("Bank A", "Bank B", "Bank C", "Bank D")
        render(names, AccountSectionKind.BANKS)
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithContentDescription(expand).performClick()
        names.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription(collapse).performClick()
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
    @Test fun creditArrowExpandsEveryCardAndCollapses() {
        val names = listOf("Card A", "Card B", "Card C")
        render(names, AccountSectionKind.CREDIT_CARDS)
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithContentDescription(expand).performClick()
        names.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription(collapse).performClick()
        names.forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
}
