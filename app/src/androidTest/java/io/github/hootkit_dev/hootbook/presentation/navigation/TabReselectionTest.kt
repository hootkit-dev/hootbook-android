package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TabReselectionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun navigationRetainsCurrentEntry() = exercise()

    private fun exercise() {
        lateinit var nav: NavHostController
        lateinit var transactions: String
        lateinit var analytics: String
        lateinit var home: String
        rule.setContent { HootbookTheme(dynamicColor = false) {
            nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            transactions = stringResource(BottomNavItem.Transactions.titleRes)
            analytics = stringResource(BottomNavItem.Analytics.titleRes)
            home = stringResource(BottomNavItem.Home.titleRes)
            Scaffold(bottomBar = {
                HootbookBottomNavigation(
                    navController = nav,
                    currentDestination = entry?.destination,
                    hideLabels = false,
                    hidePill = false,
                    visible = true
                )
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NavHost(nav, startDestination = Home) {
                        composable<Home>(enterTransition = MainTabMotion.enter, exitTransition = MainTabMotion.exit, popEnterTransition = MainTabMotion.popEnter, popExitTransition = MainTabMotion.popExit) { Text("Home fixture") }
                        composable<Analytics>(enterTransition = MainTabMotion.enter, exitTransition = MainTabMotion.exit, popEnterTransition = MainTabMotion.popEnter, popExitTransition = MainTabMotion.popExit) { Text("Analytics fixture") }
                        composable<Transactions>(enterTransition = MainTabMotion.enter, exitTransition = MainTabMotion.exit, popEnterTransition = MainTabMotion.popEnter, popExitTransition = MainTabMotion.popExit) { Text("Transactions fixture") }
                    }
                }
            }
        } }
        rule.onNodeWithContentDescription(transactions, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        var originalId = ""
        rule.runOnIdle {
            originalId = nav.currentBackStackEntry!!.id
            nav.currentBackStackEntry!!.savedStateHandle["test_scroll_marker"] = 42
        }
        repeat(3) { rule.onNodeWithContentDescription(transactions, useUnmergedTree = true).performClick(); rule.waitForIdle() }
        val afterReselect = rule.runOnIdle {
            nav.currentBackStackEntry!!.id to nav.currentBackStackEntry!!.savedStateHandle.get<Int>("test_scroll_marker")
        }
        assertEquals("CURRENT_TAB_RECREATED: $style", originalId, afterReselect.first)
        assertEquals(42, afterReselect.second)
        rule.onNodeWithContentDescription(analytics, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(transactions, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val restoredMarker = rule.runOnIdle { nav.currentBackStackEntry!!.savedStateHandle.get<Int>("test_scroll_marker") }
        assertEquals(42, restoredMarker)
        rule.runOnIdle { nav.navigate(Transactions(category = "test-category", focusSearch = true)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(transactions, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val rootRoute = rule.runOnIdle { nav.currentBackStackEntry!!.toRoute<Transactions>() }
        assertEquals("Contextual entry must still return to the root tab", Transactions(), rootRoute)
        fun callback(label: String): () -> Boolean {
            val node = rule.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription(label)), useUnmergedTree = true).fetchSemanticsNode()
            return node.config[SemanticsActions.OnClick].action!!
        }
        val clickHome = callback(home)
        val clickAnalytics = callback(analytics)
        val clickTransactions = callback(transactions)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { clickHome(); clickAnalytics(); clickTransactions() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val finalRoute = rule.runOnIdle { nav.currentBackStackEntry!!.toRoute<Transactions>() }
        assertEquals("Last rapid request must win", Transactions(), finalRoute)
        val finalTag = rule.runOnIdle { nav.currentBackStackEntry!!.mainTabTag() }
        assertEquals("transactions", finalTag)

    }
}
