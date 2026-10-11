package io.github.hootkit_dev.hootbook.presentation.core.analytics

import io.github.hootkit_dev.hootbook.utils.DateFormats

import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.tween

import io.github.hootkit_dev.hootbook.utils.maxDoubleOrNull
import io.github.hootkit_dev.hootbook.utils.sumOfDouble
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hootkit.core.Category
import dev.hootkit.core.CategoryTotal
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
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
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import kotlin.math.abs

@Composable
fun SpendingLineChart(data: List<CoreDailySummary>, currency: String, typeFilters: Set<TransactionKind> = setOf(TransactionKind.EXPENSE)) =
    CorePeriodLineChart(data, { it.chartAmount(typeFilters) }, { it.date.javaDate().atStartOfDay() }, currency, typeFilters)

@Composable
fun BudgetHistoryLineChart(data: List<dev.hootkit.core.BudgetPeriodSummary>, currency: String, kind: dev.hootkit.core.BudgetKind) =
    CorePeriodLineChart(data, { it.spent }, { it.start.javaDate().atStartOfDay() }, currency,
        if (kind == dev.hootkit.core.BudgetKind.SAVINGS) setOf(TransactionKind.INCOME) else setOf(TransactionKind.EXPENSE))

@Composable
private fun <T> CorePeriodLineChart(
    data: List<T>,
    amount: (T) -> java.math.BigDecimal,
    date: (T) -> java.time.LocalDateTime,
    currency: String,
    typeFilters: Set<TransactionKind> = setOf(TransactionKind.EXPENSE)
) {
    if (data.isEmpty()) return
    val themeColors = MaterialTheme.colorScheme
    val suffixMillion = stringResource(R.string.suffix_million)
    val suffixThousand = stringResource(R.string.suffix_thousand)
    val totalLabel = stringResource(R.string.total)
    val filterLabelStrings = typeFilters.map { CoreKindLabel(it) }

    val cashFlowData = remember(data, typeFilters) { data.map { amount(it).toDouble() } }
    val labels = remember(data, typeFilters) {
        val isYearly = data.size > 1 && data.all { date(it).dayOfYear == 1 }
        val isMonthly = !isYearly && data.all { date(it).dayOfMonth == 1 }
        val spansMultipleYears = if (data.isNotEmpty()) {
            date(data.first()).year != date(data.last()).year
        } else false

        data.map {
            val date = date(it)
            when {
                isYearly -> date.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> date.format(DateFormats.shortYearMonthFormatter())
                isMonthly -> date.format(DateFormats.monthFormatter())
                else -> date.format(DateFormats.monthDayFormatter())
            }
        }.thinned()
    }

    val axisStart = indicatorWidth(cashFlowData.maxOrNull() ?: 0.0, currency, suffixMillion, suffixThousand)
    Column {
    LineChart(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = Spacing.md),
        data = listOf(
            Line(
                label = if (typeFilters.contains(TransactionKind.UNKNOWN) || typeFilters.size > 2) {
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
                dotProperties = DotProperties(
                    enabled = true,
                    color = SolidColor(themeColors.primary),
                    strokeWidth = 3.dp,
                    radius = 4.dp,
                    strokeColor = SolidColor(themeColors.surface)
                )
            )
        ),
        dividerProperties = DividerProperties(
            enabled = true,
            xAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            ),
            yAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            )
        ),
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurfaceVariant.copy(0.6f),
                textAlign = TextAlign.Center
            ),
            padding = IndicatorPadding,
            contentBuilder = { value -> formatPremiumCurrency(value, currency, suffixMillion, suffixThousand) }
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurface,
                textAlign = TextAlign.End
            ),
        ),
        labelProperties = LabelProperties(
            enabled = false,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurface.copy(0.6f),
                textAlign = TextAlign.End
            ),
            labels = labels,
            padding = 16.dp,
            rotation = LabelProperties.Rotation(
                mode = LabelProperties.Rotation.Mode.Force,
                degree = 0f
            )
        ),
        zeroLineProperties = ZeroLineProperties(
            enabled = true,
            style = StrokeStyle.Dashed(),
            color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
        ),
        gridProperties = GridProperties(
            enabled = true,
            xAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            ),
            yAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            )
        ),
        animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
        popupProperties = chartPopup(currency),
    )
    DateAxis(labels, axisStart)
    }
}

