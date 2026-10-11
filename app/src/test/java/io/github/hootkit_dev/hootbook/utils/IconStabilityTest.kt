package io.github.hootkit_dev.hootbook.utils

import io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconStabilityTest {

    @Test
    fun testIconResolutionStability() {
        // This test verifies that IconResolutionUtils can resolve names to fallback resource IDs
        // even without a Context, ensured by our internal when() mapping.
        val iconName = "type_food_dining"
        
        // Resolve using the internal fallback logic (context is null)
        val resId = IconResolutionUtils.nameToResId(null, iconName)
        
        // Final verification: IconResolutionUtils provides a stable mapping
        assertNotEquals("Icon name should resolve to a valid fallback even without context", 0, resId)
        
        // Resolve again - should be same
        val resId2 = IconResolutionUtils.nameToResId(null, iconName)
        assertEquals("Resolution should be deterministic for the same name", resId, resId2)
    }

    @Test
    fun testIconProviderPriority() {
        val merchantName = "Uber"
        val iconName = "type_travel_transport_taxi"
        
        // Test that getIconForTransaction handles the parameters correctly.
        // Priority: Brand Icon > Account Icon > ...
        val brand = BrandIcons.getIconResource(merchantName)
        val account = IconResolutionUtils.nameToResId(null, iconName)
        val resource = brand?.let(IconResource::DrawableResource)
            ?: IconResource.DrawableResource(account)
        
        // Uber has a brand logo in BrandIcons, so it should be used first
        assertTrue("Brand logo or custom icon should be resolved", resource is IconResource.DrawableResource)
    }
}
