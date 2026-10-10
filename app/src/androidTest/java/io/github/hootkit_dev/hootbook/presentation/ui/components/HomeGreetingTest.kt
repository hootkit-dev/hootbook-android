package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeGreetingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun usernameIsTheLargeTitleAndAllActionsWork() {
        var profile = 0; var notification = 0; var more = 0
        lateinit var profileLabel: String; lateinit var notificationLabel: String; lateinit var moreLabel: String
        rule.setContent { HootbookTheme(dynamicColor = false) {
            profileLabel = stringResource(R.string.profile_desc)
            notificationLabel = stringResource(R.string.notification)
            moreLabel = stringResource(R.string.more_options)
            GreetingCard(userName = "Hachichi", profileImageUri = null, profileBackgroundColor = Color.Transparent,
                onProfileClick = { profile++ },
                onNotificationClick = { notification++ }, onMoreClick = { more++ })
        } }
        val name = rule.onNodeWithText("Hachichi").assertIsDisplayed()
        rule.onNodeWithText("Hootbook").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(28f, layouts.single().layoutInput.style.fontSize.value, 0.1f)
        rule.onNodeWithContentDescription(profileLabel).performClick()
        rule.onNodeWithContentDescription(notificationLabel).performClick()
        rule.onNodeWithContentDescription(moreLabel).performClick()
        rule.runOnIdle { assertEquals(1, profile); assertEquals(1, notification); assertEquals(1, more) }
    }

    @Test fun longUsernameAndLargeFontKeepActionsVisible() {
        lateinit var notificationLabel: String; lateinit var moreLabel: String
        var clicked = false
        rule.setContent { HootbookTheme(dynamicColor = false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                notificationLabel = stringResource(R.string.notification)
                moreLabel = stringResource(R.string.more_options)
                GreetingCard(Modifier.width(320.dp), "很长的用户名 Hachichi Hachichi", null,
                    Color.Transparent, onMoreClick = { clicked = true })
            }
        } }
        rule.onNodeWithText("很长的用户名 Hachichi Hachichi").assertIsDisplayed()
        rule.onNodeWithContentDescription(notificationLabel).assertIsDisplayed()
        rule.onNodeWithContentDescription(moreLabel).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }
}
