package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AccountOverviewGridTest {
    @get:Rule val rule=createComposeRule()
    private val entries=AccountCategory.entries.map { AccountOverviewItem(it,3,BigDecimal("2380.50"),"CNY",OverviewStatus.READY) }
    @Test fun fourRowsAreOrderedAndOpenTheirOwnCategory() {
        var selected: AccountCategory?=null
        rule.setContent { HootbookTheme(dynamicColor=false) { AccountOverviewList(entries,{selected=it},Modifier.width(380.dp)) } }
        val bounds=AccountCategory.entries.map { rule.onNodeWithTag("overview_${it.name}").fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (a,b) -> assertTrue(b.top >= a.bottom);assertEquals(a.left,b.left);assertEquals(a.right,b.right) }
        AccountCategory.entries.forEach { category ->
            rule.onNodeWithTag("overview_${category.name}").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(category,selected) }
        }
    }
    @Test fun narrowLargeTextKeepsFourTilesOperable() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
            HootbookTheme(dynamicColor=false) { AccountOverviewList(entries.map { it.copy(amount=BigDecimal("-1234567.89")) },{},Modifier.width(320.dp)) }
        } }
        AccountCategory.entries.forEach { rule.onNodeWithTag("overview_${it.name}").assertIsDisplayed().assertHasClickAction() }
    }
    @Test fun unconnectedInvestmentShowsActionInsteadOfZero() {
        rule.setContent { HootbookTheme(dynamicColor=false) { AccountOverviewList(entries.dropLast(1)+AccountOverviewItem(AccountCategory.INVESTMENTS,status=OverviewStatus.CONNECT),{}) } }
        val tile=rule.onNodeWithTag("overview_INVESTMENTS")
        tile.assertIsDisplayed().assertHasClickAction()
        rule.onAllNodesWithText("¥2,380.5", substring=true).assertCountEquals(3)
    }
    @Test fun totalAndAllCategoriesShareOneNonClickableParent() {
        var headerClicks=0
        var selected: AccountCategory?=null
        rule.setContent { HootbookTheme(dynamicColor=false) {
            AccountOverviewPanel(entries,{selected=it},Modifier.width(380.dp)) {
                TextButton(onClick={headerClicks++},modifier=Modifier.testTag("total_header")) { Text("Net worth") }
            }
        } }
        rule.onNodeWithTag("networth_accounts_panel").assertHasNoClickAction()
        rule.onNodeWithTag("total_header").assert(hasAnyAncestor(hasTestTag("networth_accounts_panel")))
        AccountCategory.entries.forEach { category ->
            rule.onNodeWithTag("overview_${category.name}").assert(hasAnyAncestor(hasTestTag("networth_accounts_panel"))).performClick()
            rule.runOnIdle { assertEquals(category,selected);assertEquals(0,headerClicks) }
        }
        rule.onNodeWithTag("total_header").performClick()
        rule.runOnIdle { assertEquals(1,headerClicks) }
    }
    @Test fun embeddedLargeTextRetainsFourEntrancesAndDebtSign() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
            HootbookTheme(dynamicColor=false) { AccountOverviewPanel(entries,{},Modifier.width(320.dp)) { Text("Net worth") } }
        } }
        AccountCategory.entries.forEach { rule.onNodeWithTag("overview_${it.name}").assertIsDisplayed().assertHasClickAction() }
        rule.onNodeWithText("−¥2,380.5",substring=true).assertIsDisplayed()
    }
}