@Composable
fun SpendingBarChart(
    data: List<CoreDailySummary>,
    currency: String,
    typeFilters: Set<TransactionKind> = setOf(TransactionKind.EXPENSE)
) {
    if (data.isEmpty()) return
    val themeColors = MaterialTheme.colorScheme
    val suffixMillion = stringResource(R.string.suffix_million)
    val suffixThousand = stringResource(R.string.suffix_thousand)
    val totalLabel = stringResource(R.string.total)
    val filterLabelStrings = typeFilters.map { CoreKindLabel(it) }

    val columnData = remember(data, typeFilters) {
        val isYearly = data.size > 1 && data.all { it.date.javaDate().atStartOfDay().dayOfYear == 1 }
        val isMonthly = !isYearly && data.all { it.date.javaDate().atStartOfDay().dayOfMonth == 1 }
        val spansMultipleYears = if (data.isNotEmpty()) {
            data.first().date.javaDate().atStartOfDay().year != data.last().date.javaDate().atStartOfDay().year
        } else false

        data.map { point ->
            val label = when {
                isYearly -> point.date.javaDate().atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> point.date.javaDate().atStartOfDay().format(DateFormats.shortYearMonthFormatter())
                isMonthly -> point.date.javaDate().atStartOfDay().format(DateFormats.monthFormatter())
                else -> point.date.javaDate().atStartOfDay().format(DateFormats.monthDayFormatter())
            }
            Bars(
                label = label,
                values = listOf(
                    Bars.Data(
                        label = if (typeFilters.contains(TransactionKind.UNKNOWN) || typeFilters.size > 2) {
                            totalLabel
                        } else {
                            filterLabelStrings.joinToString(" & ")
                        },
                        value = point.chartAmount(typeFilters).toDouble(),
                        color = SolidColor(themeColors.primary.copy(alpha = 0.8f))
                    )
                )
            )
        }
    }

    val maxValue = remember(data, typeFilters) { (data.maxDoubleOrNull { point: CoreDailySummary -> point.chartAmount(typeFilters).toDouble() } ?: 0.0) * 1.2 }

    val axisStart = indicatorWidth(maxValue, currency, suffixMillion, suffixThousand)
    Column {
    ColumnChart(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = Spacing.md),
        data = columnData,
        maxValue = maxValue,
        barProperties = BarProperties(
            cornerRadius = Bars.Data.Radius.Rectangle(
                topLeft = 6.dp,
                topRight = 6.dp,
                bottomLeft = 6.dp,
                bottomRight = 6.dp
            ),
            spacing = 8.dp,
            thickness = 12.dp
        ),
        dividerProperties = DividerProperties(
            enabled = true,
            xAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            ),
            yAxisProperties = LineProperties(
                color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                thickness = 0.dp
            )
        ),
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurfaceVariant.copy(0.6f),
                textAlign = TextAlign.Center
            ),
            padding = IndicatorPadding,
            contentBuilder = { value -> formatPremiumCurrency(value, currency, suffixMillion, suffixThousand) }
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurface,
                textAlign = TextAlign.End
            ),
        ),
        labelProperties = LabelProperties(
            enabled = false,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = themeColors.onSurface.copy(0.6f),
                textAlign = TextAlign.End
            ),
            padding = 16.dp,
            rotation = LabelProperties.Rotation(
                mode = LabelProperties.Rotation.Mode.Force,
                degree = 0f
            )
        ),
        gridProperties = GridProperties(
            enabled = true,
            xAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            ),
            yAxisProperties = GridProperties.AxisProperties(
                enabled = true,
                style = StrokeStyle.Dashed(),
                color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
            )
        ),
        animationSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
        popupProperties = chartPopup(currency),
    )
    DateAxis(columnData.map { it.label }, axisStart)
    }
}

