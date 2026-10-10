package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionLeadingSlotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fixedSizeAndSelectionClickSurviveModeChanges() {
        val mode = mutableStateOf(false)
        val selected = mutableStateOf(false)
        var toggles = 0
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionLeadingSlot(mode.value, selected.value, {
                toggles++; selected.value = !selected.value
            }, Modifier.testTag("slot")) {
                Box(Modifier.fillMaxSize().testTag("logo"))
            }
        } }
        rule.onNodeWithTag("slot").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        rule.onNodeWithTag("logo").assertIsDisplayed()
        rule.runOnIdle { mode.value = true }
        rule.waitForIdle()
        rule.onNodeWithTag("slot").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        rule.onNode(hasClickAction()).performClick()
        rule.runOnIdle { assertEquals(1, toggles); assertEquals(true, selected.value) }
        rule.runOnIdle { mode.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("logo").assertIsDisplayed()
    }

    @Test fun interruptedSelectionFadesSettleOnTheLastMode() {
        val mode = mutableStateOf(false)
        rule.setContent { HootbookTheme(dynamicColor = false) {
            TransactionLeadingSlot(mode.value, false, {}, Modifier.testTag("slot")) {
                Box(Modifier.fillMaxSize().testTag("logo"))
            }
        } }
        rule.mainClock.autoAdvance = false
        repeat(6) {
            rule.runOnIdle { mode.value = !mode.value }
            rule.mainClock.advanceTimeBy(32)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("logo").assertIsDisplayed()
        rule.onNodeWithTag("slot").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
    }
}
