@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.hootkit.core.Category
import dev.hootkit.core.TransactionFilter
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.LocalWindowLayout
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CollapsibleFilterRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateRangePickerDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.TypeFilterIcon
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Chart2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Grid2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Menu
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Status
import io.github.hootkit_dev.hootbook.presentation.ui.icons.StatusUp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.DateRangeUtils
import java.math.BigDecimal

enum class ChartType(val icon: ImageVector, @StringRes val labelRes: Int) {
    LINE(Iconax.StatusUp, R.string.chart_type_line),
    BAR(Iconax.Chart2, R.string.chart_type_bar),
    HEATMAP(Iconax.Grid2, R.string.chart_type_heatmap),
}

enum class BreakdownType {
    PIE,
    LIST,
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun AnalyticsScreen(
    analyticsViewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToTransactions: (TransactionFilter) -> Unit = {},
    animatedContentScope: AnimatedContentScope? = null,
    blurEffects: Boolean,
) {
    androidx.compose.animation.SharedTransitionLayout {
        val uiState by analyticsViewModel.uiState.collectAsStateWithLifecycle()
        val selectedPeriod = uiState.period
        val transactionTypeFilter = uiState.types
        val selectedCurrency = uiState.selectedCurrency
        val availableCurrencies = uiState.availableCurrencies
        val customDateRange = uiState.customRange
        var showAdvancedFilters by remember { mutableStateOf(false) }
        var showDateRangePicker by remember { mutableStateOf(false) }

        // Chart Selection State
        var selectedChartType by remember { mutableStateOf(ChartType.LINE) }
        var showChartTypeSelector by remember { mutableStateOf(false) }
        val mixedTrend = trendTypes(transactionTypeFilter).size > 1
        val chartType = if (mixedTrend && selectedChartType == ChartType.HEATMAP) ChartType.LINE else selectedChartType
        var selectedBreakdownType by remember { mutableStateOf(BreakdownType.PIE) }

        // Calculate active filter count
        val activeFilterCount =
            if (transactionTypeFilter.contains(TransactionTypeFilter.ALL)) 0
            else transactionTypeFilter.size

        // Cache expensive operations
        val timePeriods = remember { TimePeriod.entries }
        val customRangeLabel =
            remember(customDateRange) { DateRangeUtils.formatDateRange(customDateRange) }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
        val lazyListState = rememberLazyListState()
        val hazeState = remember { HazeState() }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CustomTitleTopAppBar(
                    title = stringResource(R.string.analytics),
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehavior,
                    hazeState = hazeState,
                    hasBackButton = false,
                )
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {

                // Filters, summary and trend; then category breakdown. Side by
                // side
                // on wide windows (an unfolded foldable), one list otherwise.
                val overviewItems: LazyListScope.() -> Unit = {
                    if (uiState.error != null)
                        item {
                            TextButton(onClick = analyticsViewModel::refresh) {
                                Text(stringResource(R.string.core_analytics_query_failed))
                            }
                        }

                    // Filters (Period and Type)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            // Time Period Filter with leading FilterChip
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Filter chip toggles advanced filters & displays active count
                                item {
                                    FilterChip(
                                        selected = activeFilterCount > 0 || showAdvancedFilters,
                                        onClick = { showAdvancedFilters = !showAdvancedFilters },
                                        label = {
                                            Text(
                                                if (activeFilterCount > 0) stringResource(R.string.txn_filter_count, activeFilterCount)
                                                else stringResource(R.string.txn_filter)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.FilterList,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderWidth = 0.dp,
                                            selected = activeFilterCount > 0 || showAdvancedFilters,
                                            enabled = true
                                        ),
                                    )
                                }
                                items(timePeriods) { period ->
                                    FilterChip(
                                        selected =
                                            if (period == TimePeriod.CUSTOM) {
                                                selectedPeriod == period && customDateRange != null
                                            } else {
                                                selectedPeriod == period
                                            },
                                        onClick = {
                                            if (period == TimePeriod.CUSTOM) {
                                                showDateRangePicker = true
                                            } else {
                                                analyticsViewModel.selectPeriod(period)
                                            }
                                        },
                                        label = {
                                            Text(
                                                if (
                                                    period == TimePeriod.CUSTOM &&
                                                        customRangeLabel != null
                                                ) {
                                                    customRangeLabel
                                                } else {
                                                    stringResource(period.labelRes)
                                                }
                                            )
                                        },
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor =
                                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                                containerColor =
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                        .copy(0.7f),
                                                labelColor = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        border =
                                            FilterChipDefaults.filterChipBorder(
                                                borderWidth = 0.dp,
                                                selected =
                                                    if (period == TimePeriod.CUSTOM) {
                                                        selectedPeriod == period &&
                                                            customDateRange != null
                                                    } else {
                                                        selectedPeriod == period
                                                    },
                                                enabled = true,
                                            ),
                                    )
                                }
                            }

                            // Expandable Filter Row for Type
                            AnimatedVisibility(
                                visible = showAdvancedFilters,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    items(
                                        listOf(
                                            TransactionTypeFilter.ALL,
                                            TransactionTypeFilter.INCOME,
                                            TransactionTypeFilter.EXPENSE,
                                        )
                                    ) { typeFilter ->
                                        FilterChip(
                                            selected = transactionTypeFilter.contains(typeFilter),
                                            onClick = {
                                                analyticsViewModel.toggleTransactionTypeFilter(
                                                    typeFilter
                                                )
                                            },
                                            label = { Text(stringResource(typeFilter.labelRes)) },
                                            leadingIcon =
                                                if (transactionTypeFilter.contains(typeFilter)) {
                                                    { TypeFilterIcon(typeFilter) }
                                                } else null,
                                            colors =
                                                FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor =
                                                        MaterialTheme.colorScheme.tertiaryContainer,
                                                    selectedLabelColor =
                                                        MaterialTheme.colorScheme
                                                            .onTertiaryContainer,
                                                    containerColor =
                                                        MaterialTheme.colorScheme
                                                            .surfaceContainerLow
                                                            .copy(0.7f),
                                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                                ),
                                            border =
                                                FilterChipDefaults.filterChipBorder(
                                                    borderWidth = 0.dp,
                                                    selected =
                                                        transactionTypeFilter.contains(typeFilter),
                                                    enabled = true,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(Spacing.md)) }

                    // Currency Selector (if multiple available)
                    if (availableCurrencies.size > 1) {
                        item {
                            CurrencyFilterRow(
                                selectedCurrency = selectedCurrency,
                                availableCurrencies = availableCurrencies,
                                onCurrencySelected = { analyticsViewModel.selectCurrency(it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(Spacing.md)) }

                    // Analytics Summary
                    item {
                        BlurredAnimatedVisibility(
                            uiState.summary != null,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        ) {
                            AnalyticsSummaryCard(
                                summary = uiState.summary!!,
                                incomeOnly = uiState.incomeOnly,
                                displayCurrency = uiState.selectedCurrency,
                                topCategory =
                                    uiState.categoryBreakdown
                                        .firstOrNull()
                                        ?.categoryId
                                        ?.let(uiState.categories::get),
                                showIncome =
                                    transactionTypeFilter.size > 1 ||
                                        TransactionTypeFilter.ALL in transactionTypeFilter,
                                transactionCount = uiState.transactionCount,
                                isLoading = uiState.isLoading,
                                onClick = { onNavigateToTransactions(uiState.filter) },
                                modifier =
                                    Modifier.padding(
                                        start = Dimensions.Padding.content,
                                        end = Dimensions.Padding.content,
                                    ),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(Spacing.md)) }

                    // Spending Trend Chart
                    if (uiState.trend.entries.toList().isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                SectionHeader(
                                    title = stringResource(R.string.trends),
                                    action = {
                                        Button(
                                            onClick = {
                                                showChartTypeSelector = !showChartTypeSelector
                                            },
                                            shapes = ButtonDefaults.shapes(),
                                            contentPadding = PaddingValues(horizontal = Spacing.sm),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                            modifier = Modifier.height(22.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = chartType.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.sm))
                                                Text(
                                                    text =
                                                        stringResource(
                                                            R.string.chart_format,
                                                            stringResource(
                                                                chartType.labelRes
                                                            ),
                                                        ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier.padding(
                                            start = Spacing.lg,
                                            end = Spacing.lg,
                                            top = Dimensions.Padding.content,
                                            bottom = Spacing.sm,
                                        ),
                                )
                                // Chart Type Selector
                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(
                                                start = Dimensions.Padding.content,
                                                end = Dimensions.Padding.content,
                                            )
                                            .animateContentSize()
                                ) {
                                    BlurredAnimatedVisibility(
                                        visible = showChartTypeSelector,
                                        modifier = Modifier.padding(horizontal = Spacing.sm),
                                    ) {
                                        HootbookCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors =
                                                CardDefaults.cardColors(
                                                    containerColor =
                                                        MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.9f
                                                        )
                                                ),
                                        ) {
                                            Column {
                                                ChartType.entries.filter { !mixedTrend || it != ChartType.HEATMAP }.forEach { type ->
                                                    Row(
                                                        modifier =
                                                            Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable {
                                                                    selectedChartType = type
                                                                    showChartTypeSelector = false
                                                                }
                                                                .padding(12.dp),
                                                        verticalAlignment =
                                                            Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                            Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            verticalAlignment =
                                                                Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = type.icon,
                                                                contentDescription = null,
                                                                tint =
                                                                    if (chartType == type)
                                                                        MaterialTheme.colorScheme
                                                                            .primary
                                                                    else
                                                                        MaterialTheme.colorScheme
                                                                            .onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Text(
                                                                text =
                                                                    stringResource(type.labelRes),
                                                                style =
                                                                    MaterialTheme.typography
                                                                        .bodyMedium,
                                                                color =
                                                                    if (chartType == type)
                                                                        MaterialTheme.colorScheme
                                                                            .primary
                                                                    else
                                                                        MaterialTheme.colorScheme
                                                                            .onSurfaceVariant,
                                                                fontWeight =
                                                                    if (chartType == type)
                                                                        FontWeight.Bold
                                                                    else FontWeight.Normal,
                                                            )
                                                        }
                                                        if (chartType == type) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint =
                                                                    MaterialTheme.colorScheme
                                                                        .primary,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HootbookCard(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            start = Dimensions.Padding.content,
                                            end = Dimensions.Padding.content,
                                        )
                                        .animateContentSize()
                            ) {
                                Column {
                                    when (chartType) {
                                        ChartType.LINE ->
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                            ) {
                                                SpendingLineChart(
                                                    data = uiState.trend.entries.toList(),
                                                    currency = uiState.currency,
                                                    typeFilters = transactionTypeFilter,
                                                )
                                            }
                                        ChartType.BAR ->
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                            ) {
                                                SpendingBarChart(
                                                    data = uiState.trend.entries.toList(),
                                                    currency = uiState.currency,
                                                    typeFilters = transactionTypeFilter,
                                                )
                                            }
                                        ChartType.HEATMAP ->
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                            ) {
                                                SpendingHeatmap(
                                                    data = uiState.trend.entries.toList(),
                                                    currency = uiState.currency,
                                                    incomeOnly = uiState.incomeOnly,
                                                    onClick = { date ->
                                                        uiState.trendFilters[date]?.let(
                                                            onNavigateToTransactions
                                                        )
                                                    },
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(Spacing.md)) }
                }
                val breakdownItems: LazyListScope.() -> Unit = {
                    // Category Breakdown
                    if (uiState.categoryAmounts.values.any { it == null })
                        item {
                            Text(
                                stringResource(
                                    R.string.core_complete_display_missing,
                                    uiState.currency,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            )
                        }
                    if (uiState.categoryBreakdown.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.top_categories),
                                action = {
                                    IconButton(
                                        shapes = IconButtonDefaults.shapes(),
                                        onClick = {
                                            selectedBreakdownType =
                                                if (selectedBreakdownType == BreakdownType.PIE) {
                                                    BreakdownType.LIST
                                                } else {
                                                    BreakdownType.PIE
                                                }
                                        },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (selectedBreakdownType == BreakdownType.PIE) {
                                                    Iconax.Menu
                                                } else {
                                                    Iconax.Status
                                                },
                                            contentDescription =
                                                stringResource(R.string.toggle_view),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                modifier =
                                    Modifier.padding(
                                        start = Spacing.lg,
                                        end = Spacing.lg,
                                        top = Dimensions.Padding.content,
                                        bottom = 0.dp,
                                    ),
                            )
                        }

                        // Charts
                        item {
                            Column(
                                modifier =
                                    Modifier.animateContentSize()
                                        .padding(
                                            start = Dimensions.Padding.content,
                                            end = Dimensions.Padding.content,
                                        )
                            ) {
                                // Pie Chart
                                BlurredAnimatedVisibility(
                                    visible = selectedBreakdownType == BreakdownType.PIE,
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                                ) {
                                    HootbookCard(
                                        modifier = Modifier.animateContentSize().fillMaxWidth()
                                    ) {
                                        Column {
                                            CategoryPieChart(
                                                categories = uiState.categoryBreakdown,
                                                currency = uiState.currency,
                                                lookups = uiState.categories,
                                                amounts = uiState.categoryAmounts,
                                                onClick = { id ->
                                                    onNavigateToTransactions(
                                                        uiState.filter.copy(
                                                            categoryIds = listOf(id)
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                                uiState.categoryBreakdown.take(5).forEach { category ->
                                    BlurredAnimatedVisibility(
                                        visible = selectedBreakdownType != BreakdownType.PIE,
                                        enter =
                                            fadeIn() + slideInVertically(initialOffsetY = { it }),
                                        exit =
                                            fadeOut() + slideOutVertically(targetOffsetY = { it }),
                                    ) {
                                        CategoryProgressItem(
                                            category = uiState.categories[category.categoryId],
                                            amount =
                                                if (
                                                    uiState.selectedCurrency != null &&
                                                        uiState.selectedCurrency !=
                                                            uiState.summary?.currency
                                                )
                                                    uiState.categoryAmounts[category.categoryId]
                                                        ?.amount
                                                else category.total,
                                            currency = uiState.currency,
                                            onClick = {
                                                onNavigateToTransactions(
                                                    uiState.filter.copy(
                                                        categoryIds = listOf(category.categoryId)
                                                    )
                                                )
                                            },
                                            animatedContentScope = animatedContentScope,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(Spacing.md)) }

                    // Empty State
                    if (
                        uiState.transactionCount == 0u && !uiState.isLoading && !uiState.isLoading
                    ) {
                        item { EmptyAnalyticsState() }
                    }
                }
                val listPadding =
                    PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = paddingValues.calculateTopPadding() + Dimensions.Padding.content,
                        bottom = Dimensions.Padding.content + 80.dp,
                    )
                val listModifier =
                    Modifier.hazeSource(state = hazeState)
                        .background(MaterialTheme.colorScheme.background)
                if (LocalWindowLayout.current.widthDp < ANALYTICS_TWO_COLUMN_MIN_WIDTH_DP) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = listModifier.fillMaxSize(),
                        contentPadding = listPadding,
                    ) {
                        overviewItems()
                        breakdownItems()
                        item { Spacer(Modifier.height(200.dp)) }
                    }
                } else {
                    val breakdownListState = rememberLazyListState()
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = listModifier.weight(1f).fillMaxHeight(),
                            contentPadding = listPadding,
                        ) {
                            overviewItems()
                            item { Spacer(Modifier.height(120.dp)) }
                        }
                        LazyColumn(
                            state = breakdownListState,
                            modifier = listModifier.weight(1f).fillMaxHeight(),
                            contentPadding = listPadding,
                        ) {
                            breakdownItems()
                            item { Spacer(Modifier.height(120.dp)) }
                        }
                    }
                }
            }

            if (showDateRangePicker) {
                DateRangePickerDialog(
                    onDismiss = { showDateRangePicker = false },
                    onConfirm = { startDate, endDate ->
                        analyticsViewModel.setCustomDateRange(startDate, endDate)
                        showDateRangePicker = false
                    },
                    initialStartDate = customDateRange?.first,
                    initialEndDate = customDateRange?.second,
                    blurEffects = blurEffects,
                    hazeState = hazeState,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CategoryProgressItem(
    category: Category?,
    amount: BigDecimal?,
    currency: String,
    onClick: () -> Unit,
    animatedContentScope: AnimatedContentScope? = null,
) {
    val name = category?.displayName.orEmpty()
    val color =
        category?.color?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: MaterialTheme.colorScheme.primary

    Column(
        modifier =
            Modifier.animateContentSize()
                .fillMaxWidth()
                .then(
                    if (animatedContentScope != null) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "category_${category?.id}"),
                            animatedVisibilityScope = animatedContentScope,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = MotionDurations.standard,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                            resizeMode =
                                SharedTransitionScope.ResizeMode.scaleToBounds(
                                    ContentScale.Fit,
                                    Alignment.Center,
                                ),
                        )
                    } else Modifier
                )
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category Icon
                Box(
                    modifier =
                        Modifier.size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    category?.let { CategoryIcon(category = it, size = 32.dp) }
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = CurrencyFormatter.format(amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyAnalyticsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center,
    ) {
        HootbookCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.no_analytics_data),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.no_analytics_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CurrencyFilterRow(
    selectedCurrency: String?,
    availableCurrencies: List<String>,
    onCurrencySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item { Spacer(modifier = Modifier.width(16.dp)) }
        items(availableCurrencies) { currency ->
            FilterChip(
                selected = selectedCurrency == currency,
                onClick = {
                    if (selectedCurrency == currency) {
                        onCurrencySelected(null)
                    } else {
                        onCurrencySelected(currency)
                    }
                },
                label = { Text(currency) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        borderWidth = 0.dp,
                        selected = selectedCurrency == currency,
                        enabled = true,
                    ),
            )
        }
        item { Spacer(modifier = Modifier.width(16.dp)) }
    }
}

/** Analytics shows overview and breakdown side by side from this window width. */
private const val ANALYTICS_TWO_COLUMN_MIN_WIDTH_DP = 720
