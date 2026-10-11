package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hootkit.core.Category
import dev.hootkit.core.CategoryTotal
import dev.hootkit.core.LedgerSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.DateFormats
import io.github.hootkit_dev.hootbook.utils.maxDoubleOrNull
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.BarProperties
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.LineProperties
import ir.ehsannarmani.compose_charts.models.Pie
import ir.ehsannarmani.compose_charts.models.StrokeStyle
import ir.ehsannarmani.compose_charts.models.ZeroLineProperties
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun SpendingLineChart(
    data: List<Map.Entry<LocalDate, LedgerSummary>>,
    currency: String,
    typeFilters: Set<TransactionTypeFilter> = setOf(TransactionTypeFilter.EXPENSE),
    incomeOnly: Boolean = false,
) {
    if (data.isEmpty()) return
    val themeColors = MaterialTheme.colorScheme
    val totalLabel = stringResource(R.string.total)
    val filterLabelStrings = typeFilters.map { stringResource(it.labelRes) }

    val cashFlowData =
        remember(data, incomeOnly, currency) {
            data.map { it.value.chartAmount(currency, incomeOnly)?.toDouble() ?: 0.0 }
        }
    val labels =
        remember(data, incomeOnly, currency) {
            val isYearly = data.size > 1 && data.all { it.key.dayOfYear == 1 }
            val isMonthly = !isYearly && data.all { it.key.dayOfMonth == 1 }
            val spansMultipleYears =
                if (data.isNotEmpty()) {
                    data.first().key.year != data.last().key.year
                } else false

            data.map {
                val date = it.key
                when {
                    isYearly -> date.format(DateTimeFormatter.ofPattern("yyyy"))
                    isMonthly && spansMultipleYears ->
                        date.format(DateFormats.shortYearMonthFormatter())
                    isMonthly -> date.format(DateFormats.monthFormatter())
                    else -> date.format(DateFormats.monthDayFormatter())
                }
            }
        }

    LineChart(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(vertical = Spacing.md),
        data =
            listOf(
                Line(
                    label =
                        if (
                            typeFilters.contains(TransactionTypeFilter.ALL) || typeFilters.size > 2
                        ) {
                            totalLabel
                        } else {
                            filterLabelStrings.joinToString(" & ")
                        },
                    values = cashFlowData,
                    color = SolidColor(themeColors.primary),
                    firstGradientFillColor = themeColors.primary.copy(alpha = 0.3f),
                    secondGradientFillColor = Color.Transparent,
                    strokeAnimationSpec = tween(1500, easing = EaseInOutCubic),
                    gradientAnimationDelay = 750,
                    drawStyle = DrawStyle.Stroke(width = 2.dp),
                    curvedEdges = true,
                    dotProperties =
                        DotProperties(
                            enabled = true,
                            color = SolidColor(themeColors.primary),
                            strokeWidth = 3.dp,
                            radius = 4.dp,
                            strokeColor = SolidColor(themeColors.surface),
                        ),
                )
            ),
        dividerProperties =
            DividerProperties(
                enabled = true,
                xAxisProperties =
                    LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp,
                    ),
                yAxisProperties =
                    LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp,
                    ),
            ),
        indicatorProperties =
            HorizontalIndicatorProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurfaceVariant.copy(0.6f),
                        textAlign = TextAlign.Center,
                    ),
                contentBuilder = { value -> CurrencyFormatter.formatCurrency(value, currency) },
            ),
        labelHelperProperties =
            LabelHelperProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurface,
                        textAlign = TextAlign.End,
                    ),
            ),
        labelProperties =
            LabelProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurface.copy(0.6f),
                        textAlign = TextAlign.End,
                    ),
                labels = labels,
                padding = 16.dp,
                rotation =
                    LabelProperties.Rotation(
                        mode = LabelProperties.Rotation.Mode.Force,
                        degree = -45f,
                    ),
            ),
        zeroLineProperties =
            ZeroLineProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
            ),
        gridProperties =
            GridProperties(
                enabled = true,
                xAxisProperties =
                    GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
                    ),
                yAxisProperties =
                    GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
                    ),
            ),
        animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
    )
}

