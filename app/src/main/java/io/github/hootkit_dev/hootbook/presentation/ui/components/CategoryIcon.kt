package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils

/** A category's own icon, resolved by name first, as the category pickers show it. */
@Composable
fun CategoryIcon(category: CategoryEntity, size: Dp) {
    val context = LocalContext.current
    val resId = remember(category) {
        if (category.iconName.isNotEmpty()) {
            IconResolutionUtils.nameToResId(context, category.iconName).takeIf { it != 0 } ?: category.iconResId
        } else category.iconResId
    }
    if (resId != 0) {
        Icon(painter = painterResource(resId), contentDescription = null, tint = Color.Unspecified,
            modifier = Modifier.size(size))
    } else {
        Icon(Iconax.Box2, contentDescription = null, modifier = Modifier.size(size))
    }
}
