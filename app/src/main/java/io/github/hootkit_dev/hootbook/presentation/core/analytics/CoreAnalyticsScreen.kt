@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.utils.DateFormats
import java.time.LocalDate as JavaDate

/**
 * Where the money went in a period: totals, the daily chart, then each category (tap one to see
 * its transactions). Periods and the custom range are chips; type and currency are in Filter.
 */
@Composable
fun CoreAnalyticsScreen(onBack: () -> Unit, onTransactions: (categoryId: String?) -> Unit = {},
    viewModel: CoreAnalyticsViewModel = hiltViewModel(), onFilterTransactions: ((TransactionFilter) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = state.query
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    fun open(categoryId: String?) {
        if (onFilterTransactions != null) onFilterTransactions(query.filter().copy(categoryIds = listOfNotNull(categoryId)))
        else onTransactions(categoryId)
    }
    SecondaryScreen(title = stringResource(R.string.analytics), onBack = null) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val overview: LazyListScope.() -> Unit = {
                item(key = "periods") { PeriodChips(query, viewModel::apply, onFilter = { showFilters = true }, onCustomRange = { showDates = true }) }
                state.error?.let { error -> item(key = "error") {
                    ErrorBanner(stringResource(error), onDismiss = viewModel::refresh, actionLabel = stringResource(R.string.core_analytics_retry), onAction = viewModel::refresh)
                } }
                state.summary?.let { summary -> item(key = "totals") {
                    LedgerTotals(summary, emptyMap(), count = state.total)
                    if (!summary.complete) Text(stringResource(R.string.core_analytics_missing, summary.missingCurrencies.joinToString()),
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } }
                item(key = "daily") { DailyChart(state, viewModel::loadOlderDays) }
            }
            val breakdown: LazyListScope.() -> Unit = {
                state.summary?.let { summary -> item(key = "categories") { CategoryBreakdown(state, summary, ::open) } }
            }
            if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f), contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing), content = overview)
                LazyColumn(Modifier.weight(1f), contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing), content = breakdown)
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) { overview(); breakdown() }
        }
    }
    if (showDates) DateRangePickerDialog(onDismiss = { showDates = false },
        onConfirm = { start, end -> showDates = false; viewModel.apply(query.copy(from = start.toString(), through = end.toString(), allTime = false)) },
        initialStartDate = runCatching { JavaDate.parse(query.from) }.getOrNull(), initialEndDate = runCatching { JavaDate.parse(query.through) }.getOrNull(), blurEffects = false)
    if (showFilters) AnalyticsFilterSheet(query, { showFilters = false }, viewModel::apply)
}

/** Filter first, then this month / last month / this year / all time, then a custom range showing its dates. */
@Composable
private fun PeriodChips(query: CoreAnalyticsQuery, apply: (CoreAnalyticsQuery) -> Unit, onFilter: () -> Unit, onCustomRange: () -> Unit) {
    val today = JavaDate.now()
    val periods = listOf(
        R.string.core_analytics_this_month to (today.withDayOfMonth(1) to today),
        R.string.core_analytics_last_month to java.time.YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() },
        R.string.core_analytics_this_year to (today.withDayOfYear(1) to today),
    )
    val preset = query.allTime || periods.any { (_, d) -> query.from == d.first.toString() && query.through == d.second.toString() }
    val filters = (if (query.kinds != setOf(TransactionKind.EXPENSE)) 1 else 0) + (if (query.currency.isNotBlank()) 1 else 0)
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(filters > 0, onFilter,
            label = { Text(if (filters > 0) stringResource(R.string.txn_filter_count, filters) else stringResource(R.string.txn_filter)) },
            leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(FilterChipDefaults.IconSize)) }) }
        items(periods) { (label, dates) ->
            FilterChip(!query.allTime && query.from == dates.first.toString() && query.through == dates.second.toString(),
                onClick = { apply(query.copy(from = dates.first.toString(), through = dates.second.toString(), allTime = false)) },
                label = { Text(stringResource(label)) })
        }
        item { FilterChip(query.allTime, { apply(query.copy(allTime = true)) }, label = { Text(stringResource(R.string.core_analytics_all_time)) }) }
        item {
            val range = if (preset) stringResource(R.string.core_analytics_custom) else runCatching {
                DateFormats.shortDate(JavaDate.parse(query.from)) + " – " + DateFormats.shortDate(JavaDate.parse(query.through))
            }.getOrDefault(stringResource(R.string.core_analytics_custom))
            FilterChip(!preset, onCustomRange, label = { Text(range) },
                leadingIcon = { Icon(Icons.Rounded.DateRange, null, Modifier.size(FilterChipDefaults.IconSize)) })
        }
    }
}

