package io.github.hootkit_dev.hootbook.presentation.ui.components

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun SubscriptionIconsStack(
    subscriptions: List<Subscription>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    maxIcons: Int = 4,
    borderColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    categoriesMap: Map<String, Category> = emptyMap(),
    subcategoriesMap: Map<String, Subcategory> = emptyMap()
) {
    val displaySubscriptions = subscriptions.take(maxIcons)
    val outerIconSize = iconSize + 4.dp

    if (displaySubscriptions.isEmpty()) return

    val totalWidth = if (displaySubscriptions.size > 1) {
        (iconSize * (displaySubscriptions.size - 1).toFloat() * 0.55f) + outerIconSize
    } else {
        outerIconSize
    }

    Box(modifier = modifier.width(totalWidth)) {
        displaySubscriptions.forEachIndexed { index, subscription ->
            val overlapOffset = (iconSize * index.toFloat() * 0.55f)

            Box(
                modifier = Modifier
                    .offset(x = overlapOffset)
                    .zIndex(index.toFloat())
                    .size(outerIconSize)
                    .background(borderColor, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val categoryEntity = categoriesMap[subscription.categoryId]
                val subcategoryEntity = if (categoryEntity != null && subscription.subcategoryId != null) {
                    subcategoriesMap[subscription.subcategoryId]
                } else null

                BrandIcon(
                    merchantName = subscription.name,
                    size = iconSize,
                    showBackground = true,
                    categoryEntity = categoryEntity,
                    subcategoryEntity = subcategoryEntity
                )
            }
        }
    }
}
