package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import io.github.hootkit_dev.hootbook.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity

@Composable
fun SubcategoryRow(
        subcategories: List<SubcategoryEntity>,
        onSubcategoryClick: (SubcategoryEntity) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier = Modifier,
        showAddButton: Boolean = true
) {
    LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(subcategories) { subcategory ->
            SubcategoryChip(
                    subcategory = subcategory,
                    onClick = { onSubcategoryClick(subcategory) }
            )
        }
        
        // Add button at the end
        if (showAddButton) {
            item {
                AddSubcategoryButton(onClick = onAddClick)
            }
        }
    }
}

@Composable
private fun SubcategoryChip(
        subcategory: SubcategoryEntity,
        onClick: () -> Unit
) {
    val backgroundColor = try {
        Color(subcategory.color.toColorInt()).copy(alpha = 0.2f)
    } catch (e: Exception) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Row(
            modifier =
                    Modifier.clip(RoundedCornerShape(60.dp))
                            .background(backgroundColor)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        val context = androidx.compose.ui.platform.LocalContext.current
        val iconResId = if (subcategory.iconName.isNotEmpty()) {
            io.github.hootkit_dev.hootbook.utils.IconResolutionUtils.nameToResId(context, subcategory.iconName)
                .takeIf { it != 0 } ?: 0
        } else {
            io.github.hootkit_dev.hootbook.utils.IconResolutionUtils.getSafeResId(
                context,
                subcategory.iconResId,
                0
            )
        }

        if (iconResId != 0) {
            Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.Unspecified
            )
        }

        // Name
        Text(
                text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(subcategory.name),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddSubcategoryButton(onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.size(28.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_subcategory),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
        )
    }
}
