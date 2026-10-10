@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

@Composable
fun CategorySelectionSheet(
    categories: List<CategoryEntity>,
    subcategoriesMap: Map<Long, List<SubcategoryEntity>>,
    onSelectionComplete: (CategoryEntity, SubcategoryEntity?) -> Unit,
    onDismiss: () -> Unit,
    recentCategories: List<String> = emptyList()
) {
    val searchLabel = stringResource(R.string.search_categories)
    val recent = remember(categories, recentCategories) {
        val byName = categories.associateBy { it.name }
        recentCategories.mapNotNull { byName[it] }
    }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    
    // Filter categories based on search
    val filteredCategories = remember(categories, subcategoriesMap, searchQuery.text) {
        if (searchQuery.text.isBlank()) {
            categories
        } else {
            categories.filter { category ->
                val categoryMatches = io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(category.name, searchQuery.text)
                val subcategoriesMatch = subcategoriesMap[category.id]?.any {
                    io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(it.name, searchQuery.text)
                } == true
                categoryMatches || subcategoriesMatch
            }
        }
    }

    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }

    // Auto-expand categories that have matching subcategories when searching
    LaunchedEffect(searchQuery.text) {
        if (searchQuery.text.isNotBlank()) {
            filteredCategories.forEach { category ->
                val hasMatchingSubcategory = subcategoriesMap[category.id]?.any {
                    io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(it.name, searchQuery.text)
                } == true
                if (hasMatchingSubcategory) {
                    expandedStates[category.id] = true
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search Bar
        Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)) {
             SearchBarBox(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                 label = {
                     AnimatedContent(
                         targetState = searchLabel,
                         transitionSpec = {
                             (fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                                     slideInVertically(
                                         initialOffsetY = { it },
                                         animationSpec = tween(400, delayMillis = 100)
                                     ))
                                 .togetherWith(
                                     fadeOut(animationSpec = tween(400)) +
                                             slideOutVertically(
                                                 targetOffsetY = { -it },
                                                 animationSpec = tween(400)
                                             )
                                 )
                         },
                         label = "SearchBarLabelAnimation"
                     ) { labelText ->
                         Text(
                             text = labelText,
                             style = MaterialTheme.typography.bodyMedium,
                             lineHeight = 14.sp,
                             fontWeight = FontWeight.SemiBold,
                             fontStyle = FontStyle.Italic,
                             textAlign = TextAlign.Center,
                             color = MaterialTheme.colorScheme.inverseSurface.copy(0.5f),
                             modifier = Modifier.fillMaxWidth()
                         )
                     }
                 },
                leadingIcon = {},
                trailingIcon = if (searchQuery.text.isNotEmpty()) {
                    {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { searchQuery = TextFieldValue("") }) {
                            Icon(Iconax.CloseCircle, contentDescription = "Clear search")
                        }
                    }
                } else { {} }
            )
        }

        // Recently used categories pick in one tap, without opening their subcategories
        if (searchQuery.text.isBlank() && recent.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs)) {
                Text(
                    text = stringResource(R.string.recently_used),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    recent.forEach { category ->
                        AssistChip(
                            onClick = { onSelectionComplete(category, null) },
                            label = { Text(io.github.hootkit_dev.hootbook.presentation.common.categoryName(category.name), maxLines = 1) },
                            leadingIcon = { CategoryIcon(category, 18.dp) }
                        )
                    }
                }
            }
        }

        if (filteredCategories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_categories_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.sm,
                        bottom = 0.dp
                    )
                    .clip(RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(
                    items = filteredCategories,
                    key = { it.id }
                ) { category ->
                    val subs = subcategoriesMap[category.id] ?: emptyList()
                    
                    val isExpanded = expandedStates[category.id] == true
                    
                    val displayedSubcategories = if (searchQuery.text.isNotBlank()) {
                        // When searching, show only matching subcategories OR all if category matches
                        val categoryMatches = io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(category.name, searchQuery.text)
                        if (categoryMatches) {
                            subs
                        } else {
                            subs.filter { io.github.hootkit_dev.hootbook.presentation.common.CategoryNames.matches(it.name, searchQuery.text) }
                        }
                    } else if (isExpanded) {
                        subs
                    } else {
                        emptyList()
                    }

                    AnimatedContent(
                        targetState = category,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) +
                                    scaleIn(initialScale = 0.95f) togetherWith fadeOut(animationSpec = tween(90))
                        },
                        label = "CategoryItemAnimation"
                    ) { animatedCategory ->
                         CategoryItem(
                            category = animatedCategory,
                            subcategories = displayedSubcategories,
                            onClick = {
                                if (subs.isNotEmpty()) {
                                    if (isExpanded) {
                                        // If already expanded, select the category itself
                                        onSelectionComplete(animatedCategory, null)
                                    } else {
                                        // Otherwise, expand to show subcategories
                                        expandedStates[animatedCategory.id] = true
                                    }
                                } else {
                                    onSelectionComplete(animatedCategory, null)
                                }
                            },
                            onAddSubcategory = {},
                            onEditSubcategory = { sub ->
                                onSelectionComplete(animatedCategory, sub)
                            },
                            showAddSubcategoryButton = false
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(Spacing.xxl))
                }
            }
        }
    }
}
