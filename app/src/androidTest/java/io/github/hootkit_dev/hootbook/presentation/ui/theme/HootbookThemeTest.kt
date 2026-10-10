package io.github.hootkit_dev.hootbook.presentation.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import io.github.hootkit_dev.hootbook.data.preferences.AccentColor
import io.github.hootkit_dev.hootbook.data.preferences.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class HootbookThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun check(dark: Boolean, style: ThemeStyle, enabled: Boolean = true) {
        lateinit var actual: ColorScheme
        lateinit var expected: ColorScheme
        composeRule.setContent {
            val context = LocalContext.current
            expected = if (style == ThemeStyle.DYNAMIC && enabled) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) getCustomDarkColorScheme(AccentColor.GREEN) else getCustomLightColorScheme(AccentColor.GREEN)
            }
            HootbookTheme(darkTheme = dark, themeStyle = style, dynamicColor = enabled, accentColor = AccentColor.GREEN) {
                val colors = MaterialTheme.colorScheme
                SideEffect { actual = colors }
            }
        }
        composeRule.runOnIdle {
            assertEquals(expected.primary, actual.primary)
            assertEquals(expected.primaryContainer, actual.primaryContainer)
            assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer)
            assertEquals(expected.surface, actual.surface)
            assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        }
    }
    @Test fun dynamicLightUsesSystemRoles() = check(false, ThemeStyle.DYNAMIC)
    @Test fun dynamicDarkUsesSystemRoles() = check(true, ThemeStyle.DYNAMIC)
    @Test fun fixedSelectionUsesGeneratedRoles() = check(false, ThemeStyle.DEFAULT)
    @Test fun previewCanExplicitlyDisableDynamicColors() = check(false, ThemeStyle.DYNAMIC, false)
}
