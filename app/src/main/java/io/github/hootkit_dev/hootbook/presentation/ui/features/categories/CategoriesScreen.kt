@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.categories

import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Category
import dev.hootkit.core.CategoryKind
import dev.hootkit.core.Subcategory
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategorySelectionSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteCategoryDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ArrowLeft02
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeApi::class
)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    categoriesViewModel: CategoriesViewModel = hiltViewModel(),
    blurEffects: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by categoriesViewModel.uiState.collectAsStateWithLifecycle()
    val categories = state.categories.values.filter { category ->
        state.searchQuery.isBlank() || category.displayName.contains(state.searchQuery, true) ||
            category.subcategories.any { it.displayName.contains(state.searchQuery, true) }
    }
    val searchQuery = state.searchQuery
    var searchInput by remember { mutableStateOf(TextFieldValue(text = searchQuery)) }
    val showAddEditDialog = state.showAddEditDialog
    val editingCategory = state.editingCategory
    val snackbarMessage = state.errorMessage
    val subcategories = state.categories.mapValues { it.value.subcategories }
    val showSubcategoryDialog = state.showSubcategoryDialog
    val editingSubcategory = state.editingSubcategory
    val showDeleteConfirmation = state.showDeleteConfirmation
    val categoryToDelete = state.categoryToDelete
    val hasTransactions = state.hasTransactions
    val showMigrationSheet = state.showMigrationSheet

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    var showFloatingLabel by remember { mutableStateOf(true) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    val searchLabel = stringResource(R.string.search_fruits)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }.collect { firstVisibleItem ->
            // Show the label only when the list is scrolled to the top
            showFloatingLabel = firstVisibleItem == 0
        }
    }
    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(operationFailed)
                categoriesViewModel.clearSnackbarMessage()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.categories),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = { NavigationContent(onNavigateBack) },
                actionContent = {
                    ActionContent(
                        showMenu = showFilterMenu,
                        onActionClick = { showFilterMenu = true },
                        onDismissMenu = { showFilterMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all)) },
                            onClick = {
                                selectedFilter = "All"
                                showFilterMenu = false
                            },
                        )
                        HorizontalDivider(
                            thickness = 1.5.dp,
                            color = MaterialTheme.colorScheme.surface
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.expense)) },
                            onClick = {
                                selectedFilter = "Expense"
                                showFilterMenu = false
                            },
                        )
                        HorizontalDivider(
                            thickness = 1.5.dp,
                            color = MaterialTheme.colorScheme.surface
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.income)) },
                            onClick = {
                                selectedFilter = "Income"
                                showFilterMenu = false
                            },
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            val fabContainerColor =  MaterialTheme.colorScheme.primaryContainer
            val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ExtendedFloatingActionButton(
                onClick = { categoriesViewModel.showAddDialog() },
                expanded = showFloatingLabel,
                icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_category)) },
                text = { Text(text = stringResource(R.string.add_category)) },
                shape = if (showFloatingLabel) MaterialTheme.shapes.extraLargeIncreased else MaterialTheme.shapes.large,
                modifier = Modifier
                    .then(
                        if (blurEffects) Modifier
                            .clip(if (showFloatingLabel) MaterialTheme.shapes.extraLargeIncreased else MaterialTheme.shapes.large)
                            .hazeEffect(
                            state = hazeState,
                            block = fun HazeEffectScope.() {
                                inputScale = HazeInputScale.Auto
                                style = HazeDefaults.style(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(fabContainerColor),
                                    blurRadius = 20.dp,
                                    noiseFactor = -1f,
                                )
                                blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                            }
                        ) else Modifier
                    ),
                containerColor = fabContainerColor,
                contentColor = fabContentColor

            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top =Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                SearchBarBox(
                    searchQuery = searchInput,
                    onSearchQueryChange = {
                        searchInput = it
                        categoriesViewModel.updateSearchQuery(it.text)
                    },
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
                    leadingIcon = { },
                    trailingIcon = if (searchInput.text.isNotEmpty()) {
                        {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                searchInput = TextFieldValue("")
                                categoriesViewModel.updateSearchQuery("")
                            }) {
                                Icon(
                                    Iconax.CloseCircle,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    } else {
                        {}
                    }
                )
            }

            // Recalculate grouped categories for the displayed list
            val dispExpenseCategories = categories.filter { it.kind != CategoryKind.INCOME }
            val dispIncomeCategories = categories.filter { it.kind == CategoryKind.INCOME }


            when (selectedFilter) {
                "All" -> {
                    // Show all categories without headers
                    items(
                        items = categories, // Use the latest categories state directly
                        key = { "all-${it.id}" }) { category ->
                        AnimatedContent(
                            targetState = category,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                        scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = tween(220, delayMillis = 90)
                                        ) togetherWith fadeOut(animationSpec = tween(90))
                            },
                            label = "CategoryItemAnimation"
                        ) { animatedCategory ->
                            val categorySubcategories = subcategories[animatedCategory.id] ?: emptyList()
                            val displayedSubs = if (searchQuery.isNotBlank()) {
                                val catMatches = animatedCategory.displayName.contains(searchQuery, ignoreCase = true)
                                if (catMatches) categorySubcategories
                                else categorySubcategories.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                            } else {
                                categorySubcategories
                            }
                            SwipeableCategoryItem(
                                category = animatedCategory,
                                subcategories = displayedSubs,
                                onEdit = { categoriesViewModel.showEditDialog(animatedCategory) },
                                onDelete = { categoriesViewModel.deleteCategory(animatedCategory) },
                                onAddSubcategory = {
                                    categoriesViewModel.showAddSubcategoryDialog(animatedCategory.id)
                                },
                                onEditSubcategory = { categoriesViewModel.showEditSubcategoryDialog(it) },
                            )
                        }
                    }
                }

                "Expense" -> {
                    // Expense Categories Section
                    if (dispExpenseCategories.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.expense_categories),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        items(
                            items = dispExpenseCategories,
                            key = { "expense-${it.id}" }) { category ->
                            val categorySubcategories = subcategories[category.id] ?: emptyList()
                            val displayedSubs = if (searchQuery.isNotBlank()) {
                                val catMatches = category.displayName.contains(searchQuery, ignoreCase = true)
                                if (catMatches) categorySubcategories
                                else categorySubcategories.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                            } else {
                                categorySubcategories
                            }
                            SwipeableCategoryItem(
                                category = category,
                                subcategories = displayedSubs,
                                onEdit = { categoriesViewModel.showEditDialog(category) },
                                onDelete = { categoriesViewModel.deleteCategory(category) },
                                onAddSubcategory = {
                                    categoriesViewModel.showAddSubcategoryDialog(category.id)
                                },
                                onEditSubcategory = {
                                    categoriesViewModel.showEditSubcategoryDialog(it)
                                },
                            )
                        }
                    }
                }

                "Income" -> {
                    // Income Categories Section
                    if (dispIncomeCategories.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.income_categories),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        items(
                            items = dispIncomeCategories,
                            key = { "income-${it.id}" }) { category ->
                            val categorySubcategories = subcategories[category.id] ?: emptyList()
                            val displayedSubs = if (searchQuery.isNotBlank()) {
                                val catMatches = category.displayName.contains(searchQuery, ignoreCase = true)
                                if (catMatches) categorySubcategories
                                else categorySubcategories.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                            } else {
                                categorySubcategories
                            }
                            SwipeableCategoryItem(
                                category = category,
                                subcategories = displayedSubs,
                                onEdit = { categoriesViewModel.showEditDialog(category) },
                                onDelete = { categoriesViewModel.deleteCategory(category) },
                                onAddSubcategory = {
                                    categoriesViewModel.showAddSubcategoryDialog(category.id)
                                },
                                onEditSubcategory = {
                                    categoriesViewModel.showEditSubcategoryDialog(it)
                                },
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    // Add/Edit Category Bottom Sheet
    if (showAddEditDialog) {
        HootbookModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { categoriesViewModel.hideDialog() },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            EditCategorySheet(
                category = editingCategory,
                onDismiss = { categoriesViewModel.hideDialog() },
                saving = state.saving,
                onSave = { name, color, iconName, isIncome ->
                    categoriesViewModel.saveCategory(name, color, iconName, isIncome)
                },
                onReset = if (editingCategory?.builtinKey != null) {
                    { categoryId -> categoriesViewModel.resetCategory(categoryId) }
                } else null,
                onDelete = if (editingCategory != null && editingCategory!!.builtinKey == null) {
                    { categoriesViewModel.deleteCategory(editingCategory!!) }
                } else null
            )
        }
    }

    // Edit Subcategory Bottom Sheet
    if (showSubcategoryDialog) {
        val currentCategory =
            editingSubcategory?.categoryId?.let { catId -> categories.find { it.id == catId } }

        HootbookModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { categoriesViewModel.hideSubcategoryDialog() },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            EditSubcategorySheet(
                subcategory = editingSubcategory,
                saving = state.saving,
                categoryColor = currentCategory?.color ?: "#757575",
                categoryIconResId = currentCategory?.let { cat ->
                    if (cat.icon.isNotEmpty()) {
                        io.github.hootkit_dev.hootbook.utils.IconResolutionUtils.nameToResId(context, cat.icon)
                            .takeIf { it != 0 } ?: R.drawable.type_food_dining
                    } else {
                        R.drawable.type_food_dining
                    }
                } ?: R.drawable.type_food_dining,
                onDismiss = { categoriesViewModel.hideSubcategoryDialog() },
                onSave = { name, iconResId, iconName, color ->
                    categoriesViewModel.saveSubcategory(name, iconResId, iconName, color)
                },
                onReset = if (editingSubcategory?.builtinKey != null) {
                    { subcategoryId -> categoriesViewModel.resetSubcategory(subcategoryId) }
                } else null,
                onDelete = if (editingSubcategory != null) {
                    { subcategoryId ->
                        editingSubcategory?.let { categoriesViewModel.deleteSubcategory(it) }

                    }
                } else null
            )
        }
    }

    // Deletion Confirmation Dialog
    if (showDeleteConfirmation && categoryToDelete != null) {
        val categoryName = categoryToDelete?.displayName ?: "this category"
        DeleteCategoryDialog(
            hasTransactions = hasTransactions,
            categoryName = categoryName,
            onMoveOthers = { categoriesViewModel.showMigrationSheet() },
            onMoveDefault = { categoriesViewModel.confirmDelete(moveToMiscellaneous = true) },
            onDismiss = { categoriesViewModel.dismissDeleteConfirmation() },
            onDelete = { categoriesViewModel.confirmDelete() },
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Category Migration Bottom Sheet
    if (showMigrationSheet && categoryToDelete != null) {
        HootbookModalBottomSheet(
            onDismissRequest = { categoriesViewModel.hideMigrationSheet() },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Box(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                CategorySelectionSheet(
                    categories = state.categories.values.filter { it.id != categoryToDelete?.id && it.kind == categoryToDelete?.kind },
                    subcategoriesMap = subcategories,
                    onSelectionComplete = { newCategory, newSubcategory ->
                        categoriesViewModel.confirmMigrationToCategory(newCategory, newSubcategory)
                    },
                    onDismiss = { categoriesViewModel.hideMigrationSheet() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCategoryItem(
        category: Category,
        subcategories: List<Subcategory>,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onAddSubcategory: () -> Unit,
        onEditSubcategory: (Subcategory) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {SwipeToDismissBoxValue.EndToStart -> {
                if (category.builtinKey == null) {
                    onDelete()
                }
                false // Don't dismiss until confirmed
            }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { if (category.builtinKey == null) {
            val color by
            animateColorAsState(
                when (dismissState.dismissDirection) {SwipeToDismissBoxValue.EndToStart ->
                    MaterialTheme.colorScheme.error
                    else -> Color.Transparent },
                label = "background color"
            )
            Box(modifier = Modifier
                .fillMaxSize()
                .background(
                    color = color,
                    shape = MaterialTheme.shapes.large
                )
                .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Iconax.Bag,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        } },
        content = {
            CategoryItem(
                category = category,
                subcategories = subcategories,
                onClick = onEdit,
                onAddSubcategory = onAddSubcategory,
                onEditSubcategory = onEditSubcategory,
            )
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = category.builtinKey == null
    )
}



/** The top bar's back button: a standard icon button, as Material 3 places navigation icons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationContent(onNavigateBack: () -> Unit) {
    TooltipIconButton(icon = Iconax.ArrowLeft02, label = stringResource(R.string.cd_navigate_back), onClick = onNavigateBack)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionContent(
    showMenu: Boolean,
    onActionClick: () -> Unit,
    onDismissMenu: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // The overflow button anchors its menu; a standard icon button, one touch target
    Box {
        TooltipIconButton(icon = Icons.Rounded.MoreHoriz, label = stringResource(R.string.more_options), onClick = onActionClick)

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
            shape = MaterialTheme.shapes.large,
            content = content
        )
    }
}
