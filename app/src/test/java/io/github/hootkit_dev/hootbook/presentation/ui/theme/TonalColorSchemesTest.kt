package io.github.hootkit_dev.hootbook.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.github.hootkit_dev.hootbook.data.preferences.AccentColor
import org.junit.Assert.*
import org.junit.Test

class TonalColorSchemesTest {
    private fun schemes() = AccentColor.entries.flatMap { accent ->
        listOf(getCustomLightColorScheme(accent), getCustomDarkColorScheme(accent))
    }
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun `every accent has readable normal text on semantic containers`() {
        schemes().forEach { s ->
            val pairs = listOf(
                s.primary to s.onPrimary, s.primaryContainer to s.onPrimaryContainer,
                s.secondary to s.onSecondary, s.secondaryContainer to s.onSecondaryContainer,
                s.tertiary to s.onTertiary, s.tertiaryContainer to s.onTertiaryContainer,
                s.error to s.onError, s.errorContainer to s.onErrorContainer,
                s.surface to s.onSurface, s.surfaceContainerHighest to s.onSurface,
                s.surfaceVariant to s.onSurfaceVariant, s.inverseSurface to s.inverseOnSurface
            )
            pairs.forEach { (background, foreground) ->
                assertTrue("Contrast ${contrast(background, foreground)} for $background / $foreground", contrast(background, foreground) >= 4.5f)
            }
        }
    }
    @Test fun `containers are independent tonal roles not saturated accent aliases`() {
        schemes().forEach { s ->
            assertNotEquals(s.primary, s.primaryContainer)
            assertNotEquals(s.secondary, s.secondaryContainer)
            assertNotEquals(s.tertiary, s.tertiaryContainer)
            assertNotEquals(s.error, s.errorContainer)
        }
    }
    @Test fun `surface elevation tones progress consistently in light and dark`() {
        fun levels(s: ColorScheme) = listOf(s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest).map { it.luminance() }
        AccentColor.entries.forEach { accent ->
            assertTrue(levels(getCustomLightColorScheme(accent)).zipWithNext().all { (a,b) -> a > b })
            assertTrue(levels(getCustomDarkColorScheme(accent)).zipWithNext().all { (a,b) -> a < b })
        }
    }
    @Test fun `green palette matches official MCU pinned generator output`() {
        val light = getCustomLightColorScheme(AccentColor.GREEN)
        val dark = getCustomDarkColorScheme(AccentColor.GREEN)
        assertEquals(0xFF406835.toInt(), light.primary.toArgb())
        assertEquals(0xFFC1EFAF.toInt(), light.primaryContainer.toArgb())
        assertEquals(0xFFF8FBF0.toInt(), light.surface.toArgb())
        assertEquals(0xFFA6D395.toInt(), dark.primary.toArgb())
        assertEquals(0xFF294F20.toInt(), dark.primaryContainer.toArgb())
        assertEquals(0xFF11140F.toInt(), dark.surface.toArgb())
    }
    @Test fun `repeated requests reuse the generated palette`() {
        AccentColor.entries.forEach { accent ->
            assertSame(getCustomLightColorScheme(accent), getCustomLightColorScheme(accent))
            assertSame(getCustomDarkColorScheme(accent), getCustomDarkColorScheme(accent))
        }
    }
}
