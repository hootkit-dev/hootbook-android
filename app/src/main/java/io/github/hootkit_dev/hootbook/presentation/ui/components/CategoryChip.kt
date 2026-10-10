@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.R
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils

@Composable
fun CategoryChip(
    category: CategoryEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showText: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val iconResId = if (category.iconName.isNotEmpty()) {
            IconResolutionUtils.nameToResId(context, category.iconName)
                .takeIf { it != 0 } ?: R.drawable.type_food_dining
        } else {
            io.github.hootkit_dev.hootbook.utils.IconResolutionUtils.getSafeResId(
                context, 
                category.iconResId, 
                R.drawable.type_food_dining
            )
        }


        if (onClick != null) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onClick,
                modifier = Modifier.size(44.dp).
                background(
                    color = parseColor(category.color).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .padding(4.dp),
                    tint = Color.Unspecified
                )
            }
        }

        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Category name
        if (showText) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(category.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BlurredAnimatedVisibility(visible = category.description.isNotEmpty()) {
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}

/**
 * Helper function to parse color string to Compose Color.
 * Handles hex colors like "#FF0000" or "FF0000".
 */
private fun parseColor(colorString: String): Color {
    return try {
        val cleanColor = if (colorString.startsWith("#")) colorString else "#$colorString"
        Color(cleanColor.toColorInt())
    } catch (e: Exception) {
        // Fallback to grey if color parsing fails
        Color(0xFF757575)
    }
}