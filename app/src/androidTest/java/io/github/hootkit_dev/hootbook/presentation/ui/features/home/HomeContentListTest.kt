package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentListTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: LazyListState

    @Before
    fun showHomeList() {
        composeRule.setContent {
            MaterialTheme {
                state = rememberLazyListState()
                HomeContentList(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(240.dp).testTag("home_content")
                ) {
                    items(12) { index ->
                        Box(Modifier.fillMaxWidth().height(64.dp).testTag("card_$index")) {
                            Text("Account $index")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun draggingPastTopDoesNotMoveTheCards() {
        val first = composeRule.onNodeWithTag("card_0")
        val before = first.fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("home_content")

        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f))
        }
        assertEquals(before, first.fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle { assertFalse(state.canScrollBackward) }
        list.performTouchInput { up() }
    }

    @Test
    fun draggingPastBottomDoesNotMoveTheCards() {
        val list = composeRule.onNodeWithTag("home_content")
        list.performScrollToIndex(11)
        val last = composeRule.onNodeWithTag("card_11")
        val before = last.fetchSemanticsNode().boundsInRoot

        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f))
        }
        assertEquals(before, last.fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle { assertFalse(state.canScrollForward) }
        list.performTouchInput { up() }
    }

    @Test
    fun normalSwipesStillScrollThroughEveryCard() {
        val list = composeRule.onNodeWithTag("home_content")
        list.performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        }
        list.performScrollToIndex(11)
        composeRule.onNodeWithTag("card_11").assertIsDisplayed()
    }
}
