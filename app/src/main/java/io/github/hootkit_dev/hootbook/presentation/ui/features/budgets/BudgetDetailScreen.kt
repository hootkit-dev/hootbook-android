package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.budgetProgressFraction

import io.github.hootkit_dev.hootbook.utils.DateFormats

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.ui.components.BudgetCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DashedLine
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.CategoriesViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SharedTransitionScope.BudgetDetailScreen(
    budgetId: String,
    startDate: String? = null,
    endDate: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (String) -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null,
    blurEffects: Boolean
) {
    val uiState by budgetViewModel.uiState.collectAsStateWithLifecycle()
    val editBudgetState by budgetViewModel.editBudgetState.collectAsStateWithLifecycle()
    val categories = uiState.categories.values.toList()
    val subcategories = emptyMap<String, List<Subcategory>>()


    var showEditSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load budget data
    LaunchedEffect(budgetId, startDate, endDate) {
        budgetViewModel.selectBudget(budgetId, startDate, endDate)
    }

    // Clean up when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            budgetViewModel.clearSelectedBudget()
        }
    }

    val budgetWithSpending = uiState.selectedBudget

    if (showEditSheet) {
        HootbookModalBottomSheet(
            onDismissRequest = {
                showEditSheet = false
                budgetViewModel.clearEditState()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            EditBudgetSheet(
                budgetState = editBudgetState,
                saving = uiState.saving, error = uiState.error,
                categories = categories,
                subcategoriesMap = subcategories,
                allAccounts = uiState.accounts.values.toList(),
                onAmountChange = budgetViewModel::updateBudgetAmount,
                onNameChange = budgetViewModel::updateBudgetName,
                onStartDateChange = budgetViewModel::updateStartDate,
                onEndDateChange = budgetViewModel::updateEndDate,
                onPeriodTypeChange = budgetViewModel::updatePeriodType,
                onBudgetKindChange = budgetViewModel::updateBudgetType,
                onAccountIdsChange = budgetViewModel::updateAccountIds,
                onColorChange = budgetViewModel::updateColor,
                onAddCategoryLimit = budgetViewModel::addCategoryLimit,
                onRemoveCategoryLimit = budgetViewModel::removeCategoryLimit,
                onSave = {
                    budgetViewModel.saveBudget(
                        onSuccess = {
                            showEditSheet = false
                            budgetViewModel.clearEditState()
                        },
                        onError = { /* TODO: Show error */ }
                    )
                },
                onDelete = {
                    val currentId = budgetWithSpending?.id
                    if (currentId != null) {
                        budgetViewModel.deleteBudget(
                            budgetId = currentId,
                            onSuccess = {
                                showEditSheet = false
                                budgetViewModel.clearEditState()
                                onNavigateBack()
                            },
                            onError = { /* TODO: Show error */ }
                        )
                    }
                },
                onDismiss = {
                    showEditSheet = false
                    budgetViewModel.clearEditState()
                },
                blurEffects = blurEffects
            )
        }
    }

    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = budgetWithSpending?.let { budgetDisplayName(it.name, it.period) } ?: stringResource(R.string.budget_details),
                hazeState = hazeState,
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    NavigationContent { onNavigateBack() }
                },
                actionContent = {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        IconButton(
                            onClick = {
                                budgetWithSpending?.let {
                                    budgetViewModel.initEditBudget(it)
                                    showEditSheet = true
                                }
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Iconax.Edit2,
                                contentDescription = stringResource(R.string.edit_budget),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            if (budgetWithSpending == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingCircle()
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + Spacing.md,
                        bottom = 100.dp
                    )
                ) {
                    // Budget Summary Card
                    item {
                        Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                            BudgetCard(
                                onHistoryClick = onNavigateToHistory,
                                budgetWithSpending = budgetWithSpending,
                                animatedVisibilityScope = animatedContentScope,
                                sharedElementKey = sharedElementKey
                            )
                        }

                        // Budget Period Text
                        val startDate = budgetWithSpending.current.start
                        val endDate = budgetWithSpending.current.end
                        val periodText = remember(startDate, endDate) {
                            val periodFormatter = DateFormats.monthDayFormatter()
                            if (startDate.year == endDate.year) {
                                "${startDate.coreDateText(periodFormatter)} - ${endDate.coreDateText(periodFormatter)}, ${startDate.year}"
                            } else {
                                val fullFormatter = DateFormats.fullDateFormatter()
                                "${startDate.coreDateText(fullFormatter)} - ${endDate.coreDateText(fullFormatter)}"
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            DashedLine(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )

                            Text(
                                text = periodText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            DashedLine(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.md))
                    }

                    if (budgetWithSpending.current.byCategory.isNotEmpty()) {
                        item {
                            BudgetCategoryPieChart(
                                categories = budgetWithSpending.current.byCategory,
                                lookup = uiState.categories,
                                currency = budgetWithSpending.amount.currency,
                                totalSpent = budgetWithSpending.current.spent,
                                selectedCategoryId = uiState.selectedCategoryId,
                                onCategoryClick = budgetViewModel::selectCategoryFilter,
                                modifier = Modifier.padding(end = Spacing.sm)
                            )
                            Spacer(modifier = Modifier.height(Spacing.xl))
                        }
                    }

                    item(key = "budget_transactions_header") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            SectionHeader(title = stringResource(R.string.core_budgets_transactions_title))
                            if (budgetWithSpending.current.byCategory.isNotEmpty()) {
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    androidx.compose.material3.FilterChip(
                                        selected = uiState.selectedCategoryId == null,
                                        onClick = { budgetViewModel.selectCategoryFilter(null) },
                                        label = { Text(stringResource(R.string.core_budgets_transactions_all_categories)) },
                                    )
                                    budgetWithSpending.current.byCategory.forEach { catSpend ->
                                        val catName = uiState.categories[catSpend.categoryId]?.displayName ?: catSpend.categoryId
                                        androidx.compose.material3.FilterChip(
                                            selected = uiState.selectedCategoryId == catSpend.categoryId,
                                            onClick = { budgetViewModel.selectCategoryFilter(catSpend.categoryId) },
                                            label = { Text(catName) },
                                        )
                                    }
                                }
                            }
                            if (uiState.transactionsPage?.complete == false) {
                                Text(
                                    text = stringResource(R.string.core_budgets_transactions_incomplete),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (uiState.transactionsPage?.items.isNullOrEmpty()) {
                                Text(
                                    text = stringResource(R.string.core_budgets_transactions_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Spacing.sm),
                                )
                            }
                        }
                    }

                    val txItems = uiState.transactionsPage?.items.orEmpty()
                    val budgetCurrency = budgetWithSpending.amount.currency
                    itemsIndexed(txItems, key = { _, item -> "btx_${item.transaction.id}" }) { index, item ->
                        val tx = item.transaction
                        val cat = tx.categoryId?.let { uiState.categories[it] }
                        val subcat = cat?.subcategories?.firstOrNull { it.id == tx.subcategoryId }
                        val acc = uiState.accounts[tx.accountId]
                        val txTags = remember(tx.tagIds, uiState.tags) {
                            tx.tagIds.mapNotNull(uiState.tags::get)
                        }
                        val isForeignOrAdjusted =
                            !tx.currency.equals(budgetCurrency, ignoreCase = true) ||
                                item.amount.compareTo(tx.amount) != 0
                        Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                            TransactionItem(
                                transaction = tx,
                                categoryEntity = cat,
                                subcategoryEntity = subcat,
                                accountIconName = acc?.icon,
                                accountColorHex = acc?.color,
                                tags = txTags,
                                shape = ListItemPosition.from(index, txItems.size).toShape(),
                                amountOverride =
                                    io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation.formatCurrency(
                                        item.amount,
                                        budgetCurrency
                                    ),
                                secondaryAmountText =
                                    if (isForeignOrAdjusted)
                                        io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation.formatCurrency(
                                            tx.amount,
                                            tx.currency
                                        )
                                    else null,
                                onClick = { onTransactionClick(tx.id) },
                                rateLoading = false,
                            )
                        }
                    }
                }
            }
            uiState.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(paddingValues)) }
        }
    }
}