/** The daily amounts as a line, bars or a heatmap, with earlier days on request. */
@Composable
private fun DailyChart(state: CoreAnalyticsUiState, loadOlder: () -> Unit) {
    var chart by rememberSaveable { mutableIntStateOf(0) }
    val kinds = state.query.kinds
    Column {
        SectionTitle(stringResource(R.string.core_analytics_daily), action = {
            Row {
                listOf(Icons.AutoMirrored.Rounded.ShowChart to R.string.chart_type_line, Icons.Rounded.BarChart to R.string.chart_type_bar,
                    Icons.Rounded.GridView to R.string.chart_type_heatmap).forEachIndexed { index, (icon, label) ->
                    IconToggleButton(checked = chart == index, onCheckedChange = { chart = index }, shapes = IconButtonDefaults.toggleableShapes()) {
                        Icon(icon, stringResource(label))
                    }
                }
            }
        })
        Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                when (chart) {
                    1 -> SpendingBarChart(state.days, state.summary?.currency.orEmpty(), kinds)
                    2 -> SpendingHeatmap(state.days, typeFilters = kinds)
                    else -> SpendingLineChart(state.days, state.summary?.currency.orEmpty(), kinds)
                }
                if (state.olderThrough != null) TextButton(onClick = loadOlder, enabled = !state.loading && !state.loadingDays) {
                    Text(stringResource(R.string.core_analytics_older))
                }
            }
        }
    }
}

/** Each category's total and count, as a pie with the list under it or the list alone. */
@Composable
private fun CategoryBreakdown(state: CoreAnalyticsUiState, summary: LedgerSummary, open: (String?) -> Unit) {
    var pie by rememberSaveable { mutableStateOf(true) }
    val rows = if (state.query.kinds == setOf(TransactionKind.INCOME)) summary.incomeByCategory else summary.expenseByCategory
    val total = rows.fold(java.math.BigDecimal.ZERO) { sum, row -> sum + row.total }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListSection(
            title = stringResource(R.string.core_analytics_categories),
            action = {
                Row {
                    IconToggleButton(checked = pie, onCheckedChange = { pie = true }, shapes = IconButtonDefaults.toggleableShapes()) {
                        Icon(Icons.Rounded.PieChart, stringResource(R.string.core_analytics_chart)) }
                    IconToggleButton(checked = !pie, onCheckedChange = { pie = false }, shapes = IconButtonDefaults.toggleableShapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.core_analytics_list)) }
                }
            },
        ) {
            if (pie && rows.isNotEmpty()) row { shape ->
                Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    CategoryPieChart(Modifier.padding(16.dp), categories = rows, currency = summary.currency, references = state.categories)
                }
            }
            rows.forEach { row ->
                val category = state.categories.find { it.id == row.categoryId }
                val share = if (total.signum() == 0) 0 else row.total.multiply(java.math.BigDecimal(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).toInt()
                item(
                    leading = { CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(40.dp)) },
                    headline = { Text(category?.displayName ?: row.categoryId) },
                    supporting = { Text(stringResource(R.string.core_analytics_count, state.categoryCounts[row.categoryId]?.toString().orEmpty()) + " · $share%") },
                    trailing = { Text(CoreMoneyPresentation.format(row.total, summary.currency), style = MaterialTheme.typography.titleMedium) },
                    chevron = true, onClick = { open(row.categoryId) },
                )
            }
        }
    }
}

/** Which transaction types and which currency the analysis covers. */
@Composable
private fun AnalyticsFilterSheet(query: CoreAnalyticsQuery, onDismiss: () -> Unit, apply: (CoreAnalyticsQuery) -> Unit) {
    var draft by remember(query) { mutableStateOf(query) }
    FormSheet(stringResource(R.string.txn_filter), onDismiss, { apply(draft); onDismiss() }, confirmLabel = stringResource(R.string.core_analytics_apply),
        confirmEnabled = draft.kinds.isNotEmpty(),
        titleAction = { TextButton(onClick = { draft = draft.copy(kinds = setOf(TransactionKind.EXPENSE), currency = "") }) { Text(stringResource(R.string.reset)) } }) {
        SectionTitle(stringResource(R.string.txn_kind), indent = 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                FilterChip(kind in draft.kinds, { draft = draft.copy(kinds = if (kind in draft.kinds) draft.kinds - kind else draft.kinds + kind) },
                    label = { Text(CoreKindLabel(kind)) })
            }
        }
        FormTextField(draft.currency, stringResource(R.string.core_analytics_filter_currency), { draft = draft.copy(currency = it.uppercase(java.util.Locale.ROOT)) },
            supporting = stringResource(R.string.core_analytics_currency_any))
    }
}
