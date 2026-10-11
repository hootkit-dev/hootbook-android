@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import io.github.hootkit_dev.hootbook.presentation.ui.components.IconSelector
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Category
import dev.hootkit.core.CategoryKind
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import androidx.compose.material3.OutlinedButton
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditCategorySheet(
    category: Category?,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    onSave: (name: String, color: String, iconName: String, isIncome: Boolean) -> Unit,
    onReset: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // A built-in name is edited as shown; left as shown, it is saved under its stored name
    val shownName = (category?.displayName ?: "")
    var name by remember { mutableStateOf(shownName) }
    val savedName = { if (category != null && name.trim() == shownName) category.displayName else name }
    var colorHex by remember { mutableStateOf(category?.color ?: "#33B5E5") }
    var iconName by remember(category) {
        mutableStateOf(
            category?.icon?.takeIf { it.isNotEmpty() }
                ?: IconResolutionUtils.resIdToName(context, R.drawable.type_food_dining)
                    .takeIf { it.isNotEmpty() } ?: "type_food_dining"
        )
    }
    var iconResId by remember(iconName) {
        mutableIntStateOf(
            IconResolutionUtils.nameToResId(context, iconName)
                .takeIf { it != 0 } ?: R.drawable.type_food_dining
        )
    }

    var isIncome by remember { mutableStateOf(category?.kind == CategoryKind.INCOME) }

    var showIconSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showIconSelector) {
        HootbookModalBottomSheet(
            onDismissRequest = { showIconSelector = false },
            sheetState = sheetState,
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

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preview Section

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { showIconSelector = true }
                        .padding(16.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier =
                                Modifier.size(64.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(64.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(
                                            Color(colorHex.toColorInt()).copy(alpha = 0.2f)
                                        ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }

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
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = name.ifEmpty { stringResource(R.string.category_name) },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        IconButton(
                            onClick = {},
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = stringResource(R.string.add_subcategory),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.preview),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.6f)
                )
            }



            Spacer(modifier = Modifier.height(4.dp))


            // Type Switcher
            if (category == null) GenericTypeSwitcher(
                selectedIndex = if (isIncome) 1 else 0,
                onIndexChange = { index -> isIncome = index == 1 },
                options = listOf(stringResource(R.string.expense), stringResource(R.string.income)),
                modifier = Modifier.fillMaxWidth()
            )


            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(
                        text= stringResource(R.string.name_label),
                        fontWeight = FontWeight.SemiBold
                    ) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        lineHeight = 18.sp,
                        fontSize = 16.sp
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            0.7f
                        )
                    )
                )
            }


            // Color Picker Section
            ColorPickerContent(
                initialColor = colorHex.toColorInt(),
                onColorChanged = { colorInt ->
                    colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                }
            )

            Spacer(modifier = Modifier.height(82.dp))

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
                // Delete button (only for non-system categories)
                if (onDelete != null && category != null && category.builtinKey == null) {
                    OutlinedButton(
                        onClick = { onDelete() },
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
                            contentDescription = stringResource(R.string.delete_category)
                        )
                    }
                }

                // Create/Update button
                Button(
                    onClick = { onSave(savedName(), colorHex, iconName, isIncome) },
                    enabled = !saving && name.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = if (category == null) stringResource(R.string.create_category) else stringResource(R.string.update_category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset button (only for system categories)
                if (category?.builtinKey != null && onReset != null) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            onReset(category.id)
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

