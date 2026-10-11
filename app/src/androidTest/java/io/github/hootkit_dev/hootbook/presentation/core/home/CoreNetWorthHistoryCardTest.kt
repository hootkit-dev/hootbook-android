package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.features.home.HomeMoneyPresentation
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoreNetWorthHistoryCardTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 11)
    private fun data() = CoreNetWorthHistoryResult("CNY", listOf(
        CoreNetWorthHistoryPoint(date.minusDays(1), BigDecimal("100.123456")),
        CoreNetWorthHistoryPoint(date, BigDecimal("200.654321"), current = true)), true)

    @Test fun plotKeepsHeightAcrossLoadingRefreshAndIncompleteStates() {
        val state = mutableStateOf(CoreNetWorthHistoryUiState())
        rule.setContent { HootbookTheme(dynamicColor = false) {
            CoreNetWorthHistoryContent(state.value, emptyMap(), {}, {})
        } }
        val loading = rule.onNodeWithTag("net_worth_history_plot").fetchSemanticsNode().boundsInRoot.height
        rule.runOnIdle { state.value = state.value.copy(data = data(), refreshing = false) }
        rule.onNodeWithTag("net_worth_history_plot").assertIsDisplayed()
        assertEquals(loading, rule.onNodeWithTag("net_worth_history_plot").fetchSemanticsNode().boundsInRoot.height, 0f)
        rule.runOnIdle { state.value = state.value.copy(refreshing = true) }
        assertEquals(loading, rule.onNodeWithTag("net_worth_history_plot").fetchSemanticsNode().boundsInRoot.height, 0f)
        rule.runOnIdle { state.value = state.value.copy(data = CoreNetWorthHistoryResult("CNY", emptyList(), false), refreshing = false) }
        assertEquals(loading, rule.onNodeWithTag("net_worth_history_plot").fetchSemanticsNode().boundsInRoot.height, 0f)
        rule.onNodeWithTag("net_worth_history_retry").assertIsDisplayed()
    }

    @Test fun rangeSelectionAndInformationRemainIndependentOfPlot() {
        var range: CoreHistoryRange? = null
        rule.setContent { HootbookTheme(dynamicColor = false) {
            CoreNetWorthHistoryContent(CoreNetWorthHistoryUiState(data = data(), refreshing = false), emptyMap(), { range = it }, {})
        } }
        rule.onNodeWithTag("net_worth_history_range_MONTH").performClick()
        rule.runOnIdle { assertEquals(CoreHistoryRange.MONTH, range) }
        rule.onNodeWithTag("net_worth_history_info").performClick()
        rule.onNode(isDialog()).assertIsDisplayed()
    }

    @Test fun flatLineDoesNotRepeatTheSameAmountAsBothAxisLabels() {
        val amount = BigDecimal("25.1234")
        val flat = CoreNetWorthHistoryResult("CNY", listOf(
            CoreNetWorthHistoryPoint(date.minusDays(1), amount), CoreNetWorthHistoryPoint(date, amount, true)), true)
        rule.setContent { HootbookTheme(dynamicColor = false) {
            CoreNetWorthHistoryContent(CoreNetWorthHistoryUiState(data = flat, refreshing = false), emptyMap(), {}, {})
        } }
        rule.onAllNodesWithText(HomeMoneyPresentation.format(amount, "CNY")).assertCountEquals(1)
    }
}
