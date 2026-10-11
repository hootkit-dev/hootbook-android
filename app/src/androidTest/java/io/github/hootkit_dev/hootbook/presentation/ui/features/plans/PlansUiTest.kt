package io.github.hootkit_dev.hootbook.presentation.ui.features.plans

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.components.BudgetCard
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.AddEditLendBorrowTransactionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.SettleUpSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions.SubscriptionsScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions.SubscriptionsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import java.io.File
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises restored controls with Core records; no native user-data mutation. */
@OptIn(ExperimentalSharedTransitionApi::class)
class PlansUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val date = LocalDate(2026, 10u, 11u)
    private val time = LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u)
    private val person = Person("person/中文?&=", "Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND, false,
        PersonTotals("GBP", BigDecimal("888"), BigDecimal("111"), BigDecimal("777"), true, false, listOf("EUR")), time)
    private val account = Account("account/中文", "Wallet", null, AccountKind.WALLET, "GBP", listOf(Pocket("GBP", BigDecimal("9"), null, null)),
        Money(BigDecimal("9"), "GBP"), false, null, null, null, null, null, null, "type_finance_bank", "#4CAF50", false, false, 0, time)
    private fun text(id: Int) = context.getString(id)
    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val target = File(context.getExternalFilesDir(null), "batch4-$name.png")
        target.outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @After fun cleanup() { rule.runOnUiThread { store.clear(); scope.cancel() } }
    @Test fun editingStandaloneDebtKeepsNullAccountAndOriginalCurrencyAndDate() {
        val debt = DebtEntry("debt/中文", person.id, DebtKind.BORROWED, Money(BigDecimal("23"), "EUR"), time, date, "Standalone", null, null, emptyList())
        var updated: Pair<String, DebtDraft>? = null
        rule.setContent { HootbookTheme(dynamicColor = false) {
            AddEditLendBorrowTransactionSheet(listOf(person), person, listOf(account), CoreAttachmentStore(context), "GBP",
                transactionToEdit = debt, showPersonSelection = false, onDismiss = {}, onAddPerson = { _, _ -> },
                onSave = { fail("Editing must not create") }, onUpdate = { id, draft -> updated = id to draft })
        } }
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.runOnIdle {
            assertEquals(debt.id, updated!!.first); assertNull(updated!!.second.accountId)
            assertEquals("EUR", updated!!.second.currency); assertEquals(time, updated!!.second.date)
        }
        shot("standalone-debt-editor")
    }
    @Test fun fullSettlementUsesCoreAmountSelectionAndIncompletePersonTotals() {
        var draft: SettleDraft? = null
        rule.setContent { HootbookTheme(dynamicColor = false) {
            SettleUpSheet(person, listOf(account), onDismiss = {}, onSettle = { draft = it })
        } }
        rule.onNodeWithText(text(R.string.settle_up) + "-" + person.name).performTouchInput { swipeUp(endY = -600f) }
        rule.onNodeWithText(text(R.string.core_debts_incomplete)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.save)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertNull(draft!!.amount); assertEquals(account.id, draft!!.accountId) }
        shot("full-settlement")
    }
    @Test fun budgetCardUsesCoreAllowanceAndAmountsAtLargeFontAndOpaqueId() {
        val budget = Budget("budget/中文", "Core budget", BudgetKind.EXPENSE, BudgetPeriod.MONTHLY, Money(BigDecimal("500"), "GBP"), date, null, null, true,
            "#4CAF50", BudgetProgress(date, date, 31u, 17u, BigDecimal("99"), BigDecimal("401"), false, BigDecimal("19.09"),
                BigDecimal("3.19"), false, listOf("EUR"), emptyList(), emptyList()), null, time, time)
        var opened: String? = null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                HootbookTheme(dynamicColor = false) { SharedTransitionLayout {
                    BudgetCard(modifier = Modifier.statusBarsPadding(), budgetWithSpending = budget, onHistoryClick = { opened = it })
                } }
            }
        }
        rule.onNodeWithText(CoreMoneyPresentation.formatCurrency(BigDecimal("19.09"), "GBP")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.core_budgets_history_incomplete)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.history)).performClick()
        rule.runOnIdle { assertEquals(budget.id, opened) }
        shot("budget-large-font")
    }
    @Test fun subscriptionSummaryUsesCoreTotalsAndPaymentOpensTypedFlow() {
        val subscription = Subscription("sub/中文", "Streaming", Money(BigDecimal("4"), "EUR"), Cycle(3u, CycleUnit.MONTH), null, date, null,
            account.id, null, null, null, false, BigDecimal("1.33"), false, true, -8)
        val summary = SubscriptionSummary("GBP", BigDecimal("55"), BigDecimal("1234"), BigDecimal("12"), 7u, false, listOf("EUR"))
        val core = object : HootKitCore(NoPointer) {
            override fun plans() = object : Plans(NoPointer) {
                override suspend fun subscriptions(today: LocalDate, includeHidden: Boolean) = listOf(subscription)
                override suspend fun subscriptionSummary(today: LocalDate) = summary
            }
            override fun categories() = object : Categories(NoPointer) { override suspend fun list(kind: CategoryKind?, includeHidden: Boolean) = emptyList<Category>() }
            override fun currency() = object : Currency(NoPointer) { override suspend fun convert(amount: BigDecimal, from: String, to: String) = BigDecimal("42") }
        }
        lateinit var vm: SubscriptionsViewModel
        rule.runOnUiThread { vm = SubscriptionsViewModel(CoreSession(scope) { core }, ChangeBus()); store.put("subscriptions", vm) }
        var payment: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { SubscriptionsScreen(vm, onRecordPayment = { payment = it }) } }
        rule.waitUntil(5000) { !vm.uiState.value.isLoading }
        rule.onNodeWithText(CoreMoneyPresentation.formatCurrency(summary.monthly, summary.currency)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.core_complete_incomplete)).assertIsDisplayed()
        shot("subscriptions")
        rule.onNodeWithText(subscription.name).performClick()
        rule.onNodeWithText(text(R.string.yes_its_paid)).performClick()
        rule.runOnIdle { assertEquals(subscription.id, payment) }
    }
}
