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
import dev.hootkit.core.Category
import dev.hootkit.core.Subcategory
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

@Composable
fun CategorySelectionSheet(
    categories: List<Category>,
    subcategoriesMap: Map<String, List<Subcategory>>,
    onSelectionComplete: (Category, Subcategory?) -> Unit,
    onDismiss: () -> Unit
) {
    val searchLabel = stringResource(R.string.search_categories)
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Filter categories based on search
    val filteredCategories = remember(categories, subcategoriesMap, searchQuery.text) {
        if (searchQuery.text.isBlank()) {
            categories
        } else {
            categories.filter { category ->
                val categoryMatches = category.displayName.contains(searchQuery.text, ignoreCase = true)
                val subcategoriesMatch = subcategoriesMap[category.id]?.filter { !it.hidden }?.any {
                    it.displayName.contains(searchQuery.text, ignoreCase = true)
                } == true
                categoryMatches || subcategoriesMatch
            }
        }
    }

    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    // Auto-expand categories that have matching subcategories when searching
    LaunchedEffect(searchQuery.text) {
        if (searchQuery.text.isNotBlank()) {
            filteredCategories.forEach { category ->
                val hasMatchingSubcategory = subcategoriesMap[category.id]?.filter { !it.hidden }?.any {
                    it.displayName.contains(searchQuery.text, ignoreCase = true)
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

        if (filteredCategories.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_categories_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val subs = subcategoriesMap[category.id]?.filter { !it.hidden } ?: emptyList()

                    val isExpanded = expandedStates[category.id] == true

                    val displayedSubcategories = if (searchQuery.text.isNotBlank()) {
                        // When searching, show only matching subcategories OR all if category matches
                        val categoryMatches = category.displayName.contains(searchQuery.text, ignoreCase = true)
                        if (categoryMatches) {
                            subs
                        } else {
                            subs.filter { it.displayName.contains(searchQuery.text, ignoreCase = true) }
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
