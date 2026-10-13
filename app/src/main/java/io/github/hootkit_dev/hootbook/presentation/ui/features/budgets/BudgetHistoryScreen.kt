package io.github.hootkit_dev.hootbook.presentation.ui.features.budgets

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.budgetProgressFraction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.LoadingCircle
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetHistoryScreen(
    budgetId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String, LocalDate?, LocalDate?) -> Unit = { _, _, _ -> },
    viewModel: BudgetHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(budgetId) {
        viewModel.loadBudgetHistory(budgetId)
    }

    LaunchedEffect(lazyListState, uiState.cursor, uiState.loadingMore) {
        androidx.compose.runtime.snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (uiState.cursor != null && !uiState.loadingMore && uiState.error == null && last >= uiState.periods.size - 4) viewModel.loadMore() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = uiState.budget?.let { stringResource(R.string.budget_history_format, budgetDisplayName(it.name, it.period)) } ?: stringResource(R.string.budget_history),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = {
                    NavigationContent { onNavigateBack() }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            if (uiState.isLoading) {
                LoadingCircle(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = stringResource(uiState.error!!),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + Spacing.md,
                        bottom = 100.dp
                    ),
                ) {
                    if (uiState.periods.isNotEmpty()) {
                        item {
                            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                BudgetHistoryChart(uiState.periods, uiState.budget?.amount?.currency.orEmpty(), Modifier.padding(Spacing.md))
                            }
                            Spacer(Modifier.height(Spacing.lg))
                        }
                    }
                    // Periods List
                    itemsIndexed(
                        items = uiState.periods,
                        key = { _, item -> item.start.toString() }
                    ) { index, period ->
                        val position = ListItemPosition.from(index, uiState.periods.size)
                        BudgetHistoryItem(
                            period = period,
                            name = budgetPeriodName(period, uiState.budget),
                            modifier = Modifier
                                .padding(horizontal = Spacing.md)
                                .clickable { onNavigateToDetail(budgetId, period.start, period.end) },
                            currency = uiState.budget?.amount?.currency.orEmpty(),
                            shape = position.toShape()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetHistoryItem(
    period: BudgetPeriodSummary,
    name: String,
    currency: String,
    modifier: Modifier = Modifier,
    shape: Shape
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 1.5.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val statusText = "${CurrencyFormatter.formatCurrency(period.spent, currency)} / ${CurrencyFormatter.formatCurrency(period.amount, currency)}"
                if (!period.complete) Text(stringResource(R.string.core_budgets_history_incomplete), color = MaterialTheme.colorScheme.error)

                Text(
                    text = statusText.replace(".00", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Percentage Circle
            val percentage = (budgetProgressFraction(period.spent, period.amount) * 100).toInt()
            val progressColor = when {
                period.over -> MaterialTheme.colorScheme.error
                budgetProgressFraction(period.spent, period.amount) > 0.8f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(progressColor.copy(alpha = 0.1f))
            ) {
                CircularProgressIndicator(
                    progress = { budgetProgressFraction(period.spent, period.amount).coerceIn(0f, 1f) },
                    modifier = Modifier.size(56.dp),
                    color = progressColor,
                    strokeWidth = 4.dp,
                    trackColor = progressColor.copy(alpha = 0.1f)
                )
                Text(
                    text = if (percentage < 1 && percentage > 0) stringResource(R.string.less_than_one_percent) else "$percentage%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
        }
    }
}

@Composable
private fun budgetPeriodName(period: BudgetPeriodSummary, budget: Budget?): String {
    if (period.start == budget?.current?.start && period.end == budget.current.end) return stringResource(R.string.budget_current_period)
    return when (budget?.period) {
        BudgetPeriod.DAILY -> io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(period.start.javaBudgetDate())
        BudgetPeriod.YEARLY -> period.start.year.toString()
        BudgetPeriod.MONTHLY -> if (period.start.day.toInt() == 1) io.github.hootkit_dev.hootbook.utils.DateFormats.yearMonth(period.start.javaBudgetDate())
            else "${io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(period.start.javaBudgetDate())} – ${io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(period.end.javaBudgetDate())}"
        else -> "${io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(period.start.javaBudgetDate())} – ${io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(period.end.javaBudgetDate())}"
    }
}
