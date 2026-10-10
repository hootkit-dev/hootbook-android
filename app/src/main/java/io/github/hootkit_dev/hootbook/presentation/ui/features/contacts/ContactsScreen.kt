@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.contacts

import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import io.github.hootkit_dev.hootbook.domain.model.PersonCategory
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteMultiplePersonsDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.AddEditPersonSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.categoryLabel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ContactsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (Long) -> Unit,
    viewModel: LendBorrowViewModel = hiltViewModel(),
    selectedPersonId: Long? = null,
    sharedElementKey: String? = null,
    animatedContentScope: AnimatedContentScope? = null,
    blurEffects: Boolean = LocalBlurEffects.current
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchInput by remember { mutableStateOf(TextFieldValue(text = uiState.searchQuery)) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != searchInput.text) {
            searchInput = searchInput.copy(text = uiState.searchQuery)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyGridState()
    val view = LocalView.current

    val isSelectionMode = uiState.isSelectionMode
    val selectedPersonIds = uiState.selectedPersonIds

    LaunchedEffect(uiState.filteredPersons, selectedPersonId) {
        if (selectedPersonId != null) {
            val index = uiState.filteredPersons.indexOfFirst { it.id == selectedPersonId }
            if (index >= 0) {
                lazyListState.scrollToItem(index + 2)
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.toggleSelectionMode()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = if (isSelectionMode) stringResource(R.string.items_selected_format, selectedPersonIds.size) else stringResource(R.string.contacts),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = {
                    if (isSelectionMode) {
                        NavigationContent { viewModel.toggleSelectionMode() }
                    } else {
                        NavigationContent(onNavigateBack)
                    }
                },
                actionContent = {
                    BlurredAnimatedVisibility(isSelectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            val allSelected = selectedPersonIds.size == uiState.filteredPersons.size && uiState.filteredPersons.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (allSelected) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAllPersons()
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = selectedPersonIds.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_selected),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                blurEffects = blurEffects
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                val fabContainerColor = MaterialTheme.colorScheme.primaryContainer
                val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showAddPersonSheet(true) },
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_person)) },
                    text = { Text(text = stringResource(R.string.add_person)) },
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    modifier = Modifier.then(
                        if (blurEffects) Modifier
                            .clip(MaterialTheme.shapes.extraLargeIncreased)
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
                                }
                            ) else Modifier
                    ),
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchBarBox(
                        searchQuery = searchInput,
                        onSearchQueryChange = {
                            searchInput = it
                            viewModel.onSearchQueryChanged(it.text)
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchInput.text.isNotEmpty()) {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                    searchInput = TextFieldValue("")
                                    viewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(
                                        imageVector = Iconax.CloseCircle,
                                        contentDescription = stringResource(R.string.clear_search)
                                    )
                                }
                            }
                        }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(Spacing.md - 1.5.dp))
                }

                itemsIndexed(uiState.filteredPersons, key = { _, it -> it.id }) { index, person ->
                    DisplayProfileImageCard(
                        person = person,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedPersonIds.contains(person.id),
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.togglePersonSelection(person.id)
                            } else {
                                onNavigateToPersonDetail(person.id)
                            }
                        },
                        onSelectionToggle = { viewModel.togglePersonSelection(person.id) },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            if (!isSelectionMode) {
                                viewModel.toggleSelectionMode()
                                viewModel.togglePersonSelection(person.id)
                            } else {
                                viewModel.togglePersonSelection(person.id)
                            }
                        },
                        animatedContentScope = animatedContentScope,
                        sharedElementKey = if (isSelectionMode || (selectedPersonId != null && person.id != selectedPersonId)) {
                            null
                        } else {
                            "person_avatar_${person.id}"
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        DeleteMultiplePersonsDialog(
            onDelete = {
                viewModel.deleteSelectedPersons()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false },
            selectedPersonIds = selectedPersonIds,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (uiState.showAddPersonSheet) {
        AddEditPersonSheet(
            attachmentService = viewModel.attachmentService,
            onDismiss = { viewModel.showAddPersonSheet(false) },
            onSave = { name, phone, notes, color, avatar, category ->
                viewModel.addPerson(name, phone, notes, color, avatar, category)
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.DisplayProfileImageCard(
    person: LendBorrowPerson,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit = {},
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null,
) {
    val isDark = isAppInDarkTheme
    val colorInt = try {
        person.color.toColorInt()
    } catch (e: Exception) {
        "#4CAF50".toColorInt()
    }
    val backgroundColor = Color(colorInt)

    val isGet = person.netBalance > BigDecimal.ZERO
    val isOwe = person.netBalance < BigDecimal.ZERO

    val sharedModifier = if (animatedContentScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        )
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .then(sharedModifier)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        border = if (isSelectionMode && isSelected) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (person.avatar != null) {
                AsyncImage(
                    model = person.avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to backgroundColor.copy(alpha = 0.9f)
                        )
                    )
            )

            if (isSelectionMode) {
                BlurredAnimatedVisibility(true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        // Over the contact's photo: a surface disc keeps the box readable
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            HootbookCheckbox(
                                checked = isSelected,
                                onCheckedChange = { onSelectionToggle() }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = categoryLabel(person.category ?: PersonCategory.OTHER),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
