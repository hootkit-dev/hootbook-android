package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionForm
import io.github.hootkit_dev.hootbook.presentation.core.transactions.TransactionDiscardDialog
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsUiState
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AddScreen
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real generated Core records exercise Compose. No Room fixtures or compatibility DTOs. */
class CoreTransactionViewsTest {
    @get:Rule val rule = createComposeRule()
    private val at = LocalDateTime(2026, 10u, 11u, 9u, 30u, 0u, 0u)
    private fun transaction() = Transaction("core/string/id", TransactionKind.TRANSFER,
        BigDecimal("1000.000000001"), "CNY", at, "source/id", "target/id", "USD",
        BigDecimal("137.123456789"), null, null, "Transfer", "An exact amount", null, null,
        emptyList(), false, at, at)

    @Test fun transactionRowUsesExactReceivedAmountForTargetAccount() {
        val row = transaction()
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionItem(row, currentAccountId = "target/id")
        } }
        rule.onNodeWithText("+" + CoreMoneyPresentation.format(row.toAmount, "USD")).assertIsDisplayed()
        rule.onNodeWithText(CoreMoneyPresentation.format(row.amount, "CNY"), substring = true).assertDoesNotExist()
    }

    @Test fun longPressSelectionAndClickPreserveStringIdentity() {
        val selecting = mutableStateOf(false)
        var opened: String? = null
        var selected: String? = null
        val row = transaction()
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionItem(row, isSelectionMode = selecting.value,
                onClick = { opened = row.id }, onLongClick = { selecting.value = true },
                onSelectionToggle = { selected = row.id })
        } }
        rule.onNodeWithTag("transaction_${row.id}").performClick()
        rule.runOnIdle { assertEquals(row.id, opened) }
        rule.onNodeWithTag("transaction_${row.id}").performTouchInput { longClick() }
        rule.onNodeWithTag("transaction_${row.id}").performClick()
        rule.runOnIdle { assertEquals(row.id, selected) }
    }

    @Test fun amountPadKeepsExactDraftAndRestoresOpenState() {
        val state = mutableStateOf(CoreTransactionsUiState(loading = false,
            form = CoreTransactionForm(amount = "12.300000001")))
        var saved: CoreTransactionForm? = null
        val restoration = StateRestorationTester(rule)
        restoration.setContent { HootbookTheme(dynamicColor = false) {
            AddScreen(state.value, { state.value = state.value.copy(form = it) }, { saved = state.value.form }, {})
        } }
        rule.onNodeWithTag("transaction_amount").performClick()
        rule.onNodeWithTag("amount_done").assertIsDisplayed()
        rule.onNodeWithTag("amount_editor").performTextReplacement("987654321.123456789")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("amount_editor").assertTextContains("987654321.123456789")
        rule.onNodeWithText(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(io.github.hootkit_dev.hootbook.R.string.done)).performClick()
        rule.onNodeWithTag("save_transaction").performClick()
        rule.runOnIdle { assertEquals(BigDecimal("987654321.123456789"), saved!!.toDraft().amount) }
    }

    @Test fun draftDiscardDialogKeepsEditingUntilExplicitDiscard() {
        var kept = 0
        var discarded = 0
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionDiscardDialog(onContinue = { kept++ }, onDiscard = { discarded++ })
        } }
        rule.onNodeWithText(context.getString(io.github.hootkit_dev.hootbook.R.string.transaction_continue_editing)).performClick()
        rule.runOnIdle { assertEquals(1, kept); assertEquals(0, discarded) }
        rule.onNodeWithText(context.getString(io.github.hootkit_dev.hootbook.R.string.transaction_discard_action)).performClick()
        rule.runOnIdle { assertEquals(1, kept); assertEquals(1, discarded) }
    }

    @Test fun receivedAmountIsExplicitAndNeverReplacedByEstimate() {
        val state = mutableStateOf(CoreTransactionsUiState(loading = false,
            transferEstimate = BigDecimal("140.25"), form = CoreTransactionForm(kind = TransactionKind.TRANSFER,
                accountId = "source/id", toAccountId = "target/id", amount = "1000.001", currency = "CNY",
                toCurrency = "USD", toAmount = "137.123456789")))
        rule.setContent { HootbookTheme(dynamicColor = false) {
            AddScreen(state.value, { state.value = state.value.copy(form = it) }, {}, {})
        } }
        rule.onNodeWithTag("transfer_received_amount").performScrollTo().assertTextContains("137.123456789")
        rule.onNodeWithTag("transfer_received_amount").performTextReplacement("136.987654321")
        rule.runOnIdle { assertEquals(BigDecimal("136.987654321"), state.value.form!!.toDraft().toAmount) }
    }

    @Test fun detailUsesCoreAmountWithoutRoundingOrLegacyAccountLookup() {
        val row = transaction()
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionDetailScreen(CoreTransactionsUiState(detail = row, detailId = row.id, loading = false), {}, {}, {})
        } }
        rule.onNodeWithTag("detail_amount").assertTextEquals(CoreMoneyPresentation.format(row.amount, "CNY"))
    }
}
