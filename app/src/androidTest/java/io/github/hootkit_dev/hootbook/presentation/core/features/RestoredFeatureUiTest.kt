package io.github.hootkit_dev.hootbook.presentation.core.features

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreAnalyticsSummaryCard
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetCard
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetLimitsCard
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetHistoryRow
import io.github.hootkit_dev.hootbook.presentation.core.categories.CoreCategoryItem
import io.github.hootkit_dev.hootbook.presentation.core.debts.CorePersonRow
import io.github.hootkit_dev.hootbook.presentation.core.debts.CoreDebtRecordRow
import io.github.hootkit_dev.hootbook.presentation.core.investments.CoreHoldingRow
import io.github.hootkit_dev.hootbook.presentation.core.investments.CoreInvestmentsContent
import io.github.hootkit_dev.hootbook.presentation.core.investments.CoreInvestmentsUiState
import io.github.hootkit_dev.hootbook.presentation.core.plans.CoreSubscriptionSummary
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Original feature surfaces rendered with generated Core records, not legacy DTO fixtures. */
class RestoredFeatureUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate(2026, 10u, 10u)
    private val at = LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u)
    private fun money(value: String) = CurrencyFormatter.formatCurrency(BigDecimal(value), "CNY")

    @Test fun budgetCardUsesCoreDailyAllowanceAndKeepsStringHistoryId() {
        val progress = BudgetProgress(day, day, 1u, 1u, BigDecimal("71.11"), BigDecimal("28.89"),
            false, BigDecimal("7.31"), BigDecimal.ZERO, false, listOf("USD"), emptyList(), emptyList())
        val budget = Budget("budget-fixture-id", "Holiday budget", BudgetKind.EXPENSE, BudgetPeriod.MONTHLY,
            Money(BigDecimal("100"), "CNY"), day, null, null, true, "#4CAF50", progress, null, at, at)
        var opened: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreBudgetCard(budget = budget, onHistoryClick = { opened = it }) } }
        rule.onNodeWithText("Holiday budget").assertIsDisplayed()
        rule.onNodeWithText(money("7.31")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.core_budgets_missing, "USD")).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.history)).performClick()
        rule.runOnIdle { assertEquals("budget-fixture-id", opened) }
    }

    @Test fun budgetLimitRowUsesCoreLimitAndKeepsExactCategoryTarget() {
        val category = Category("limit-category", CategoryKind.EXPENSE, null, "Food", "Food", "restaurant", "#334455", 0, false, emptyList())
        val limit = BudgetLimitStatus("limit-id", category.id, BigDecimal("34.56"), BigDecimal("12.34"), BigDecimal("22.22"), false)
        val progress = BudgetProgress(day, day, 1u, 1u, BigDecimal("12.34"), BigDecimal("87.66"), false, BigDecimal("87.66"), BigDecimal.ZERO, true, emptyList(), listOf(limit), emptyList())
        val budget = Budget("budget-id", "Budget", BudgetKind.EXPENSE, BudgetPeriod.MONTHLY, Money(BigDecimal("100"), "CNY"), day, null, null, true, "#4CAF50", progress, null, at, at)
        var edited: String? = null
        var removed: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreBudgetLimitsCard(budget, listOf(category), true, {}, { edited = it.id }, { removed = it }) } }
        rule.onNodeWithText(money("34.56")).assertIsDisplayed()
        rule.onNodeWithText("Food").performClick()
        rule.runOnIdle { assertEquals("limit-category", edited) }
        rule.onNodeWithContentDescription(context.getString(R.string.core_budgets_limit_remove)).performClick()
        rule.runOnIdle { assertEquals("limit-category", removed) }
    }

    @Test fun budgetHistoryRingKeepsOverspendPercentageAndPeriodNavigation() {
        val period = BudgetPeriodSummary(day, day, BigDecimal("100"), BigDecimal("125"), true, false)
        var opened = false
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreBudgetHistoryRow(period, "CNY", ListItemPosition.Single) { opened = true } } }
        rule.onNodeWithText("125%").assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.core_budgets_history_incomplete)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(true, opened) }
    }

    @Test fun categoryChipKeepsExactCoreSubcategoryAndEditableParent() {
        val child = Subcategory("child-id", "parent-id", null, "Coffee", "Coffee", "coffee", "#112233", 0, false)
        val parent = Category("parent-id", CategoryKind.EXPENSE, null, "Food", "Food", "restaurant", "#334455", 0, false, listOf(child))
        var edited: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) {
            CoreCategoryItem(parent, onEdit = { edited = parent.id }, onDetails = {}, onAddSubcategory = {},
                onEditSubcategory = { edited = it.id }, onMove = {})
        } }
        rule.onNodeWithText("Food").assertIsDisplayed()
        rule.onNodeWithText("Coffee").performClick()
        rule.runOnIdle { assertEquals("child-id", edited) }
    }

    @Test fun customCategorySwipeRequestsConfirmationWithoutRemovingTheRow() {
        val category = Category("swipe-id", CategoryKind.EXPENSE, null, "Travel", "Travel", "flight", "#334455", 0, false, emptyList())
        val secondCategory = category.copy(id = "second-swipe-id", name = "Food", displayName = "Food")
        var requested = 0
        var secondRequested = 0
        var pending by mutableStateOf<String?>(null)
        rule.setContent { HootbookTheme(dynamicColor = false) {
            Column {
                CoreCategoryItem(category, onEdit = {}, onDetails = {}, onAddSubcategory = {}, onEditSubcategory = {}, onMove = {}, onDelete = { requested++; pending = category.id })
                CoreCategoryItem(secondCategory, onEdit = {}, onDetails = {}, onAddSubcategory = {}, onEditSubcategory = {}, onMove = {}, onDelete = { secondRequested++; pending = secondCategory.id })
            }
            if (pending != null) AlertDialog(onDismissRequest = { pending = null },
                text = { Text("Confirm category deletion") },
                confirmButton = { TextButton(onClick = { pending = null }) { Text("Cancel category deletion") } })
        } }
        rule.onNodeWithText("Travel").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, requested); assertEquals(0, secondRequested) }
        rule.onNodeWithText("Cancel category deletion").performClick()
        rule.onNodeWithText("Travel").assertIsDisplayed()
        // No confirmed deletion: the same row remains actionable after dismissing confirmation.
        rule.onNodeWithText("Travel").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, requested); assertEquals(0, secondRequested) }
        rule.onNodeWithText("Cancel category deletion").performClick()
        rule.onNodeWithText("Food").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, requested); assertEquals(1, secondRequested) }
        rule.onNodeWithText("Cancel category deletion").performClick()
        rule.onNodeWithText("Travel").assertIsDisplayed()
        rule.onNodeWithText("Food").assertIsDisplayed()
    }

    @Test fun subscriptionSummaryDisplaysCoreMonthlyAndYearlyAndPartialState() {
        val summary = SubscriptionSummary("CNY", BigDecimal("12.34"), BigDecimal("150.07"), BigDecimal("18"), 2u, false, listOf("USD"))
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreSubscriptionSummary(summary) } }
        rule.onNodeWithText(money("12.34")).assertIsDisplayed()
        rule.onNodeWithText(money("150.07")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.core_plans_missing_rates, "USD")).assertIsDisplayed()
    }

    @Test fun analyticsSummaryRendersSelectedIncomeWithoutClientAggregation() {
        val summary = LedgerSummary("CNY", BigDecimal("71.23"), BigDecimal("11.11"), BigDecimal("60.12"), true, emptyList(), emptyList(), emptyList(), emptyList())
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreAnalyticsSummaryCard(summary, 3u, setOf(TransactionKind.INCOME)) } }
        rule.onNodeWithText(money("71.23")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.transactions_format, 3)).assertIsDisplayed()
    }

    @Test fun personRowShowsCoreNetAndRetainsContactNavigation() {
        val person = Person("person-fixture-id", "Alex", null, null, "#334455", null, PersonRelation.FRIEND, false,
            PersonTotals("CNY", BigDecimal.ZERO, BigDecimal("33.21"), BigDecimal("-33.21"), true, true, emptyList()), at)
        var opened: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { CorePersonRow(person, onClick = { opened = person.id }) } }
        rule.onNodeWithText("Alex").assertIsDisplayed().performClick()
        rule.onNodeWithText(money("33.21")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.core_debts_overdue)).assertIsDisplayed()
        rule.runOnIdle { assertEquals("person-fixture-id", opened) }
    }

    @Test fun compactDebtRecordKeepsLongPressActionAndExactAmount() {
        val debt = DebtEntry("debt-id", "person-id", DebtKind.LENT, Money(BigDecimal("14.56"), "CNY"), at, null, "Lunch loan", null, null, emptyList())
        var opened = false
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreDebtRecordRow(debt, ListItemPosition.Single, onClick = {}, onLongClick = { opened = true }) } }
        rule.onNodeWithText("Lunch loan").assertIsDisplayed().performTouchInput { longClick() }
        rule.onNodeWithText(money("14.56")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.core_debts_delete)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(true, opened) }
    }

    @Test fun investmentAddButtonUsesCreationInsteadOfManagement() {
        var created = false
        var managed = false
        rule.setContent { HootbookTheme(dynamicColor = false) {
            CoreInvestmentsContent(CoreInvestmentsUiState(loading = false), providerName = { it }, onBack = {}, onReload = {}, onRefresh = {}, onRefreshAll = {},
                onCancel = {}, onClearError = {}, onConnect = {}, onSelect = {}, onHolding = {}, onLink = {}, onDisconnect = {},
                onManageAccounts = { managed = true }, onAccountHistory = {}, onAddInvestment = { created = true })
        } }
        rule.onNodeWithContentDescription(context.getString(R.string.investments_add)).performClick()
        rule.runOnIdle { assertEquals(true, created); assertEquals(false, managed) }
    }

    @Test fun holdingRowDisplaysReportedValueAndPreservesInstrumentSelection() {
        val holding = Holding("instrument-id", "EXM", "Example holding", "USD", BigDecimal("0.125"), BigDecimal("10.25"), BigDecimal("12.50"), BigDecimal("-2.25"), "STK", "")
        var opened: String? = null
        rule.setContent { HootbookTheme(dynamicColor = false) { CoreHoldingRow(holding, ListItemPosition.Single) { opened = holding.instrumentId } } }
        rule.onNodeWithText("EXM").assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.investments_value, "USD", "10.25")).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.investments_pnl, "USD", "-2.25")).assertIsDisplayed()
        rule.runOnIdle { assertEquals("instrument-id", opened) }
    }
}
