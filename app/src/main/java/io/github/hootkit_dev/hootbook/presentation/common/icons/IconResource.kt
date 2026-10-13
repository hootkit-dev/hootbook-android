package io.github.hootkit_dev.hootbook.presentation.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Pure visual resource shape, with no entity/category inference or ledger rules. */
sealed class IconResource {
    data class DrawableResource(val resId: Int) : IconResource()
    data class VectorIcon(val icon: ImageVector, val tint: Color) : IconResource()
    data class TintedResIcon(val resId: Int, val tint: Color) : IconResource()
}
