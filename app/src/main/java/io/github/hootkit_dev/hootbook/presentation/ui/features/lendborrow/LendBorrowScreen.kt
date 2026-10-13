@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteMultiplePersonsDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoanBalanceContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.SubtitleTag
import io.github.hootkit_dev.hootbook.presentation.ui.components.listSingleItemShape
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Balance
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LendBorrowScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    viewModel: LendBorrowViewModel = hiltViewModel(),
    animatedContentScope: AnimatedContentScope? = null,
    blurEffects: Boolean = LocalBlurEffects.current
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var searchInput by remember { mutableStateOf(TextFieldValue(text = uiState.searchQuery)) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != searchInput.text) {
            searchInput = searchInput.copy(text = uiState.searchQuery)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    var showFloatingLabel by remember { mutableStateOf(true) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }.collect { firstVisibleItem ->
            showFloatingLabel = firstVisibleItem == 0
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.toggleSelectionMode()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = if (uiState.isSelectionMode) {
                    stringResource(R.string.items_selected_format, uiState.selectedPersonIds.size)
                } else {
                    stringResource(R.string.lend_borrow_title)
                },
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = {
                    if (uiState.isSelectionMode) {
                        NavigationContent { viewModel.toggleSelectionMode() }
                    } else {
                        NavigationContent(onNavigateBack)
                    }
                },
                actionContent = {
                    BlurredAnimatedVisibility(uiState.isSelectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            val allSelected = uiState.selectedPersonIds.size == uiState.filteredPersons.size &&
                                    uiState.filteredPersons.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (allSelected) viewModel.clearSelection()
                                    else viewModel.selectAllPersons()
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = uiState.selectedPersonIds.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Iconax.Bag,
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            )
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                val fabContainerColor = MaterialTheme.colorScheme.primaryContainer
                val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showAddTransactionSheet(true) },
                    expanded = showFloatingLabel,
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_record)) },
                    text = { Text(text = stringResource(R.string.add_record)) },
                    shape = if (showFloatingLabel) MaterialTheme.shapes.extraLargeIncreased else MaterialTheme.shapes.large,
                    modifier = Modifier.then(
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
                                }
                            ) else Modifier
                    ),
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor
                )
            }
        }
    ) { paddingValues ->
        uiState.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(paddingValues)) }
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
            LazyColumn(
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
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                item {
                    uiState.summary?.let { summary -> DebtSummaryHeader(
                        summary = summary,
                            people = uiState.persons,
                        currency = uiState.baseCurrency,
                        animatedContentScope = animatedContentScope
                    ) }
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.md - 1.5.dp))
                    val filters = listOf(
                        LendBorrowFilter.ALL,
                        LendBorrowFilter.YOU_GET,
                        LendBorrowFilter.YOU_OWE,
                        LendBorrowFilter.OVERDUE
                    )
                    GenericTypeSwitcher(
                        selectedIndex = filters.indexOf(uiState.selectedFilter).coerceIn(0, filters.lastIndex),
                        onIndexChange = { index -> viewModel.onFilterSelected(filters[index]) },
                        options = filters.map { filter ->
                            when (filter) {
                                LendBorrowFilter.ALL -> stringResource(R.string.type_all)
                                LendBorrowFilter.YOU_GET -> stringResource(R.string.lend_borrow_filter_lent)
                                LendBorrowFilter.YOU_OWE -> stringResource(R.string.lend_borrow_filter_owed)
                                LendBorrowFilter.OVERDUE -> stringResource(R.string.overdue)
                                LendBorrowFilter.SETTLED -> stringResource(R.string.settled_tag)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.md - 1.5.dp))
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
                                imageVector = Iconax.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Box {
                                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showCategoryMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreHoriz,
                                            contentDescription = stringResource(R.string.category_filter),
                                            tint = if (uiState.selectedCategory != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showCategoryMenu,
                                        onDismissRequest = { showCategoryMenu = false },
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.category_all)) },
                                            onClick = {
                                                viewModel.onCategorySelected(null)
                                                showCategoryMenu = false
                                            }
                                        )
                                        HorizontalDivider(
                                            thickness = 1.5.dp,
                                            color = MaterialTheme.colorScheme.surface
                                        )
                                        PersonRelation.entries.filter { it != PersonRelation.UNKNOWN }.forEachIndexed { index, category ->
                                            DropdownMenuItem(
                                                text = { Text(categoryLabel(category)) },
                                                onClick = {
                                                    viewModel.onCategorySelected(category)
                                                    showCategoryMenu = false
                                                }
                                            )
                                            if (index < PersonRelation.entries.size - 1) {
                                                HorizontalDivider(
                                                    thickness = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.surface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.md - 1.5.dp))
                }

                if (uiState.filteredPersons.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.Padding.empty),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Iconax.Balance,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Text(
                                    text = stringResource(R.string.no_persons_yet),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = stringResource(R.string.no_persons_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(uiState.filteredPersons, key = { _, it -> it.id }) { index, person ->
                        val position = ListItemPosition.from(index, uiState.filteredPersons.size)
                        PersonListItemCard(
                            person = person,
                        attachmentStore = viewModel.attachmentStore,
                            onClick = { onNavigateToPersonDetail(person.id) },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                if (!uiState.isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                    viewModel.selectPersonSet(setOf(person.id))
                                } else {
                                    viewModel.togglePersonSelection(person.id)
                                }
                            },
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedPersonIds.contains(person.id),
                            onSelectionToggle = { viewModel.togglePersonSelection(person.id) },
                            currency = uiState.baseCurrency,
                            animatedContentScope = animatedContentScope,
                            sharedElementKey = "person_avatar_${person.id}",
                            shape = position.toShape()
                        )
                    }
                }
            }
        }

        if (uiState.showAddTransactionSheet) {
            AddEditLendBorrowTransactionSheet(
                personsList = uiState.persons,
                initialPerson = uiState.selectedPersonForTx,
                accounts = uiState.accounts.values.toList(),
                attachmentStore = viewModel.attachmentStore,
                onDismiss = { viewModel.showAddTransactionSheet(false) },
                onAddPerson = viewModel::createPerson,
                mainCurrency = uiState.baseCurrency,
                saving = uiState.saving, error = uiState.error,
                onSave = viewModel::addTransaction
            )
        }

        if (showDeleteConfirmation) {
            DeleteMultiplePersonsDialog(
                onDelete = {
                    val count = uiState.selectedPersonIds.size
                    viewModel.deleteSelectedPersons()
                    showDeleteConfirmation = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.persons_deleted_format, count),
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onDismiss = { showDeleteConfirmation = false },
                selectedPersonIds = uiState.selectedPersonIds,
                blurEffects = blurEffects,
                hazeState = hazeState
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.DebtSummaryHeader(
    summary: DebtSummary,
    people: List<Person>,
    currency: String,
    animatedContentScope: AnimatedContentScope? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.card)
        ) {
            LoanBalanceContent(
                summary = summary,
                people = people,
                currency = currency,
                animatedContentScope = animatedContentScope
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PersonListItemCard(
    person: Person,
    attachmentStore: CoreAttachmentStore,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    currency: String,
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null,
    shape: CornerBasedShape = listSingleItemShape
) {
    val isDark = isAppInDarkTheme
    val colorInt = try {
        android.graphics.Color.parseColor(person.color)
    } catch (e: Exception) {
        android.graphics.Color.parseColor("#4CAF50")
    }

    val isGet = person.totals.net > BigDecimal.ZERO
    val isOwe = person.totals.net < BigDecimal.ZERO
    val statusText = when {
        isGet -> stringResource(R.string.gets_amount, CurrencyFormatter.formatCurrency(person.totals.net, currency))
        isOwe -> stringResource(R.string.owes_amount, CurrencyFormatter.formatCurrency(person.totals.net.abs(), currency))
        else -> stringResource(R.string.settled_tag)
    }

    val statusColor = when {
        isGet -> if (!isDark) income_light else income_dark
        isOwe -> if (!isDark) expense_light else expense_dark
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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

    ListItem(
        headline = {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        },
        supporting = {
            if (!person.totals.complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.basicMarquee()
            ) {
                SubtitleTag(
                    text = categoryLabel(person.relation ?: PersonRelation.OTHER),
                    color = categoryColor(person.relation ?: PersonRelation.OTHER)
                )
                if (person.totals.overdue) {
                    SubtitleTag(
                        text = stringResource(R.string.overdue_tag),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leading = {
            BlurredAnimatedVisibility(isSelectionMode) {
                HootbookCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle?.invoke() },
                    modifier = Modifier.size(40.dp)
                )
            }
            BlurredAnimatedVisibility(!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .then(sharedModifier)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt)),
                    contentAlignment = Alignment.Center
                ) {
                    if (person.avatar != null) {
                        AsyncImage(
                            model = person.avatar?.let(attachmentStore::resolve),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = person.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        trailing = {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        },
        onClick = {
            if (isSelectionMode) {
                onSelectionToggle?.invoke()
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        shape = shape,
        listColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        padding = PaddingValues(0.dp)
    )
}

@Composable
fun categoryLabel(category: PersonRelation): String = when (category) {
    PersonRelation.FRIEND -> stringResource(R.string.category_friends)
    PersonRelation.FAMILY -> stringResource(R.string.category_family)
    PersonRelation.COLLEAGUE -> stringResource(R.string.category_colleagues)
    PersonRelation.OTHER, PersonRelation.UNKNOWN -> stringResource(R.string.category_other)
}

@Composable
fun categoryColor(category: PersonRelation): Color = when (category) {
    PersonRelation.FRIEND -> Color(0xFF6D7CFF)
    PersonRelation.FAMILY -> Color(0xFFF06292)
    PersonRelation.COLLEAGUE -> Color(0xFF26A69A)
    PersonRelation.OTHER, PersonRelation.UNKNOWN -> Color(0xFF9E9E9E)
}