@Composable
fun SpendingBarChart(
    data: List<Map.Entry<LocalDate, LedgerSummary>>,
    currency: String,
    typeFilters: Set<TransactionTypeFilter> = setOf(TransactionTypeFilter.EXPENSE),
    incomeOnly: Boolean = false,
) {
    if (data.isEmpty()) return
    val themeColors = MaterialTheme.colorScheme
    val totalLabel = stringResource(R.string.total)
    val filterLabelStrings = typeFilters.map { stringResource(it.labelRes) }

    val columnData =
        remember(data, incomeOnly, currency) {
            val isYearly = data.size > 1 && data.all { it.key.dayOfYear == 1 }
            val isMonthly = !isYearly && data.all { it.key.dayOfMonth == 1 }
            val spansMultipleYears =
                if (data.isNotEmpty()) {
                    data.first().key.year != data.last().key.year
                } else false

            data.map { point ->
                val label =
                    when {
                        isYearly -> point.key.format(DateTimeFormatter.ofPattern("yyyy"))
                        isMonthly && spansMultipleYears ->
                            point.key.format(DateFormats.shortYearMonthFormatter())
                        isMonthly -> point.key.format(DateFormats.monthFormatter())
                        else -> point.key.format(DateFormats.monthDayFormatter())
                    }
                Bars(
                    label = label,
                    values =
                        listOf(
                            Bars.Data(
                                label =
                                    if (
                                        typeFilters.contains(TransactionTypeFilter.ALL) ||
                                            typeFilters.size > 2
                                    ) {
                                        totalLabel
                                    } else {
                                        filterLabelStrings.joinToString(" & ")
                                    },
                                value =
                                    point.value.chartAmount(currency, incomeOnly)?.toDouble()
                                        ?: 0.0,
                                color = SolidColor(themeColors.primary.copy(alpha = 0.8f)),
                            )
                        ),
                )
            }
        }

    val maxValue =
        remember(data, incomeOnly, currency) {
            (data.maxDoubleOrNull { point: Map.Entry<LocalDate, LedgerSummary> ->
                (point.value.chartAmount(currency, incomeOnly)?.toDouble() ?: 0.0)
            } ?: 0.0) * 1.2
        }

    ColumnChart(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(vertical = Spacing.md),
        data = columnData,
        maxValue = maxValue,
        barProperties =
            BarProperties(
                cornerRadius =
                    Bars.Data.Radius.Rectangle(
                        topLeft = 6.dp,
                        topRight = 6.dp,
                        bottomLeft = 6.dp,
                        bottomRight = 6.dp,
                    ),
                spacing = 8.dp,
                thickness = 12.dp,
            ),
        dividerProperties =
            DividerProperties(
                enabled = true,
                xAxisProperties =
                    LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp,
                    ),
                yAxisProperties =
                    LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp,
                    ),
            ),
        indicatorProperties =
            HorizontalIndicatorProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurfaceVariant.copy(0.6f),
                        textAlign = TextAlign.Center,
                    ),
                contentBuilder = { value -> CurrencyFormatter.formatCurrency(value, currency) },
            ),
        labelHelperProperties =
            LabelHelperProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurface,
                        textAlign = TextAlign.End,
                    ),
            ),
        labelProperties =
            LabelProperties(
                enabled = true,
                textStyle =
                    TextStyle.Default.copy(
                        fontSize = 10.sp,
                        color = themeColors.onSurface.copy(0.6f),
                        textAlign = TextAlign.End,
                    ),
                padding = 16.dp,
                rotation =
                    LabelProperties.Rotation(
                        mode = LabelProperties.Rotation.Mode.Force,
                        degree = -45f,
                    ),
            ),
        gridProperties =
            GridProperties(
                enabled = true,
                xAxisProperties =
                    GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
                    ),
                yAxisProperties =
                    GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
                    ),
            ),
        animationSpec =
            tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
    )
}

