package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.utils.formatBalance
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class AccountBalanceListTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun account(
        name: String,
        last4: String,
        balance: String,
        currency: String = "CNY",
        creditCard: Boolean = false
    ) = AccountBalanceEntity(
        bankName = name,
        accountLast4 = last4,
        balance = BigDecimal(balance),
        currency = currency,
        isCreditCard = creditCard,
        timestamp = LocalDateTime.of(2026, 9, 7, 12, 0)
    )

    @Test
    fun allBalancesAreVisibleWithoutSwiping() {
        val bank = account("Bank account", "1234", "12345.67")
        val wallet = account("Wallet", "", "0.00").copy(isWallet = true)
        val card = account("Credit card", "5678", "89.10", "USD", creditCard = true)
        composeRule.setContent {
            MaterialTheme {
                SharedTransitionLayout {
                    AccountBalanceList(
                        bankAccounts = listOf(bank, wallet),
                        creditCards = listOf(card),
                        blurEffects = false
                    )
                }
            }
        }

        listOf(bank, wallet, card).forEach { account ->
            composeRule.onNodeWithTag("account_balance_${account.bankName}_${account.accountLast4}")
                .assertIsDisplayed()
            composeRule.onNodeWithText(account.formatBalance(), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun tappingAnAccountKeepsItsDetailDestination() {
        val bank = account("Bank account", "1234", "123.45")
        var selected: Pair<String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                SharedTransitionLayout {
                    AccountBalanceList(
                        bankAccounts = listOf(bank),
                        creditCards = emptyList(),
                        blurEffects = false,
                        onAccountClick = { name, last4 -> selected = name to last4 }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account_balance_Bank account_1234").performClick()
        composeRule.runOnIdle { assertEquals("Bank account" to "1234", selected) }
    }

    @Test
    fun emptyAccountsDoNotLeaveAnEmptySection() {
        composeRule.setContent {
            MaterialTheme {
                SharedTransitionLayout {
                    AccountBalanceList(
                        bankAccounts = emptyList(),
                        creditCards = emptyList(),
                        blurEffects = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account_balance_list").assertDoesNotExist()
    }

    @Test
    fun narrowScreenAndLargeTextKeepTheFullNegativeBalance() {
        val bank = account("A long account name", "4321", "-1234567.89", "USD")
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MaterialTheme {
                    SharedTransitionLayout {
                        AccountBalanceList(
                            bankAccounts = listOf(bank),
                            creditCards = emptyList(),
                            blurEffects = false,
                            modifier = Modifier.width(320.dp)
                        )
                    }
                }
            }
        }

        val balanceNode = composeRule.onNodeWithText(bank.formatBalance(), useUnmergedTree = true)
        balanceNode.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        balanceNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.any { it.hasVisualOverflow })
    }
}