@Composable
fun CategoryPieChart(modifier: Modifier = Modifier, categories: List<CategoryTotal>, currency: String, references: List<Category> = emptyList()) =
    CoreCategoryPieChart(modifier, categories, { it.categoryId }, { it.total }, currency, references)

@Composable
fun BudgetCategoryPieChart(modifier: Modifier = Modifier, categories: List<dev.hootkit.core.CategorySpend>, currency: String, references: List<Category> = emptyList()) =
    CoreCategoryPieChart(modifier, categories, { it.categoryId }, { it.spent }, currency, references)

@Composable
private fun <T> CoreCategoryPieChart(
    modifier: Modifier = Modifier,
    categories: List<T>,
    categoryId: (T) -> String,
    amount: (T) -> java.math.BigDecimal,
    currency: String,
    references: List<Category> = emptyList()
) {
    if (categories.isEmpty()) return

    val total = categories.sumOfDouble { cat -> amount(cat).toDouble() }
    if (total == 0.0) return

    // Categories without a mapped color fall back to the scheme's neutral, not a fixed grey
    val fallbackColor = MaterialTheme.colorScheme.outline
    val pieData = remember(categories, references, fallbackColor) {
        categories.map { category ->
            Pie(
                label = references.find { it.id == categoryId(category) }?.displayName ?: categoryId(category),
                data = amount(category).toDouble(),
                color = references.find { it.id == categoryId(category) }?.let { runCatching { Color(android.graphics.Color.parseColor(it.color)) }.getOrNull() } ?: fallbackColor,
                selectedColor = (references.find { it.id == categoryId(category) }?.let { runCatching { Color(android.graphics.Color.parseColor(it.color)) }.getOrNull() }
                    ?: fallbackColor).copy(alpha = 0.8f),
                selected = false,
                scaleAnimEnterSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
                colorAnimEnterSpec = tween(500)
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(160.dp),
                data = chartData,
                onPieClick = { clickedPie ->
                    val pieIndex = chartData.indexOf(clickedPie)
                    chartData = chartData.mapIndexed { index, pie ->
                        pie.copy(selected = index == pieIndex)
                    }
                },
                selectedScale = 1.1f,
                scaleAnimEnterSpec = tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing),
                colorAnimEnterSpec = tween(500),
                style = Pie.Style.Stroke(width = 12.dp)
            )

            // Center Icon Overlay
            val selectedPie = chartData.find { it.selected }
            BlurredAnimatedVisibility(
                visible = selectedPie != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() +scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (selectedPie != null && selectedPie.label != null) {
                    CoreRecordIcon(
                        name = references.find { it.displayName == selectedPie.label }?.icon.orEmpty(),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(selectedPie.color.copy(alpha = 0.2f))
                            .padding(12.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Spacing.md)
        ) {
            items(chartData.sortedByDescending { it.data }) { pie ->
                LegendItem(
                    label = pie.label.orEmpty(),
                    value = pie.data,
                    color = pie.color,
                    isSelected = pie.selected,
                    currency = currency,
                    totalAmount = total,
                    onClick = {
                        val pieIndex = chartData.indexOf(pie)
                        chartData = chartData.mapIndexed { index, p ->
                            p.copy(selected = index == pieIndex)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LegendItem(
    label: String,
    value: Double,
    color: Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: () -> Unit
) {
    val percentage = (value / totalAmount * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
            .padding(6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatPremiumCurrency(value, currency, stringResource(R.string.suffix_million), stringResource(R.string.suffix_thousand)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.percentage_format, percentage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SpendingHeatmap(
    data: List<CoreDailySummary>,
    modifier: Modifier = Modifier,
    typeFilters: Set<TransactionKind> = setOf(TransactionKind.EXPENSE)
) {
    if (data.isEmpty()) return

    val maxAmount = remember(data, typeFilters) { data.maxDoubleOrNull { point: CoreDailySummary -> point.chartAmount(typeFilters).toDouble() } ?: 1.0 }
    val groupedData = remember(data, typeFilters) {
        data.associate { it.date.javaDate().atStartOfDay().toLocalDate() to it.chartAmount(typeFilters).toDouble() }
    }

    val sortedDates = remember(data, typeFilters) { data.map { it.date.javaDate().atStartOfDay().toLocalDate() }.distinct().sorted() }
    val startDate = sortedDates.first().with(java.time.DayOfWeek.MONDAY)
    val endDate = sortedDates.last()

    val totalWeeks = ChronoUnit.WEEKS.between(startDate, endDate.plusDays(1)).toInt() + 1

    val monthLabels = remember(startDate, endDate) {
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
    LaunchedEffect(data) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.horizontalScroll(scrollState)
        ) {
            // Heatmap Grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                for (w in 0 until totalWeeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until 7) {
                            val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                            val amount = groupedData[date] ?: 0.0
                            val intensity = if (maxAmount > 0) (amount / maxAmount).toFloat().coerceIn(0f, 1f) else 0f

                            val primary = MaterialTheme.colorScheme.primary
                            val color = when {
                                date > endDate -> MaterialTheme.colorScheme.surfaceContainerHigh
                                amount == 0.0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                intensity < 0.25f -> primary.copy(alpha = 0.25f)
                                intensity < 0.5f -> primary.copy(alpha = 0.5f)
                                intensity < 0.75f -> primary.copy(alpha = 0.75f)
                                else -> primary
                            }

                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
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

                        modifier = Modifier.offset(x = xOffset)
                    )
                }
            }
        }
    }
}

fun formatPremiumCurrency(value: Double, currency: String, suffixMillion: String, suffixThousand: String): String {
    val absValue = abs(value)
    val symbol = CurrencyFormatter.getCurrencySymbol(currency)

    return when {
        absValue >= 1_000_000 -> {
            val millions = absValue / 1_000_000
            val suffix = suffixMillion
            "${symbol}${String.format("%.1f", millions)}${suffix}"
        }
        absValue >= 1_000 -> {
            val thousands = absValue / 1_000
            val suffix = suffixThousand
            "${symbol}${String.format("%.1f", thousands)}${suffix}"
        }
        else -> "${symbol}${absValue.toInt()}"
    }
}

/** Selects a Core amount for drawing only; it does not aggregate, convert, or mutate money. */
internal fun CoreDailySummary.chartAmount(kinds: Set<TransactionKind>) =
    if (kinds == setOf(TransactionKind.INCOME)) summary.income else summary.expense

/**
 * At most six date labels, picked evenly from first to last. The chart gives every label a slot
 * as wide as the widest and spreads the slots edge to edge, so a label per point would overflow.
 */
private fun List<String>.thinned(max: Int = 6): List<String> =
    if (size <= max) this else (0 until max).map { this[Math.round(it * (size - 1) / (max - 1).toFloat())] }

/** The value under a tapped point, as money in the chart's currency. */
@Composable
private fun chartPopup(currency: String) = ir.ehsannarmani.compose_charts.models.PopupProperties(
    textStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface),
    containerColor = MaterialTheme.colorScheme.inverseSurface,
    contentBuilder = { popup ->
        io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation.format(
            java.math.BigDecimal.valueOf(popup.value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros(), currency)
    },
)

private val IndicatorPadding = 12.dp

/** Width the chart gives its value labels: the widest of zero and the top value, plus their padding. */
@Composable
private fun indicatorWidth(max: Double, currency: String, million: String, thousand: String): androidx.compose.ui.unit.Dp {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widest = listOf(max, 0.0).maxOf { measurer.measure(formatPremiumCurrency(it, currency, million, thousand), style).size.width }
    return with(density) { widest.toDp() } + IndicatorPadding
}

/**
 * The dates under a chart: up to six, from its first point to its last. Drawn here rather than by
 * the chart library, which packs one label per point from the left instead of under each point.
 */
@Composable
private fun DateAxis(labels: List<String>, start: androidx.compose.ui.unit.Dp) {
    val shown = labels.thinned()
    Row(Modifier.fillMaxWidth().padding(start = start, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        shown.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}