@Composable
fun CategoryPieChart(
    modifier: Modifier = Modifier,
    categories: List<CategoryTotal>,
    currency: String,
    lookups: Map<String, Category>,
    amounts: Map<String, dev.hootkit.core.Money?> = emptyMap(),
    onClick: (String) -> Unit = {},
) {
    if (categories.isEmpty() || amounts.values.any { it == null }) return

    if (categories.none { it.total.signum() > 0 }) return

    // Categories without a mapped color fall back to the scheme's neutral, not a fixed grey
    val fallbackColor = MaterialTheme.colorScheme.outline
    val pieData =
        remember(categories, lookups, amounts, fallbackColor) {
            categories.map { category ->
                Pie(
                    label = lookups[category.categoryId]?.displayName.orEmpty(),
                    data =
                        (if (category.categoryId in amounts) amounts[category.categoryId]?.amount
                            else category.total)
                            ?.toDouble() ?: 0.0,
                    color =
                        lookups[category.categoryId]?.color?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                        } ?: fallbackColor,
                    selectedColor =
                        (lookups[category.categoryId]?.color?.let {
                                runCatching { Color(android.graphics.Color.parseColor(it)) }
                                    .getOrNull()
                            } ?: fallbackColor)
                            .copy(alpha = 0.8f),
                    selected = false,
                    scaleAnimEnterSpec =
                        tween(
                            durationMillis = MotionDurations.standard,
                            easing = FastOutSlowInEasing,
                        ),
                    colorAnimEnterSpec = tween(500),
                )
            }
        }

    var chartData by remember(pieData) { mutableStateOf(pieData) }

    Row(
        modifier = modifier.fillMaxWidth().height(200.dp).padding(Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PieChart(
                modifier = Modifier.size(160.dp),
                data = chartData,
                onPieClick = { clickedPie ->
                    val pieIndex = chartData.indexOf(clickedPie)
                    categories.getOrNull(pieIndex)?.let { onClick(it.categoryId) }
                    chartData =
                        chartData.mapIndexed { index, pie ->
                            pie.copy(selected = index == pieIndex)
                        }
                },
                selectedScale = 1.1f,
                scaleAnimEnterSpec =
                    tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
                colorAnimEnterSpec = tween(500),
                style = Pie.Style.Stroke(width = 12.dp),
            )

            // Center Icon Overlay
            val selectedPie = chartData.find { it.selected }
            BlurredAnimatedVisibility(
                visible = selectedPie != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                if (selectedPie != null && selectedPie.label != null) {
                    lookups[categories[chartData.indexOf(selectedPie)].categoryId]?.let {
                        Box(
                            Modifier.size(56.dp)
                                .clip(CircleShape)
                                .background(selectedPie.color.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            CategoryIcon(category = it, size = 32.dp)
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = Spacing.md)) {
            items(chartData.sortedByDescending { it.data }) { pie ->
                LegendItem(
                    label = pie.label.orEmpty(),
                    value =
                        categories[chartData.indexOf(pie)].let { category ->
                            if (category.categoryId in amounts) amounts[category.categoryId]?.amount
                            else category.total
                        },
                    color = pie.color,
                    isSelected = pie.selected,
                    currency = currency,
                    onClick = {
                        val pieIndex = chartData.indexOf(pie)
                        categories.getOrNull(pieIndex)?.let { onClick(it.categoryId) }
                        chartData =
                            chartData.mapIndexed { index, p ->
                                p.copy(selected = index == pieIndex)
                            }
                    },
                )
            }
        }
    }
}

@Composable
fun LegendItem(
    label: String,
    value: BigDecimal?,
    color: Color,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else Color.Transparent
                )
                .padding(6.dp)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.format(value, currency),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SpendingHeatmap(
    data: List<Map.Entry<LocalDate, LedgerSummary>>,
    incomeOnly: Boolean = false,
    currency: String,
    onClick: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return

    val maxAmount =
        remember(data, incomeOnly, currency) {
            data.maxDoubleOrNull { point: Map.Entry<LocalDate, LedgerSummary> ->
                (point.value.chartAmount(currency, incomeOnly)?.toDouble() ?: 0.0)
            } ?: 1.0
        }
    val groupedData =
        remember(data, incomeOnly, currency) {
            data.associate {
                it.key to (it.value.chartAmount(currency, incomeOnly)?.toDouble() ?: 0.0)
            }
        }

    val sortedDates =
        remember(data, incomeOnly, currency) { data.map { it.key }.distinct().sorted() }
    val startDate = sortedDates.first().with(java.time.DayOfWeek.MONDAY)
    val endDate = sortedDates.last()

    val totalWeeks = ChronoUnit.WEEKS.between(startDate, endDate.plusDays(1)).toInt() + 1

    val monthLabels =
        remember(startDate, endDate) {
            val allMonthStarts = mutableListOf<Pair<Int, String>>()
            var current = startDate
            var lastMonth = -1
            var weekIndex = 0

            while (current <= endDate) {
                if (current.monthValue != lastMonth) {
                    val formatter = DateFormats.monthFormatter()
                    allMonthStarts.add(weekIndex to current.format(formatter))
                    lastMonth = current.monthValue
                }
                current = current.plusWeeks(1)
                weekIndex++
            }

            val filteredLabels = mutableListOf<Pair<Int, String>>()
            for (i in allMonthStarts.indices) {
                val (week, label) = allMonthStarts[i]

                if (i == 0 && allMonthStarts.size > 1) {
                    val nextWeek = allMonthStarts[1].first
                    if (nextWeek - week < 4) continue
                }

                if (filteredLabels.isEmpty()) {
                    filteredLabels.add(week to label)
                } else {
                    val lastAddedWeek = filteredLabels.last().first
                    if (week - lastAddedWeek >= 4) {
                        filteredLabels.add(week to label)
                    }
                }
            }
            filteredLabels
        }

    val scrollState = rememberScrollState()
    LaunchedEffect(data) { scrollState.scrollTo(scrollState.maxValue) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Heatmap Grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                for (w in 0 until totalWeeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until 7) {
                            val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                            val amount = groupedData[date] ?: 0.0
                            val intensity =
                                if (maxAmount > 0) (amount / maxAmount).toFloat().coerceIn(0f, 1f)
                                else 0f

                            val primary = MaterialTheme.colorScheme.primary
                            val color =
                                when {
                                    date > endDate -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    amount == 0.0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    intensity < 0.25f -> primary.copy(alpha = 0.25f)
                                    intensity < 0.5f -> primary.copy(alpha = 0.5f)
                                    intensity < 0.75f -> primary.copy(alpha = 0.75f)
                                    else -> primary
                                }

                            Box(
                                modifier =
                                    Modifier.size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .clickable(enabled = date in groupedData) { onClick(date) }
                            )
                        }
                    }
                }
            }

            // Labels
            Box(modifier = Modifier.fillMaxWidth()) {
                monthLabels.forEach { (weekIndex, label) ->
                    val xOffset = (weekIndex * 20).dp // 16.dp size + 4.dp space
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.offset(x = xOffset),
                    )
                }
            }
        }
    }
}

/** Select a Core-returned amount; never add kinds or convert money in chart code. */
private fun LedgerSummary.chartAmount(currency: String, incomeOnly: Boolean): BigDecimal? =
    if (currency == this.currency) {
        if (incomeOnly) income else expense
    } else
        perCurrency
            .find { it.currency == currency }
            ?.let { if (incomeOnly) it.income else it.expense }
