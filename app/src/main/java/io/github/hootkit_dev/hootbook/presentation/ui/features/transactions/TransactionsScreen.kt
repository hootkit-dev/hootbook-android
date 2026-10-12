@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import androidx.compose.material3.ButtonDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ImportArrow01
import androidx.compose.animation.core.tween
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.BatchEditTransactionsBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencySelectionBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateRangePickerDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteMultipleTransactionsDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionTotalsCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import dev.hootkit.core.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Folder2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Menu
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Search
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class
)
@Composable
fun TransactionsScreen(
    // Export is the list's own action, in the top bar
    initialFilter: dev.hootkit.core.TransactionFilter? = null,
    initialCategory: String? = null,
    initialAccount: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    initialType: String? = null,
    focusSearch: Boolean = false,
    transactionsViewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onTransactionClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    animatedContentScope: AnimatedVisibilityScope? = null,
    blurEffects: Boolean,
    // Set on wide windows: a tap shows the transaction in the detail pane beside the list
    // instead of the summary sheet
    onTransactionSelected: ((String) -> Unit)? = null
) {
    var showExport by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by transactionsViewModel.uiState.collectAsStateWithLifecycle()
    if (showExport) ExportTransactionsDialog(filter = uiState.filter, total = uiState.total, onDismiss = { showExport = false })
    val searchQuery = uiState.filter.text.orEmpty()
    val selectedPeriod = uiState.period
    val categoryFilter = uiState.filter.categoryIds
    val transactionTypeFilter = uiState.filter.kinds
    val categoriesMap = uiState.categories
    val allSubcategoriesByCategoryId = remember(categoriesMap) { categoriesMap.mapValues { it.value.subcategories } }
    val availableCurrencies = uiState.currencies
    val selectedCurrency = uiState.filter.currency.orEmpty()
    val baseCurrency = uiState.summary?.currency.orEmpty()
    val sortOption = SortOption.entries.first { it.core == uiState.filter.sort }
    val customDateRange = uiState.customDateRange
    val selectionMode = uiState.selectionMode
    val selectedTransactionIds = uiState.selectedIds
    val groupedTransactions = remember(uiState.transactions) { groupTransactionsByDate(uiState.transactions) }
    val subcategoriesById = remember(uiState.categories) {
        val map = HashMap<String, dev.hootkit.core.Subcategory>()
        uiState.categories.values.forEach { cat ->
            cat.subcategories.forEach { sub -> map[sub.id] = sub }
        }
        map
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(operationFailed); transactionsViewModel.clearError() }
    }
    LaunchedEffect(uiState.undoToken) {
        uiState.undoToken?.let { token ->
            val result = snackbarHostState.showSnackbar(context.getString(R.string.transaction_deleted), context.getString(R.string.undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) transactionsViewModel.undoDelete(token)
            else transactionsViewModel.clearUndo()
        }
    }
    val scope = rememberCoroutineScope()
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showBatchEditSheet by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }


    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var searchTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        )
    }

    // Sync with external updates (like initial filters)
    LaunchedEffect(searchQuery) {
        if (searchQuery != searchTextFieldValue.text) {
            searchTextFieldValue = searchTextFieldValue.copy(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        }
    }

    // Calculate active filter count for filters
    val activeFilterCount = listOf(
        transactionTypeFilter.isNotEmpty(),
        categoryFilter.isNotEmpty()
    ).count { it }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(listState, uiState.transactions.size, uiState.total, uiState.loadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { index ->
                if (index >= listState.layoutInfo.totalItemsCount - 6) transactionsViewModel.loadMore()
            }
    }
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 48.dp.roundToPx() }
    val isCollapsed by remember(collapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    // Cache expensive operations
    val timePeriods = remember { TimePeriod.entries }
    val customRangeLabel = remember(customDateRange) {
        DateRangeUtils.formatDateRange(customDateRange)
    }

    // Apply initial filters only once when screen is first created
    LaunchedEffect(Unit) {
        if (initialFilter != null) transactionsViewModel.initialize(initialFilter)
        else transactionsViewModel.applyInitialFilters(
            initialCategory,
            initialMerchant,
            initialPeriod,
            initialCurrency,
            initialType, initialAccount
        )
        // Data preparation is dispatched off-main; do not add an artificial half-second wait.

    }

    // Focus search field if requested
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // Handle back button in selection mode
    BackHandler(enabled = selectionMode) {
        transactionsViewModel.toggleSelectionMode()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehaviorLarge.nestedScrollConnection)
,
        topBar = {
            CustomTitleTopAppBar(
                title = if (selectionMode) stringResource(R.string.items_selected_format, selectedTransactionIds.size) else stringResource(R.string.transactions),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                hazeState = hazeState,
                hasBackButton = selectionMode,
                navigationContent = {
                    if (selectionMode) {
                        NavigationContent {
                            transactionsViewModel.toggleSelectionMode()
                        }
                    }
                },
                actionContent = {
                    if (!selectionMode) {
                        TooltipIconButton(
                            icon = Iconax.ImportArrow01,
                            label = stringResource(R.string.export_transactions),
                            onClick = { showExport = true }
                        )
                    }
                    BlurredAnimatedVisibility(selectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Select All/Deselect All toggle button
                            val allSelected =
                                selectedTransactionIds.size.toUInt() == uiState.total && uiState.transactions.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (allSelected) {
                                        transactionsViewModel.clearSelection()
                                    } else {
                                        transactionsViewModel.selectAllTransactions()
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all)
                                )
                            }
                            // Delete button
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = selectedTransactionIds.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Iconax.Bag,
                                    contentDescription = stringResource(R.string.delete_selected)
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            ) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
            // Search Bar with Sort Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBarBox(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .then(Modifier),
                    searchQuery = searchTextFieldValue,
                    onSearchQueryChange = {
                        searchTextFieldValue = it
                        transactionsViewModel.updateSearchQuery(it.text)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Iconax.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    },
                    trailingIcon = {
                        Row{
                            BlurredAnimatedVisibility(searchTextFieldValue.text.isNotEmpty()) {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                    searchTextFieldValue = TextFieldValue("")
                                    transactionsViewModel.updateSearchQuery("")
                                }) {
                                    Icon(
                                        imageVector = Iconax.CloseCircle,
                                        contentDescription = stringResource(R.string.clear_search),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }
                            }
                            // More options button
                            Box {
                                IconButton(
                                    shapes = IconButtonDefaults.shapes(),
                                    onClick = { showMainMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = stringResource(R.string.more_options),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMainMenu,
                                    onDismissRequest = { showMainMenu = false },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.filters)) },
                                        onClick = { showFilterSheet = true; showMainMenu = false },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Iconax.Folder2,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                    HorizontalDivider(
                                        thickness = 1.5.dp,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort)) },
                                        onClick = { showSortMenu = true; showMainMenu = false },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Iconax.Menu,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    SortOption.entries.forEachIndexed { index, option ->
                                        val isFirstItem = index == 0
                                        val isLastItem = index == SortOption.entries.lastIndex
                                        val isMiddleItem = !isFirstItem && !isLastItem
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = sortOption == option,
                                                        onClick = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(stringResource(option.labelRes))
                                                }
                                            },
                                            onClick = {
                                                transactionsViewModel.selectSortOption(option)
                                                showSortMenu = false
                                            }
                                        )
                                        // Add a Spacer for middle items
                                        if (isMiddleItem || (isFirstItem && SortOption.entries.size > 2) ) {
                                            HorizontalDivider(
                                                thickness = 1.5.dp,
                                                color = MaterialTheme.colorScheme.surface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = if (categoryFilter.isNotEmpty()) {
                                stringResource(R.string.search_in_categories_format, categoryFilter.map { uiState.categories[it]?.displayName.orEmpty() }.joinToString("、"))
                            } else stringResource(R.string.search_transactions),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        )
                    }
                )
            }

            // Period Filter Chips - hide on scroll down
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Period filter chips
                        items(timePeriods) { period ->
                            FilterChip(
                                // Only show CUSTOM as selected if both period is CUSTOM AND dates are set
                                selected = if (period == TimePeriod.CUSTOM) {
                                    selectedPeriod == period && customDateRange != null
                                } else {
                                    selectedPeriod == period
                                },
                                onClick = {
                                    if (period == TimePeriod.CUSTOM) {
                                        showDateRangePicker = true
                                        // Don't change selectedPeriod until user confirms dates
                                    } else {
                                        transactionsViewModel.selectPeriod(period)
                                    }
                                },
                                label = {
                                    Text(
                                        if (period == TimePeriod.CUSTOM && customRangeLabel != null) {
                                            customRangeLabel
                                        } else {
                                            stringResource(period.labelRes)
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderWidth = 0.dp,
                                    selected = if (period == TimePeriod.CUSTOM) {
                                        selectedPeriod == period && customDateRange != null
                                    } else {
                                        selectedPeriod == period
                                    },
                                    enabled = true
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
            }

            // Transaction List
            when {
                uiState.isLoading && uiState.transactions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimensions.Padding.content),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingCircle()
                    }
                }
                uiState.transactions.isEmpty() -> {
                    EmptyTransactionsState(
                        searchQuery = searchQuery,
                        selectedPeriod = selectedPeriod
                    )
                }
                else -> {
                    // Benchmarks time the tab tap to this trace section: the first frame
                    // that draws the list, i.e. with rows.
                    val firstDrawTraced = remember { BooleanArray(1) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .drawWithContent {
                                drawContent()
                                if (!firstDrawTraced[0]) {
                                    firstDrawTraced[0] = true
                                    androidx.core.os.trace("TransactionsList.firstDraw") {}
                                }
                            }
                            .weight(1f)
                            .padding(horizontal = Dimensions.Padding.content)
                            .padding(top = Spacing.sm)
                            .clip(RoundedCornerShape(20.dp)),
                        contentPadding = PaddingValues(
                            bottom = 150.dp
                        )

                    ) {
                        stickyItemHeader(key = "totals", contentType = "totals") {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                uiState.summary?.let { summary -> TransactionTotalsCard(
                                    income = summary.income,
                                    expenses = summary.expense,
                                    netBalance = summary.net,
                                    currency = baseCurrency,
                                    isLoading = uiState.isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = Spacing.sm)
                                )
                                if (!summary.complete) Text(stringResource(R.string.core_complete_incomplete))
                                }
                                }
                            }
                        }

                        // Iterate through date groups in order
                        groupedTransactions.forEachIndexed { sectionIndex, (dateGroup, transactions) ->
                            run {
                                // Date group header
                                stickyItemHeader(key = "date_${dateGroup.name}_$sectionIndex", contentType = "date_header") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        modifier = Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surface,
                                                        MaterialTheme.colorScheme.surface,
                                                        MaterialTheme.colorScheme.surface,
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .padding(
                                                top = Spacing.md,
                                                bottom = Spacing.md,
                                                start = Spacing.sm
                                            )

                                    ) {
                                        Spacer(
                                            modifier =
                                                Modifier
                                                    .height(16.dp)
                                                    .width(4.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.tertiary,
                                                        RoundedCornerShape(14.dp)
                                                    )

                                        )
                                        SectionHeader(title = stringResource(dateGroup.labelRes))
                                    }
                                }

                                // Transactions in this group
                                itemsIndexed(
                                    items = transactions,
                                    key = { _, it -> it.id },
                                    contentType = { _, _ -> "transaction" }
                                ) { index, transaction ->
                                    val position = ListItemPosition.from(index, transactions.size)
                                    val person = uiState.people[uiState.debts[transaction.debtId]?.personId]
                                    TransactionItem(
                                        linkedLoanPersonName = person?.name,
                                        linkedLoanPersonColor = person?.color,
                                        linkedLoanPersonAvatar = person?.avatar,
                                        convertedAmount = uiState.convertedAmounts[transaction.id],
                                        transaction = transaction,
                                        categoryEntity = uiState.categories[transaction.categoryId],
                                        subcategoryEntity = transaction.subcategoryId?.let { subcategoriesById[it] },
                                        accountIconName = uiState.accounts[transaction.accountId]?.icon,
                                        accountColorHex = uiState.accounts[transaction.accountId]?.color,
                                        showDate = dateGroup == DateGroup.EARLIER,
                                        shape = position.toShape(),
                                        onClick = {
                                            onTransactionSelected?.invoke(transaction.id)
                                                ?: onTransactionClick(transaction.id, "transaction_${transaction.id}")
                                        },
                                        isSelectionMode = selectionMode,
                                        isSelected = selectedTransactionIds.contains(transaction.id),
                                        onSelectionToggle = { transactionsViewModel.toggleTransactionSelection(transaction.id) },
                                        onLongClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            if (!selectionMode) {
                                                transactionsViewModel.toggleSelectionMode()
                                                transactionsViewModel.selectTransactionSet(setOf(transaction.id))
                                            } else {
                                                transactionsViewModel.toggleTransactionSelection(transaction.id)
                                            }
                                        },
                                        mainCurrency = uiState.displayCurrency
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            // Floating Pill Shape Button for Batch Edit
            AnimatedVisibility(
                visible = selectionMode && selectedTransactionIds.isNotEmpty(),
                enter = fadeIn(tween(durationMillis = MotionDurations.standard)) + slideInVertically(tween(durationMillis = MotionDurations.standard)) { it / 2 },
                exit = fadeOut(tween(durationMillis = MotionDurations.standard)) + slideOutVertically(tween(durationMillis = MotionDurations.standard)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .navigationBarsPadding()
                    .zIndex(10f)
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showBatchEditSheet = true
                    },
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .padding(horizontal = Spacing.md)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Iconax.Edit2,
                            contentDescription = stringResource(R.string.edit_selected_transactions),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.edit_selected_count_format, selectedTransactionIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Export Dialog

    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                transactionsViewModel.setCustomDateRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customDateRange?.first,
            initialEndDate = customDateRange?.second,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showCurrencySheet) {
        CurrencySelectionBottomSheet(
            selectedCurrency = selectedCurrency,
            availableCurrencies = availableCurrencies,
            onCurrencySelected = {
                transactionsViewModel.selectCurrency(it)
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        DeleteMultipleTransactionsDialog(
            busy = uiState.writing,
            onDelete = {
                transactionsViewModel.deleteSelectedTransactions()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false },
            selectedTransactionIds = selectedTransactionIds,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showFilterSheet) {
        TransactionsFilterBottomSheet(
            viewModel = transactionsViewModel,
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showBatchEditSheet) {
        BatchEditTransactionsBottomSheet(
            selectedIdsCount = selectedTransactionIds.size,
            categories = categoriesMap,
            subcategoriesMap = allSubcategoriesByCategoryId,
            onDismiss = { showBatchEditSheet = false },
            onApply = { newDate, newTime, newCategory, newSubcategory, newAmount, newNote ->
                transactionsViewModel.batchUpdateTransactions(
                    selectedIds = selectedTransactionIds,
                    newDate = newDate,
                    newTime = newTime,
                    newCategory = newCategory,
                    newSubcategory = newSubcategory,
                    newAmount = newAmount,
                    newNote = newNote
                )
            },
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }
}




@Composable
private fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        HootbookCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Iconax.ReceiptItem,
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = when {
                        searchQuery.isNotEmpty() -> stringResource(R.string.no_transactions_matching_format, searchQuery)
                        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.no_transactions_for_format, stringResource(selectedPeriod.labelRes).lowercase())
                        else -> stringResource(R.string.no_transactions_yet)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.transactions_appear_here),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.stickyItemHeader(
    key: Any? = null,
    contentType: Any? = null,
    content: @Composable LazyItemScope.(Int) -> Unit
) {
    stickyHeader(key = key, contentType = contentType, content = content)
}
