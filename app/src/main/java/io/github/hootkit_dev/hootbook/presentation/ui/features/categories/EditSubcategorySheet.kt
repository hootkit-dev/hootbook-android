@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteSubcategoryDialog
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSubcategorySheet(
    subcategory: SubcategoryEntity?,
    categoryColor: String,
    categoryIconResId: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, iconResId: Int, iconName: String, color: String) -> Unit,
    onReset: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    // A built-in name is edited as shown; left as shown, it is saved under its stored name
    val shownName = io.github.hootkit_dev.hootbook.presentation.common.categoryName(subcategory?.name)
    var name by remember { mutableStateOf(shownName) }
    val savedName = { if (subcategory != null && name.trim() == shownName) subcategory.name else name }
    var colorHex by remember { mutableStateOf(subcategory?.color ?: categoryColor) }
    var iconName by remember(subcategory) {
        mutableStateOf(
            subcategory?.iconName?.takeIf { it.isNotEmpty() }
                ?: IconResolutionUtils.resIdToName(context, subcategory?.iconResId ?: categoryIconResId)
                    .takeIf { it.isNotEmpty() } ?: "type_food_dining"
        )
    }
    var iconResId by remember(iconName) {
        mutableIntStateOf(
            IconResolutionUtils.nameToResId(context, iconName)
                .takeIf { it != 0 } ?: R.drawable.type_food_dining
        )
    }


    var showIconSelector by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    if (showIconSelector) {
        HootbookModalBottomSheet(
            onDismissRequest = { showIconSelector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            IconSelector(
                selectedIconName = iconName,
                onIconSelected = { name ->
                    iconName = name
                    iconResId = IconResolutionUtils.nameToResId(context, name)
                    showIconSelector = false
                }
            )
        }
    }

    if (showDeleteConfirmation && subcategory != null) {
        DeleteSubcategoryDialog(
            subcategoryName = subcategory.name,
            onDismiss = { showDeleteConfirmation = false },
            onDelete = {
                onDelete?.invoke(subcategory.id)
                showDeleteConfirmation = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preview Section
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable { showIconSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex.toColorInt()).copy(alpha = 0.2f))
                        )
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.Unspecified
                        )

                        // Add icon overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.surface,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Text(
                        text = name.ifEmpty { stringResource(R.string.subcategory) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(colorHex.toColorInt())
                    )
                }
            }

            item {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(
                        text= stringResource(R.string.name_label),
                        fontWeight = FontWeight.SemiBold
                    ) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        lineHeight = 20.sp,
                        fontSize = 16.sp
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                )
            }

            // Color Picker Section
            item {
                ColorPickerContent(
                    initialColor = colorHex.toColorInt(),
                    onColorChanged = { colorInt ->
                        colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(62.dp))
            }
        }
        // Action Buttons at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button (only for non-system subcategories)
                if (onDelete != null && subcategory != null && !subcategory.isSystem) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.error
                                )
                            )
                        ),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Iconax.Bag,
                            contentDescription = stringResource(R.string.delete_subcategory)
                        )
                    }
                }

                // Create/Update button
                Button(
                    onClick = { onSave(savedName(), iconResId, iconName, colorHex) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = if (subcategory == null) stringResource(R.string.create_subcategory) else stringResource(R.string.update_subcategory),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset button (only for system subcategories)
                if (subcategory?.isSystem == true && onReset != null) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            name = io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.display(subcategory.defaultName ?: subcategory.name)
                            colorHex = subcategory.defaultColor ?: categoryColor
                            iconName = subcategory.defaultIconName ?: subcategory.iconName
                            iconResId = IconResolutionUtils.nameToResId(context, iconName)
                            onReset(subcategory.id)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraExtraLarge
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.reset_to_default),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
