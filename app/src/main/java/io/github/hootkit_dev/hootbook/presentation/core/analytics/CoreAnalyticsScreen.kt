@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateRangePickerDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.time.LocalDate as JavaDate

/** Original filter strip, overview/trend and category split. All amounts come from Core summaries. */
@Composable
fun CoreAnalyticsScreen(onBack: () -> Unit, onTransactions: (categoryId: String?) -> Unit = {},
    viewModel: CoreAnalyticsViewModel = hiltViewModel(), onFilterTransactions: ((TransactionFilter) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = state.query
    var currency by rememberSaveable(query.currency) { mutableStateOf(query.currency) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var chart by rememberSaveable { mutableIntStateOf(0) }
    var pie by rememberSaveable { mutableStateOf(true) }
    var showDays by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    fun open(categoryId: String?) {
        if (onFilterTransactions != null) onFilterTransactions(query.filter().copy(categoryIds = listOfNotNull(categoryId)))
        else onTransactions(categoryId)
    }
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        CustomTitleTopAppBar(title = stringResource(R.string.analytics),
            scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(onBack) })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val overview: LazyListScope.() -> Unit = {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val today = JavaDate.now()
                        val periods = listOf(
                            R.string.core_analytics_this_month to (today.withDayOfMonth(1) to today),
                            R.string.core_analytics_last_month to java.time.YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() },
                            R.string.core_analytics_this_year to (today.withDayOfYear(1) to today),
                        )
                        items(periods) { (label, dates) ->
                            FilterChip(selected = !query.allTime && query.from == dates.first.toString() && query.through == dates.second.toString(),
                                onClick = { viewModel.apply(query.copy(from = dates.first.toString(), through = dates.second.toString(), allTime = false)) },
                                label = { Text(stringResource(label)) })
                        }
                        item { FilterChip(query.allTime, { viewModel.apply(query.copy(allTime = true)) }, label = { Text(stringResource(R.string.core_analytics_all_time)) }) }
                        item { FilterChip(false, { showDates = true }, label = { Icon(Icons.Rounded.DateRange, stringResource(R.string.core_analytics_from)) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { showFilters = true }, label = { Text(stringResource(R.string.filters)) }, leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) })
                        Text(if (query.allTime) stringResource(R.string.core_analytics_all_time) else "${query.from} – ${query.through}",
                            Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.error?.let { error -> item {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.core_analytics_retry)) }
                } }
                state.summary?.let { summary -> item {
                    CoreAnalyticsSummaryCard(summary, state.total, query.kinds)
                    if (!summary.complete) Text(stringResource(R.string.core_analytics_missing, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
                    summary.perCurrency.forEach { native ->
                        Text("${native.currency} · ${native.income.toPlainString()} / ${native.expense.toPlainString()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.core_analytics_daily), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                listOf(Icons.Rounded.ShowChart, Icons.Rounded.BarChart, Icons.Rounded.GridView).forEachIndexed { index, icon ->
                                    IconToggleButton(checked = chart == index, onCheckedChange = { chart = index }) {
                                        Icon(icon, stringResource(listOf(R.string.chart_type_line, R.string.chart_type_bar, R.string.chart_type_heatmap)[index]))
                                    }
                                }
                            }
                            when (chart) {
                                1 -> SpendingBarChart(state.days, state.summary?.currency.orEmpty(), query.kinds)
                                2 -> SpendingHeatmap(state.days, typeFilters = query.kinds)
                                else -> SpendingLineChart(state.days, state.summary?.currency.orEmpty(), query.kinds)
                            }
                            Text(stringResource(R.string.core_analytics_daily_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.olderThrough != null) TextButton(onClick = viewModel::loadOlderDays, enabled = !state.loading && !state.loadingDays) { Text(stringResource(R.string.core_analytics_older)) }
                        }
                    }
                }
                item { TextButton(onClick = { showDays = !showDays }) {
                    Icon(if (showDays) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    Text(stringResource(R.string.core_analytics_daily))
                } }
                if (showDays) items(state.days, key = { it.date.coreDateText() }) { day ->
                    ListItem(headlineContent = { Text(day.date.coreDateText()) }, supportingContent = {
                        Text(stringResource(R.string.core_analytics_count, day.transactions.toString()))
                    }, trailingContent = { Text(CurrencyFormatter.formatCurrency(day.chartAmount(query.kinds), day.summary.currency)) })
                    if (!day.summary.complete) Text(stringResource(R.string.core_analytics_missing, day.summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error)
                }
            }
            val breakdown: LazyListScope.() -> Unit = {
                state.summary?.let { summary ->
                    val rows = if (query.kinds == setOf(TransactionKind.INCOME)) summary.incomeByCategory else summary.expenseByCategory
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.core_analytics_categories), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconToggleButton(checked = pie, onCheckedChange = { pie = true }) { Icon(Icons.Rounded.PieChart, stringResource(R.string.core_analytics_categories)) }
                            IconToggleButton(checked = !pie, onCheckedChange = { pie = false }) { Icon(Icons.Rounded.ViewList, stringResource(R.string.transactions)) }
                        }
                    }
                    if (pie && rows.isNotEmpty()) item { CategoryPieChart(categories = rows, currency = summary.currency, references = state.categories) }
                    items(rows, key = { "category:" + it.categoryId }) { row ->
                        val category = state.categories.find { it.id == row.categoryId }
                        Card(onClick = { open(row.categoryId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(category?.displayName ?: row.categoryId, style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.core_analytics_count, state.categoryCounts[row.categoryId]?.toString().orEmpty()), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(CurrencyFormatter.formatCurrency(row.total, summary.currency), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.top_merchants), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.core_analytics_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = overview)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = breakdown)
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { overview(); breakdown() }
        }
    }
    if (showDates) DateRangePickerDialog(onDismiss = { showDates = false },
        onConfirm = { start, end -> showDates = false; viewModel.apply(query.copy(from = start.toString(), through = end.toString(), allTime = false)) },
        initialStartDate = runCatching { JavaDate.parse(query.from) }.getOrNull(), initialEndDate = runCatching { JavaDate.parse(query.through) }.getOrNull(), blurEffects = false)
    if (showFilters) HootbookModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                    FilterChip(kind in query.kinds, { viewModel.apply(query.copy(kinds = if (kind in query.kinds) query.kinds - kind else query.kinds + kind)) }, label = { Text(CoreKindLabel(kind)) })
                }
            }
            OutlinedTextField(currency, { currency = it.uppercase(java.util.Locale.ROOT) }, label = { Text(stringResource(R.string.core_analytics_filter_currency)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7L, 30L, 365L).forEach { days -> FilterChip(false, {
                    val today = JavaDate.now(); viewModel.apply(query.copy(from = today.minusDays(days - 1).toString(), through = today.toString(), allTime = false))
                }, label = { Text(stringResource(R.string.core_analytics_days, days.toString())) }) }
            }
            Button(onClick = { viewModel.apply(query.copy(currency = currency)); showFilters = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_analytics_apply)) }
        }
    }
}

@Composable
internal fun CoreAnalyticsSummaryCard(summary: LedgerSummary, count: UInt, kinds: Set<TransactionKind>) {
    val incomeOnly = kinds == setOf(TransactionKind.INCOME)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (incomeOnly) R.string.income else R.string.analytics_spent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.formatCurrency(if (incomeOnly) summary.income else summary.expense, summary.currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                if (!incomeOnly) Text(stringResource(R.string.analytics_income_beside, CurrencyFormatter.formatCurrency(summary.income, summary.currency)), style = MaterialTheme.typography.bodyMedium)
            }
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(24.dp))
                    Text(stringResource(R.string.transactions_format, count.toInt()), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 24.dp))
        Text(stringResource(R.string.core_analytics_net, CurrencyFormatter.formatCurrency(summary.net, summary.currency)), style = MaterialTheme.typography.titleMedium)
    }
}
